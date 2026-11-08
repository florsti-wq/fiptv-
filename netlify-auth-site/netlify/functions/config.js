const { json, options } = require("./_backend");

exports.handler = async (event) => {
  const cors = options(event);
  if (cors) return cors;

  return json(200, {
    backend: "netlify",
    cloudSync: "netlify_account_snapshot",
    authSurface: "netlify_native_auth",
    tvAuthStorage: "netlify_blobs",
    migratedPasswordSetup: true,
    emailProviderConfigured: !!(
      process.env.RESEND_API_KEY ||
      process.env.POSTMARK_SERVER_TOKEN ||
      process.env.SENDGRID_API_KEY
    ),
    authSecretConfigured: !!process.env.ARVIO_AUTH_SECRET,
    // fIPTV: nur Länge, nie den Wert – zum Prüfen, ob der App-Schlüssel ankommt
    appKeyLength: String(process.env.APP_ANON_KEY || "").length,
    databaseConfigured: !!(process.env.NETLIFY_DB_URL || process.env.NETLIFY_DATABASE_URL || process.env.DATABASE_URL),
    proxies: {
      tmdb: true,
      trakt: true
    },
    supabaseFallback: false,
    mediaProxy: false,
    timestamp: new Date().toISOString()
  });
};
