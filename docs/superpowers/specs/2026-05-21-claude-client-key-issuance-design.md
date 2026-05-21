# Design: Claude-Issuable Client API Keys

**Ticket:** [UID2-6903](https://thetradedesk.atlassian.net/browse/UID2-6903) (broader epic; this spec covers the *client API key* slice only)
**Runbook being automated:** [How to provision/create a new client API key/secret and private operator key](https://thetradedesk.atlassian.net/wiki/spaces/UID2/pages/25235533)
**Author:** Sophia Chen
**Date:** 2026-05-21

## Problem

Issuing a client API key for a new partner is a recurring on-call task. The current manual flow (per the runbook) requires an engineer to: log in to UID2 Admin behind Tailscale, check that paperwork is signed, look up or create the site, click through Client Key Management to add the key with the right role, copy the one-time-revealed plaintext key + secret, paste them into a 1Password ephemeral share, reply in the Jira ticket and Slack thread, and update a tracker spreadsheet. The mechanical core — site-lookup-or-create plus key creation — is low-complexity but high-frequency, and it blocks partner onboarding behind on-call availability.

UID2-6903 proposes exposing the relevant admin operations as documented, authenticated APIs so Claude can drive them. This spec narrows scope to the **client API key** path; operator keys and Databricks Cleanroom access are explicit non-goals (see [Out of scope](#out-of-scope)).

## Goal

A Claude skill (`/uid2-client-key`) that, given a Jira ticket key, drives the entire client-key-issuance workflow against the admin service and returns a 1Password ephemeral share link the engineer can paste into the ticket reply. The skill is invokable by an on-call engineer; the underlying auth model is machine-auth so the same plumbing can later support fully autonomous (cron) execution.

## Non-goals

- Replacing the engineer's judgment on whether paperwork is signed or which roles to grant. The skill surfaces the decision; a human still confirms.
- Removing the engineer from the loop on Slack reply, spreadsheet update, or marking the Jira ticket Done. The skill produces the artifacts; the engineer pastes/clicks.
- Operator-key creation, CSTG keypair creation, Databricks Cleanroom provisioning, EUID issuance, key rotation/disable. Each is a separate skill (see [Out of scope](#out-of-scope)).

## Architecture

Two deliverables, in two repos:

```
┌─────────────────────────┐         ┌──────────────────────────────┐
│  uid2 plugin (skills)   │         │  uid2-admin (Java/Vert.x)    │
│                         │         │                              │
│  skills/                │         │  OktaCustomScope.java        │
│    uid2-client-key/     │ ──HTTP──▶   + CLIENT_KEY_ISSUANCE      │
│      SKILL.md           │ Bearer  │     → Role.MAINTAINER        │
│                         │  token  │                              │
└─────────────────────────┘         │  (existing endpoints,        │
         │                          │   no other code changes)     │
         │ op CLI                   │                              │
         ▼                          │  POST /api/client/add        │
┌─────────────────────────┐         │  POST /api/site/add          │
│  1Password ephemeral    │         │  GET  /api/site/list         │
│  share + Jira comment   │         │  GET  /api/client/list/...   │
└─────────────────────────┘         └──────────────────────────────┘
```

### Backend change (uid2-admin)

One new entry in `src/main/java/com/uid2/admin/auth/OktaCustomScope.java`:

```java
CLIENT_KEY_ISSUANCE("uid2.admin.client-key-issuance", Role.MAINTAINER),
```

That's the entire code change. `AdminAuthMiddleware.validateAccessToken` (lines 140–162) already routes machine tokens through `isAuthorizedService(scopes)`, which looks up the role for each scope and admits the request if any required role matches. Adding the enum entry makes all `MAINTAINER`-protected endpoints callable by service tokens carrying this scope — including `/api/client/add`, `/api/site/add`, `/api/site/list`, and the read-only `/api/client/list*` endpoints.

We deliberately do **not** grant `SUPER_USER` or `PRIVILEGED` via this scope. Delete (`/api/client/del`, requires `SUPER_USER`) and reveal-by-contact (`/api/client/reveal`, requires `PRIVILEGED`) remain unreachable from this scope — which is what we want, since the skill never needs them.

### Okta service account (one-time setup, outside this repo)

A new Okta service account (suggested name: `uid2-admin-claude-automation`) is created in the UID2 Okta tenant by an Okta admin, with the `uid2.admin.client-key-issuance` scope granted. Its client_id and client_secret are stored in 1Password under a well-known item name. Two such accounts are needed — one for the integ Okta tenant, one for prod — matching the two admin environments (`admin-integ.uidapi.com`, `admin-prod.uidapi.com`).

This is a one-time operational task; the spec documents the requirement and the 1Password item name convention, but the setup itself is a manual provisioning step.

### Skill (uid2 plugin)

New skill at `skills/uid2-client-key/SKILL.md` (lives in the `ttd/uid2` plugin alongside the existing `auto-vul-scan`, `uid2-epic`, etc.). YAML frontmatter:

```yaml
---
name: uid2-client-key
description: >
  Issue a UID2 client API key + secret for a partner from a Jira ticket. Reads
  the ticket, ensures the site exists, calls the admin service to create the
  key, packages the plaintext key+secret into a 1Password ephemeral share, and
  comments the metadata back on the ticket. Usage: /uid2-client-key UID2-1234
  [--env integ|prod]
---
```

Invocation: `/uid2-client-key UID2-1234` (defaults to prod per the runbook's "if not specified and paperwork is signed, assume prod" convention) or `/uid2-client-key UID2-1234 --env integ`.

## Data flow

```
engineer types /uid2-client-key UID2-1234
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. Preflight checks                                             │
│    - Tailscale reachable? (curl admin-{env}.uidapi.com)         │
│    - op CLI signed in? (op whoami)                              │
│    - 1Password item exists for env's service account?           │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Read Jira ticket (Atlassian MCP)                             │
│    Extract: participant name, type (publisher / advertiser /    │
│    DSP / data-provider), env if explicit, paperwork status,     │
│    contact email.                                               │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Decide & confirm                                             │
│    Present extracted plan to engineer:                          │
│      - Participant: "Acme Co" (advertiser)                      │
│      - Env: prod                                                │
│      - Role(s) to grant: MAPPER       ← derived from type       │
│      - Paperwork: signed (per ticket field X)                   │
│    Block on engineer confirmation. Halt if paperwork unsigned.  │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Acquire admin-service access token                           │
│    a. op read 1password://...service-account...client-id        │
│    b. op read 1password://...service-account...client-secret    │
│    c. POST {okta_auth_server}/v1/token                          │
│       grant_type=client_credentials                             │
│       scope=uid2.admin.client-key-issuance                      │
│    → bearer token, cached in-memory for the skill run only      │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Resolve site                                                 │
│    GET /api/site/list                                           │
│    Match by exact name. If found → site_id.                     │
│    If not found:                                                │
│      Confirm "no existing site named X — create new?"           │
│      POST /api/site/add?name=...&types=...                      │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Check for existing key on this site                          │
│    GET /api/client/list/{site_id}                               │
│    If a key with the desired role already exists, surface it    │
│    and block — runbook says use a suffixed name in this case;   │
│    let the engineer decide rather than auto-suffix.             │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. Create client key                                            │
│    POST /api/client/add?name=...&roles=...&site_id=...          │
│    Response = RevealedKey<ClientKey> with plaintext key+secret  │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. Package secrets                                              │
│    Write key + secret to a transient 1Password item, then       │
│    `op item share --expires-in 7d` to get the ephemeral link.   │
│    (No quotes in the share content — matches runbook step.)     │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 9. Comment back on the Jira ticket                              │
│    Body = JSON metadata from RevealedKey response with          │
│    `key` and `secret` fields removed (matches runbook step      │
│    20 exactly), plus the 1Password share URL.                   │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 10. Report summary to engineer with next-step checklist         │
│     (Slack reply, spreadsheet update, mark Done — manual)       │
└─────────────────────────────────────────────────────────────────┘
```

## Role mapping

The runbook's role-by-type table, encoded into the skill (so the engineer doesn't have to remember it):

| Participant type | Role granted | Notes |
|---|---|---|
| Publisher | `GENERATOR` | |
| Advertiser | `MAPPER` | |
| Data provider | `MAPPER` | Same endpoints as advertiser. |
| DSP | `ID_READER` | Skill prompts which client SDK; flagged in summary for follow-up. |
| Sharer | `SHARER` | Skill blocks and surfaces the runbook's prerequisite check — sharing onboarding must be complete first; engineer overrides if confirmed. |

Multi-role keys are uncommon and explicitly cautioned against in the runbook ("a participant should never receive all API roles"). The skill grants exactly one role per invocation and instructs the engineer to re-run for a second role.

## Error handling

- **Tailscale not reachable.** Fail before touching credentials. Exit code 1, message says "Connect to UID2 Tailscale, then re-run."
- **Okta token request fails.** Surface the Okta error (401 = bad credentials, 400 = bad scope). Do not retry — credentials in 1Password are the most likely culprit and silent retry hides that.
- **Admin endpoint returns 401.** Almost certainly means the scope→role mapping isn't deployed yet. Surface explicitly and link to this design doc.
- **Site already exists with same name but different type.** Don't auto-update. Block and surface mismatch for engineer.
- **Client key already exists with the requested role on the site.** Block per runbook. Engineer either renames the new key or invokes a (future) rotate flow.
- **`/api/client/add` succeeds but 1Password share fails.** The plaintext key is not retrievable again. Skill writes the response to a local file at `~/uid2-client-key-recovery-<ticket>-<ts>.json` (chmod 600), tells the engineer where it is, and instructs them to share manually. Do not delete this file automatically — engineer cleans up after confirming the share.
- **Jira comment post fails.** Treat as warning, not failure. Key is already issued; print the comment body to terminal so the engineer can paste it manually.

## Testing

**Backend (uid2-admin):**
- Unit test for `OktaCustomScope.fromName("uid2.admin.client-key-issuance")` returns the new enum value with `Role.MAINTAINER`. There are existing tests in this style in the auth test package; mirror them.
- Integration test: with `is_auth_disabled=false` and a stubbed `AccessTokenVerifier` that returns a JWT carrying `scp: ["uid2.admin.client-key-issuance"]`, `POST /api/client/add` returns 200. There are existing analogous tests for `ss-portal`; mirror that pattern.
- No new tests needed for `ClientKeyService` itself — that code is unchanged.

**Skill:**
- Manual run against integ: invoke `/uid2-client-key <test-ticket> --env integ` end-to-end against a real `ttd_dev_demo` participant. Validates the full happy path including 1Password share creation and Jira comment posting.
- Dry-run flag (`--dry-run`): performs steps 1–6 (preflight, ticket read, site resolution, existing-key check) and prints the planned `/api/client/add` call without executing it. Used for the first prod run.
- No prod smoke test until a real ticket comes in; the dry-run flag is the proxy for that.

## Out of scope

The following are intentionally excluded from this spec. Each is a candidate follow-up.

- **Operator key issuance.** Different protocols (aws-nitro / gcp-oidc / azure-cc), different endpoint, different post-creation steps (Private Operator List page). Separate skill, separate scope or reused.
- **CSTG client-side keypair.** Different endpoint family (`/api/client_side_keypairs/*`), CSTG has its own runbook.
- **Databricks Cleanroom provisioning.** Mentioned in UID2-6903 but unrelated to admin service.
- **EUID issuance.** Same code path on a different deployment; deferred until UID2 flow is validated.
- **Key rotation / disable.** Runbook covers these as separate sections. The skill should grow into them, but each adds judgment calls (when is the old key safe to disable?) that need their own thinking.
- **Slack thread reply.** A `slack-response` skill already exists in the plugin; engineer chains it manually.
- **Participant tracker spreadsheet update.** Manual for now. The runbook's spreadsheet has many fields not derivable from the admin response.
- **UID2 Portal automation.** The runbook's "Yes paperwork signed" branch routes to a portal team, not engineers — automation there is a separate org's problem.

## Open questions

- **1Password share automation.** `op item share` exists; need to verify it supports plaintext-content shares (not just file shares) and that the resulting URL is the same shape the runbook expects (the "ephemeral UID2 secrets" Confluence page should clarify). Implementation phase: confirm via `op item share --help` before committing to this path; fallback is to print the secret to terminal and instruct manual share.
- **Service-account credentials storage convention.** Suggested 1Password item path: `uid2-admin-claude-automation/{integ,prod}` with fields `okta_client_id`, `okta_client_secret`, `okta_auth_server`. Confirm with the Okta admin who provisions the accounts.
