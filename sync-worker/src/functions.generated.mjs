// automatisch erzeugt von sync-worker (Liste der Funktionen)
import * as f_account_delete_start from "../../netlify-auth-site/netlify/functions/account-delete-start.js";
import * as f_account_delete_status from "../../netlify-auth-site/netlify/functions/account-delete-status.js";
import * as f_account_delete_worker_background from "../../netlify-auth-site/netlify/functions/account-delete-worker-background.js";
import * as f_account_sync_cursor from "../../netlify-auth-site/netlify/functions/account-sync-cursor.js";
import * as f_account_sync_delta from "../../netlify-auth-site/netlify/functions/account-sync-delta.js";
import * as f_account_sync_progress from "../../netlify-auth-site/netlify/functions/account-sync-progress.js";
import * as f_account_sync_pull from "../../netlify-auth-site/netlify/functions/account-sync-pull.js";
import * as f_account_sync_push from "../../netlify-auth-site/netlify/functions/account-sync-push.js";
import * as f_app_usage_event from "../../netlify-auth-site/netlify/functions/app-usage-event.js";
import * as f_auth_login from "../../netlify-auth-site/netlify/functions/auth-login.js";
import * as f_auth_password_complete from "../../netlify-auth-site/netlify/functions/auth-password-complete.js";
import * as f_auth_password_start from "../../netlify-auth-site/netlify/functions/auth-password-start.js";
import * as f_auth_refresh from "../../netlify-auth-site/netlify/functions/auth-refresh.js";
import * as f_catalog_packs_list from "../../netlify-auth-site/netlify/functions/catalog-packs-list.js";
import * as f_cloud_auth_email from "../../netlify-auth-site/netlify/functions/cloud-auth-email.js";
import * as f_cloud_auth_reset from "../../netlify-auth-site/netlify/functions/cloud-auth-reset.js";
import * as f_config from "../../netlify-auth-site/netlify/functions/config.js";
import * as f_discord_auth_callback from "../../netlify-auth-site/netlify/functions/discord-auth-callback.js";
import * as f_discord_auth_start from "../../netlify-auth-site/netlify/functions/discord-auth-start.js";
import * as f_discord_auth_status from "../../netlify-auth-site/netlify/functions/discord-auth-status.js";
import * as f_discord_premium_callback from "../../netlify-auth-site/netlify/functions/discord-premium-callback.js";
import * as f_discord_premium_reconcile from "../../netlify-auth-site/netlify/functions/discord-premium-reconcile.js";
import * as f_discord_premium_start from "../../netlify-auth-site/netlify/functions/discord-premium-start.js";
import * as f_entitlement_admin from "../../netlify-auth-site/netlify/functions/entitlement-admin.js";
import * as f_entitlement_link from "../../netlify-auth-site/netlify/functions/entitlement-link.js";
import * as f_entitlement_status from "../../netlify-auth-site/netlify/functions/entitlement-status.js";
import * as f_kofi_webhook from "../../netlify-auth-site/netlify/functions/kofi-webhook.js";
import * as f_member_email_optout from "../../netlify-auth-site/netlify/functions/member-email-optout.js";
import * as f_partner_api from "../../netlify-auth-site/netlify/functions/partner-api.js";
import * as f_partner_reconcile from "../../netlify-auth-site/netlify/functions/partner-reconcile.js";
import * as f_premium_exit_survey from "../../netlify-auth-site/netlify/functions/premium-exit-survey.js";
import * as f_premium_funnel_event from "../../netlify-auth-site/netlify/functions/premium-funnel-event.js";
import * as f_premium_funnel_report from "../../netlify-auth-site/netlify/functions/premium-funnel-report.js";
import * as f_premium_funnel_visit from "../../netlify-auth-site/netlify/functions/premium-funnel-visit.js";
import * as f_referral_claim from "../../netlify-auth-site/netlify/functions/referral-claim.js";
import * as f_referral_status from "../../netlify-auth-site/netlify/functions/referral-status.js";
import * as f_retention_cleanup from "../../netlify-auth-site/netlify/functions/retention-cleanup.js";
import * as f_simkl_proxy from "../../netlify-auth-site/netlify/functions/simkl-proxy.js";
import * as f_sports_metadata from "../../netlify-auth-site/netlify/functions/sports-metadata.mjs";
import * as f_stream_ticket_key from "../../netlify-auth-site/netlify/functions/stream-ticket-key.js";
import * as f_stream_ticket from "../../netlify-auth-site/netlify/functions/stream-ticket.js";
import * as f_tmdb_proxy from "../../netlify-auth-site/netlify/functions/tmdb-proxy.js";
import * as f_trakt_proxy from "../../netlify-auth-site/netlify/functions/trakt-proxy.js";
import * as f_trial_email_worker from "../../netlify-auth-site/netlify/functions/trial-email-worker.js";
import * as f_tv_auth_approve from "../../netlify-auth-site/netlify/functions/tv-auth-approve.js";
import * as f_tv_auth_complete from "../../netlify-auth-site/netlify/functions/tv-auth-complete.js";
import * as f_tv_auth_poll from "../../netlify-auth-site/netlify/functions/tv-auth-poll.js";
import * as f_tv_auth_start from "../../netlify-auth-site/netlify/functions/tv-auth-start.js";
import * as f_tv_auth_status from "../../netlify-auth-site/netlify/functions/tv-auth-status.js";
import * as f_tv_auth_web from "../../netlify-auth-site/netlify/functions/tv-auth-web.js";

export const functions = {
  "account-delete-start": f_account_delete_start,
  "account-delete-status": f_account_delete_status,
  "account-delete-worker-background": f_account_delete_worker_background,
  "account-sync-cursor": f_account_sync_cursor,
  "account-sync-delta": f_account_sync_delta,
  "account-sync-progress": f_account_sync_progress,
  "account-sync-pull": f_account_sync_pull,
  "account-sync-push": f_account_sync_push,
  "app-usage-event": f_app_usage_event,
  "auth-login": f_auth_login,
  "auth-password-complete": f_auth_password_complete,
  "auth-password-start": f_auth_password_start,
  "auth-refresh": f_auth_refresh,
  "catalog-packs-list": f_catalog_packs_list,
  "cloud-auth-email": f_cloud_auth_email,
  "cloud-auth-reset": f_cloud_auth_reset,
  "config": f_config,
  "discord-auth-callback": f_discord_auth_callback,
  "discord-auth-start": f_discord_auth_start,
  "discord-auth-status": f_discord_auth_status,
  "discord-premium-callback": f_discord_premium_callback,
  "discord-premium-reconcile": f_discord_premium_reconcile,
  "discord-premium-start": f_discord_premium_start,
  "entitlement-admin": f_entitlement_admin,
  "entitlement-link": f_entitlement_link,
  "entitlement-status": f_entitlement_status,
  "kofi-webhook": f_kofi_webhook,
  "member-email-optout": f_member_email_optout,
  "partner-api": f_partner_api,
  "partner-reconcile": f_partner_reconcile,
  "premium-exit-survey": f_premium_exit_survey,
  "premium-funnel-event": f_premium_funnel_event,
  "premium-funnel-report": f_premium_funnel_report,
  "premium-funnel-visit": f_premium_funnel_visit,
  "referral-claim": f_referral_claim,
  "referral-status": f_referral_status,
  "retention-cleanup": f_retention_cleanup,
  "simkl-proxy": f_simkl_proxy,
  "sports-metadata": f_sports_metadata,
  "stream-ticket-key": f_stream_ticket_key,
  "stream-ticket": f_stream_ticket,
  "tmdb-proxy": f_tmdb_proxy,
  "trakt-proxy": f_trakt_proxy,
  "trial-email-worker": f_trial_email_worker,
  "tv-auth-approve": f_tv_auth_approve,
  "tv-auth-complete": f_tv_auth_complete,
  "tv-auth-poll": f_tv_auth_poll,
  "tv-auth-start": f_tv_auth_start,
  "tv-auth-status": f_tv_auth_status,
  "tv-auth-web": f_tv_auth_web,
};
