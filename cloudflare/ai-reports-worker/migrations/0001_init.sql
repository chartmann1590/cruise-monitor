CREATE TABLE reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at TEXT NOT NULL,
  user_message TEXT NOT NULL,
  ai_message TEXT NOT NULL,
  reason_category TEXT NOT NULL,
  reason_detail TEXT,
  app_version TEXT,
  synced_to_github INTEGER NOT NULL DEFAULT 0,
  github_issue_number INTEGER
);

CREATE INDEX idx_reports_unsynced ON reports (synced_to_github);

CREATE TABLE rate_limits (
  ip_hash TEXT PRIMARY KEY,
  window_start TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0
);
