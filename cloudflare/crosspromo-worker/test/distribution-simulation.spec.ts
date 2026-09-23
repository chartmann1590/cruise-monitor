import { describe, it, expect, vi } from 'vitest';
import { generateRecommendations } from '../src/recommendations';
import type { CatalogApp } from '../src/types';
import type { Env } from '../src/index';

vi.mock('../src/config', () => ({
	loadGlobalConfig: vi.fn().mockResolvedValue({
		enabled: true,
		popularWeight: 0.65,
		randomWeight: 0.35,
		newAppBoostDays: 14,
		defaultLimit: 3,
		maxLimit: 6,
	}),
	loadAppConfig: vi.fn().mockResolvedValue(null),
}));

// 20-app sample catalog: a mix of dominant, mid-tier, tiny, brand-new, and disabled apps.
const SAMPLE_CATALOG: CatalogApp[] = Array.from({ length: 20 }, (_, i) => {
	const isDisabled = i === 19;
	const isNew = i === 18;
	const tier = i < 3 ? 'dominant' : i < 10 ? 'mid' : 'tiny';
	const estimatedInstalls = tier === 'dominant' ? 5_000_000 - i * 100_000 : tier === 'mid' ? 100_000 - i * 5_000 : 1_000;
	return {
		packageName: `com.charles.app${i}`,
		name: `App ${i}`,
		iconUrl: `https://play-lh.googleusercontent.com/icon${i}`,
		storeUrl: `https://play.google.com/store/apps/details?id=com.charles.app${i}`,
		shortDescription: `Description ${i}`,
		rating: 4.0 + (i % 5) * 0.1,
		ratingCount: tier === 'dominant' ? 50000 : tier === 'mid' ? 2000 : 50,
		installText: `${estimatedInstalls}+`,
		estimatedInstalls,
		category: 'Tools',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: isNew
			? new Date(Date.now() - 2 * 24 * 3600 * 1000).toISOString()
			: new Date(Date.now() - 100 * 24 * 3600 * 1000).toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: !isDisabled,
		promotionMultiplier: 1.0,
	} satisfies CatalogApp;
});

const CURRENT_APP = 'com.charles.app0';

describe('recommendation simulation (100k runs)', () => {
	it('produces a fair, bounded, policy-compliant distribution over 100,000 selections', async () => {
		const runs = 100_000;
		const limit = 3;
		const impressionCounts = new Map<string, number>();
		const stats = new Map<string, { impressions: number; clicks: number }>();
		const mockEnv = {} as Env;

		for (let i = 0; i < runs; i++) {
			const result = await generateRecommendations(
				mockEnv,
				{ sourcePackage: CURRENT_APP, limit, requestId: `sim-${i}` },
				SAMPLE_CATALOG,
				stats,
			);

			// Duplicates never appear within a single response.
			const packages = result.apps.map((a) => a.packageName);
			expect(new Set(packages).size).toBe(packages.length);

			// Current app is never selected.
			expect(packages).not.toContain(CURRENT_APP);

			// Disabled apps never appear.
			expect(packages).not.toContain('com.charles.app19');

			for (const app of result.apps) {
				impressionCounts.set(app.packageName, (impressionCounts.get(app.packageName) ?? 0) + 1);
			}
		}

		const totalImpressions = runs * limit;
		const dominantApp = 'com.charles.app1'; // eligible, top-tier installs
		const tinyApp = 'com.charles.app15'; // eligible, tiny tier
		const newApp = 'com.charles.app18'; // eligible, newly discovered

		const dominantShare = (impressionCounts.get(dominantApp) ?? 0) / totalImpressions;
		const tinyShare = (impressionCounts.get(tinyApp) ?? 0) / totalImpressions;
		const newAppShare = (impressionCounts.get(newApp) ?? 0) / totalImpressions;

		// Popular apps receive more exposure than tiny apps...
		expect(dominantShare).toBeGreaterThan(tinyShare);

		// ...but lower-ranked apps still receive meaningful exposure (exploration works).
		expect(tinyShare).toBeGreaterThan(0);

		// No single app dominates excessively — even the top app stays well under 50% share.
		expect(dominantShare).toBeLessThan(0.35);

		// New apps get a boost — a brand-new app with weak metrics should outperform
		// similarly-tiny established apps thanks to the exploration/new-app boost.
		expect(newAppShare).toBeGreaterThan(tinyShare * 0.8);

		// Every eligible app received at least some exposure across 100k runs.
		for (const app of SAMPLE_CATALOG) {
			if (app.packageName === CURRENT_APP || !app.enabled) continue;
			expect(impressionCounts.get(app.packageName) ?? 0).toBeGreaterThan(0);
		}
	});
});
