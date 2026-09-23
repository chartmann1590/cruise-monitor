import type { Env } from './index';
import type { GlobalConfig, AppConfig } from './types';
import { DEFAULT_CONFIG } from './types';

const CONFIG_TTL_SECONDS = 300;

function jsonResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json', 'Access-Control-Allow-Origin': '*' },
	});
}

function nowIso(): string {
	return new Date().toISOString();
}

export async function loadGlobalConfig(env: Env): Promise<GlobalConfig> {
	// Try KV cache first.
	const cached = await env.CROSS_PROMO_KV.get('config:global:v1', { type: 'json' });
	if (cached && isConfig(cached)) {
		return cached;
	}

	const row = await env.DB.prepare('SELECT value FROM global_config WHERE key = ?').bind('global').first<{ value: string }>();
	if (row) {
		try {
			const parsed = JSON.parse(row.value) as Partial<GlobalConfig>;
			const merged: GlobalConfig = {
				enabled: parsed.enabled ?? DEFAULT_CONFIG.enabled,
				popularWeight: parsed.popularWeight ?? DEFAULT_CONFIG.popularWeight,
				randomWeight: parsed.randomWeight ?? DEFAULT_CONFIG.randomWeight,
				newAppBoostDays: parsed.newAppBoostDays ?? DEFAULT_CONFIG.newAppBoostDays,
				defaultLimit: parsed.defaultLimit ?? DEFAULT_CONFIG.defaultLimit,
				maxLimit: parsed.maxLimit ?? DEFAULT_CONFIG.maxLimit,
			};
			await env.CROSS_PROMO_KV.put('config:global:v1', JSON.stringify(merged), {
				expirationTtl: CONFIG_TTL_SECONDS,
			});
			return merged;
		} catch {
			// fall through to defaults
		}
	}

	await env.CROSS_PROMO_KV.put('config:global:v1', JSON.stringify(DEFAULT_CONFIG), {
		expirationTtl: CONFIG_TTL_SECONDS,
	});
	return DEFAULT_CONFIG;
}

function isConfig(obj: unknown): obj is GlobalConfig {
	if (typeof obj !== 'object' || obj === null) return false;
	const c = obj as Record<string, unknown>;
	return (
		typeof c.enabled === 'boolean' &&
		typeof c.popularWeight === 'number' &&
		typeof c.randomWeight === 'number' &&
		typeof c.newAppBoostDays === 'number' &&
		typeof c.defaultLimit === 'number' &&
		typeof c.maxLimit === 'number'
	);
}

export async function loadAppConfig(env: Env, sourcePackage: string): Promise<AppConfig | null> {
	const key = `config:app:${sourcePackage}:v1`;
	const cached = await env.CROSS_PROMO_KV.get(key, { type: 'json' });
	if (cached && isAppConfig(cached, sourcePackage)) {
		return cached;
	}

	const row = await env.DB.prepare(
		'SELECT enabled, max_cards, placements FROM app_config WHERE source_package = ?',
	).bind(sourcePackage).first<{ enabled: number; max_cards: number | null; placements: string | null }>();

	if (!row) {
		return null;
	}

	const config: AppConfig = {
		sourcePackage,
		enabled: row.enabled === 1,
		maxCards: row.max_cards ?? DEFAULT_CONFIG.defaultLimit,
		placements: row.placements ? JSON.parse(row.placements) : [],
	};
	await env.CROSS_PROMO_KV.put(key, JSON.stringify(config), { expirationTtl: CONFIG_TTL_SECONDS });
	return config;
}

function isAppConfig(obj: unknown, sourcePackage: string): obj is AppConfig {
	if (typeof obj !== 'object' || obj === null) return false;
	const c = obj as Record<string, unknown>;
	return c.sourcePackage === sourcePackage && typeof c.enabled === 'boolean';
}

export async function saveGlobalConfig(env: Env, config: Partial<GlobalConfig>): Promise<GlobalConfig> {
	const current = await loadGlobalConfig(env);
	const merged = { ...current, ...config };
	await env.DB.prepare(
		'INSERT INTO global_config (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at',
	).bind('global', JSON.stringify(merged), nowIso()).run();
	await env.CROSS_PROMO_KV.delete('config:global:v1');
	return merged;
}

export async function saveAppConfig(env: Env, config: AppConfig): Promise<void> {
	await env.DB.prepare(
		'INSERT INTO app_config (source_package, enabled, max_cards, placements, updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(source_package) DO UPDATE SET enabled = excluded.enabled, max_cards = excluded.max_cards, placements = excluded.placements, updated_at = excluded.updated_at',
	).bind(config.sourcePackage, config.enabled ? 1 : 0, config.maxCards, JSON.stringify(config.placements), nowIso()).run();
	await env.CROSS_PROMO_KV.delete(`config:app:${config.sourcePackage}:v1`);
}

export { jsonResponse, nowIso };
