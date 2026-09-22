import { env, applyD1Migrations, createExecutionContext, waitOnExecutionContext } from 'cloudflare:test';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker, { runWeeklySync } from '../src/index';

declare module 'cloudflare:test' {
	interface ProvidedEnv {
		DB: D1Database;
		GITHUB_TOKEN: string;
	}
}

beforeEach(async () => {
	await applyD1Migrations(env.DB, [
		{
			name: '0001_init.sql',
			queries: [
				`CREATE TABLE IF NOT EXISTS reports (
					id INTEGER PRIMARY KEY AUTOINCREMENT,
					created_at TEXT NOT NULL,
					user_message TEXT NOT NULL,
					ai_message TEXT NOT NULL,
					reason_category TEXT NOT NULL,
					reason_detail TEXT,
					app_version TEXT,
					synced_to_github INTEGER NOT NULL DEFAULT 0,
					github_issue_number INTEGER
				)`,
				`CREATE TABLE IF NOT EXISTS rate_limits (
					ip_hash TEXT PRIMARY KEY,
					window_start TEXT NOT NULL,
					count INTEGER NOT NULL DEFAULT 0
				)`,
			],
		},
	]);
	await env.DB.exec('DELETE FROM reports');
	await env.DB.exec('DELETE FROM rate_limits');
});

function makeRequest(body: unknown, ip = '1.2.3.4'): Request {
	return new Request('https://example.com/report', {
		method: 'POST',
		headers: { 'content-type': 'application/json', 'CF-Connecting-IP': ip },
		body: JSON.stringify(body),
	});
}

describe('POST /report', () => {
	it('rejects an invalid payload with 400', async () => {
		const ctx = createExecutionContext();
		const response = await worker.fetch(makeRequest({ userMessage: '' }), env);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(400);
	});

	it('rejects an unknown reason category', async () => {
		const ctx = createExecutionContext();
		const response = await worker.fetch(
			makeRequest({ userMessage: 'hi', aiMessage: 'hello', reasonCategory: 'not-a-real-reason' }),
			env,
		);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(400);
	});

	it('accepts a valid report and returns 201', async () => {
		const ctx = createExecutionContext();
		const response = await worker.fetch(
			makeRequest({ userMessage: 'What do I do?', aiMessage: 'Call the cruise line.', reasonCategory: 'inaccurate' }),
			env,
		);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(201);

		const row = await env.DB.prepare('SELECT * FROM reports').first();
		expect(row?.ai_message).toBe('Call the cruise line.');
		expect(row?.synced_to_github).toBe(0);
	});

	it('rate-limits after 5 reports from the same IP within an hour', async () => {
		const payload = { userMessage: 'q', aiMessage: 'a', reasonCategory: 'other' };
		for (let i = 0; i < 5; i++) {
			const ctx = createExecutionContext();
			const response = await worker.fetch(makeRequest(payload, '9.9.9.9'), env);
			await waitOnExecutionContext(ctx);
			expect(response.status).toBe(201);
		}

		const ctx = createExecutionContext();
		const response = await worker.fetch(makeRequest(payload, '9.9.9.9'), env);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(429);
	});

	it('does not rate-limit a different IP', async () => {
		const payload = { userMessage: 'q', aiMessage: 'a', reasonCategory: 'other' };
		for (let i = 0; i < 5; i++) {
			const ctx = createExecutionContext();
			await worker.fetch(makeRequest(payload, '9.9.9.9'), env);
			await waitOnExecutionContext(ctx);
		}

		const ctx = createExecutionContext();
		const response = await worker.fetch(makeRequest(payload, '8.8.8.8'), env);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(201);
	});
});

describe('runWeeklySync', () => {
	it('does nothing and makes no GitHub call when there are no unsynced reports', async () => {
		const fetchSpy = vi.spyOn(globalThis, 'fetch');
		const result = await runWeeklySync(env);
		expect(result).toEqual({ groupsSynced: 0, groupsFailed: 0 });
		expect(fetchSpy).not.toHaveBeenCalled();
		fetchSpy.mockRestore();
	});

	it('creates one issue per distinct ai_message and marks those rows synced', async () => {
		await env.DB.batch([
			env.DB.prepare(
				`INSERT INTO reports (created_at, user_message, ai_message, reason_category) VALUES (?, ?, ?, ?)`,
			).bind('2026-09-01T00:00:00.000Z', 'q1', 'bad answer A', 'inaccurate'),
			env.DB.prepare(
				`INSERT INTO reports (created_at, user_message, ai_message, reason_category) VALUES (?, ?, ?, ?)`,
			).bind('2026-09-02T00:00:00.000Z', 'q1-dup', 'bad answer A', 'inaccurate'),
			env.DB.prepare(
				`INSERT INTO reports (created_at, user_message, ai_message, reason_category) VALUES (?, ?, ?, ?)`,
			).bind('2026-09-03T00:00:00.000Z', 'q2', 'bad answer B', 'unsafe'),
		]);

		let issueNumber = 100;
		const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
			const url = typeof input === 'string' ? input : input.toString();
			if (url.includes('/labels')) {
				return new Response(null, { status: 201 });
			}
			issueNumber++;
			return new Response(JSON.stringify({ number: issueNumber }), { status: 201 });
		});

		const result = await runWeeklySync(env);
		expect(result).toEqual({ groupsSynced: 2, groupsFailed: 0 });

		// Two distinct ai_message values -> two issue-creation calls (plus one label-ensure call).
		const issueCalls = fetchSpy.mock.calls.filter(([input]) => {
			const url = typeof input === 'string' ? input : (input as URL | Request).toString();
			return url.includes('/issues');
		});
		expect(issueCalls.length).toBe(2);

		const rows = await env.DB.prepare('SELECT ai_message, synced_to_github, github_issue_number FROM reports').all();
		expect(rows.results.every((r: any) => r.synced_to_github === 1)).toBe(true);
		const dupGroup = rows.results.filter((r: any) => r.ai_message === 'bad answer A');
		expect(dupGroup[0].github_issue_number).toBe(dupGroup[1].github_issue_number);

		fetchSpy.mockRestore();
	});

	it('continues to the next group when one GitHub issue creation fails', async () => {
		await env.DB.batch([
			env.DB.prepare(
				`INSERT INTO reports (created_at, user_message, ai_message, reason_category) VALUES (?, ?, ?, ?)`,
			).bind('2026-09-01T00:00:00.000Z', 'q1', 'will fail', 'inaccurate'),
			env.DB.prepare(
				`INSERT INTO reports (created_at, user_message, ai_message, reason_category) VALUES (?, ?, ?, ?)`,
			).bind('2026-09-02T00:00:00.000Z', 'q2', 'will succeed', 'unsafe'),
		]);

		const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
			const url = typeof input === 'string' ? input : input.toString();
			if (url.includes('/labels')) return new Response(null, { status: 201 });
			const body = init?.body ? JSON.parse(init.body as string) : {};
			if (typeof body.title === 'string' && body.title.includes('will fail')) {
				return new Response('nope', { status: 500 });
			}
			return new Response(JSON.stringify({ number: 999 }), { status: 201 });
		});

		const result = await runWeeklySync(env);
		expect(result).toEqual({ groupsSynced: 1, groupsFailed: 1 });

		const rows = await env.DB.prepare('SELECT ai_message, synced_to_github FROM reports').all();
		const failed = rows.results.find((r: any) => r.ai_message === 'will fail');
		const succeeded = rows.results.find((r: any) => r.ai_message === 'will succeed');
		expect(failed?.synced_to_github).toBe(0);
		expect(succeeded?.synced_to_github).toBe(1);

		fetchSpy.mockRestore();
	});
});
