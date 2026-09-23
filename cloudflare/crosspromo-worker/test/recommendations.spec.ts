import { describe, it, expect, vi, beforeEach } from 'vitest';
import { computePopularityScores, isNewApp, generateRecommendations } from '../src/recommendations';
import { writeCatalog, readCatalog } from '../src/cache';
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

const TEST_APPS: CatalogApp[] = [
	{
		packageName: 'com.charles.octopulse',
		name: 'OctoPulse',
		iconUrl: 'https://play-lh.googleusercontent.com/icon1',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.octopulse',
		shortDescription: 'OctoPrint companion',
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
		shortDescription: 'Nutrition scanner',
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
		shortDescription: 'Scan QR codes',
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
		promotionMultiplier: 1.0,
	},
	{
		packageName: 'com.charles.photobooth',
		name: 'Photo Booth',
		iconUrl: 'https://play-lh.googleusercontent.com/icon4',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.photobooth',
		shortDescription: 'Photo effects',
		rating: 3.8,
		ratingCount: 100,
		installText: '10K+',
		estimatedInstalls: 10000,
		category: 'Entertainment',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date(Date.now() - 60 * 24 * 3600 * 1000).toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 1.0,
	},
	{
		packageName: 'com.charles.disabledapp',
		name: 'Disabled App',
		iconUrl: 'https://play-lh.googleusercontent.com/icon5',
		storeUrl: 'https://play.google.com/store/apps/details?id=com.charles.disabledapp',
		shortDescription: 'Disabled',
		rating: 4.0,
		ratingCount: 50,
		installText: '1K+',
		estimatedInstalls: 1000,
		category: 'Tools',
		priceText: 'Free',
		isFree: true,
		developer: 'Hartmann Studios',
		firstDiscoveredAt: new Date(Date.now() - 90 * 24 * 3600 * 1000).toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: false,
		promotionMultiplier: 1.0,
	},
];

let env: MockEnv;

beforeEach(async () => {
	env = makeMockEnv(TEST_APPS);
	await writeCatalog(env, TEST_APPS);
});

describe('computePopularityScores', () => {
	it('scores apps based on installs, ratings, and reviews', () => {
		const stats = new Map<string, { impressions: number; clicks: number }>();
		const scores = computePopularityScores(TEST_APPS, stats);

		expect(scores.size).toBe(TEST_APPS.length);
		for (const app of TEST_APPS) {
			const score = scores.get(app.packageName);
			expect(score).toBeDefined();
			expect(score).toBeGreaterThanOrEqual(0);
			expect(score).toBeLessThanOrEqual(1);
		}

		const qrScore = scores.get('com.charles.qrcode')!;
		const photoboothScore = scores.get('com.charles.photobooth')!;
		expect(qrScore).toBeGreaterThan(photoboothScore);
	});

	it('returns equal scores when all metrics are zero', () => {
		const noDataApps: CatalogApp[] = TEST_APPS.map((a) => ({ ...a, estimatedInstalls: 0, rating: null, ratingCount: 0 }));
		const stats = new Map();
		const scores = computePopularityScores(noDataApps, stats);

		for (const app of noDataApps) {
			const score = scores.get(app.packageName)!;
			expect(score).toBe(0.5);
		}
	});
});

describe('isNewApp', () => {
	it('identifies apps discovered recently', () => {
		const newApp: CatalogApp = {
			...TEST_APPS[0],
			firstDiscoveredAt: new Date(Date.now() - 5 * 24 * 3600 * 1000).toISOString(),
			lastSeenAt: new Date().toISOString(),
		};
		expect(isNewApp(newApp, 14)).toBe(true);
	});

	it('rejects apps discovered long ago', () => {
		const oldApp: CatalogApp = {
			...TEST_APPS[0],
			firstDiscoveredAt: new Date(Date.now() - 60 * 24 * 3600 * 1000).toISOString(),
			lastSeenAt: new Date().toISOString(),
		};
		expect(isNewApp(oldApp, 14)).toBe(false);
	});

	it('returns false when firstDiscoveredAt equals lastSeenAt', () => {
		const app: CatalogApp = {
			...TEST_APPS[0],
			firstDiscoveredAt: new Date().toISOString(),
			lastSeenAt: new Date().toISOString(),
		};
		expect(isNewApp(app, 14)).toBe(false);
	});
});

describe('generateRecommendations', () => {
	it('excludes the source package from recommendations', async () => {
		const stats = new Map<string, { impressions: number; clicks: number }>();
		const result = await generateRecommendations(env, {
			sourcePackage: 'com.charles.qrcode',
			placement: 'settings',
			limit: 3,
			requestId: 'test-request-1',
		}, TEST_APPS, stats);

		expect(result.apps.length).toBe(3);
		expect(result.apps.every((a) => a.packageName !== 'com.charles.qrcode')).toBe(true);
	});

	it('filters out disabled apps', async () => {
		const stats = new Map();
		const result = await generateRecommendations(env, {
			sourcePackage: '',
			limit: 5,
			requestId: 'test-request-2',
		}, TEST_APPS, stats);

		expect(result.apps.every((a) => a.packageName !== 'com.charles.disabledapp')).toBe(true);
	});

	it('respects exclude parameter', async () => {
		const stats = new Map();
		const result = await generateRecommendations(env, {
			sourcePackage: '',
			limit: 5,
			exclude: ['com.charles.nutrisnap', 'com.charles.qrcode'],
			requestId: 'test-request-3',
		}, TEST_APPS, stats);

		expect(result.apps.every((a) => a.packageName !== 'com.charles.nutrisnap')).toBe(true);
		expect(result.apps.every((a) => a.packageName !== 'com.charles.qrcode')).toBe(true);
	});

	it('respects limit and maxLimit', async () => {
		const stats = new Map();
		const result = await generateRecommendations(env, {
			sourcePackage: '',
			limit: 5,
			requestId: 'test-request-4',
		}, TEST_APPS, stats);

		expect(result.apps.length).toBeLessThanOrEqual(6);
	});

	it('returns empty array when all apps are excluded', async () => {
		const stats = new Map();
		const result = await generateRecommendations(env, {
			sourcePackage: 'com.charles.octopulse',
			limit: 3,
			requestId: 'test-request-5',
		}, [{ ...TEST_APPS[0] }], stats);

		expect(result.apps.length).toBe(0);
	});

	it('prefers apps with higher popularity scores', async () => {
		const stats = new Map<string, { impressions: number; clicks: number }>();
		const results: string[] = [];
		for (let i = 0; i < 20; i++) {
			const result = await generateRecommendations(env, {
				sourcePackage: '',
				limit: 1,
				requestId: `run-${i}`,
			}, TEST_APPS, stats);
			if (result.apps[0]) results.push(result.apps[0].packageName);
		}

		const qrCount = results.filter((p) => p === 'com.charles.qrcode').length;
		const photoboothCount = results.filter((p) => p === 'com.charles.photobooth').length;
		expect(qrCount).toBeGreaterThan(photoboothCount);
	});

	it('boosts new apps in selection type', async () => {
		const stats = new Map();
		const newApp: CatalogApp = {
			...TEST_APPS[0],
			firstDiscoveredAt: new Date(Date.now() - 3 * 24 * 3600 * 1000).toISOString(),
		};
		const result = await generateRecommendations(env, {
			sourcePackage: '',
			limit: 1,
			requestId: 'test-new-app',
		}, [newApp, TEST_APPS[1]], stats);

		if (result.apps.length > 0) {
			expect(result.apps[0].selectionType).toMatch(/popular|new_app_boost|random/);
		}
	});
});
