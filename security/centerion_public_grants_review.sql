-- REVIEW ONLY — do not execute against production without explicit approval.
-- Read-only audit on 2026-11-09 confirmed anon and authenticated have broad table
-- privileges (including TRUNCATE, REFERENCES, TRIGGER and MAINTAIN) on the five
-- public Centerion tables. RLS does not protect TRUNCATE.
--
-- The Edge Function verifies the Supabase JWT, derives user_id/owner_id from
-- auth.getUser(), then uses the server-side service_role. No owner ID is trusted
-- from the request body. This script does not alter RLS policies or JWT settings.
--
-- Existing tables: remove all anon grants; keep only row-level CRUD for
-- authenticated users, with existing owner RLS policies continuing to apply.
-- Future tables: default-deny anon/authenticated for both observed creators
-- (postgres and supabase_admin). Add explicit grants only after a table has
-- a reviewed owner policy. Service-role grants are not modified.

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

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  public.centerion_conversations,
  public.centerion_messages,
  public.centerion_user_settings,
  public.centerion_feedback,
  public.centerion_memory_documents
TO authenticated;

-- Default privileges currently grant broad access on future public tables.
-- These schema-scoped revokes do not affect storage/graphql or other schemas.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL PRIVILEGES ON TABLES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL PRIVILEGES ON TABLES FROM authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
  REVOKE ALL PRIVILEGES ON TABLES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
  REVOKE ALL PRIVILEGES ON TABLES FROM authenticated;

-- Default privileges for future sequences and RPC functions were also audited
-- on 2026-11-14. No current public sequence grants to anon/authenticated were
-- found, but both roles still had default SELECT/UPDATE/USAGE on future
-- sequences and EXECUTE on future public functions for postgres and
-- supabase_admin. Deny by default; grant explicitly after reviewing each object.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL PRIVILEGES ON SEQUENCES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL PRIVILEGES ON SEQUENCES FROM authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
  REVOKE ALL PRIVILEGES ON SEQUENCES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
  REVOKE ALL PRIVILEGES ON SEQUENCES FROM authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM authenticated;

COMMIT;

-- Post-application verification (run separately after an approved deployment):
-- SELECT c.relname,
--   has_table_privilege('anon', c.oid, 'TRUNCATE') AS anon_truncate,
--   has_table_privilege('authenticated', c.oid, 'TRUNCATE') AS authenticated_truncate,
--   has_table_privilege('anon', c.oid, 'SELECT') AS anon_select,
--   has_table_privilege('authenticated', c.oid, 'SELECT') AS authenticated_select,
--   has_table_privilege('authenticated', c.oid, 'INSERT') AS authenticated_insert
-- FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
-- WHERE n.nspname='public' AND c.relkind='r' AND c.relname LIKE 'centerion_%';
--
-- Expected for current tables: anon privileges false; authenticated CRUD true;
-- both TRUNCATE flags false. Current public sequences have no direct grants to
-- anon/authenticated. After applying the reviewed default ACL statements, the
-- following query should return zero rows for client roles and object types
-- r (tables), S (sequences), and f (functions):
--
-- SELECT r.rolname AS creator_role, d.defaclobjtype,
--        x.grantee::regrole::text AS grantee, x.privilege_type
-- FROM pg_default_acl d
-- JOIN pg_roles r ON r.oid=d.defaclrole
-- JOIN pg_namespace n ON n.oid=d.defaclnamespace
-- CROSS JOIN LATERAL aclexplode(d.defaclacl) x
-- WHERE n.nspname='public' AND d.defaclobjtype IN ('r','S','f')
--   AND x.grantee::regrole::text IN ('anon','authenticated');
--
-- This review file is not a production migration and has not been executed.
