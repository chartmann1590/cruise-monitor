import type { Env } from './index';
import type { GlobalConfig, AppConfig } from './types';
import { loadGlobalConfig, loadAppConfig, saveGlobalConfig, saveAppConfig } from './config';
import { readCatalog, readRefreshDiagnostic, readLastKnownGood } from './cache';
import { loadStats } from './events';
import { runCatalogRefresh } from './refresh';

function jsonResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' },
	});
}

function checkAdminAuth(request: Request, env: Env): boolean {
	const provided = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '');
	if (!provided || !env.ADMIN_TOKEN) return false;
	// Constant-time comparison
	const providedBytes = new TextEncoder().encode(provided);
	const expectedBytes = new TextEncoder().encode(env.ADMIN_TOKEN);
	if (providedBytes.length !== expectedBytes.length) return false;
	let diff = 0;
	for (let i = 0; i < providedBytes.length; i++) {
		diff |= providedBytes[i] ^ expectedBytes[i];
	}
	return diff === 0;
}

function requireAdmin(request: Request, env: Env): Response | null {
	if (!checkAdminAuth(request, env)) {
		return jsonResponse({ error: 'Unauthorized' }, 401);
	}
	return null;
}

export async function handleAdminCatalog(request: Request, env: Env): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	const apps = await readCatalog(env);
	return jsonResponse({
		count: apps.length,
		apps: apps.map((a) => ({
			packageName: a.packageName,
			name: a.name,
			iconUrl: a.iconUrl,
			storeUrl: a.storeUrl,
			shortDescription: a.shortDescription,
			rating: a.rating,
			ratingCount: a.ratingCount,
			installText: a.installText,
			estimatedInstalls: a.estimatedInstalls,
			category: a.category,
			priceText: a.priceText,
			isFree: a.isFree,
			developer: a.developer,
			firstDiscoveredAt: a.firstDiscoveredAt,
			lastSeenAt: a.lastSeenAt,
			enabled: a.enabled,
			promotionMultiplier: a.promotionMultiplier,
		})),
	}, 200);
}

export async function handleAdminDiagnostics(request: Request, env: Env): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	const diag = await readRefreshDiagnostic(env);
	const lastKnownGood = await readLastKnownGood(env);
	return jsonResponse({
		diagnostic: diag,
		lastKnownGoodApps: lastKnownGood?.length ?? 0,
	}, 200);
}

export async function handleAdminStats(request: Request, env: Env): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	const stats = await loadStats(env);
	const result: Record<string, { impressions: number; clicks: number; ctr: number }> = {};
	for (const [pkg, s] of stats) {
		result[pkg] = {
			impressions: s.impressions,
			clicks: s.clicks,
			ctr: s.impressions > 0 ? s.clicks / s.impressions : 0,
		};
	}
	return jsonResponse(result, 200);
}

export async function handleAdminConfigGet(request: Request, env: Env): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	const config = await loadGlobalConfig(env);
	return jsonResponse(config, 200);
}

export async function handleAdminConfigPost(request: Request, env: Env): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	let body: unknown;
	try {
		body = await request.json();
	} catch {
		return jsonResponse({ error: 'Invalid JSON' }, 400);
	}

	if (typeof body !== 'object' || body === null) {
		return jsonResponse({ error: 'Invalid config object' }, 400);
	}

	const b = body as Record<string, unknown>;
	const patch: Partial<GlobalConfig> = {};

	if (b.enabled !== undefined) {
		if (typeof b.enabled !== 'boolean') return jsonResponse({ error: 'enabled must be boolean' }, 400);
		patch.enabled = b.enabled;
	}
	if (b.popularWeight !== undefined) {
		const v = Number(b.popularWeight);
		if (isNaN(v) || v < 0 || v > 1) return jsonResponse({ error: 'popularWeight must be 0-1' }, 400);
		patch.popularWeight = v;
	}
	if (b.randomWeight !== undefined) {
		const v = Number(b.randomWeight);
		if (isNaN(v) || v < 0 || v > 1) return jsonResponse({ error: 'randomWeight must be 0-1' }, 400);
		patch.randomWeight = v;
	}
	if (b.newAppBoostDays !== undefined) {
		const v = Number(b.newAppBoostDays);
		if (isNaN(v) || v < 0 || v > 365) return jsonResponse({ error: 'newAppBoostDays must be 0-365' }, 400);
		patch.newAppBoostDays = v;
	}
	if (b.defaultLimit !== undefined) {
		const v = Number(b.defaultLimit);
		if (!Number.isInteger(v) || v < 1 || v > 10) return jsonResponse({ error: 'defaultLimit must be 1-10' }, 400);
		patch.defaultLimit = v;
	}
	if (b.maxLimit !== undefined) {
		const v = Number(b.maxLimit);
		if (!Number.isInteger(v) || v < 1 || v > 20) return jsonResponse({ error: 'maxLimit must be 1-20' }, 400);
		patch.maxLimit = v;
	}

	const updated = await saveGlobalConfig(env, patch);
	return jsonResponse(updated, 200);
}

export async function handleAdminAppConfigPost(request: Request, env: Env): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	let body: unknown;
	try {
		body = await request.json();
	} catch {
		return jsonResponse({ error: 'Invalid JSON' }, 400);
	}

	if (typeof body !== 'object' || body === null) {
		return jsonResponse({ error: 'Invalid config object' }, 400);
	}

	const b = body as Record<string, unknown>;
	if (typeof b.sourcePackage !== 'string') return jsonResponse({ error: 'sourcePackage required' }, 400);

	const config: AppConfig = {
		sourcePackage: b.sourcePackage,
		enabled: b.enabled !== false,
		maxCards: typeof b.maxCards === 'number' ? b.maxCards : 3,
		placements: Array.isArray(b.placements) ? b.placements.map(String) : [],
	};

	await saveAppConfig(env, config);
	return jsonResponse({ ok: true, config }, 200);
}

export async function handleAdminRefresh(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
	const auth = requireAdmin(request, env);
	if (auth) return auth;

	// Run refresh synchronously so callers see the result immediately
	const diag = await runCatalogRefresh(env);
	return jsonResponse({ status: 'completed', diagnostic: diag }, 200);
}
