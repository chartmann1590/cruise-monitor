import type { Env } from './index';
import type { CatalogApp, PromoApp, RecommendationResponse, GlobalConfig, AppConfig } from './types';
import { loadGlobalConfig, loadAppConfig } from './config';
import { CATALOG_CACHE_KEY, RECS_CACHE_PREFIX } from './types';

export interface SelectionContext {
	sourcePackage: string;
	placement?: string;
	sessionId?: string;
	exclude?: string[];
	locale?: string;
	limit: number;
	requestId: string;
}

interface ScoredApp {
	app: CatalogApp;
	score: number;
}

/**
 * Build weighted cumulative distribution for weighted random selection.
 * Returns array of (cumulativeWeight, app) pairs.
 */
function buildCumulativeWeights(scored: ScoredApp[]): { cumulative: number; app: CatalogApp }[] {
	const total = scored.reduce((sum, s) => sum + Math.max(s.score, 0), 0);
	if (total <= 0) {
		// Equal weights if all scores are zero
		return scored.map((s, i) => ({ cumulative: (i + 1) / scored.length, app: s.app }));
	}
	let acc = 0;
	const result: { cumulative: number; app: CatalogApp }[] = [];
	for (const s of scored) {
		acc += Math.max(s.score, 0) / total;
		result.push({ cumulative: acc, app: s.app });
	}
	return result;
}

/**
 * Weighted random selection from a cumulative distribution.
 * Returns the selected app and its index in the original array.
 */
function weightedPick(items: { cumulative: number; app: CatalogApp }[]): { app: CatalogApp; index: number } {
	const r = Math.random();
	for (let i = 0; i < items.length; i++) {
		if (r <= items[i].cumulative) return { app: items[i].app, index: i };
	}
	return { app: items[items.length - 1].app, index: items.length - 1 };
}

/**
 * Normalize a set of values to 0-1 range.
 */
function normalize(values: number[]): number[] {
	const max = Math.max(...values, 0);
	if (max <= 0) return values.map(() => 0);
	return values.map((v) => Math.min(v / max, 1));
}

/**
 * Compute popularity scores for each app using available metrics.
 * Popularity = installsScore * 0.50 + reviewScore * 0.25 + ratingScore * 0.15 + promoPerformanceScore * 0.10
 * Falls back gracefully when data is missing.
 */
export function computePopularityScores(
	apps: CatalogApp[],
	statsByPackage: Map<string, { impressions: number; clicks: number }>,
): Map<string, number> {
	const scores = new Map<string, number>();

	// Gather raw metrics
	const installs = apps.map((a) => a.estimatedInstalls ?? 0);
	const reviewCounts = apps.map((a) => a.ratingCount ?? 0);
	const ratings = apps.map((a) => (a.rating ?? 0) / 5); // normalize 0-5 to 0-1

	const installsNorm = normalize(installs);
	const reviewNorm = normalize(reviewCounts);
	const ratingNorm = normalize(ratings);

	// CTR for promo performance (clicks / impressions, 0 if no impressions)
	const ctrValues = apps.map((a) => {
		const s = statsByPackage.get(a.packageName);
		if (!s || s.impressions === 0) return 0;
		return s.clicks / s.impressions;
	});
	const ctrNorm = normalize(ctrValues);

	for (let i = 0; i < apps.length; i++) {
		const app = apps[i];
		let hasInstalls = (app.estimatedInstalls ?? 0) > 0;
		let hasReviews = (app.ratingCount ?? 0) > 0;
		let hasRating = (app.rating ?? 0) > 0;
		let hasCtrData = ctrValues[i] > 0;

		let score = 0;

		if (hasInstalls) {
			// Full formula
			score =
				installsNorm[i] * 0.5 +
				reviewNorm[i] * 0.25 +
				ratingNorm[i] * 0.15 +
				ctrNorm[i] * 0.1;
		} else if (hasReviews || hasRating) {
			// Fallback: use review count + rating
			const reviewWeight = hasReviews ? 0.6 : 0;
			const ratingWeight = hasRating ? 0.4 : 0;
			score = (hasReviews ? reviewNorm[i] * 0.6 : 0) + (hasRating ? ratingNorm[i] * 0.4 : 0);
			if (hasCtrData) {
				const total = reviewWeight + ratingWeight;
				score = (score * (1 - 0.1)) + (hasCtrData ? ctrNorm[i] * 0.1 : 0);
			}
		} else {
			// Minimal: use CTR if available, otherwise equal score (pure random)
			score = hasCtrData ? ctrNorm[i] * 0.5 : 0.5;
		}

		// Ensure non-negative
		scores.set(app.packageName, Math.max(score, 0));
	}

	return scores;
}

/**
 * Check if an app should get a new-app exploration boost.
 */
export function isNewApp(app: CatalogApp, newAppBoostDays: number): boolean {
	const discovered = new Date(app.firstDiscoveredAt).getTime();
	if (isNaN(discovered)) return false;
	const ageDays = (Date.now() - discovered) / (1000 * 60 * 60 * 24);
	return ageDays <= newAppBoostDays && app.firstDiscoveredAt !== app.lastSeenAt;
}

/**
 * Generate recommendations from the catalog.
 *
 * Strategy:
 * - 65% popular-weighted selection (weighted by popularity score, promotion multiplier, new-app boost)
 * - 35% random/exploration selection
 * - New apps get a temporary exploration boost
 * - Current app is never selected
 * - Disabled apps are filtered out
 * - No duplicates
 */
export async function generateRecommendations(
	env: Env,
	ctx: SelectionContext,
	catalog: CatalogApp[],
	statsByPackage: Map<string, { impressions: number; clicks: number }>,
): Promise<RecommendationResponse> {
	const config = await loadGlobalConfig(env);
	const appConfig = ctx.sourcePackage ? await loadAppConfig(env, ctx.sourcePackage) : null;

	const globalEnabled = config.enabled;
	const appEnabled = appConfig?.enabled ?? true;
	if (!globalEnabled || !appEnabled) {
		return emptyResponse(ctx.requestId);
	}

	// Determine effective limit
	let limit = ctx.limit;
	if (limit <= 0) limit = appConfig?.maxCards ?? config.defaultLimit;
	limit = Math.min(limit, config.maxLimit);

	// Filter catalog: exclude source package, exclude disabled apps, apply exclusions
	const eligible = catalog.filter((app) => {
		if (app.packageName === ctx.sourcePackage) return false; // Current-app exclusion
		if (!app.enabled) return false; // Never promote disabled apps
		if (ctx.exclude?.includes(app.packageName)) return false; // Already shown recently
		return true;
	});

	if (eligible.length === 0) {
		return emptyResponse(ctx.requestId);
	}

	// Compute scores
	const scores = computePopularityScores(eligible, statsByPackage);

	// Apply promotion multiplier to scores
	const scored: ScoredApp[] = eligible.map((app) => ({
		app,
		score: scores.get(app.packageName)! * (app.promotionMultiplier ?? 1.0),
	}));

	// Identify new apps for boost
	const newAppPackages = new Set(
		eligible.filter((a) => isNewApp(a, config.newAppBoostDays)).map((a) => a.packageName),
	);

	const now = new Date();
	const expiresAt = new Date(now.getTime() + 6 * 60 * 60 * 1000).toISOString(); // 6 hours
	const generatedAt = now.toISOString();

	const selected: PromoApp[] = [];
	const usedPackages = new Set<string>();

	const popularWeight = Math.max(config.popularWeight, 0);
	const randomWeight = Math.max(config.randomWeight, 0);
	const totalWeight = popularWeight + randomWeight;

	while (selected.length < limit && eligible.length - usedPackages.size > 0) {
		const roll = Math.random() * totalWeight;

		let chosen: CatalogApp | null = null;
		let selectionType: 'popular' | 'random' | 'new_app_boost' = 'random';

		if (roll < popularWeight) {
			// Popular-weighted selection
			const popularPool = scored.filter((s) => !usedPackages.has(s.app.packageName));
			if (popularPool.length > 0) {
				// Apply new-app boost: increase weight for new apps
				const boosted = popularPool.map((s) => {
					const boost = newAppPackages.has(s.app.packageName) ? 2.0 : 1.0;
					return { ...s, score: s.score * boost };
				});
				const cumWeights = buildCumulativeWeights(boosted);
				const picked = weightedPick(cumWeights);
				chosen = picked.app;
				selectionType = newAppPackages.has(chosen.packageName) ? 'new_app_boost' : 'popular';
			}
		}

		if (chosen === null) {
			// Random/exploration selection (includes new-app exploration)
			const explorable = eligible.filter((a) => !usedPackages.has(a.packageName));
			if (explorable.length > 0) {
				// New apps get 3x weight in exploration
				const weightedExplorable: { app: CatalogApp; weight: number }[] = explorable.map((a) => ({
					app: a,
					weight: newAppPackages.has(a.packageName) ? 3.0 : 1.0,
				}));
				const totalExpWeight = weightedExplorable.reduce((s, w) => s + w.weight, 0);
				let r = Math.random() * totalExpWeight;
				let chosen2: CatalogApp | null = null;
				for (const item of weightedExplorable) {
					r -= item.weight;
					if (r <= 0) {
						chosen2 = item.app;
						break;
					}
				}
				chosen = chosen2 ?? weightedExplorable[weightedExplorable.length - 1].app;
				selectionType = newAppPackages.has(chosen.packageName) ? 'new_app_boost' : 'random';
			}
		}

		if (chosen) {
			usedPackages.add(chosen.packageName);
			selected.push(toPromoApp(chosen, selectionType));
		}
	}

	return {
		version: 1,
		requestId: ctx.requestId,
		generatedAt,
		expiresAt,
		apps: selected,
	};
}

function toPromoApp(app: CatalogApp, selectionType: 'popular' | 'random' | 'new_app_boost'): PromoApp {
	return {
		packageName: app.packageName,
		name: app.name,
		iconUrl: app.iconUrl,
		shortDescription: app.shortDescription,
		rating: app.rating,
		ratingCount: app.ratingCount,
		installText: app.installText,
		storeUrl: app.storeUrl,
		selectionType,
	};
}

function emptyResponse(requestId: string): RecommendationResponse {
	const now = new Date();
	return {
		version: 1,
		requestId,
		generatedAt: now.toISOString(),
		expiresAt: new Date(now.getTime() + 6 * 60 * 60 * 1000).toISOString(),
		apps: [],
	};
}
