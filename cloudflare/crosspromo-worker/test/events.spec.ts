import { describe, it, expect, beforeEach, vi } from 'vitest';
import { handleEvents, loadStats } from '../src/events';
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

const TEST_CATALOG: CatalogApp[] = [
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
		firstDiscoveredAt: new Date().toISOString(),
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
		firstDiscoveredAt: new Date().toISOString(),
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
		firstDiscoveredAt: new Date().toISOString(),
		lastSeenAt: new Date().toISOString(),
		enabled: true,
		promotionMultiplier: 1.0,
	},
];

let env: MockEnv;

beforeEach(async () => {
	env = makeMockEnv(TEST_CATALOG);
	await writeCatalog(env, TEST_CATALOG);
});

function makeEventRequest(body: unknown, ip = '1.2.3.4'): Request {
	return new Request('https://example.com/api/v1/events', {
		method: 'POST',
		headers: {
			'content-type': 'application/json',
			'CF-Connecting-IP': ip,
		},
		body: JSON.stringify(body),
	});
}

describe('POST /api/v1/events', () => {
it('accepts a valid single event', async () => {
		const response = await handleEvents(
			makeEventRequest({
				event: 'promo_impression',
				sourcePackage: 'com.charles.octopulse',
				targetPackage: 'com.charles.nutrisnap',
				placement: 'home',
				rankPosition: 1,
				selectionType: 'random',
				sessionId: 'deadbeef-1234',
				recommendationRequestId: 'abcdef01-2345',
				sdkVersion: '1.0.0',
			}),
			env,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(1);
		expect(result.rejected).toBe(0);
	});

	it('accepts a batch of events', async () => {
		const response = await handleEvents(
			makeEventRequest([
				{
					event: 'promo_impression',
					sourcePackage: 'com.charles.octopulse',
					targetPackage: 'com.charles.nutrisnap',
					placement: 'home',
					sessionId: 'deadbeef-1234',
					recommendationRequestId: 'abcdef01-2345',
				},
				{
					event: 'promo_click',
					sourcePackage: 'com.charles.octopulse',
					targetPackage: 'com.charles.qrcode',
					placement: 'home',
					sessionId: 'deadbeef-1234',
					recommendationRequestId: 'abcdef01-2345',
				},
			]),
			env,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(2);
		expect(result.rejected).toBe(0);
	});

	it('rejects invalid JSON with 400', async () => {
		const response = await handleEvents(
			new Request('https://example.com/api/v1/events', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: 'not json',
			}),
			env,
		);

		expect(response.status).toBe(400);
	});

	it('rejects unknown event types', async () => {
		const response = await handleEvents(
			makeEventRequest({
				event: 'unknown_event',
				sourcePackage: 'com.charles.octopulse',
				targetPackage: 'com.charles.nutrisnap',
				placement: 'home',
			}),
			env,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(0);
		expect(result.rejected).toBe(1);
	});

	it('rejects events for unknown packages', async () => {
		const response = await handleEvents(
			makeEventRequest({
				event: 'promo_impression',
				sourcePackage: 'com.charles.octopulse',
				targetPackage: 'com.nonexistent.app',
				placement: 'home',
			}),
			env,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(0);
		expect(result.rejected).toBe(1);
	});

	it('rejects events with invalid package names', async () => {
		const response = await handleEvents(
			makeEventRequest({
				event: 'promo_impression',
				sourcePackage: 'invalid-package',
				targetPackage: 'com.charles.nutrisnap',
				placement: 'home',
			}),
			env,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(0);
		expect(result.rejected).toBe(1);
	});

	it('rejects events with invalid placement', async () => {
		const response = await handleEvents(
			makeEventRequest({
				event: 'promo_impression',
				sourcePackage: 'com.charles.octopulse',
				targetPackage: 'com.charles.nutrisnap',
				placement: 'invalid placement!',
			}),
			env,
		);

		expect(response.status).toBe(200);
		const result = await response.json();
		expect(result.received).toBe(0);
		expect(result.rejected).toBe(1);
	});

	it('rejects batches larger than 50 events', async () => {
		const events: unknown[] = [];
		for (let i = 0; i < 51; i++) {
			events.push({
				event: 'promo_impression',
				sourcePackage: 'com.charles.octopulse',
				targetPackage: 'com.charles.nutrisnap',
				placement: 'home',
			});
		}

		const response = await handleEvents(makeEventRequest(events), env);

		// 51 events exceed 4096 byte payload limit → 413 (Payload Too Large)
		expect(response.status).toBe(413);
	});
});

describe('loadStats', () => {
	it('returns aggregate stats from D1', async () => {
		env._db.insert('promo_stats', {
			target_package: 'com.charles.nutrisnap',
			source_package: 'com.charles.octopulse',
			placement: 'home',
			selection_type: 'popular',
			impressions: 10,
			clicks: 2,
		});

		const stats = await loadStats(env);
		expect(stats.size).toBe(1);
		const entry = stats.get('com.charles.nutrisnap');
		expect(entry).toBeDefined();
		expect(entry!.impressions).toBe(10);
		expect(entry!.clicks).toBe(2);
	});

	it('returns empty map when no stats exist', async () => {
		const stats = await loadStats(env);
		expect(stats.size).toBe(0);
	});
});
