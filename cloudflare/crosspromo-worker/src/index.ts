import type { CatalogApp, RecommendationResponse, GlobalConfig, HealthResponse, AppConfig } from './types';

export interface Env {
	DB: D1Database;
	CROSS_PROMO_KV: KVNamespace;
	ADMIN_TOKEN: string;
	DEVELOPER_ID: string;
	PLAY_STORE_BASE_URL: string;
	SDK_VERSION: string;
	CATALOG_CACHE_TTL_SECONDS: string;
	RECOMMENDATION_CACHE_TTL_SECONDS: string;
}

import { readCatalog, readLastKnownGood } from './cache';
import { generateRecommendations } from './recommendations';
import { handleEvents, loadStats } from './events';
import { handleHealth } from './health';
import { runCatalogRefresh } from './refresh';
import {
	handleAdminCatalog,
	handleAdminDiagnostics,
	handleAdminStats,
	handleAdminConfigGet,
	handleAdminConfigPost,
	handleAdminAppConfigPost,
	handleAdminRefresh,
} from './admin';
import { loadGlobalConfig, loadAppConfig } from './config';

function jsonResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: {
			'content-type': 'application/json',
			'Access-Control-Allow-Origin': '*',
			'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
			'Access-Control-Allow-Headers': 'Content-Type, Authorization',
		},
	});
}

const corsHeaders: Record<string, string> = {
	'Access-Control-Allow-Origin': '*',
	'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
	'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

function isValidPackageName(pkg: string): boolean {
	return /^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$/.test(pkg) && pkg.length <= 150;
}

function isValidSessionId(s: string): boolean {
	return /^[a-f0-9-]{8,64}$/.test(s);
}

function isValidPlacement(s: string): boolean {
	return /^[a-zA-Z0-9_-]{1,64}$/.test(s);
}

function uuidv4(): string {
	return crypto.randomUUID();
}

/** GET /api/v1/catalog — return the full normalized catalog. */
async function handleCatalog(request: Request, env: Env): Promise<Response> {
	try {
		const apps = await readCatalog(env);
		return jsonResponse({
			version: 1,
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
	} catch (error) {
		console.error('[crosspromo] Catalog error:', error);
		return jsonResponse({ error: 'Failed to load catalog' }, 500);
	}
}

/** GET /api/v1/recommendations — generate personalized recommendations. */
async function handleRecommendations(request: Request, env: Env): Promise<Response> {
	const url = new URL(request.url);

	const sourcePackage = url.searchParams.get('sourcePackage') ?? null;
	if (sourcePackage !== null && !isValidPackageName(sourcePackage)) {
		return jsonResponse({ error: 'Invalid sourcePackage' }, 400);
	}

	const placement = url.searchParams.get('placement') ?? undefined;
	if (placement !== undefined && !isValidPlacement(placement)) {
		return jsonResponse({ error: 'Invalid placement' }, 400);
	}

	let limit = parseInt(url.searchParams.get('limit') ?? '', 10);
	if (isNaN(limit) || limit < 1) {
		const config = await loadGlobalConfig(env);
		const appCfg = sourcePackage ? await loadAppConfig(env, sourcePackage) : null;
		limit = appCfg?.maxCards ?? config.defaultLimit;
	}
	limit = Math.min(limit, await getMaxLimit(env));

	const excludeParam = url.searchParams.get('exclude');
	const exclude = excludeParam
		? excludeParam
				.split(',')
				.map((s) => s.trim())
				.filter((s) => s.length > 0 && isValidPackageName(s))
		: undefined;

	const sessionId = url.searchParams.get('sessionId');
	if (sessionId !== null && sessionId !== undefined && !isValidSessionId(sessionId)) {
		return jsonResponse({ error: 'Invalid sessionId' }, 400);
	}

	const locale = url.searchParams.get('locale') ?? undefined;
	if (locale !== undefined && locale.length > 10) {
		return jsonResponse({ error: 'Invalid locale' }, 400);
	}

	const requestId = uuidv4();

	// Read catalog
	const catalog = await readCatalog(env);
	if (catalog.length === 0) {
		// Try last-known-good
		const lkg = await readLastKnownGood(env);
		if (!lkg || lkg.length === 0) {
			const now = new Date();
			return jsonResponse({
				version: 1,
				requestId,
				generatedAt: now.toISOString(),
				expiresAt: new Date(now.getTime() + 6 * 3600 * 1000).toISOString(),
				apps: [],
			} as RecommendationResponse, 200);
		}
	}

	// Load aggregate stats for CTR
	const stats = await loadStats(env);

	// Generate recommendations
	const result = await generateRecommendations(env, {
		sourcePackage: sourcePackage ?? '',
		placement,
		sessionId: sessionId ?? undefined,
		exclude,
		locale,
		limit,
		requestId,
	}, catalog, stats);

	return jsonResponse(result, 200);
}

async function getMaxLimit(env: Env): Promise<number> {
	const config = await loadGlobalConfig(env);
	return config.maxLimit;
}

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		const url = new URL(request.url);
		const path = url.pathname.replace(/\/+$/, '') || '/';

		if (request.method === 'OPTIONS') {
			return new Response(null, { status: 204, headers: corsHeaders });
		}

		try {
			// Health endpoint (no auth required)
			if (path === '/api/v1/health' && request.method === 'GET') {
				return await handleHealth(env);
			}

			// Catalog endpoint (no auth required)
			if (path === '/api/v1/catalog' && request.method === 'GET') {
				return await handleCatalog(request, env);
			}

			// Recommendations endpoint
			if (path === '/api/v1/recommendations' && request.method === 'GET') {
				return await handleRecommendations(request, env);
			}

			// Events endpoint
			if (path === '/api/v1/events' && request.method === 'POST') {
				return await handleEvents(request, env);
			}

			// Admin endpoints (require bearer token)
			if (path.startsWith('/api/v1/admin/')) {
				if (path === '/api/v1/admin/catalog' && request.method === 'GET') {
					return await handleAdminCatalog(request, env);
				}
				if (path === '/api/v1/admin/diagnostics' && request.method === 'GET') {
					return await handleAdminDiagnostics(request, env);
				}
				if (path === '/api/v1/admin/stats' && request.method === 'GET') {
					return await handleAdminStats(request, env);
				}
				if (path === '/api/v1/admin/config' && request.method === 'GET') {
					return await handleAdminConfigGet(request, env);
				}
				if (path === '/api/v1/admin/config' && request.method === 'POST') {
					return await handleAdminConfigPost(request, env);
				}
				if (path === '/api/v1/admin/app-config' && request.method === 'POST') {
					return await handleAdminAppConfigPost(request, env);
				}
				if (path === '/api/v1/admin/refresh' && request.method === 'POST') {
					return await handleAdminRefresh(request, env, ctx);
				}
				return jsonResponse({ error: 'Not found' }, 404);
			}

			return jsonResponse({ error: 'Not found' }, 404);
		} catch (error) {
			console.error('[crosspromo] Unhandled error:', error);
			return jsonResponse({ error: 'Internal server error' }, 500);
		}
	},

	async scheduled(controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
		const diag = await runCatalogRefresh(env);
		console.log(`[crosspromo] Scheduled refresh: status=${diag.status}, apps=${diag.appsDiscovered}`);
	},
};
