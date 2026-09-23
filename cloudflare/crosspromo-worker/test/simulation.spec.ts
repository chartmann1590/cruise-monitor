import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker from '../src';
import { writeCatalog } from '../src/cache';
import type { CatalogApp } from '../src/types';
import { makeMockEnv, type MockEnv } from './helpers';

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
	saveGlobalConfig: vi.fn(),
	saveAppConfig: vi.fn(),
}));

const SIM_CATALOG: CatalogApp[] = [
	{
		packageName: 'com.charles.octopulse',
		name: 'OctoPulse: OctoPrint Companion',
		iconUrl: 'https://play-lh.googleusercontent.com/icon1',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.octopulse',
		shortDescription: 'Control your OctoPrint 3D printer from your phone.',
		rating: 4.5,
		ratingCount: 1000,
		installText: '100K+',
		estimatedInstalls: 100000,
		category: 'Tools',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date(Date.now() - 3 * 24 * 3600 * 1000).toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 1.0,
	},
	{
		packageName: 'com.charles.nutrisnap',
		name: 'NutriSnap',
		iconUrl: 'https://play-lh.googleusercontent.com/icon2',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.nutrisnap',
		shortDescription: 'Scan your food for nutrition facts',
		rating: 4.2,
		ratingCount: 500,
		installText: '50K+',
		estimatedInstalls: 50000,
		category: 'Health',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date(Date.now() - 20 * 24 * 3600 * 1000).toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 1.0,
	},
	{
		packageName: 'com.charles.qrcode',
		name: 'QR Code Scanner',
		iconUrl: 'https://play-lh.googleusercontent.com/icon3',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.qrcode',
		shortDescription: 'Scan QR codes quickly',
		rating: 4.0,
		ratingCount: 2000,
		installText: '1M+',
		estimatedInstalls: 1000000,
		category: 'Tools',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date(Date.now() - 60 * 24 * 3600 * 1000).toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 2.0,
	},
	{
		packageName: 'com.charles.photobooth',
		name: 'Photo Booth',
		iconUrl: 'https://play-lh.googleusercontent.com/icon4',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.photobooth',
		shortDescription: 'Fun photo effects',
		rating: 3.8,
		ratingCount: 100,
		installText: '10K+',
		estimatedInstalls: 10000,
		category: 'Entertainment',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date().toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 1.0,
	},
	{
		packageName: 'com.charles.tradecoachai',
		name: 'Trade Coach AI',
		iconUrl: 'https://play-lh.googleusercontent.com/icon5',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.tradecoachai',
		shortDescription: 'AI trading assistant',
		rating: 4.3,
		ratingCount: 300,
		installText: '100K+',
		estimatedInstalls: 100000,
		category: 'Finance',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date().toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 1.0,
	},
];

let env: MockEnv;
const mockCtx = {
	waitUntil: (p: Promise<unknown>) => { void p; },
	} as any;

beforeEach(async () => {
	env = makeMockEnv(SIM_CATALOG);
	await writeCatalog(env, SIM_CATALOG);
});

describe('End-to-End Cross-Promo Simulation', () => {
	it('full flow: health -> catalog -> recommendations -> events -> stats', async () => {
		// Step 1: Health check
		const healthResponse = await worker.fetch(
			new Request('https://example.com/api/v1/health'),
			env,
			mockCtx,
		);
		expect(healthResponse.status).toBe(200);

		const health = await healthResponse.json();
		expect(health.status).toBe('ok');
		expect(health.catalogApps).toBe(5);

		// Step 2: Fetch catalog
		const catalogResponse = await worker.fetch(
			new Request('https://example.com/api/v1/catalog'),
			env,
			mockCtx,
		);
		expect(catalogResponse.status).toBe(200);

		const catalog = await catalogResponse.json();
		expect(catalog.count).toBe(5);
		expect(catalog.apps.length).toBe(5);
		expect(catalog.apps.find((a: any) => a.packageName === 'com.charles.qrcode').name).toBe('QR Code Scanner');

		// Step 3: Get recommendations for OctoPulse (should exclude itself)
		const recResponse = await worker.fetch(
			new Request('https://example.com/api/v1/recommendations?sourcePackage=com.charles.octopulse&limit=3&placement=settings&sessionId=deadbeef-1234'),
			env,
			mockCtx,
		);
		expect(recResponse.status).toBe(200);

		const rec = await recResponse.json();
		expect(rec.version).toBe(1);
		expect(rec.apps.length).toBe(3);
		expect(rec.apps.every((a: any) => a.packageName !== 'com.charles.octopulse')).toBe(true);
		expect(rec.requestId).toBeTruthy();

		// Step 4: Send events for impressions
		const eventsBody = rec.apps.map((app: any, idx: number) => ({
			event: 'promo_impression',
			sourcePackage: 'com.charles.octopulse',
			targetPackage: app.packageName,
			placement: 'settings',
			rankPosition: idx,
			selectionType: app.selectionType,
			sessionId: 'deadbeef-1234',
			recommendationRequestId: rec.requestId,
		}));
		const eventsResponse = await worker.fetch(
			new Request('https://example.com/api/v1/events', {
				method: 'POST',
				headers: { 'content-type': 'application/json', 'CF-Connecting-IP': '192.168.1.1' },
				body: JSON.stringify(eventsBody),
			}),
			env,
			mockCtx,
		);
		expect(eventsResponse.status).toBe(200);

		const eventsResult = await eventsResponse.json();
		expect(eventsResult.received).toBe(3);
		expect(eventsResult.rejected).toBe(0);

		// Step 5: Send events for click on first app
		const clickResponse = await worker.fetch(
			new Request('https://example.com/api/v1/events', {
				method: 'POST',
				headers: { 'content-type': 'application/json', 'CF-Connecting-IP': '192.168.1.1' },
				body: JSON.stringify({
					event: 'promo_click',
					sourcePackage: 'com.charles.octopulse',
					targetPackage: rec.apps[0].packageName,
					placement: 'settings',
					rankPosition: 0,
					selectionType: rec.apps[0].selectionType,
					sessionId: 'deadbeef-1234',
					recommendationRequestId: rec.requestId,
				}),
			}),
			env,
			mockCtx,
		);
		expect(clickResponse.status).toBe(200);

		// Step 6: Verify stats were recorded in D1
		const statsRows = env._db.getRows('promo_stats');
		expect(statsRows.length).toBeGreaterThanOrEqual(1);

		for (const row of statsRows) {
			expect(row.target_package).not.toBe('com.charles.octopulse');
			expect(row.source_package).toBe('com.charles.octopulse');
			expect(row.placement).toBe('settings');
		}

		const firstAppStats = statsRows.find((r) => r.target_package === rec.apps[0].packageName);
		expect(firstAppStats).toBeDefined();
		expect(Number(firstAppStats!.impressions)).toBeGreaterThanOrEqual(1);
		expect(Number(firstAppStats!.clicks)).toBe(1);
	});

	it('handles empty catalog gracefully', async () => {
		env._db.clear();
		await env.CROSS_PROMO_KV.delete('catalog:v1');

		const response = await worker.fetch(
			new Request('https://example.com/api/v1/recommendations?sourcePackage=com.charles.octopulse&limit=3'),
			env,
			mockCtx,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.apps).toEqual([]);
	});

	it('rejects events for non-existent source package', async () => {
		const response = await worker.fetch(
			new Request('https://example.com/api/v1/events', {
				method: 'POST',
				headers: { 'content-type': 'application/json', 'CF-Connecting-IP': '10.0.0.1' },
				body: JSON.stringify({
					event: 'promo_impression',
					sourcePackage: 'com.nonexistent.app',
					targetPackage: 'com.charles.nutrisnap',
					placement: 'home',
				}),
			}),
			env,
			mockCtx,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(0);
		expect(result.rejected).toBe(1);
	});

	it('applies CORS headers', async () => {
		const response = await worker.fetch(
			new Request('https://example.com/api/v1/health'),
			env,
			mockCtx,
		);

		expect(response.headers.get('Access-Control-Allow-Origin')).toBe('*');
	});

	it('handles OPTIONS preflight', async () => {
		const response = await worker.fetch(
			new Request('https://example.com/api/v1/recommendations', { method: 'OPTIONS' }),
			env,
			mockCtx,
		);

		expect(response.status).toBe(204);
		expect(response.headers.get('Access-Control-Allow-Methods')).toBe('GET, POST, OPTIONS');
	});

	it('returns 404 for unknown routes', async () => {
		const response = await worker.fetch(
			new Request('https://example.com/api/v1/unknown'),
			env,
			mockCtx,
		);

		expect(response.status).toBe(404);
	});

	it('enforces admin auth on admin routes', async () => {
		const response = await worker.fetch(
			new Request('https://example.com/api/v1/admin/catalog', {
				method: 'GET',
				headers: { Authorization: 'Bearer wrong-token' },
			}),
			env,
			mockCtx,
		);

		expect(response.status).toBe(401);
	});
});
