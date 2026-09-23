-- Global configuration table (single row).
CREATE TABLE IF NOT EXISTS global_config (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- Per-source-app configuration overrides.
CREATE TABLE IF NOT EXISTS app_config (
  source_package TEXT PRIMARY KEY,
  enabled INTEGER NOT NULL DEFAULT 1,
  max_cards INTEGER,
  placements TEXT,
  updated_at TEXT NOT NULL
);

-- Normalized app catalog (single source of truth for serving).
CREATE TABLE IF NOT EXISTS catalog_apps (
  package_name TEXT PRIMARY KEY,
  name TEXT,
  icon_url TEXT,
  store_url TEXT NOT NULL,
  short_description TEXT,
  rating REAL,
  rating_count INTEGER,
  install_text TEXT,
  estimated_installs INTEGER,
  category TEXT,
  price_text TEXT,
  is_free INTEGER,
  developer TEXT,
  first_discovered_at TEXT,
  last_seen_at TEXT,
  enabled INTEGER NOT NULL DEFAULT 1,
  promotion_multiplier REAL NOT NULL DEFAULT 1.0
);

-- Catalog refresh diagnostic history.
CREATE TABLE IF NOT EXISTS catalog_refreshes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  started_at TEXT NOT NULL,
  completed_at TEXT,
  status TEXT NOT NULL,
  apps_discovered INTEGER,
  apps_rejected INTEGER,
  metadata_failures INTEGER,
  suspicious BOOLEAN DEFAULT 0,
  error_message TEXT
);

-- Aggregate promotion analytics stats (incremented per event).
CREATE TABLE IF NOT EXISTS promo_stats (
  target_package TEXT NOT NULL,
  source_package TEXT NOT NULL,
  placement TEXT NOT NULL,
  selection_type TEXT NOT NULL,
  impressions INTEGER NOT NULL DEFAULT 0,
  clicks INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (target_package, source_package, placement, selection_type)
);

-- Raw analytics events (time-series). Bounded by TTL / sampling.
CREATE TABLE IF NOT EXISTS promo_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  source_package TEXT NOT NULL,
  target_package TEXT NOT NULL,
  placement TEXT NOT NULL,
  rank_position INTEGER,
  selection_type TEXT NOT NULL,
  session_id TEXT,
  recommendation_request_id TEXT,
  sdk_version TEXT,
  timestamp TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_promo_events_type ON promo_events (event_type);
CREATE INDEX IF NOT EXISTS idx_promo_events_ts ON promo_events (timestamp);
CREATE INDEX IF NOT EXISTS idx_promo_events_session ON promo_events (session_id);
