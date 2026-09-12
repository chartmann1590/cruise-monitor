# AI message reporting + Cloudflare Worker + weekly GitHub sync — design spec

Date: 2026-09-12
Status: approved for planning

## Summary

Lets a user flag a bad response from the on-device AI refund assistant. The flagged message pair (the user's
question and the AI's response), a short reason, and minimal app metadata are sent to a new Cloudflare Worker
and stored in Cloudflare D1. A weekly Cron Trigger on the same Worker reviews unsynced reports and opens one
GitHub Issue per distinct flagged message in this repo (`chartmann1590/cruise-monitor`) — creating nothing if
no reports came in that week. This is the one intentional exception to the AI assistant's otherwise fully
on-device, nothing-ever-sent design, and is opt-in per message.

## Background

The AI refund assistant (`app/src/main/java/com/cruisewatch/app/ai/`) runs entirely on-device via MediaPipe
(`LlmChatEngine`), driven by `AssistantViewModel` and rendered by `AssistantScreen.kt`'s `ChatBubble`/`ChatView`
composables. `ChatMessage(val fromUser: Boolean, val text: String)` is the only message model today — no id,
no timestamp. The app's privacy policy (`website/privacy.html`) currently states the assistant's questions and
answers are "never sent to CruiseWatch, Google, or any third party." This feature is a deliberate, narrow,
user-initiated exception to that promise, and the privacy policy must be updated alongside it.

There is no existing Cloudflare presence in this repo, and no existing mechanism for surfacing AI quality
issues to the developer other than manual testing.

## Goals

- A user can flag a specific AI response as bad, with a reason, directly from the chat screen.
- Only the flagged exchange (not the full conversation, not account/cruise data) is ever sent.
- Reports land in a Cloudflare Worker + D1 database for free, with basic abuse protection.
- Once a week, any new reports become GitHub Issues in this repo — one per distinct problem, deduplicating
  exact repeats — and weeks with zero reports create nothing.
- The privacy policy accurately discloses this new, narrow data flow.

## Non-goals

- Reporting anything other than the on-device AI assistant's messages (e.g. no "report a bug" for the rest of
  the app).
- Real-time/immediate GitHub issue creation — sync is weekly by design.
- User accounts, authentication, or any way to look up a report after submitting it (fire-and-forget).
- Sophisticated duplicate-detection (fuzzy matching, embeddings) — exact-text dedupe only.
- Rich abuse protection (CAPTCHA, Turnstile) — a simple per-IP rate limit is sufficient for this app's scale.

## Architecture

### 1. In-app reporting UI

`ChatBubble` (in `AssistantScreen.kt`) gains a small flag icon, shown only on AI messages (`!message.fromUser`).
`ChatView`'s `items(messages)` becomes `itemsIndexed(messages)` so each bubble knows its own index (used to
find the preceding user message and to track local "already reported" state — `messages` is append-only within
a session, so index is a stable-enough key here). Tapping the flag opens a `ModalBottomSheet` with:

- A reason list (single-select, matching Android's usual radio-button or `FilterChip` group):
  `assistant_report_reason_inaccurate` ("Inaccurate or wrong information"),
  `assistant_report_reason_unsafe` ("Unsafe or harmful advice"),
  `assistant_report_reason_offtopic` ("Off-topic or ignored my question"),
  `assistant_report_reason_other` ("Other") — selecting "Other" reveals an optional single-line text field.
- A Submit button, disabled until a reason is selected.

On submit, `AssistantViewModel` (or a new small `AiReportRepository`) POSTs to the Worker (see below) on a
background coroutine, fire-and-forget — the UI doesn't block on the network call succeeding; a `runCatching`
swallows failures (matching the existing pattern for the assistant's own model calls) so a flaky network never
blocks the chat UI. The flag icon becomes a filled/checked "Reported" state for that message for the rest of
the session (local-only, not persisted across app restarts — re-reporting after a restart is harmless since
the Worker dedupes by exact message text anyway).

### 2. Report payload

```json
{
  "userMessage": "string — the message that triggered the flagged response",
  "aiMessage": "string — the flagged AI response",
  "reasonCategory": "inaccurate | unsafe | offtopic | other",
  "reasonDetail": "string | null — optional free text, only when reasonCategory is 'other'",
  "appVersion": "string — e.g. '0.2.0', from BuildConfig.VERSION_NAME"
}
```

No device id, account id, advertising id, or any other cruise/account data is included. The Worker itself
notes the request's source IP only transiently, for the rate-limit check (never stored alongside the report
row — see Data flow below).

### 3. Cloudflare Worker

New top-level `cloudflare/ai-reports-worker/` directory: `wrangler.jsonc`, `src/index.ts`, a D1 migration file,
`package.json`. Uses `compatibility_date` set to the date this is implemented, `nodejs_compat` enabled per the
Workers best-practices skill's guidance.

**D1 schema** (binding name `DB`):

```sql
CREATE TABLE reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at TEXT NOT NULL,           -- ISO 8601, set by the Worker (crypto/Date, not client-supplied)
  user_message TEXT NOT NULL,
  ai_message TEXT NOT NULL,
  reason_category TEXT NOT NULL,      -- 'inaccurate' | 'unsafe' | 'offtopic' | 'other'
  reason_detail TEXT,
  app_version TEXT,
  synced_to_github INTEGER NOT NULL DEFAULT 0,
  github_issue_number INTEGER
);

CREATE TABLE rate_limits (
  ip_hash TEXT PRIMARY KEY,           -- SHA-256 of the request IP, never the raw IP
  window_start TEXT NOT NULL,         -- ISO 8601 start of the current hour window
  count INTEGER NOT NULL DEFAULT 0
);
```

**`POST /report`:**

1. Parse and validate the JSON body (required string fields non-empty and under a generous length cap — e.g.
   4000 chars each — to bound storage and issue size; `reasonCategory` must be one of the four allowed values;
   reject with `400` otherwise).
2. Rate limit: hash the request's `CF-Connecting-IP` header with SHA-256 (`crypto.subtle.digest`, never store
   the raw IP — this hash is not reversible and isn't linked to anything else). Look up/insert its
   `rate_limits` row for the current hour window; if `count >= 5`, respond `429`. Otherwise increment and
   proceed.
3. Insert the report row (`created_at` from `new Date().toISOString()`, server-side).
4. Respond `201` with no body (the app doesn't need anything back).

Uses the D1 binding directly (no REST API calls for D1, per best practice). All promises are awaited; no
module-level mutable state; errors return a structured `500` JSON body rather than relying on
`passThroughOnException`.

**Weekly Cron Trigger** (`wrangler.jsonc` `triggers.crons`, e.g. `"0 9 * * 1"` — Monday 09:00 UTC):

1. Query `SELECT * FROM reports WHERE synced_to_github = 0`.
2. If empty, exit — no GitHub API call, no issue created.
3. Group by exact `ai_message` text (`Map<string, Report[]>`) — each group is one "distinct problem."
4. For each group, `POST` to `https://api.github.com/repos/chartmann1590/cruise-monitor/issues` (GitHub REST
   API directly — this is an external third-party API, not a Cloudflare binding, so the "bindings over REST"
   rule doesn't apply here) using a `GITHUB_TOKEN` secret (a fine-grained PAT scoped to Issues:write on this
   repo only, set via `wrangler secret put`), with:
   - Title: `AI report: ` + reason category + a truncated (first ~80 chars) preview of `ai_message`.
   - Body: the full user message, the full AI message (in a fenced code block so any Markdown in the AI's own
     output can't break issue formatting), the reason category + detail, the count of reports in this group,
     and the date range covered.
   - Label: `ai-report` (created once, reused).
5. On a successful GitHub response, update every report row in that group: `synced_to_github = 1`,
   `github_issue_number = <the new issue's number>`.
6. If the GitHub API call for one group fails, log it and continue to the next group — one failure shouldn't
   block the rest of that week's sync; that group's rows stay unsynced and are retried next week.

### 4. Privacy policy update

`website/privacy.html` gains a new subsection (alongside the existing "On-device AI refund assistant"
paragraph): explains that reporting a message is optional and user-initiated, sends only that one exchange
plus the reason (no account or cruise data), is stored in Cloudflare D1, and may be copied into a GitHub issue
in the developer's private repository for tracking — distinct from the assistant's normal fully-offline
operation. Section 3's "Who we share data with" table gains a row: `AI message reports (opt-in)` → `Cloudflare
D1` → `Stores messages you explicitly flag as bad, to help fix the AI assistant`.

## Error handling

- App: a failed report POST fails silently (fire-and-forget, `runCatching`) — the user still sees the
  "Reported" confirmation locally, since forcing them to retry a background telemetry call is worse UX than an
  occasional lost report.
- Worker `/report`: malformed input → `400`; rate-limited → `429`; unexpected error → `500` with a generic
  JSON error body (no stack traces leaked).
- Weekly sync: per-group try/catch around the GitHub API call, as above — one bad group never blocks the rest.

## Testing

- Worker: unit tests (via `vitest` + `@cloudflare/vitest-pool-workers`, the standard Workers testing setup) for
  `/report`'s validation, rate-limiting logic, and the sync job's dedupe-and-issue-creation logic (with a faked
  GitHub API call).
- App: no new Compose tests (matching this codebase's existing convention of no Compose test infra) — manual
  on-device verification that the flag icon appears only on AI messages, the reason sheet works, and a
  successful report doesn't disrupt the chat.

## Files touched (expected)

- New: `cloudflare/ai-reports-worker/wrangler.jsonc`, `cloudflare/ai-reports-worker/src/index.ts`,
  `cloudflare/ai-reports-worker/migrations/0001_init.sql`, `cloudflare/ai-reports-worker/package.json`, Worker
  test files.
- New (app): `app/src/main/java/com/cruisewatch/app/ai/AiReportRepository.kt` (or similar — the HTTP client for
  the report endpoint).
- Modified: `AssistantScreen.kt` (flag icon + reason bottom sheet), `strings.xml` (new report-related strings,
  translated like everything else), `website/privacy.html` (new disclosure).
