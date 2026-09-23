import type { Env } from './index';
import type { CatalogApp } from './types';
import { PlayStoreCatalogProvider } from './catalog';
import { readCatalog, writeCatalog, writeLastKnownGood, readLastKnownGood, validateCatalogRefresh, writeRefreshDiagnostic } from './cache';

const BATCH_CONCURRENCY = 8;
const MIN_APPROVED_APPS = 1;

export interface RefreshDiagnostics {
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

/**
 * Full catalog refresh: discover apps, fetch metadata, validate, and store.
 * Called by the cron trigger and admin endpoint.
 */
export async function runCatalogRefresh(env: Env): Promise<RefreshDiagnostics> {
	const startedAt = new Date().toISOString();
	const provider = new PlayStoreCatalogProvider();

	// Read previous catalog for safety validation
	const previousCatalog = await readCatalog(env);
	const previousCount = previousCatalog.length;

	let discoveredPackages: string[];
	try {
		discoveredPackages = await provider.discoverApps(env.DEVELOPER_ID);
	} catch (error) {
		return {
			refreshStartedAt: startedAt,
			refreshCompletedAt: new Date().toISOString(),
			status: 'failed',
			appsDiscovered: 0,
			appsRejected: 0,
			metadataFailures: 0,
			previousCount,
			newCount: 0,
			suspicious: false,
			errorMessage: error instanceof Error ? error.message : 'Unknown discovery error',
		};
	}

	// Fetch metadata for all discovered packages in parallel batches
	const apps: CatalogApp[] = [];
	let metadataFailures = 0;
	let appsRejected = 0;

	for (let i = 0; i < discoveredPackages.length; i += BATCH_CONCURRENCY) {
		const batch = discoveredPackages.slice(i, i + BATCH_CONCURRENCY);
		const results = await Promise.allSettled(
			batch.map((pkg) => provider.fetchAppMetadata(pkg)),
		);

		for (const result of results) {
			if (result.status === 'fulfilled' && result.value) {
				const meta = result.value;
				if (meta.packageName) {
					const app: CatalogApp = {
						packageName: meta.packageName,
						name: meta.name ?? null,
						iconUrl: meta.iconUrl ?? null,
						storeUrl: meta.storeUrl ?? `https://play.google.com/store/apps/details?id=${meta.packageName}`,
						shortDescription: meta.shortDescription ?? null,
						rating: meta.rating ?? null,
						ratingCount: meta.ratingCount ?? null,
						installText: meta.installText ?? null,
						estimatedInstalls: meta.estimatedInstalls ?? null,
						category: meta.category ?? null,
						priceText: meta.priceText ?? null,
						isFree: meta.isFree ?? null,
						developer: meta.developer ?? null,
						firstDiscoveredAt: meta.firstDiscoveredAt ?? startedAt,
						lastSeenAt: startedAt,
						enabled: true,
						promotionMultiplier: 1.0,
					};
					apps.push(app);
				}
			}
			if (result.status === 'rejected') {
				metadataFailures++;
			}
		}
	}

	// Validate the new catalog against the previous one
	const validation = validateCatalogRefresh(previousCatalog, apps);

	if (!validation.valid) {
		// Suspicious refresh: do NOT overwrite the catalog, keep last-known-good
		const diag: RefreshDiagnostics = {
			refreshStartedAt: startedAt,
			refreshCompletedAt: new Date().toISOString(),
			status: 'rejected',
			appsDiscovered: apps.length,
			appsRejected,
			metadataFailures,
			previousCount,
			newCount: apps.length,
			suspicious: true,
			errorMessage: validation.reason,
		};
		await writeRefreshDiagnostic(env, diag);
		console.log(`[crosspromo] Catalog refresh rejected: ${validation.reason}`);
		return diag;
	}

	// Preserve firstDiscoveredAt for apps that were already in the catalog
	const now = new Date().toISOString();
	for (const app of apps) {
		const prev = previousCatalog.find((p) => p.packageName === app.packageName);
		if (prev && prev.firstDiscoveredAt) {
			app.firstDiscoveredAt = prev.firstDiscoveredAt;
		} else {
			app.firstDiscoveredAt = now;
		}
	}

	// Write the new catalog to D1 + KV, and update last-known-good
	await Promise.all([
		writeCatalog(env, apps),
		writeLastKnownGood(env, apps),
	]);

	const diag: RefreshDiagnostics = {
		refreshStartedAt: startedAt,
		refreshCompletedAt: now,
		status: 'ok',
		appsDiscovered: apps.length,
		appsRejected,
		metadataFailures,
		previousCount,
		newCount: apps.length,
		suspicious: false,
		errorMessage: null,
	};
	await writeRefreshDiagnostic(env, diag);
	console.log(`[crosspromo] Catalog refresh completed: ${apps.length} apps discovered`);
	return diag;
}
