import type { Env } from './index';
import type { AnalyticsEvent } from './types';

const EVENT_TYPES = new Set(['promo_impression', 'promo_click', 'crosspromo_impression', 'crosspromo_click', 'crosspromo_install']);
const PLACEMENT_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;
const MAX_EVENTS_PER_BATCH = 50;
const EVENT_PAYLOAD_MAX = 4096;
const RATE_LIMIT_PER_MINUTE = 60;

// In-memory rate limit buckets (per-IP, per-minute)
const rateBuckets = new Map<string, { windowStart: number; count: number }>();

function jsonResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' },
	});
}

function checkRateLimit(ip: string): boolean {
	const now = Date.now();
	const windowStart = Math.floor(now / 60000) * 60000;
	const key = `${ip}`;
	const entry = rateBuckets.get(key);
	if (!entry || entry.windowStart !== windowStart) {
		rateBuckets.set(key, { windowStart, count: 1 });
		return true;
	}
	if (entry.count >= RATE_LIMIT_PER_MINUTE) return false;
	entry.count += 1;
	return true;
}

function isValidPackageName(pkg: string): boolean {
	return /^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$/.test(pkg) && pkg.length <= 150;
}

function isValidSessionId(sid: string): boolean {
	return /^[a-f0-9-]{8,64}$/.test(sid);
}

function isValidRequestId(rid: string): boolean {
	return /^[a-f0-9-]{8,64}$/.test(rid);
}

function parseEvent(raw: Record<string, unknown>): AnalyticsEvent | null {
	const event = typeof raw.event === 'string' ? raw.event : null;
	const sourcePackage = typeof raw.sourcePackage === 'string' ? raw.sourcePackage : null;
	const targetPackage = typeof raw.targetPackage === 'string' ? raw.targetPackage : null;
	const placement = typeof raw.placement === 'string' ? raw.placement : null;
	const sessionId = typeof raw.sessionId === 'string' ? raw.sessionId : null;
	const recommendationRequestId = typeof raw.recommendationRequestId === 'string' ? raw.recommendationRequestId : null;
	const sdkVersion = typeof raw.sdkVersion === 'string' ? raw.sdkVersion : undefined;

	if (!event || !sourcePackage || !targetPackage || !placement) return null;
	if (!EVENT_TYPES.has(event)) return null;
	if (!isValidPackageName(sourcePackage) || !isValidPackageName(targetPackage)) return null;
	if (!PLACEMENT_REGEX.test(placement)) return null;
	if (sessionId !== null && !isValidSessionId(sessionId)) return null;
	if (recommendationRequestId !== null && !isValidRequestId(recommendationRequestId)) return null;

	const rankPosition = typeof raw.rankPosition === 'number' ? Math.max(0, Math.min(raw.rankPosition, 100)) : 0;
	const selectionType =
		raw.selectionType === 'popular' || raw.selectionType === 'random' || raw.selectionType === 'new_app_boost'
			? raw.selectionType
			: 'popular';

	return {
		event,
		sourcePackage,
		targetPackage,
		placement,
		rankPosition,
		selectionType,
		sessionId: sessionId ?? '',
		recommendationRequestId: recommendationRequestId ?? '',
		sdkVersion,
	};
}

export async function handleEvents(request: Request, env: Env): Promise<Response> {
	const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown';
	if (!checkRateLimit(ip)) {
		return jsonResponse({ error: 'Too many requests' }, 429);
	}

	let body: unknown;
	try {
		body = await request.json();
	} catch {
		return jsonResponse({ error: 'Invalid JSON' }, 400);
	}

	const payloadSize = JSON.stringify(body).length;
	if (payloadSize > EVENT_PAYLOAD_MAX) {
		return jsonResponse({ error: 'Payload too large' }, 413);
	}

	// Accept either a single event or an array of events
	const rawEvents: Record<string, unknown>[] = Array.isArray(body) ? body : [body as Record<string, unknown>];
	if (rawEvents.length > MAX_EVENTS_PER_BATCH) {
		return jsonResponse({ error: `Max ${MAX_EVENTS_PER_BATCH} events per batch` }, 400);
	}

	// Validate all events against the catalog — reject events for unknown packages
	const allPackages = new Set<string>();
	const pkgRows = await env.DB.prepare('SELECT package_name FROM catalog_apps').all<{ package_name: string }>();
	for (const row of pkgRows.results ?? []) {
		allPackages.add(row.package_name);
	}

	const events: AnalyticsEvent[] = [];
	let rejected = 0;
	for (const raw of rawEvents) {
		const parsed = parseEvent(raw);
		if (!parsed) {
			rejected++;
			continue;
		}
		// Bot/spam protection: both source and target must be known Hartmann Studios packages
		if (!allPackages.has(parsed.targetPackage) || !allPackages.has(parsed.sourcePackage)) {
			rejected++;
			continue;
		}
		events.push(parsed);
	}

	if (events.length === 0) {
		return jsonResponse({ received: 0, rejected }, 200);
	}

	const now = new Date().toISOString();
	const eventId = () => `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

	// Batch insert events
	const stmt = env.DB.prepare(
		`INSERT INTO promo_events (
			event_id, event_type, source_package, target_package, placement,
			rank_position, selection_type, session_id, recommendation_request_id, sdk_version, timestamp
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
	);
	const batch = events.map((e) =>
		stmt.bind(
			eventId(),
			e.event,
			e.sourcePackage,
			e.targetPackage,
			e.placement,
			e.rankPosition,
			e.selectionType,
			e.sessionId,
			e.recommendationRequestId,
			e.sdkVersion ?? null,
			now,
		),
	);
	await env.DB.batch(batch);

	// Update aggregate stats
	await updateStats(env, events);

	return jsonResponse({ received: events.length, rejected }, 200);
}

async function updateStats(env: Env, events: AnalyticsEvent[]): Promise<void> {
	// Group by (target, source, placement, selection_type)
	const groups = new Map<string, { impressions: number; clicks: number }>();
	for (const e of events) {
		const key = `${e.targetPackage}|${e.sourcePackage}|${e.placement}|${e.selectionType}`;
		const g = groups.get(key) ?? { impressions: 0, clicks: 0 };
		if (e.event === 'promo_impression' || e.event === 'crosspromo_impression') g.impressions++;
		if (e.event === 'promo_click' || e.event === 'crosspromo_click') g.clicks++;
		groups.set(key, g);
	}

	for (const [key, g] of groups) {
		const [targetPackage, sourcePackage, placement, selectionType] = key.split('|');
		await env.DB.prepare(
			`INSERT INTO promo_stats (target_package, source_package, placement, selection_type, impressions, clicks)
			 VALUES (?, ?, ?, ?, ?, ?)
			 ON CONFLICT(target_package, source_package, placement, selection_type)
			 DO UPDATE SET impressions = impressions + excluded.impressions, clicks = clicks + excluded.clicks`,
		)
			.bind(targetPackage, sourcePackage, placement, selectionType, g.impressions, g.clicks)
			.run();
	}
}

export async function loadStats(env: Env): Promise<Map<string, { impressions: number; clicks: number }>> {
	const rows = await env.DB.prepare(
		'SELECT target_package, impressions, clicks FROM promo_stats',
	).all<{ target_package: string; impressions: number; clicks: number }>();

	const map = new Map<string, { impressions: number; clicks: number }>();
	for (const row of rows.results ?? []) {
		const existing = map.get(row.target_package) ?? { impressions: 0, clicks: 0 };
		existing.impressions += row.impressions;
		existing.clicks += row.clicks;
		map.set(row.target_package, existing);
	}
	return map;
}
