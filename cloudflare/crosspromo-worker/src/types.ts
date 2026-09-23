/** Normalized internal model for a Hartmann Studios app discovered on Google Play. */
export interface CatalogApp {
	packageName: string;
	name: string | null;
	iconUrl: string | null;
	storeUrl: string;
	shortDescription: string | null;
	rating: number | null;
	ratingCount: number | null;
	installText: string | null;
	estimatedInstalls: number | null;
	category: string | null;
	priceText: string | null;
	isFree: boolean | null;
	developer: string | null;
	firstDiscoveredAt: string;
	lastSeenAt: string;
	enabled: boolean;
	promotionMultiplier: number;
}

/** A normalized recommendation result returned to the Android client. */
export interface PromoApp {
	packageName: string;
	name: string | null;
	iconUrl: string | null;
	shortDescription: string | null;
	rating: number | null;
	ratingCount: number | null;
	installText: string | null;
	storeUrl: string;
	selectionType: 'popular' | 'random' | 'new_app_boost';
}

export interface RecommendationResponse {
	version: number;
	requestId: string;
	generatedAt: string;
	expiresAt: string;
	apps: PromoApp[];
}

export interface HealthResponse {
	status: 'ok' | 'degraded';
	catalogApps: number;
	lastCatalogRefresh: string | null;
	lastSuccessfulRefresh: string | null;
	cacheStatus: 'fresh' | 'stale' | 'empty';
	lastDiscoverySource: string | null;
}

export interface GlobalConfig {
	enabled: boolean;
	popularWeight: number;
	randomWeight: number;
	newAppBoostDays: number;
	defaultLimit: number;
	maxLimit: number;
}

export interface AppConfig {
	sourcePackage: string;
	enabled: boolean;
	maxCards: number;
	placements: string[];
}

export interface AnalyticsEvent {
	event: string;
	sourcePackage: string;
	targetPackage: string;
	placement: string;
	rankPosition: number;
	selectionType: 'popular' | 'random' | 'new_app_boost';
	sessionId: string;
	recommendationRequestId: string;
	sdkVersion?: string;
}

export const DEFAULT_CONFIG: GlobalConfig = {
	enabled: true,
	popularWeight: 0.65,
	randomWeight: 0.35,
	newAppBoostDays: 14,
	defaultLimit: 3,
	maxLimit: 6,
};

export const SDK_VERSION = '1.0.0';
export const CATALOG_CACHE_KEY = 'catalog:v1';
export const RECS_CACHE_PREFIX = 'recommendations:v1';
export const LAST_KNOWN_GOOD_KEY = 'catalog:last-known-good:v1';
export const REFRESH_DIAGNOSTIC_KEY = 'catalog:diagnostic:v1';
