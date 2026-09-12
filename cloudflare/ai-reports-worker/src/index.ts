export interface Env {
	DB: D1Database;
	GITHUB_TOKEN: string;
}

const MAX_FIELD_LENGTH = 4000;
const RATE_LIMIT_PER_HOUR = 5;
const ALLOWED_REASONS = new Set(['inaccurate', 'unsafe', 'offtopic', 'other']);
const GITHUB_REPO = 'chartmann1590/cruise-monitor';
const GITHUB_LABEL = 'ai-report';

interface ReportPayload {
	userMessage: string;
	aiMessage: string;
	reasonCategory: string;
	reasonDetail?: string | null;
	appVersion?: string | null;
}

function jsonResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' },
	});
}

function isNonEmptyString(value: unknown, maxLength: number): value is string {
	return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

function validatePayload(body: unknown): ReportPayload | null {
	if (typeof body !== 'object' || body === null) return null;
	const candidate = body as Record<string, unknown>;

	if (!isNonEmptyString(candidate.userMessage, MAX_FIELD_LENGTH)) return null;
	if (!isNonEmptyString(candidate.aiMessage, MAX_FIELD_LENGTH)) return null;
	if (typeof candidate.reasonCategory !== 'string' || !ALLOWED_REASONS.has(candidate.reasonCategory)) return null;

	const reasonDetail = candidate.reasonDetail;
	if (reasonDetail != null && (typeof reasonDetail !== 'string' || reasonDetail.length > MAX_FIELD_LENGTH)) {
		return null;
	}

	const appVersion = candidate.appVersion;
	if (appVersion != null && (typeof appVersion !== 'string' || appVersion.length > 64)) {
		return null;
	}

	return {
		userMessage: candidate.userMessage as string,
		aiMessage: candidate.aiMessage as string,
		reasonCategory: candidate.reasonCategory,
		reasonDetail: (reasonDetail as string | null | undefined) ?? null,
		appVersion: (appVersion as string | null | undefined) ?? null,
	};
}

async function hashIp(ip: string): Promise<string> {
	const data = new TextEncoder().encode(ip);
	const digest = await crypto.subtle.digest('SHA-256', data);
	return Array.from(new Uint8Array(digest))
		.map((b) => b.toString(16).padStart(2, '0'))
		.join('');
}

function currentHourWindow(): string {
	const now = new Date();
	return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), now.getUTCHours())).toISOString();
}

/** Returns true if the request should be allowed (under the per-hour limit for this IP). */
async function checkAndIncrementRateLimit(db: D1Database, ipHash: string): Promise<boolean> {
	const window = currentHourWindow();
	const existing = await db
		.prepare('SELECT window_start, count FROM rate_limits WHERE ip_hash = ?')
		.bind(ipHash)
		.first<{ window_start: string; count: number }>();

	if (!existing || existing.window_start !== window) {
		await db
			.prepare('INSERT INTO rate_limits (ip_hash, window_start, count) VALUES (?, ?, 1) ' + 'ON CONFLICT(ip_hash) DO UPDATE SET window_start = excluded.window_start, count = 1')
			.bind(ipHash, window)
			.run();
		return true;
	}

	if (existing.count >= RATE_LIMIT_PER_HOUR) {
		return false;
	}

	await db.prepare('UPDATE rate_limits SET count = count + 1 WHERE ip_hash = ?').bind(ipHash).run();
	return true;
}

async function handleReport(request: Request, env: Env): Promise<Response> {
	let body: unknown;
	try {
		body = await request.json();
	} catch {
		return jsonResponse({ error: 'Invalid JSON body' }, 400);
	}

	const payload = validatePayload(body);
	if (!payload) {
		return jsonResponse({ error: 'Invalid report payload' }, 400);
	}

	const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown';
	const ipHash = await hashIp(ip);

	const allowed = await checkAndIncrementRateLimit(env.DB, ipHash);
	if (!allowed) {
		return jsonResponse({ error: 'Rate limit exceeded' }, 429);
	}

	await env.DB.prepare(
		`INSERT INTO reports (created_at, user_message, ai_message, reason_category, reason_detail, app_version)
		 VALUES (?, ?, ?, ?, ?, ?)`,
	)
		.bind(new Date().toISOString(), payload.userMessage, payload.aiMessage, payload.reasonCategory, payload.reasonDetail, payload.appVersion)
		.run();

	return jsonResponse({ status: 'received' }, 201);
}

interface ReportRow {
	id: number;
	created_at: string;
	user_message: string;
	ai_message: string;
	reason_category: string;
	reason_detail: string | null;
	app_version: string | null;
}

function groupByAiMessage(rows: ReportRow[]): Map<string, ReportRow[]> {
	const groups = new Map<string, ReportRow[]>();
	for (const row of rows) {
		const existing = groups.get(row.ai_message);
		if (existing) {
			existing.push(row);
		} else {
			groups.set(row.ai_message, [row]);
		}
	}
	return groups;
}

function buildIssueBody(group: ReportRow[]): { title: string; body: string } {
	const first = group[0];
	const preview = first.ai_message.length > 80 ? `${first.ai_message.slice(0, 80)}…` : first.ai_message;
	const reasons = Array.from(new Set(group.map((r) => r.reason_category))).join(', ');
	const dates = group.map((r) => r.created_at).sort();
	const details = group
		.map((r) => r.reason_detail)
		.filter((d): d is string => !!d)
		.map((d) => `- ${d}`)
		.join('\n');

	const title = `AI report (${first.reason_category}): ${preview}`;
	const body = [
		`**Reason(s):** ${reasons}`,
		`**Reports:** ${group.length} (${dates[0]} → ${dates[dates.length - 1]})`,
		'',
		'**User message:**',
		'```',
		first.user_message,
		'```',
		'',
		'**AI response:**',
		'```',
		first.ai_message,
		'```',
		details ? `\n**Additional detail from reporters:**\n${details}` : '',
	].join('\n');

	return { title, body };
}

async function ensureLabelExists(env: Env): Promise<void> {
	await githubFetch(env, `/repos/${GITHUB_REPO}/labels`, {
		method: 'POST',
		body: JSON.stringify({ name: GITHUB_LABEL, color: 'd93f0b', description: 'Flagged AI assistant response' }),
	}).catch(() => {
		// Label likely already exists — creation is best-effort, not required for issue creation to succeed.
	});
}

async function githubFetch(env: Env, path: string, init: RequestInit): Promise<Response> {
	return fetch(`https://api.github.com${path}`, {
		...init,
		headers: {
			Authorization: `Bearer ${env.GITHUB_TOKEN}`,
			Accept: 'application/vnd.github+json',
			'User-Agent': 'cruisewatch-ai-reports-worker',
			'content-type': 'application/json',
			...(init.headers ?? {}),
		},
	});
}

export async function runWeeklySync(env: Env): Promise<{ groupsSynced: number; groupsFailed: number }> {
	const { results } = await env.DB.prepare('SELECT * FROM reports WHERE synced_to_github = 0').all<ReportRow>();

	if (!results || results.length === 0) {
		return { groupsSynced: 0, groupsFailed: 0 };
	}

	await ensureLabelExists(env);

	const groups = groupByAiMessage(results);
	let groupsSynced = 0;
	let groupsFailed = 0;

	for (const group of groups.values()) {
		try {
			const { title, body } = buildIssueBody(group);
			const response = await githubFetch(env, `/repos/${GITHUB_REPO}/issues`, {
				method: 'POST',
				body: JSON.stringify({ title, body, labels: [GITHUB_LABEL] }),
			});

			if (!response.ok) {
				groupsFailed++;
				continue;
			}

			const issue = (await response.json()) as { number: number };
			const ids = group.map((r) => r.id);
			const placeholders = ids.map(() => '?').join(',');
			await env.DB.prepare(`UPDATE reports SET synced_to_github = 1, github_issue_number = ? WHERE id IN (${placeholders})`)
				.bind(issue.number, ...ids)
				.run();
			groupsSynced++;
		} catch {
			groupsFailed++;
		}
	}

	return { groupsSynced, groupsFailed };
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);

		if (request.method === 'POST' && url.pathname === '/report') {
			try {
				return await handleReport(request, env);
			} catch {
				return jsonResponse({ error: 'Internal error' }, 500);
			}
		}

		return jsonResponse({ error: 'Not found' }, 404);
	},

	async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
		ctx.waitUntil(runWeeklySync(env).then(() => undefined));
	},
};
