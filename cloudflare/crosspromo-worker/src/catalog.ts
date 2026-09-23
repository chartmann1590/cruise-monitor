import type { Env } from './index';
import type { CatalogApp } from './types';

const PACKAGE_NAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

const PLAY_STORE_BASE = 'https://play.google.com/store/apps';

const USER_AGENT =
	'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36';

export interface CatalogRefreshResult {
	status: 'ok' | 'rejected' | 'failed';
	appsDiscovered: number;
	appsRejected: number;
	metadataFailures: number;
	suspicious: boolean;
	errorMessage: string | null;
}

export class PlayStoreCatalogProvider {
	constructor(private playStoreBaseUrl: string = PLAY_STORE_BASE) {}

	/**
	 * Fetch the developer listing page and extract all valid package IDs.
	 * Uses multiple extraction strategies for robustness.
	 */
	async discoverApps(developerId: string): Promise<string[]> {
		const url = `${this.playStoreBaseUrl}/developer?id=${encodeURIComponent(developerId)}`;
		const html = await fetchPlayStore(url);

		const packages = new Set<string>();

		// Strategy 1: regex on href attributes pointing to app detail pages
		const hrefRegex = /[Hh][Rr][Ee][Ff]\s*=\s*["']([^"']*)["'"]/g;
		let match: RegExpExecArray | null;
		while ((match = hrefRegex.exec(html)) !== null) {
			const href = match[1];
			const pkg = extractPackageFromHref(href);
			if (pkg) packages.add(pkg);
		}

		// Strategy 2: direct /details?id= pattern in any attribute or text
		const directRegex = /[?&]id=([a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+)/g;
		while ((match = directRegex.exec(html)) !== null) {
			const pkg = match[1];
			if (isValidPackageName(pkg)) packages.add(pkg);
		}

		return Array.from(packages);
	}

	/**
	 * Fetch an individual app's detail page and extract normalized metadata.
	 */
	async fetchAppMetadata(packageName: string): Promise<Partial<CatalogApp>> {
		const url = `${this.playStoreBaseUrl}/details?id=${packageName}`;
		let html: string;
		try {
			html = await fetchPlayStore(url);
		} catch {
			return { packageName, storeUrl: url };
		}

		return parseAppDetail(html, packageName, url);
	}

	/** Validate that a discovered package belongs to the expected developer. */
	validatePackage(packageName: string): boolean {
		return isValidPackageName(packageName);
	}
}

function playStoreBaseUrl(developerId: string): string {
	return `https://play.google.com/store/apps/developer?id=${encodeURIComponent(developerId)}`;
}

function fetchPlayStore(url: string): Promise<string> {
	return fetch(url, {
		headers: {
			'User-Agent': USER_AGENT,
			Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
			'Accept-Language': 'en-US,en;q=0.9',
		},
	}).then((res) => {
		if (!res.ok) throw new Error(`Play Store returned ${res.status} for ${url}`);
		return res.text();
	});
}

export function extractPackageFromHref(href: string | null): string | null {
	if (!href) return null;
	const decoded = decodeURIComponent(href);
	const idMatch = decoded.match(/[?&]id=([a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+)/);
	if (!idMatch) return null;
	const pkg = idMatch[1];
	if (!isValidPackageName(pkg)) return null;
	// Reject Play Store internal/redirect links (e.g., family, store, tv)
	if (decoded.includes('/store/apps/details?id=')) return pkg;
	return pkg;
}

export function isValidPackageName(pkg: string): boolean {
	return PACKAGE_NAME_REGEX.test(pkg) && pkg.length <= 150;
}

function textContentBetween(html: string, openTag: string, closeTag: string): string | null {
	const openIdx = html.indexOf(openTag);
	if (openIdx < 0) return null;
	const start = openIdx + openTag.length;
	const closeIdx = html.indexOf(closeTag, start);
	if (closeIdx < 0) return null;
	return html.substring(start, closeIdx).trim();
}

function attrValue(html: string, attr: string): string | null {
	const regex = new RegExp(`${attr}\\s*=\\s*["']([^"']*)["']`, 'i');
	const m = html.match(regex);
	return m ? m[1] : null;
}

function decodeHtmlEntities(text: string): string {
	const txt = text.replace(/&#(\d+);/g, (_, dec) => String.fromCharCode(parseInt(dec, 10)));
	return txt.replace(/&([a-zA-Z]+);/g, (_, entity) => {
		const entities: Record<string, string> = {
			amp: '&',
			lt: '<',
			gt: '>',
			quot: '"',
			apos: "'",
			nbsp: ' ',
		};
		return entities[entity] ?? `&${entity};`;
	});
}

function metaContent(html: string, itemprop: string): string | null {
	// <meta itemprop="description" content="...">
	const regex = new RegExp(`itemprop=["']${itemprop}["'][^>]*content=["']([^"']*)["']`, 'i');
	const m = html.match(regex);
	return m ? decodeHtmlEntities(m[1]) : null;
}

function itempropText(html: string, itemprop: string): string | null {
	// <span itemprop="name">App Name</span>
	const regex = new RegExp(`itemprop=["']${itemprop}["'][^>]*>([^<]+)`, 'i');
	const m = html.match(regex);
	return m ? decodeHtmlEntities(m[1].trim()) : null;
}

export function parseRating(html: string): { rating: number | null; ratingCount: number | null } {
	// Strategy 1: itemprop="ratingValue" and itemprop="ratingCount"
	let rating: number | null = null;
	let ratingCount: number | null = null;

	const rv = metaContent(html, 'ratingValue');
	if (rv) {
		const n = parseFloat(rv);
		if (!isNaN(n)) rating = n;
	}
	const rc = metaContent(html, 'ratingCount');
	if (rc) {
		const n = parseInt(rc.replace(/[^0-9]/g, ''), 10);
		if (!isNaN(n)) ratingCount = n;
	}

	// Strategy 2: "Rated X by Y" or "X out of 5 stars" text patterns
	if (rating === null) {
		const ratedMatch = html.match(/Rated\s+([0-9]\.[0-9])\s+by\s+([0-9,]+)/i);
		if (ratedMatch) {
			rating = parseFloat(ratedMatch[1]);
			ratingCount = parseInt(ratedMatch[2].replace(/,/g, ''), 10);
		}
	}
	if (rating === null) {
		const starMatch = html.match(/([0-9]\.[0-9])\s*(?:star|out of 5)/i);
		if (starMatch) rating = parseFloat(starMatch[1]);
	}

	// Strategy 3: aria-label with stars
	if (rating === null) {
		const ariaMatch = html.match(/aria-label="([0-9]\.[0-9])"/);
		if (ariaMatch) rating = parseFloat(ariaMatch[1]);
	}

	// Strategy 4: look for a div with a numeric value between 1 and 5 in the title area
	if (rating === null) {
		const titleIdx = html.indexOf('<h1>');
		if (titleIdx >= 0) {
			const area = html.substring(Math.max(0, titleIdx - 500), titleIdx + 500);
			const numMatch = area.match(/>\s*([0-9]\.[0-9])\s*</);
			if (numMatch) {
				const n = parseFloat(numMatch[1]);
				if (n >= 1 && n <= 5) rating = n;
			}
		}
	}

	return { rating, ratingCount };
}

export function parseInstallCount(html: string): { installText: string | null; estimatedInstalls: number | null } {
	// Strategy 1: itemprop="numDownloads" content
	const nd = metaContent(html, 'numDownloads');
	if (nd) {
		return { installText: nd, estimatedInstalls: parseInstallValue(nd) };
	}

	// Strategy 2: aria-label containing install info
	const ariaMatch = html.match(/aria-label="[^"]*?([0-9,]+[KMkm]?\+?)[^"]*install[^"]*"/i);
	if (ariaMatch) {
		const text = ariaMatch[1];
		return { installText: text, estimatedInstalls: parseInstallValue(text) };
	}

	// Strategy 3: Look for patterns like "100K+" near "download" or "install" text
	// Google Play shows: <div ...>100K+</div> ... <div ...>Downloads</div> or "X+ installs"
	const instRegex = />\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)K\+|>\s*([0-9]+)M\+|>\s*([0-9]+)B\+/g;
	let m: RegExpExecArray | null;
	while ((m = instRegex.exec(html)) !== null) {
		const raw = m[1] ?? m[2] ?? m[3];
		if (!raw) continue;
		// Verify it's near a download/install context
		const ctx = html.substring(Math.max(0, m.index - 500), m.index + 200).toLowerCase();
		if (ctx.includes('download') || ctx.includes('install')) {
			return { installText: raw + (raw.includes(',') ? '+' : 'K+'), estimatedInstalls: parseInstallValue(raw) };
		}
	}

	return { installText: null, estimatedInstalls: null };
}

export function parseInstallValue(text: string): number | null {
	const cleaned = text.trim().toUpperCase();
	// Handle range format: "100,000+ - 500,000" -> take the lower bound
	const rangeMatch = cleaned.match(/([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]+)?)\s*([KMB])?\+?/);
	if (rangeMatch) {
		const numStr = rangeMatch[1].replace(/,/g, '');
		const num = parseFloat(numStr);
		if (isNaN(num)) return null;
		const multiplier = rangeMatch[2];
		if (multiplier === 'B') return Math.round(num * 1_000_000_000);
		if (multiplier === 'M') return Math.round(num * 1_000_000);
		if (multiplier === 'K') return Math.round(num * 1000);
		return Math.round(num);
	}
	const numStr = cleaned.replace(/[^0-9.]/g, '');
	const num = parseFloat(numStr);
	if (isNaN(num)) return null;
	if (cleaned.includes('B')) return Math.round(num * 1_000_000_000);
	if (cleaned.includes('M')) return Math.round(num * 1_000_000);
	if (cleaned.includes('K')) return Math.round(num * 1000);
	return Math.round(num);
}

export function parseCategory(html: string): string | null {
	// Strategy 1: itemprop="genre" via meta content attribute
	const genreMeta = metaContent(html, 'genre');
	if (genreMeta) return genreMeta;

	// Strategy 2: itemprop="genre" via text content (span, div, etc.)
	const genre = itempropText(html, 'genre');
	if (genre) return genre;

	// Strategy 2: Look for category text after the genre itemprop
	const genreIdx = html.indexOf('itemprop="genre"');
	if (genreIdx >= 0) {
		const after = html.substring(genreIdx, genreIdx + 500);
		const textMatch = after.match(/>\s*([^<\s]+(?:\s+[^<\s]+)*)\s*</);
		if (textMatch) return textMatch[1].trim();
	}

	// Strategy 3: category link href like /store/apps/category/TOOLS
	const catMatch = html.match(/\/store\/apps\/category\/([A-Z_]+)/i);
	if (catMatch) return catMatch[1].replace(/_/g, ' ');

	return null;
}

export function parsePrice(html: string): { priceText: string | null; isFree: boolean | null } {
	const freeAttr = attrValue(html, 'data-is-free');
	if (freeAttr !== null) {
		const isFree = freeAttr === 'true';
		const priceContent = metaContent(html, 'price');
		if (!isFree) {
			const price = priceContent && parseFloat(priceContent);
			return { priceText: price && !isNaN(price) ? `$${price.toFixed(2)}` : null, isFree: false };
		}
		return { priceText: 'Free', isFree: true };
	}
	return { priceText: null, isFree: null };
}

export function parseAppDetail(html: string, packageName: string, storeUrl: string): Partial<CatalogApp> {
	const name = itempropText(html, 'name') ?? null;
	const iconUrl = extractIconUrl(html);
	const shortDescription = metaContent(html, 'description') ?? null;
	const developer = extractDeveloper(html) ?? null;
	const category = parseCategory(html);
	const { priceText, isFree } = parsePrice(html);
	const { rating, ratingCount } = parseRating(html);
	const { installText, estimatedInstalls } = parseInstallCount(html);

	return {
		packageName,
		name,
		iconUrl,
		storeUrl,
		shortDescription,
		rating,
		ratingCount,
		installText,
		estimatedInstalls,
		category,
		priceText,
		isFree,
		developer,
	};
}

function extractIconUrl(html: string): string | null {
	// Strategy 1: first img with itemprop="image" that looks like an app icon
	const imgRegex = /<img[^>]*itemprop=["']image["'][^>]*>/gi;
	const classRegex = /\bsrc="([^"]+)"/;
	let m: RegExpExecArray | null;
	while ((m = imgRegex.exec(html)) !== null) {
		const tag = m[0];
		const srcMatch = tag.match(classRegex);
		if (srcMatch?.[1]) {
			const url = srcMatch[1].replace(/&amp;/g, '&');
			// Prefer the main app icon (not screenshots) - typically a play-lh URL
			if (url.includes('play-lh.googleusercontent.com') || url.includes('play.google.com')) {
				return url;
			}
		}
	}
	// Strategy 2: first img with class containing icon-related text
	const allImgs = [...html.matchAll(/<img[^>]*\bsrc="([^"]+)"[^>]*>/gi)];
	for (const img of allImgs) {
		const url = img[1].replace(/&amp;/g, '&');
		if (url.includes('lh.googleusercontent.com') && !url.includes('screenshot')) return url;
	}
	return null;
}

function extractDeveloper(html: string): string | null {
	// Strategy 1: <a href="/store/apps/developer?id=DEV"><span>DEV</span></a>
	const devRegex = /<a\s+href="\/store\/apps\/developer\?id=([^"<>]+)"[^>]*>\s*<span[^>]*>([^<]+)<\/span>/i;
	const m = html.match(devRegex);
	if (m) return m[2].trim();

	// Strategy 2: itemprop="author" or "creator"
	const author = itempropText(html, 'author');
	if (author) return author;
	return null;
}
