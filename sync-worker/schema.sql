-- fIPTV Sync-Server: Ersatz für Netlify Blobs
CREATE TABLE IF NOT EXISTS blobs (
  store TEXT NOT NULL,
  key TEXT NOT NULL,
  etag TEXT NOT NULL,
  metadata TEXT,
  parts INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (store, key)
);
CREATE TABLE IF NOT EXISTS blob_parts (
  store TEXT NOT NULL,
  key TEXT NOT NULL,
  part INTEGER NOT NULL,
  data TEXT NOT NULL,
  PRIMARY KEY (store, key, part)
);
