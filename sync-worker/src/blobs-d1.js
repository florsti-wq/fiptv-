// fIPTV: Ersatz für @netlify/blobs auf Cloudflare D1 (gleiche Schnittstelle, soweit der
// Sync-Server sie nutzt: get, getWithMetadata, setJSON (inkl. onlyIfNew/onlyIfMatch),
// delete, list). Große Werte werden in Teilen gespeichert (D1-Zeilen max. 2 MB).
"use strict";

const PART_SIZE = 900_000; // Zeichen je Teil (UTF-8 kann bis 3 Byte je Zeichen brauchen → < 2,7 MB … sicher unter 2 MB bei ASCII-JSON)

function db() {
  const env = globalThis.__FIPTV_ENV;
  if (!env || !env.DB) throw new Error("D1-Datenbank (DB) ist nicht verbunden");
  return env.DB;
}

function newEtag() {
  return `"${crypto.randomUUID().replace(/-/g, "")}"`;
}

function split(text) {
  const parts = [];
  for (let i = 0; i < text.length; i += PART_SIZE) parts.push(text.slice(i, i + PART_SIZE));
  return parts.length ? parts : [""];
}

async function readValue(store, key) {
  const head = await db().prepare("SELECT etag, metadata, parts FROM blobs WHERE store = ?1 AND key = ?2").bind(store, key).first();
  if (!head) return null;
  let text;
  if (head.parts <= 1) {
    const row = await db().prepare("SELECT data FROM blob_parts WHERE store = ?1 AND key = ?2 AND part = 0").bind(store, key).first();
    text = row ? row.data : "";
  } else {
    const rows = await db().prepare("SELECT part, data FROM blob_parts WHERE store = ?1 AND key = ?2 ORDER BY part").bind(store, key).all();
    text = (rows.results || []).map((r) => r.data).join("");
  }
  let metadata = {};
  try { metadata = head.metadata ? JSON.parse(head.metadata) : {}; } catch { metadata = {}; }
  return { text, etag: head.etag, metadata };
}

function convert(text, type) {
  if (text == null) return null;
  if (type === "json") {
    try { return JSON.parse(text); } catch { return null; }
  }
  if (type === "arrayBuffer") return new TextEncoder().encode(text).buffer;
  if (type === "blob") return new Blob([text]);
  if (type === "stream") return new Blob([text]).stream();
  return text;
}

async function writeParts(store, key, text) {
  const parts = split(text);
  const stmts = [db().prepare("DELETE FROM blob_parts WHERE store = ?1 AND key = ?2").bind(store, key)];
  parts.forEach((data, part) => {
    stmts.push(db().prepare("INSERT INTO blob_parts (store, key, part, data) VALUES (?1, ?2, ?3, ?4)").bind(store, key, part, data));
  });
  await db().batch(stmts);
  return parts.length;
}

async function write(store, key, text, options = {}) {
  const etag = newEtag();
  const metadata = JSON.stringify(options.metadata || {});
  const now = Date.now();
  const partsCount = split(text).length;
  let claimed;
  if (options.onlyIfNew) {
    const res = await db().prepare(
      "INSERT INTO blobs (store, key, etag, metadata, parts, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6) ON CONFLICT(store, key) DO NOTHING"
    ).bind(store, key, etag, metadata, partsCount, now).run();
    claimed = (res.meta && res.meta.changes) > 0;
  } else if (options.onlyIfMatch) {
    const res = await db().prepare(
      "UPDATE blobs SET etag = ?3, metadata = ?4, parts = ?5, updated_at = ?6 WHERE store = ?1 AND key = ?2 AND etag = ?7"
    ).bind(store, key, etag, metadata, partsCount, now, options.onlyIfMatch).run();
    claimed = (res.meta && res.meta.changes) > 0;
  } else {
    await db().prepare(
      "INSERT INTO blobs (store, key, etag, metadata, parts, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6) " +
      "ON CONFLICT(store, key) DO UPDATE SET etag = excluded.etag, metadata = excluded.metadata, parts = excluded.parts, updated_at = excluded.updated_at"
    ).bind(store, key, etag, metadata, partsCount, now).run();
    claimed = true;
  }
  if (!claimed) return { modified: false };
  await writeParts(store, key, text);
  return { modified: true, etag };
}

function makeStore(name) {
  if (!name) throw new Error("Blob-Store braucht einen Namen");
  return {
    async get(key, options = {}) {
      const value = await readValue(name, key);
      return value ? convert(value.text, options.type) : null;
    },
    async getWithMetadata(key, options = {}) {
      const value = await readValue(name, key);
      if (!value) return null;
      return { data: convert(value.text, options.type), etag: value.etag, metadata: value.metadata };
    },
    async getMetadata(key) {
      const head = await db().prepare("SELECT etag, metadata FROM blobs WHERE store = ?1 AND key = ?2").bind(name, key).first();
      if (!head) return null;
      let metadata = {};
      try { metadata = head.metadata ? JSON.parse(head.metadata) : {}; } catch { metadata = {}; }
      return { etag: head.etag, metadata };
    },
    async set(key, value, options = {}) {
      const text = typeof value === "string" ? value : value instanceof ArrayBuffer ? new TextDecoder().decode(value) : await new Response(value).text();
      return write(name, key, text, options);
    },
    async setJSON(key, value, options = {}) {
      return write(name, key, JSON.stringify(value), options);
    },
    async delete(key) {
      await db().batch([
        db().prepare("DELETE FROM blobs WHERE store = ?1 AND key = ?2").bind(name, key),
        db().prepare("DELETE FROM blob_parts WHERE store = ?1 AND key = ?2").bind(name, key),
      ]);
    },
    list(options = {}) {
      const prefix = options.prefix || "";
      const pageSize = 1000;
      const page = async (cursor) => {
        const rows = await db().prepare(
          "SELECT key, etag FROM blobs WHERE store = ?1 AND key >= ?2 AND key < ?3 AND key > ?4 ORDER BY key LIMIT ?5"
        ).bind(name, prefix, prefix + "￿", cursor || "", pageSize + 1).all();
        const list = rows.results || [];
        const more = list.length > pageSize;
        const blobs = list.slice(0, pageSize).map((r) => ({ key: r.key, etag: r.etag }));
        return { blobs, directories: [], cursor: more ? blobs[blobs.length - 1].key : undefined };
      };
      if (options.paginate) {
        return (async function* () {
          let cursor = options.cursor;
          do {
            const result = await page(cursor);
            yield result;
            cursor = result.cursor;
          } while (cursor);
        })();
      }
      return page(options.cursor);
    },
  };
}

function getStore(input) {
  const name = typeof input === "string" ? input : input && input.name;
  return makeStore(name);
}

// Netlify-spezifisch, hier ohne Wirkung
function connectLambda() {}
function getDeployStore(input) { return getStore(typeof input === "string" ? `deploy:${input}` : { ...input, name: `deploy:${input && input.name}` }); }

module.exports = { getStore, getDeployStore, connectLambda };
