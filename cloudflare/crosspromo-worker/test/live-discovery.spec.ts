import { describe, it, expect } from 'vitest';
import { PlayStoreCatalogProvider } from '../src/catalog';

declare const process: { env: Record<string, string | undefined> };

// Hits the real Google Play developer page. Skipped by default (no network in CI);
// run manually with: LIVE_DISCOVERY_TEST=1 npx vitest run test/live-discovery.spec.ts
describe.skipIf(!process.env.LIVE_DISCOVERY_TEST)('LIVE Play Store discovery', () => {
	it('discovers real Hartmann Studios packages and fetches real metadata', async () => {
		const provider = new PlayStoreCatalogProvider();
		const packages = await provider.discoverApps('Hartmann Studios');
		expect(packages.length).toBeGreaterThan(0);

		const meta = await provider.fetchAppMetadata(packages[0]);
		expect(meta.packageName).toBe(packages[0]);
		console.log(`Discovered ${packages.length} packages; sample metadata:`, meta);
	}, 30000);
});
