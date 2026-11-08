-- REVIEW ONLY — do not execute against production without explicit approval.
-- Read-only audit on 2026-11-08 confirmed anon and authenticated have TRUNCATE,
-- REFERENCES and TRIGGER privileges on the five public Centerion tables.
-- RLS does not protect TRUNCATE. The existing owner RLS policies are not changed here.
-- The Edge Function uses the server-side service_role after verifying the JWT and
-- deriving user_id/owner_id from auth.getUser(); service_role grants are untouched.

BEGIN;

REVOKE ALL PRIVILEGES ON TABLE
  public.centerion_conversations,
  public.centerion_messages,
  public.centerion_user_settings,
  public.centerion_feedback,
  public.centerion_memory_documents
FROM anon;

REVOKE ALL PRIVILEGES ON TABLE
  public.centerion_conversations,
  public.centerion_messages,
  public.centerion_user_settings,
  public.centerion_feedback,
  public.centerion_memory_documents
FROM authenticated;

-- Preserve only row-level CRUD for authenticated clients; existing RLS policies
-- continue to enforce per-user ownership. No TRUNCATE/REFERENCES/TRIGGER is granted.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  public.centerion_conversations,
  public.centerion_messages,
  public.centerion_user_settings,
  public.centerion_feedback,
  public.centerion_memory_documents
TO authenticated;

COMMIT;

-- Post-application verification (run separately after an approved deployment):
-- SELECT c.relname,
--   has_table_privilege('anon', c.oid, 'TRUNCATE') AS anon_truncate,
--   has_table_privilege('authenticated', c.oid, 'TRUNCATE') AS authenticated_truncate,
--   has_table_privilege('authenticated', c.oid, 'SELECT') AS authenticated_select,
--   has_table_privilege('authenticated', c.oid, 'INSERT') AS authenticated_insert
-- FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
-- WHERE n.nspname='public' AND c.relkind='r' AND c.relname LIKE 'centerion_%';

-- Expected: both TRUNCATE columns false; authenticated SELECT/INSERT true.
-- This file does not alter RLS, policies, JWT verification, secrets, or table data.