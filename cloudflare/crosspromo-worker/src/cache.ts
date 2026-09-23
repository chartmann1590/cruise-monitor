import type { Env } from './index';
import type { CatalogApp } from './types';
import {
	CATALOG_CACHE_KEY,
	LAST_KNOWN_GOOD_KEY,
	REFRESH_DIAGNOSTIC_KEY,
} from './types';

const CATALOG_CACHE_TTL = 3600; // 1 hour
const LKG_TTL = 86400 * 30; // 30 days for last-known-good

export interface CatalogCacheEntry {
	apps: CatalogApp[];
	source: string;
	refreshStartedAt: string;
	refreshCompletedAt: string | null;
}

export interface RefreshDiagnostic {
	refreshStartedAt: string;
	refreshCompletedAt: string | null;
	status: 'ok' | 'rejected' | 'failed';
	appsDiscovered: number;
	appsRejected: number;
	metadataFailures: number;
	previousCount: number;
	newCount: number;
	suspicious: boolean;
	errorMessage: string | null;
}

/** Read the current catalog from KV cache, falling back to D1. */
export async function readCatalog(env: Env): Promise<CatalogApp[]> {
	// Try KV cache first
	const cached = await env.CROSS_PROMO_KV.get(CATALOG_CACHE_KEY, { type: 'json' }) as CatalogCacheEntry | null;
	if (cached && Array.isArray(cached.apps)) {
		return cached.apps;
	}

	// Fallback: read from D1
	const rows = await env.DB.prepare(
		`SELECT package_name, name, icon_url, store_url, short_description, rating, rating_count,
			 install_text, estimated_installs, category, price_text, is_free, developer,
			 first_discovered_at, last_seen_at, enabled, promotion_multiplier
		  FROM catalog_apps ORDER BY first_discovered_at ASC`,
	).all<any>();

	const apps: CatalogApp[] = (rows.results ?? []).map((row) => ({
		packageName: row.package_name,
		name: row.name,
		iconUrl: row.icon_url,
		storeUrl: row.store_url,
		shortDescription: row.short_description,
		rating: row.rating,
		ratingCount: row.rating_count,
		installText: row.install_text,
		estimatedInstalls: row.estimated_installs,
		category: row.category,
		priceText: row.price_text,
		isFree: row.is_free ? true : false,
		developer: row.developer,
		firstDiscoveredAt: row.first_discovered_at,
		lastSeenAt: row.last_seen_at,
		enabled: row.enabled === 1,
		promotionMultiplier: row.promotion_multiplier ?? 1.0,
	}));

	// Warm the KV cache
	if (apps.length > 0) {
		await writeCatalogCache(env, apps, 'd1-fallback', new Date().toISOString());
	}

	return apps;
}

/** Write the catalog to KV cache and D1. */
export async function writeCatalog(env: Env, apps: CatalogApp[]): Promise<void> {
	await writeCatalogCache(env, apps, 'discovery', new Date().toISOString());

	// Upsert into D1 in a transaction
	const now = new Date().toISOString();
	const upsertStmt = env.DB.prepare(
		`INSERT INTO catalog_apps (
			package_name, name, icon_url, store_url, short_description, rating, rating_count,
			install_text, estimated_installs, category, price_text, is_free, developer,
			first_discovered_at, last_seen_at, enabled, promotion_multiplier
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(package_name) DO UPDATE SET
			name = excluded.name,
			icon_url = excluded.icon_url,
			store_url = excluded.store_url,
			short_description = excluded.short_description,
			rating = excluded.rating,
			rating_count = excluded.rating_count,
			install_text = excluded.install_text,
			estimated_installs = excluded.estimated_installs,
			category = excluded.category,
			price_text = excluded.price_text,
			is_free = excluded.is_free,
			developer = excluded.developer,
			last_seen_at = excluded.last_seen_at`,
	);

	const batch = apps.map((app) =>
		upsertStmt.bind(
			app.packageName,
			app.name,
			app.iconUrl,
			app.storeUrl,
			app.shortDescription,
			app.rating,
			app.ratingCount,
			app.installText,
			app.estimatedInstalls,
			app.category,
			app.priceText,
			app.isFree ? 1 : 0,
			app.developer,
			app.firstDiscoveredAt,
			app.lastSeenAt,
			app.enabled ? 1 : 0,
			app.promotionMultiplier,
		),
	);
	await env.DB.batch(batch);
}

/** Write only the KV cache layer (for quick writes without D1). */
async function writeCatalogCache(
	env: Env,
	apps: CatalogApp[],
	source: string,
	completedAt: string,
): Promise<void> {
	const entry: CatalogCacheEntry = {
		apps,
		source,
		refreshStartedAt: completedAt,
		refreshCompletedAt: completedAt,
	};
	await env.CROSS_PROMO_KV.put(CATALOG_CACHE_KEY, JSON.stringify(entry), {
		expirationTtl: CATALOG_CACHE_TTL,
	});
}

/** Read last-known-good catalog from KV. */
export async function readLastKnownGood(env: Env): Promise<CatalogApp[] | null> {
	const cached = await env.CROSS_PROMO_KV.get(LAST_KNOWN_GOOD_KEY, { type: 'json' }) as CatalogCacheEntry | null;
	if (cached && Array.isArray(cached?.apps)) {
		return cached.apps;
	}
	// Fallback to D1 (all enabled apps)
	const apps = await readCatalog(env);
	return apps.length > 0 ? apps : null;
}

/** Store the last-known-good catalog. */
export async function writeLastKnownGood(env: Env, apps: CatalogApp[]): Promise<void> {
	const entry: CatalogCacheEntry = {
		apps,
		source: 'last-known-good',
		refreshStartedAt: new Date().toISOString(),
		refreshCompletedAt: new Date().toISOString(),
	};
	await env.CROSS_PROMO_KV.put(LAST_KNOWN_GOOD_KEY, JSON.stringify(entry), {
		expirationTtl: LKG_TTL,
	});
}

/** Store refresh diagnostic information. */
export async function writeRefreshDiagnostic(env: Env, diag: RefreshDiagnostic): Promise<void> {
	await env.CROSS_PROMO_KV.put(REFRESH_DIAGNOSTIC_KEY, JSON.stringify(diag), {
		expirationTtl: 86400 * 7,
	});
}

/** Read refresh diagnostic from KV. */
export async function readRefreshDiagnostic(env: Env): Promise<RefreshDiagnostic | null> {
	return (await env.CROSS_PROMO_KV.get(REFRESH_DIAGNOSTIC_KEY, { type: 'json' })) as RefreshDiagnostic | null;
}

/** Validate a freshly-discovered catalog against the previous one.
 *  Returns false if the refresh is suspicious (e.g., dramatic drop in app count). */
export function validateCatalogRefresh(
	previous: CatalogApp[],
	discovered: CatalogApp[],
): { valid: boolean; suspicious: boolean; reason: string | null } {
	const prevCount = previous.length;
	const newCount = discovered.length;

	if (newCount === 0) {
		return { valid: false, suspicious: true, reason: 'Zero apps discovered' };
	}

	// If previous catalog had apps and new catalog has dramatically fewer (< 50%), flag as suspicious
	if (prevCount > 0 && newCount < prevCount * 0.5) {
		return { valid: false, suspicious: true, reason: `Dramatic drop: ${prevCount} -> ${newCount} apps` };
	}

	// If new catalog has only 1 app and previous had more than 5, flag as suspicious
	if (prevCount > 5 && newCount === 1) {
		return { valid: false, suspicious: true, reason: `Single-app catalog when previous had ${prevCount}` };
	}

	return { valid: true, suspicious: false, reason: null };
}
