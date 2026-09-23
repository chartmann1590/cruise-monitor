import { readFileSync } from 'node:fs';
import { describe, it, expect, vi } from 'vitest';
import { PlayStoreCatalogProvider, extractPackageFromHref, isValidPackageName, parseAppDetail, parseRating, parseInstallCount, parseInstallValue, parseCategory, parsePrice } from '../src/catalog';

function loadFixture(name: string): string {
	return readFileSync(new URL(`./fixtures/${name}`, import.meta.url), 'utf8');
}

describe('extractPackageFromHref', () => {
	it('extracts valid package from Play Store details URL', () => {
		expect(extractPackageFromHref('/store/apps/details?id=com.charles.octopulse')).toBe('com.charles.octopulse');
	});

	it('extracts from URL with additional params', () => {
		expect(extractPackageFromHref('/store/apps/details?id=com.hartmann.pixeldream&hl=en_US')).toBe('com.hartmann.pixeldream');
	});

	it('returns null for invalid package', () => {
		expect(extractPackageFromHref('/store/apps/details?id=not-a-package')).toBeNull();
	});

	it('returns null for non-Play-Store href', () => {
		expect(extractPackageFromHref('/store/apps/dev?id=some-dev')).toBeNull();
	});

	it('returns null for null input', () => {
		expect(extractPackageFromHref(null)).toBeNull();
	});
});

describe('isValidPackageName', () => {
	it('accepts valid package names', () => {
		expect(isValidPackageName('com.charles.octopulse')).toBe(true);
		expect(isValidPackageName('com.hartmann.pixeldream')).toBe(true);
		expect(isValidPackageName('com.a.b')).toBe(true);
	});

	it('rejects invalid package names', () => {
		expect(isValidPackageName('invalid')).toBe(false);
		expect(isValidPackageName('123package')).toBe(false);
		expect(isValidPackageName('com.')).toBe(false);
	});

	it('rejects excessively long names', () => {
		const long = 'a'.repeat(151) + '.com';
		expect(isValidPackageName(long)).toBe(false);
	});
});

describe('PlayStoreCatalogProvider.discoverApps', () => {
	const provider = new PlayStoreCatalogProvider();

	it('discovers all packages from the developer listing fixture', async () => {
		const html = loadFixture('developer-listing.html');
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(
			new Response(html, { status: 200, headers: { 'content-type': 'text/html' } }),
		);

		const packages = await provider.discoverApps('Hartmann+Studios');

		expect(packages.length).toBe(20);
		expect(packages).toContain('com.charles.octopulse');
		expect(packages).toContain('com.hartmann.pixeldream');
		expect(packages).toContain('com.charles.nutrisnap');
		expect(packages).toContain('com.chartmann.knightfall');
		expect(packages).toContain('com.charles.warmwords');

		vi.restoreAllMocks();
	});

	it('handles fetch failure gracefully', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(
			new Response('Not found', { status: 404 }),
		);

		await expect(provider.discoverApps('Hartmann+Studios')).rejects.toThrow();

		vi.restoreAllMocks();
	});
});

describe('PlayStoreCatalogProvider.fetchAppMetadata', () => {
	const provider = new PlayStoreCatalogProvider();

	it('parses app detail page with itemprop attributes', async () => {
		const html = loadFixture('app-detail-octopulse.html');
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(
			new Response(html, { status: 200, headers: { 'content-type': 'text/html' } }),
		);

		const meta = await provider.fetchAppMetadata('com.charles.octopulse');

		expect(meta.packageName).toBe('com.charles.octopulse');
		expect(meta.name).toBe('OctoPulse: OctoPrint Companion');
		expect(meta.shortDescription).toBe('Control your OctoPrint 3D printer from your phone. Monitor & print on Wi-Fi.');
		expect(meta.category).toBe('Tools');
		expect(meta.developer).toBe('Hartmann Studios');
		expect(meta.storeUrl).toBe('https://play.google.com/store/apps/details?id=com.charles.octopulse');
		expect(meta.isFree).toBe(true);
		expect(meta.priceText).toBe('Free');
		expect(meta.iconUrl).toMatch(/https:\/\/play-lh\.googleusercontent\.com\//);

		vi.restoreAllMocks();
	});

	it('returns minimal metadata on fetch failure', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(
			new Response('Not found', { status: 404 }),
		);

		const meta = await provider.fetchAppMetadata('com.example.app');

		expect(meta.packageName).toBe('com.example.app');
		expect(meta.storeUrl).toBe('https://play.google.com/store/apps/details?id=com.example.app');

		vi.restoreAllMocks();
	});
});

describe('parseRating', () => {
	it('extracts rating from itemprop meta tags', () => {
		const html = '<meta itemprop="ratingValue" content="4.5"><meta itemprop="ratingCount" content="1234">';
		const result = parseRating(html);
		expect(result.rating).toBe(4.5);
		expect(result.ratingCount).toBe(1234);
	});

	it('extracts rating from rated-by pattern', () => {
		const html = 'Rated 4.2 by 5,678';
		const result = parseRating(html);
		expect(result.rating).toBe(4.2);
		expect(result.ratingCount).toBe(5678);
	});

	it('extracts rating from out-of-5 stars pattern', () => {
		const html = '4.3 out of 5 stars';
		const result = parseRating(html);
		expect(result.rating).toBe(4.3);
	});

	it('returns null when no rating info found', () => {
		const html = '<html>no rating here</html>';
		const result = parseRating(html);
		expect(result.rating).toBeNull();
		expect(result.ratingCount).toBeNull();
	});
});

describe('parseInstallCount', () => {
	it('extracts from itemprop numDownloads', () => {
		const html = '<meta itemprop="numDownloads" content="100,000+ - 500,000">';
		const result = parseInstallCount(html);
		expect(result.installText).toBe('100,000+ - 500,000');
		expect(result.estimatedInstalls).toBe(100000);
	});

	it('extracts from aria-label with install context', () => {
		const html = '<div aria-label="100K+ installs">Some text</div>';
		const result = parseInstallCount(html);
		expect(result.estimatedInstalls).toBe(100000);
	});

	it('returns null when no install data found', () => {
		const html = '<html>no installs info</html>';
		const result = parseInstallCount(html);
		expect(result.installText).toBeNull();
		expect(result.estimatedInstalls).toBeNull();
	});
});

describe('parseInstallValue', () => {
	it('parses K values', () => {
		expect(parseInstallValue('100K+')).toBe(100000);
		expect(parseInstallValue('1.5K')).toBe(1500);
	});

	it('parses M values', () => {
		expect(parseInstallValue('5M+')).toBe(5000000);
	});

	it('parses B values', () => {
		expect(parseInstallValue('2B+')).toBe(2000000000);
	});

	it('parses plain numbers', () => {
		expect(parseInstallValue('500')).toBe(500);
	});

	it('parses range format (lower bound)', () => {
		expect(parseInstallValue('100,000+ - 500,000')).toBe(100000);
	});

	it('returns null for invalid input', () => {
		expect(parseInstallValue('abc')).toBeNull();
	});
});

describe('parseCategory', () => {
	it('extracts from meta itemprop genre', () => {
		const html = '<meta itemprop="genre" content="Tools">';
		expect(parseCategory(html)).toBe('Tools');
	});

	it('extracts from category link', () => {
		const html = '<a href="/store/apps/category/TOOLS">Tools</a>';
		expect(parseCategory(html)).toBe('TOOLS');
	});

	it('returns null when no category found', () => {
		const html = '<html>no genre</html>';
		expect(parseCategory(html)).toBeNull();
	});
});

describe('parsePrice', () => {
	it('detects free apps', () => {
		const html = '<div data-is-free="true"></div>';
		const result = parsePrice(html);
		expect(result.isFree).toBe(true);
		expect(result.priceText).toBe('Free');
	});

	it('detects paid apps', () => {
		const html = '<div data-is-free="false"></div><meta itemprop="price" content="2.99">';
		const result = parsePrice(html);
		expect(result.isFree).toBe(false);
		expect(result.priceText).toBe('$2.99');
	});

	it('returns null when price info not found', () => {
		const html = '<html>no price info</html>';
		const result = parsePrice(html);
		expect(result.isFree).toBeNull();
		expect(result.priceText).toBeNull();
	});
});

describe('parseAppDetail', () => {
	it('parses complete app detail page from fixture', () => {
		const html = loadFixture('app-detail-octopulse.html');
		const result = parseAppDetail(html, 'com.charles.octopulse', 'https://play.google.com/store/apps/details?id=com.charles.octopulse');

		expect(result.packageName).toBe('com.charles.octopulse');
		expect(result.name).toBe('OctoPulse: OctoPrint Companion');
		expect(result.shortDescription).toBe('Control your OctoPrint 3D printer from your phone. Monitor & print on Wi-Fi.');
		expect(result.category).toBe('Tools');
		expect(result.developer).toBe('Hartmann Studios');
		expect(result.isFree).toBe(true);
		expect(result.priceText).toBe('Free');
		expect(result.iconUrl).toMatch(/play-lh\.googleusercontent\.com/);
		expect(result.rating).toBeNull();
		expect(result.ratingCount).toBeNull();
		expect(result.estimatedInstalls).toBeNull();
	});
});
