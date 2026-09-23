import type { Env } from './index';
import type { HealthResponse } from './types';
import { readCatalog } from './cache';
import { readRefreshDiagnostic } from './cache';

function jsonResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json', 'Access-Control-Allow-Origin': '*' },
	});
}

export async function handleHealth(env: Env): Promise<Response> {
	try {
		const apps = await readCatalog(env);
		const diag = await readRefreshDiagnostic(env);

		let cacheStatus: 'fresh' | 'stale' | 'empty' = 'empty';
		if (apps.length > 0) {
			const now = Date.now();
			const lastRefresh = diag?.refreshCompletedAt ? new Date(diag.refreshCompletedAt).getTime() : 0;
			if (lastRefresh > 0) {
				const ageMinutes = (now - lastRefresh) / 60000;
				cacheStatus = ageMinutes < 1440 ? 'fresh' : 'stale';
			} else {
				cacheStatus = 'fresh';
			}
		}

		const response: HealthResponse = {
			status: apps.length > 0 ? 'ok' : 'degraded',
			catalogApps: apps.length,
			lastCatalogRefresh: diag?.refreshStartedAt ?? null,
			lastSuccessfulRefresh: diag?.status === 'ok' ? diag.refreshCompletedAt : null,
			cacheStatus,
			lastDiscoverySource: diag?.status === 'ok' ? 'google-play-developer-page' : null,
		};
		return jsonResponse(response, 200);
	} catch (error) {
		console.error('[crosspromo] Health check error:', error);
		const response: HealthResponse = {
			status: 'degraded',
			catalogApps: 0,
			lastCatalogRefresh: null,
			lastSuccessfulRefresh: null,
			cacheStatus: 'empty',
			lastDiscoverySource: null,
		};
		return jsonResponse(response, 200);
	}
}
