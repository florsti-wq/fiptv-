-- fIPTV: Datenbank für den eigenen Sync-Server einrichten.
-- In Supabase: SQL Editor → New query → diesen Inhalt einfügen → Run. Mehrfaches Ausführen schadet nicht.

-- ===== 20260618165000_arvio_cloud_backend =====
CREATE TABLE IF NOT EXISTS public.arvio_accounts (
  id uuid PRIMARY KEY DEFAULT (md5(random()::text || clock_timestamp()::text)::uuid),
  email text NOT NULL,
  email_normalized text NOT NULL UNIQUE,
  supabase_user_id uuid UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_arvio_accounts_supabase_user_id
  ON public.arvio_accounts (supabase_user_id);

CREATE TABLE IF NOT EXISTS public.account_sync_snapshots (
  account_id uuid PRIMARY KEY REFERENCES public.arvio_accounts(id) ON DELETE CASCADE,
  payload jsonb NOT NULL,
  payload_version integer NOT NULL DEFAULT 1,
  restore_rank integer NOT NULL DEFAULT 0,
  profile_count integer,
  scoped_coverage integer NOT NULL DEFAULT 0,
  payload_updated_at timestamptz,
  source text NOT NULL DEFAULT 'netlify',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_sync_snapshots_updated_at
  ON public.account_sync_snapshots (updated_at DESC);

CREATE TABLE IF NOT EXISTS public.account_sync_items (
  account_id uuid NOT NULL REFERENCES public.arvio_accounts(id) ON DELETE CASCADE,
  scope text NOT NULL,
  profile_id text NOT NULL DEFAULT '',
  entity_key text NOT NULL,
  payload jsonb,
  deleted_at timestamptz,
  version bigint NOT NULL DEFAULT 1,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, scope, profile_id, entity_key)
);

CREATE INDEX IF NOT EXISTS idx_account_sync_items_account_scope
  ON public.account_sync_items (account_id, scope, profile_id);

CREATE TABLE IF NOT EXISTS public.account_sync_delta_events (
  event_id bigserial PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES public.arvio_accounts(id) ON DELETE CASCADE,
  scope text NOT NULL,
  profile_id text NOT NULL DEFAULT '',
  entity_key text NOT NULL,
  operation text NOT NULL CHECK (operation IN ('upsert', 'delete')),
  payload jsonb,
  item_version bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_sync_delta_events_account_event
  ON public.account_sync_delta_events (account_id, event_id);

CREATE TABLE IF NOT EXISTS public.legacy_supabase_users (
  supabase_user_id uuid PRIMARY KEY,
  email text NOT NULL,
  email_normalized text NOT NULL,
  created_at timestamptz,
  last_sign_in_at timestamptz,
  imported_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_legacy_supabase_users_email
  ON public.legacy_supabase_users (email_normalized);

CREATE TABLE IF NOT EXISTS public.legacy_supabase_snapshots (
  supabase_user_id uuid PRIMARY KEY,
  email text,
  email_normalized text,
  payload jsonb NOT NULL,
  payload_version integer NOT NULL DEFAULT 1,
  restore_rank integer NOT NULL DEFAULT 0,
  profile_count integer,
  scoped_coverage integer NOT NULL DEFAULT 0,
  payload_updated_at timestamptz,
  source text NOT NULL DEFAULT 'supabase_export',
  imported_at timestamptz NOT NULL DEFAULT now(),
  claimed_account_id uuid REFERENCES public.arvio_accounts(id) ON DELETE SET NULL,
  claimed_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_legacy_supabase_snapshots_email
  ON public.legacy_supabase_snapshots (email_normalized);

CREATE TABLE IF NOT EXISTS public.app_usage_daily (
  usage_date date NOT NULL,
  account_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
  install_id text NOT NULL DEFAULT '',
  event_name text NOT NULL,
  event_count integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (usage_date, account_id, install_id, event_name)
);

CREATE TABLE IF NOT EXISTS public.legacy_supabase_rows (
  table_name text NOT NULL,
  supabase_user_id uuid,
  row_key text NOT NULL,
  row_data jsonb NOT NULL,
  imported_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (table_name, row_key)
);

CREATE INDEX IF NOT EXISTS idx_legacy_supabase_rows_user_table
  ON public.legacy_supabase_rows (supabase_user_id, table_name);

-- ===== 20260721110000_catalog_packs =====
CREATE TABLE IF NOT EXISTS public.catalog_packs (
  id uuid PRIMARY KEY DEFAULT (md5(random()::text || clock_timestamp()::text)::uuid),
  name text NOT NULL,
  url text NOT NULL,
  author text,
  version text DEFAULT '1.0.0',
  description text,
  catalogs jsonb NOT NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_catalog_packs_status ON public.catalog_packs(status);

-- Seed approved catalog packs if not already present
INSERT INTO public.catalog_packs (id, name, url, author, version, description, catalogs, status)
VALUES
  ('c0a37e5e-5b12-4eb0-a5ea-9d84c1737e51', 'Cinema Essentials', '/packs/cinema-essentials.json', 'ARVIO Team', '1.0.0', 'All the trending movies, popular lists, and upcoming releases you need for a perfect movie night.', '["Trending in Movies", "Top 10 Movies Today", "Top Movies This Week", "Coming Soon"]'::jsonb, 'approved'),
  ('c0a37e5e-5b12-4eb0-a5ea-9d84c1737e52', 'TV Show Binge Pack', '/packs/tv-binge.json', 'ARVIO Team', '1.0.0', 'Never miss an episode. Popular, trending, and latest airing series in one convenient bundle.', '["Trending in Shows", "Top 10 Shows Today", "Latest Airing"]'::jsonb, 'approved'),
  ('c0a37e5e-5b12-4eb0-a5ea-9d84c1737e53', 'Otaku & K-Drama Hub', '/packs/anime-kdrama.json', 'Community', '1.1.2', 'The ultimate pack for anime lovers and K-Drama fans. Auto-updated lists of trending episodes and releases.', '["Trending in Anime", "New in K-Dramas"]'::jsonb, 'approved'),
  ('c0a37e5e-5b12-4eb0-a5ea-9d84c1737e54', 'Action & Franchise Classics', '/packs/classics-franchises.json', 'Cinephile', '1.0.5', 'Full box-sets and classic franchise collections, including James Bond, Harry Potter, Lord of the Rings, and Jurassic Park.', '["James Bond Collection", "Harry Potter Collection", "The Matrix Collection", "Lord of the Rings and Hobbit Collection", "Jurassic Park Collection"]'::jsonb, 'approved')
ON CONFLICT (id) DO NOTHING;

-- ===== 20260722120000_catalog_pack_submission_guards =====
ALTER TABLE public.catalog_packs
  ADD COLUMN IF NOT EXISTS normalized_url text;

UPDATE public.catalog_packs
SET normalized_url = lower(
  rtrim(
    CASE
      WHEN url LIKE '/%' THEN 'https://arvio.app' || url
      ELSE url
    END,
    '/'
  )
)
WHERE normalized_url IS NULL OR btrim(normalized_url) = '';

ALTER TABLE public.catalog_packs
  ALTER COLUMN normalized_url SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_catalog_packs_normalized_url
  ON public.catalog_packs(normalized_url);

CREATE TABLE IF NOT EXISTS public.submission_rate_limits (
  ip_hash text PRIMARY KEY,
  count integer NOT NULL DEFAULT 1,
  last_submission_at timestamptz NOT NULL DEFAULT now()
);

-- ===== 20260812120000_simkl_proxy_rate_limits =====
CREATE TABLE IF NOT EXISTS simkl_proxy_rate_limits (
  key_hash TEXT PRIMARY KEY,
  window_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  request_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS simkl_proxy_rate_limits_window_idx
  ON simkl_proxy_rate_limits (window_started_at);

-- ===== 20261103131500_discord_premium_links =====
CREATE TABLE IF NOT EXISTS public.discord_premium_links (
  account_id text PRIMARY KEY,
  email_hash text NOT NULL UNIQUE CHECK (email_hash ~ '^[0-9a-f]{64}$'),
  discord_id text NOT NULL UNIQUE CHECK (discord_id ~ '^[0-9]{17,20}$'),
  next_check_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS discord_premium_links_due
  ON public.discord_premium_links (next_check_at, account_id);

ALTER TABLE public.discord_premium_links ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.discord_premium_links FROM PUBLIC;
