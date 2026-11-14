# Centerion Edge Function source mirror

This source was copied from the deployed Centerion Edge Function v10 on 2026-11-11 and changed only to use the shared strict request guard.

- Platform JWT verification must remain enabled: `verify_jwt = true`.
- The handler still calls Supabase Auth `getUser()` and derives the user ID from the verified JWT.
- No request-body owner ID is accepted.
- No secrets or environment values are committed.
- The strict request reader enforces the body limit on streamed UTF-8 bytes before full buffering.
- Oversized strings are rejected; they are no longer silently truncated.
- This branch is a review candidate only. It has not been deployed to Supabase production.
- The separate `kolai-bridge` function remains a security blocker because its deployed platform setting is `verify_jwt=false`; this PR does not change it.
