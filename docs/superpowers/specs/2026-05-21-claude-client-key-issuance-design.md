# Design: Claude-Issuable Client API Keys

**Ticket:** [UID2-6903](https://thetradedesk.atlassian.net/browse/UID2-6903) (broader epic; this spec covers the *client API key* slice only)
**Runbook being automated:** [How to provision/create a new client API key/secret and private operator key](https://thetradedesk.atlassian.net/wiki/spaces/UID2/pages/25235533)
**Author:** Sophia Chen
**Date:** 2026-05-21

## Problem

Issuing a client API key for a new partner is a recurring on-call task. The current manual flow (per the runbook) requires an engineer to: log in to UID2 Admin behind Tailscale, check that paperwork is signed, look up or create the site, click through Client Key Management to add the key with the right role, copy the one-time-revealed plaintext key + secret, paste them into a 1Password ephemeral share, reply in the Jira ticket and Slack thread, and update a tracker spreadsheet. The mechanical core — site-lookup-or-create plus key creation — is low-complexity but high-frequency, and it blocks partner onboarding behind on-call availability.

UID2-6903 proposes exposing the relevant admin operations as documented, authenticated APIs so Claude can drive them. This spec narrows scope to the **client API key** path; operator keys and Databricks Cleanroom access are explicit non-goals (see [Out of scope](#out-of-scope)).

## Goal

A Claude skill (`/uid2-client-key`) that, given a Jira ticket key, drives the entire client-key-issuance workflow against the admin service. The skill prints the plaintext key+secret to the terminal once (the engineer then shares them via the Confluence-documented secret-sharing flow), and posts the metadata-only response back to the Jira ticket. The skill is invokable by an on-call engineer; the underlying auth model is machine-auth so the same plumbing can later support fully autonomous (cron) execution.

## Non-goals

- Replacing the engineer's judgment on whether paperwork is signed or which roles to grant. The skill surfaces the decision; a human still confirms.
- Removing the engineer from the loop on Slack reply, spreadsheet update, or marking the Jira ticket Done. The skill produces the artifacts; the engineer pastes/clicks.
- Automating the partner-facing secret share. The plaintext key+secret are printed to terminal once; the engineer shares them with the partner via the existing Confluence-documented ephemeral-secret flow (the 1Password web UI is fine — we just don't use the 1Password CLI from the skill).
- Auto-creating sites without engineer authorisation. If the site lookup misses, the skill halts and asks whether the participant name is correct (re-check) or whether to create a new site.
- 1Password CLI integration. Credentials live in shell environment variables; secret distribution is manual.
- Operator-key creation, CSTG keypair creation, Databricks Cleanroom provisioning, EUID issuance, key rotation/disable. Each is a separate skill (see [Out of scope](#out-of-scope)).

## Architecture

Two deliverables, in two repos:

```
   shell env vars                    ┌──────────────────────────────┐
   UID2_ADMIN_CLAUDE_OKTA_*          │  uid2-admin (Java/Vert.x)    │
   per-env CLIENT_ID/SECRET          │                              │
        │                            │  OktaCustomScope.java        │
        ▼                            │   + CLIENT_KEY_ISSUANCE      │
┌─────────────────────────┐          │     → Role.MAINTAINER        │
│  uid2 plugin (skills)   │ ──HTTP──▶│                              │
│  skills/uid2-client-key/│ Bearer   │  (existing endpoints, no     │
│    SKILL.md             │  token   │   other code changes)        │
└─────────────────────────┘          │                              │
        │                            │  POST /api/client/add        │
        │ terminal output            │  GET  /api/site/list         │
        ▼                            │  GET  /api/client/list/...   │
┌─────────────────────────┐          └──────────────────────────────┘
│  Terminal: plaintext    │
│  key+secret (one-time); │
│  Jira comment with      │
│  metadata only          │
└─────────────────────────┘
```

The three target admin deployments share one Okta tenant but each validates the JWT's `environment` claim against its own config (`AdminAuthMiddleware.java:148`), so a separate service account is provisioned per env:

| `--env` | Admin base URL | Service-account env vars |
|---|---|---|
| `test` | `https://admin.test.uidapi.com` | `UID2_ADMIN_CLAUDE_TEST_OKTA_CLIENT_ID` / `_SECRET` |
| `integ` | `https://admin-integ.uidapi.com` | `UID2_ADMIN_CLAUDE_INTEG_OKTA_CLIENT_ID` / `_SECRET` |
| `prod` | `https://admin-prod.uidapi.com` | `UID2_ADMIN_CLAUDE_PROD_OKTA_CLIENT_ID` / `_SECRET` |

Plus one shared variable: `UID2_ADMIN_CLAUDE_OKTA_AUTH_SERVER` (single Okta tenant URL, e.g. `https://uid2.okta.com/oauth2/aus...`).

### Backend change (uid2-admin)

One new entry in `src/main/java/com/uid2/admin/auth/OktaCustomScope.java`:

```java
CLIENT_KEY_ISSUANCE("uid2.admin.client-key-issuance", Role.MAINTAINER),
```

That's the entire code change. `AdminAuthMiddleware.validateAccessToken` (lines 140–162) already routes machine tokens through `isAuthorizedService(scopes)`, which looks up the role for each scope and admits the request if any required role matches. Adding the enum entry makes all `MAINTAINER`-protected endpoints callable by service tokens carrying this scope — including `/api/client/add`, `/api/site/add`, `/api/site/list`, and the read-only `/api/client/list*` endpoints.

We deliberately do **not** grant `SUPER_USER` or `PRIVILEGED` via this scope. Delete (`/api/client/del`, requires `SUPER_USER`) and reveal-by-contact (`/api/client/reveal`, requires `PRIVILEGED`) remain unreachable from this scope — which is what we want, since the skill never needs them.

### Okta service account (one-time setup, outside this repo)

The UID2 Okta tenant is the single source for service-account auth across all three admin deployments. Because the admin service validates the JWT's `environment` claim against its own configured `environment` value (`AdminAuthMiddleware.java:148`), one service account is provisioned **per env**: `uid2-admin-claude-automation-test`, `-integ`, `-prod`. Each is granted the `uid2.admin.client-key-issuance` scope, and each issues tokens carrying the matching `environment` claim.

Credentials are handed to the engineer who installs the skill and stored as shell environment variables (e.g. via `direnv`, a gitignored `.envrc`, or the engineer's password manager of choice — but **not** the 1Password CLI; see [Non-goals](#non-goals)). The convention is documented in [Architecture](#architecture).

This is a one-time operational task per env; the spec documents the requirement and the env-var convention, but the setup itself is a manual provisioning step.

### Skill (uid2 plugin)

New skill at `skills/uid2-client-key/SKILL.md` (lives in the `ttd/uid2` plugin alongside the existing `auto-vul-scan`, `uid2-epic`, etc.). YAML frontmatter:

```yaml
---
name: uid2-client-key
description: >
  Issue a UID2 client API key + secret for a partner from a Jira ticket. Reads
  the ticket, ensures the site exists, calls the admin service to create the
  key, prints the plaintext key + secret once to the terminal, and comments
  metadata-only back on the ticket. Usage: /uid2-client-key UID2-1234
  [--env test|integ|prod]
---
```

Invocation: `/uid2-client-key UID2-1234` (defaults to prod per the runbook's "if not specified and paperwork is signed, assume prod" convention), `/uid2-client-key UID2-1234 --env integ`, or `/uid2-client-key UID2-1234 --env test`.

## Data flow

```
engineer types /uid2-client-key UID2-1234
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. Preflight checks                                             │
│    - Tailscale reachable? (admin host responds)                 │
│    - Required env vars set for chosen --env?                    │
│      UID2_ADMIN_CLAUDE_<ENV>_OKTA_CLIENT_ID                     │
│      UID2_ADMIN_CLAUDE_<ENV>_OKTA_CLIENT_SECRET                 │
│      UID2_ADMIN_CLAUDE_OKTA_AUTH_SERVER                         │
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
│    a. Read CLIENT_ID/CLIENT_SECRET from env vars for chosen env │
│    b. POST $UID2_ADMIN_CLAUDE_OKTA_AUTH_SERVER/v1/token         │
│       grant_type=client_credentials                             │
│       scope=uid2.admin.client-key-issuance                      │
│    → bearer token, held in shell variable for this run only;    │
│       never written to disk                                     │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Resolve site                                                 │
│    GET /api/site/list                                           │
│    Match by case-insensitive trimmed name. If found → site_id.  │
│    If not found, prompt the engineer with two options:          │
│      (a) Confirm a corrected name (or paste exact name from     │
│          ticket); skill re-checks against /api/site/list        │
│      (b) Authorise creating a new site                          │
│          → POST /api/site/add?name=...&types=...                │
│    Loop on (a) until match, halt, or engineer picks (b).        │
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
│ 8. Print plaintext key + secret to terminal (one-time)          │
│    Surfaced in a clearly-marked block. Engineer copies them     │
│    and shares with the partner via the existing Confluence-     │
│    documented ephemeral-secret flow (1Password web share or     │
│    whatever the runbook currently prescribes).                  │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 9. Comment back on the Jira ticket                              │
│    Body = JSON metadata from RevealedKey response with          │
│    `plaintext_key` and `authorizable.secret` removed            │
│    (matches runbook step 20 exactly). No share URL — sharing    │
│    happens out-of-band.                                         │
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
- **Required env vars missing for the chosen `--env`.** List the missing variable names. Do not attempt to fall back to a different env.
- **Okta token request fails.** Surface the Okta error (401 = bad credentials, 400 = bad scope). Do not retry — wrong values in env vars are the most likely culprit and silent retry hides that.
- **Admin endpoint returns 401.** Almost certainly means the scope→role mapping isn't deployed yet in the target env, **or** the JWT's `environment` claim doesn't match the admin service (e.g. using the test service account against integ). Surface both possibilities and link to this design doc.
- **Site already exists with same name but different type.** Don't auto-update. Block and surface mismatch for engineer.
- **Client key already exists with the requested role on the site.** Block per runbook. Engineer either renames the new key (via `--name-suffix`) or invokes a (future) rotate flow.
- **`/api/client/add` succeeds but the Jira comment fails.** The plaintext key is not retrievable from the admin service again, but it was already printed to the terminal in step 8 — the engineer has it. Print the would-be comment body to the terminal so the engineer can paste it manually. Treat as warning, not failure.
- **`/api/client/add` returns a non-2xx status.** Print the response body and exit. Do not retry — admin write operations are not idempotent and a retry may create a duplicate key on the second attempt.

## Testing

**Backend (uid2-admin):**
- Unit test for `OktaCustomScope.fromName("uid2.admin.client-key-issuance")` returns the new enum value with `Role.MAINTAINER`. There are existing tests in this style in the auth test package; mirror them.
- Integration test: with `is_auth_disabled=false` and a stubbed `AccessTokenVerifier` that returns a JWT carrying `scp: ["uid2.admin.client-key-issuance"]`, `POST /api/client/add` returns 200. There are existing analogous tests for `ss-portal`; mirror that pattern.
- No new tests needed for `ClientKeyService` itself — that code is unchanged.

**Skill:**
- Manual run against the **test** admin deployment (`https://admin.test.uidapi.com`): invoke `/uid2-client-key <test-ticket> --env test` end-to-end against a real `ttd_dev_demo`-style participant. Validates the full happy path including the terminal-output secret print and Jira comment posting. The test env runs the same code path as integ/prod (real Okta auth, real scope check) so this is a faithful smoke test of the auth wiring.
- Dry-run flag (`--dry-run`): performs steps 1–7 (preflight, ticket read, plan confirm, token, site resolve, existing-key check) and prints the planned `/api/client/add` call without executing it. Used for the first integ/prod run on a new participant pattern.
- No integ or prod smoke test until a real ticket comes in; the test-env happy path plus the dry-run flag are the proxies for that.

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

- **Test env auth state.** This spec assumes `https://admin.test.uidapi.com` enforces Okta auth (same tenant as integ/prod, validates the JWT `environment=test` claim). Confirm with whoever owns the test deployment before the implementation phase — if test is in fact `is_auth_disabled=true`, the skill needs a no-token short-circuit for `--env test` and Phase 3 only provisions integ/prod service accounts.
- **Existing convention for storing service-account credentials locally.** This spec proposes shell env vars (`UID2_ADMIN_CLAUDE_<ENV>_OKTA_CLIENT_ID/_SECRET`, plus shared `UID2_ADMIN_CLAUDE_OKTA_AUTH_SERVER`). If the team already has a different convention for skill credentials (e.g. a shared `~/.config/uid2/` dir), follow that instead.
