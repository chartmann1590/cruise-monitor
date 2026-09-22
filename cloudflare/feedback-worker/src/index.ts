export interface Env {
  GITHUB_TOKEN: string;
  GITHUB_REPO_OWNER: string;
  GITHUB_REPO_NAME: string;
  FEEDBACK_ASSETS_DIR: string;
}

const TITLE_MAX = 200;
const ISSUE_BODY_MAX = 50 * 1024;
const COMMENT_BODY_MAX = 25 * 1024;
const ASSET_BASE64_MAX = Math.floor((8 * 1024 * 1024 * 4) / 3) + 1024; // ~8MB binary
const REQUEST_BODY_MAX = 12 * 1024 * 1024;

const ALLOWED_EXTENSIONS = new Set(["png", "jpg", "jpeg", "webp"]);
const RATE_LIMIT_PER_MINUTE = 20;

// Simple in-memory per-isolate rate limiter (best-effort; Cloudflare may
// add first-party rate limiting in front of this Worker separately).
const rateBuckets = new Map<string, { windowStart: number; count: number }>();

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function safeError(message: string): Response {
  return jsonResponse({ error: message }, statusFor(message));
}

function statusFor(_message: string): number {
  return 500;
}

function checkRateLimit(ip: string): boolean {
  const now = Date.now();
  const windowStart = Math.floor(now / 60000) * 60000;
  const entry = rateBuckets.get(ip);
  if (!entry || entry.windowStart !== windowStart) {
    rateBuckets.set(ip, { windowStart, count: 1 });
    return true;
  }
  if (entry.count >= RATE_LIMIT_PER_MINUTE) return false;
  entry.count += 1;
  return true;
}

function isValidIssueNumber(value: string): boolean {
  return /^\d{1,8}$/.test(value) && Number(value) >= 1;
}

function sanitizeFileName(raw: string): string | null {
  const base = raw.split("/").pop()?.split("\\").pop() ?? "";
  const cleaned = base.replace(/[^a-zA-Z0-9._-]/g, "-").replace(/-+/g, "-").slice(0, 128);
  if (!cleaned || cleaned === "." || cleaned === "..") return null;
  const dot = cleaned.lastIndexOf(".");
  if (dot <= 0 || dot === cleaned.length - 1) return null;
  const ext = cleaned.slice(dot + 1).toLowerCase();
  if (!ALLOWED_EXTENSIONS.has(ext)) return null;
  return cleaned;
}

async function githubRequest(env: Env, path: string, init?: RequestInit): Promise<Response> {
  return fetch(`https://api.github.com${path}`, {
    ...init,
    headers: {
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      "User-Agent": "CruiseWatch-Feedback-Worker",
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
}

function repoPath(env: Env): string {
  return `/repos/${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}`;
}

function repoConfigured(env: Env): boolean {
  return Boolean(env.GITHUB_REPO_OWNER) && Boolean(env.GITHUB_REPO_NAME);
}

async function readJsonWithLimits(request: Request): Promise<{ ok: true; value: unknown } | { ok: false; response: Response }> {
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) {
    return { ok: false, response: jsonResponse({ error: "Content-Type must be application/json." }, 400) };
  }
  const contentLength = request.headers.get("content-length");
  if (contentLength && Number(contentLength) > REQUEST_BODY_MAX) {
    return { ok: false, response: jsonResponse({ error: "Request too large." }, 413) };
  }
  let text: string;
  try {
    text = await request.text();
  } catch {
    return { ok: false, response: jsonResponse({ error: "Unable to read request." }, 400) };
  }
  if (text.length > REQUEST_BODY_MAX) {
    return { ok: false, response: jsonResponse({ error: "Request too large." }, 413) };
  }
  try {
    return { ok: true, value: text.length === 0 ? {} : JSON.parse(text) };
  } catch {
    return { ok: false, response: jsonResponse({ error: "Invalid JSON body." }, 400) };
  }
}

function normalizeIssue(issue: Record<string, unknown>): Record<string, unknown> {
  return {
    number: issue["number"],
    title: issue["title"],
    state: issue["state"],
    htmlUrl: issue["html_url"],
    createdAt: issue["created_at"],
    body: issue["body"] ?? null,
  };
}

function normalizeComment(comment: Record<string, unknown>): Record<string, unknown> {
  const user = (comment["user"] as Record<string, unknown> | null) ?? null;
  return {
    id: comment["id"],
    body: comment["body"],
    createdAt: comment["created_at"],
    user: { login: user?.["login"] ?? "unknown" },
  };
}

async function handleHealth(env: Env): Promise<Response> {
  return jsonResponse(
    {
      ok: true,
      service: "feedback-api",
      githubRepositoryConfigured: repoConfigured(env),
    },
    200,
  );
}

async function handleCreateIssue(request: Request, env: Env): Promise<Response> {
  const parsed = await readJsonWithLimits(request);
  if (!parsed.ok) return parsed.response;
  const body = parsed.value as Record<string, unknown>;
  const title = typeof body["title"] === "string" ? (body["title"] as string).trim() : "";
  const issueBody = typeof body["body"] === "string" ? (body["body"] as string) : "";

  if (!title || !issueBody.trim()) {
    return jsonResponse({ error: "Title and body are required." }, 400);
  }
  if (title.length > TITLE_MAX) {
    return jsonResponse({ error: "Title is too long." }, 400);
  }
  if (issueBody.length > ISSUE_BODY_MAX) {
    return jsonResponse({ error: "Body is too large." }, 413);
  }

  const gh = await githubRequest(env, `${repoPath(env)}/issues`, {
    method: "POST",
    body: JSON.stringify({ title, body: issueBody }),
  });
  if (!gh.ok) {
    return jsonResponse({ error: "Unable to create issue." }, 502);
  }
  const issue = (await gh.json()) as Record<string, unknown>;
  return jsonResponse(
    {
      number: issue["number"],
      title: issue["title"],
      state: issue["state"],
      htmlUrl: issue["html_url"],
      createdAt: issue["created_at"],
    },
    201,
  );
}

async function handleGetIssue(number: string, env: Env): Promise<Response> {
  if (!isValidIssueNumber(number)) {
    return jsonResponse({ error: "Invalid issue number." }, 400);
  }
  const gh = await githubRequest(env, `${repoPath(env)}/issues/${number}`, { method: "GET" });
  if (gh.status === 404) return jsonResponse({ error: "Issue not found." }, 404);
  if (!gh.ok) return jsonResponse({ error: "Unable to fetch issue." }, 502);
  const issue = (await gh.json()) as Record<string, unknown>;
  return jsonResponse(normalizeIssue(issue), 200);
}

async function handleGetComments(number: string, env: Env): Promise<Response> {
  if (!isValidIssueNumber(number)) {
    return jsonResponse({ error: "Invalid issue number." }, 400);
  }
  const gh = await githubRequest(env, `${repoPath(env)}/issues/${number}/comments?per_page=100`, {
    method: "GET",
  });
  if (gh.status === 404) return jsonResponse({ error: "Issue not found." }, 404);
  if (!gh.ok) return jsonResponse({ error: "Unable to fetch comments." }, 502);
  const comments = (await gh.json()) as Record<string, unknown>[];
  const normalized = Array.isArray(comments) ? comments.map(normalizeComment) : [];
  return jsonResponse(normalized, 200);
}

async function handlePostComment(request: Request, number: string, env: Env): Promise<Response> {
  if (!isValidIssueNumber(number)) {
    return jsonResponse({ error: "Invalid issue number." }, 400);
  }
  const parsed = await readJsonWithLimits(request);
  if (!parsed.ok) return parsed.response;
  const body = parsed.value as Record<string, unknown>;
  const commentBody = typeof body["body"] === "string" ? body["body"] as string : "";
  if (!commentBody.trim()) {
    return jsonResponse({ error: "Comment body is required." }, 400);
  }
  if (commentBody.length > COMMENT_BODY_MAX) {
    return jsonResponse({ error: "Comment is too large." }, 413);
  }
  const gh = await githubRequest(env, `${repoPath(env)}/issues/${number}/comments`, {
    method: "POST",
    body: JSON.stringify({ body: commentBody }),
  });
  if (gh.status === 404) return jsonResponse({ error: "Issue not found." }, 404);
  if (!gh.ok) return jsonResponse({ error: "Unable to post comment." }, 502);
  const comment = (await gh.json()) as Record<string, unknown>;
  return jsonResponse(normalizeComment(comment), 201);
}

function isBase64(value: string): boolean {
  if (value.length === 0 || value.length % 4 !== 0) return false;
  return /^[A-Za-z0-9+/]*={0,2}$/.test(value);
}

async function handleUploadAsset(request: Request, env: Env): Promise<Response> {
  const parsed = await readJsonWithLimits(request);
  if (!parsed.ok) return parsed.response;
  const body = parsed.value as Record<string, unknown>;
  const fileName = typeof body["fileName"] === "string" ? body["fileName"] as string : "";
  const contentBase64 = typeof body["contentBase64"] === "string" ? (body["contentBase64"] as string).replace(/\s+/g, "") : "";

  const safeName = sanitizeFileName(fileName);
  if (!safeName) {
    return jsonResponse({ error: "Invalid file name or extension." }, 400);
  }
  if (!contentBase64 || contentBase64.length > ASSET_BASE64_MAX || !isBase64(contentBase64)) {
    return jsonResponse({ error: "Invalid or too large image payload." }, contentBase64.length > ASSET_BASE64_MAX ? 413 : 400);
  }

  const assetsDir = (env.FEEDBACK_ASSETS_DIR || "feedback-assets").replace(/[^a-zA-Z0-9_-]/g, "") || "feedback-assets";
  const ghPath = `${repoPath(env)}/contents/${assetsDir}/${safeName}`;
  const gh = await githubRequest(env, ghPath, {
    method: "PUT",
    body: JSON.stringify({
      message: `Upload feedback attachment ${safeName}`,
      content: contentBase64,
    }),
  });
  if (!gh.ok) {
    return jsonResponse({ error: "Unable to upload attachment." }, 502);
  }
  const result = (await gh.json()) as { content?: { download_url?: string; html_url?: string } };
  return jsonResponse(
    {
      downloadUrl: result.content?.download_url ?? null,
      htmlUrl: result.content?.html_url ?? null,
    },
    201,
  );
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    try {
      if (path === "/health" && request.method === "GET") {
        return await handleHealth(env);
      }

      if (!repoConfigured(env) || !env.GITHUB_TOKEN) {
        return jsonResponse({ error: "Feedback service is not configured." }, 500);
      }

      const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
      if (!checkRateLimit(ip)) {
        return jsonResponse({ error: "Too many requests." }, 429);
      }

      if (path === "/api/issues" && request.method === "POST") {
        return await handleCreateIssue(request, env);
      }
      if (path === "/api/issues" || path.startsWith("/api/issues/")) {
        const match = path.match(/^\/api\/issues\/(\d+)(\/comments)?$/);
        if (match) {
          const number = match[1];
          const isComments = Boolean(match[2]);
          if (!isComments && request.method === "GET") return await handleGetIssue(number, env);
          if (isComments && request.method === "GET") return await handleGetComments(number, env);
          if (isComments && request.method === "POST") return await handlePostComment(request, number, env);
        } else if (path.startsWith("/api/issues/")) {
          return jsonResponse({ error: "Invalid issue number." }, 400);
        }
        if (path === "/api/issues") {
          return jsonResponse({ error: "Method not allowed." }, 405);
        }
        return jsonResponse({ error: "Method not allowed." }, 405);
      }

      if (path === "/api/assets" && request.method === "POST") {
        return await handleUploadAsset(request, env);
      }
      if (path === "/api/assets") {
        return jsonResponse({ error: "Method not allowed." }, 405);
      }

      return jsonResponse({ error: "Not found." }, 404);
    } catch {
      return jsonResponse({ error: "Internal server error." }, 500);
    }
  },
};
