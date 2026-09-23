import type { Env } from '../src/index';
import type { CatalogApp, GlobalConfig, AppConfig } from '../src/types';
import { DEFAULT_CONFIG } from '../src/types';

type Row = Record<string, unknown>;

class MockD1Statement {
	private _query: string;
	private _bindings: unknown[] = [];
	resultRows: Row[] = [];
	_db: MockD1Database | null = null;

	constructor(query: string, db: MockD1Database | null = null) {
		this._query = query;
		this._db = db;
	}

	bind(...bindings: unknown[]): MockD1Statement {
		const clone = new MockD1Statement(this._query, this._db);
		clone.resultRows = this.resultRows;
		clone._bindings = bindings;
		return clone;
	}

	first<T = any>(): T | null {
		if (!this.resultRows || this.resultRows.length === 0) return null;
		return this.resultRows[0] as T;
	}

	all<T = any>(): { results: T[]; success: boolean } {
		return {
			results: (this.resultRows ?? []) as unknown as T[],
			success: true,
		};
	}

	run(): { success: boolean } {
		if (!this._db) return { success: true };
		return this._db._executeRun(this._query, this._bindings);
	}

	get query(): string {
		return this._query;
	}

	get boundValues(): unknown[] {
		return this._bindings;
	}
}

class MockD1Database {
	private tables: Map<string, Row[]> = new Map();
	executed: string[] = [];

	private TABLE_NAMES = [
		'global_config', 'app_config', 'catalog_apps', 'catalog_refreshes',
		'promo_stats', 'promo_events',
	];

	constructor() {
		for (const table of this.TABLE_NAMES) {
			this.tables.set(table, []);
		}
	}

	prepare(query: string): MockD1Statement {
		const stmt = new MockD1Statement(query, this);
		const table = this._findTable(query);
		if (table) {
			stmt.resultRows = this.tables.get(table) ?? [];
		}
		return stmt;
	}

	_executeRun(query: string, bindings: unknown[]): { success: boolean } {
		const table = this._findTable(query);
		if (!table) return { success: true };

		const data = this.tables.get(table) ?? [];
		const upper = query.trim().toUpperCase();
		const cols = this._extractColumns(query);

		if (upper.startsWith('INSERT')) {
			const row: Row = {};
			for (let i = 0; i < cols.length && i < bindings.length; i++) {
				row[cols[i]] = bindings[i];
			}

			const conflictIdx = query.toUpperCase().indexOf('ON CONFLICT');
			const conflictPart = conflictIdx >= 0 ? query.substring(conflictIdx) : '';
			const conflictCols = this._extractConflictColumns(conflictPart);

			if (conflictCols.length > 0) {
				const existingIdx = data.findIndex((r) =>
					conflictCols.every((c) => r[c] === bindings[cols.indexOf(c)])
				);
				if (existingIdx >= 0) {
					const setOps = this._extractSetOps(query);
					for (const { col, colIdx, mode } of setOps) {
						const bindingVal = colIdx >= 0 ? bindings[colIdx] : 0;
						const existingVal = Number(data[existingIdx][col]) || 0;
						if (mode === 'add') {
							data[existingIdx][col] = existingVal + Number(bindingVal);
						} else {
							data[existingIdx][col] = bindingVal;
						}
					}
				} else {
					data.push(row);
				}
			} else {
				data.push(row);
			}
			this.tables.set(table, data);
		} else if (upper.startsWith('UPDATE')) {
			const whereIdx = query.toUpperCase().indexOf('WHERE');
			if (whereIdx >= 0) {
				const whereClause = query.substring(whereIdx);
				const col = this._extractWhereColumn(whereClause);
				const val = bindings[0];
				if (col) {
					const setCols = this._extractSetColumns(query);
					for (const row of data) {
						if (row[col] === val) {
							const setIndices = setCols.map((c) => cols.indexOf(c)).filter((i) => i >= 0);
							let bindIdx = 1;
							for (const setCol of setCols) {
								const colIdx = setIndices[setCols.indexOf(setCol)];
								if (colIdx >= 0) {
									row[setCol] = bindings[bindIdx];
									bindIdx++;
								}
							}
						}
					}
				}
			}
		} else if (upper.startsWith('DELETE')) {
			this.tables.set(table, []);
		}

		return { success: true };
	}

	exec(sql: string): { results: any[]; success: boolean } {
		this.executed.push(sql);
		for (const table of this.TABLE_NAMES) {
			this.tables.set(table, []);
		}
		return { results: [], success: true };
	}

	batch(statements: MockD1Statement[]): { success: boolean } {
		for (const stmt of statements) {
			stmt._db?._executeRun(stmt.query, stmt.boundValues);
		}
		return { success: true };
	}

	private _findTable(query: string): string | null {
		const upper = query.toUpperCase();
		for (const table of this.TABLE_NAMES) {
			if (upper.includes(table.toUpperCase())) return table;
		}
		return null;
	}

	_extractColumns(query: string): string[] {
		const match = query.match(/\(([^)]+)\)\s*(?:VALUES|ON CONFLICT)/i);
		if (match) {
			return match[1].split(',').map((s) => s.trim().replace(/[?`"]/g, ''));
		}
		return [];
	}

	private _extractConflictColumns(part: string): string[] {
		const match = part.match(/ON CONFLICT\s*\(\s*([^)]+?)\s*\)/i);
		if (match) {
			return match[1].split(',').map((s) => s.trim().replace(/[?`"]/g, ''));
		}
		return [];
	}

	private _extractWhereColumn(whereClause: string): string | null {
		const match = whereClause.match(/WHERE\s+(\w+)\s*=/i);
		return match ? match[1] : null;
	}

	_extractSetColumns(query: string): string[] {
		return this._extractSetOps(query).map((op) => op.col);
	}

	private _extractSetOps(query: string): { col: string; colIdx: number; mode: 'set' | 'add' }[] {
		const setMatch = query.match(/SET\s+([\s\S]+?)\s*(?:WHERE|ON CONFLICT\s*\(|\s*$)/i);
		if (!setMatch) return [];
		const cols = this._extractColumns(query);
		return setMatch[1].split(',').map((s) => {
			const trimmed = s.trim();
			const col = trimmed.split('=')[0].trim().replace(/[?`"]/g, '');
			const colIdx = cols.indexOf(col);
			const mode = trimmed.includes('excluded.') && /[+\-]/.test(trimmed.split('excluded')[0]) ? 'add' : 'set';
			return { col, colIdx, mode };
		});
	}

	insert(table: string, row: Row): void {
		const data = this.tables.get(table) ?? [];
		data.push(row);
		this.tables.set(table, data);
	}

	getRows(table: string): Row[] {
		return this.tables.get(table) ?? [];
	}

	setRows(table: string, rows: Row[]): void {
		this.tables.set(table, rows);
	}

	clear(): void {
		for (const table of this.TABLE_NAMES) {
			this.tables.set(table, []);
		}
	}
}

class MockKV implements KVNamespace {
	data: Map<string, string> = new Map();

	async get(key: string, opts?: { type?: string; cacheTtl?: number }): Promise<any> {
		const val = this.data.get(key);
		if (val === undefined) return null;
		if (opts?.type === 'json') return JSON.parse(val);
		return val;
	}

	async put(key: string, value: string, opts?: { expirationTtl?: number; metadata?: any; customMetadata?: any }): Promise<void> {
		this.data.set(key, value);
	}

	async delete(key: string): Promise<void> {
		this.data.delete(key);
	}

	async list(opts?: { prefix?: string; limit?: number }): Promise<{ keys: string[]; list_complete: boolean; cacheStatus?: string; count?: number }> {
		const keys = Array.from(this.data.keys()).filter((k) => !opts?.prefix || k.startsWith(opts.prefix));
		return { keys: opts?.limit ? keys.slice(0, opts.limit) : keys, list_complete: true, count: keys.length };
	}

	async getWithMetadata?(key: string, opts?: { type?: string }): Promise<{ value: any; metadata: any }> {
		const val = this.data.get(key);
		if (val === undefined) return { value: null, metadata: null };
		if (opts?.type === 'json') return { value: JSON.parse(val), metadata: null };
		return { value: val, metadata: null };
	}
}

export const ADMIN_TOKEN = 'test-admin-token';

export function makeMockEnv(apps: CatalogApp[] = []): Env & { _db: MockD1Database; _kv: MockKV } {
	const db = new MockD1Database();
	const kv = new MockKV();

	// Seed catalog_apps from the provided apps
	for (const app of apps) {
		db.insert('catalog_apps', {
			package_name: app.packageName,
			name: app.name,
			icon_url: app.iconUrl,
			store_url: app.storeUrl,
			short_description: app.shortDescription,
			rating: app.rating,
			rating_count: app.ratingCount,
			install_text: app.installText,
			estimated_installs: app.estimatedInstalls,
			category: app.category,
			price_text: app.priceText,
			is_free: app.isFree ? 1 : 0,
			developer: app.developer,
			first_discovered_at: app.firstDiscoveredAt,
			last_seen_at: app.lastSeenAt,
			enabled: app.enabled ? 1 : 0,
			promotion_multiplier: app.promotionMultiplier,
		});
	}

	// Pre-seed global_config with defaults so config module doesn't need KV
	db.insert('global_config', { key: 'global', value: JSON.stringify(DEFAULT_CONFIG), updated_at: new Date().toISOString() });

	return {
		DB: db as unknown as D1Database,
		CROSS_PROMO_KV: kv as unknown as KVNamespace,
		ADMIN_TOKEN,
		DEVELOPER_ID: 'Hartmann+Studios',
		PLAY_STORE_BASE_URL: 'https://play.google.com',
		SDK_VERSION: '1.0.0',
		CATALOG_CACHE_TTL_SECONDS: '1800',
		RECOMMENDATION_CACHE_TTL_SECONDS: '3600',
		_db: db,
		_kv: kv,
	} as any;
}

export function makeMockEnvWithConfig(config: Partial<GlobalConfig> = {}): Env & { _db: MockD1Database; _kv: MockKV } {
	const env = makeMockEnv();
	env._db.insert('global_config', { key: 'global', value: JSON.stringify({ ...DEFAULT_CONFIG, ...config }), updated_at: new Date().toISOString() });
	return env;
}

export type MockEnv = ReturnType<typeof makeMockEnv>;
export { MockD1Database, MockKV, MockD1Statement, DEFAULT_CONFIG };
