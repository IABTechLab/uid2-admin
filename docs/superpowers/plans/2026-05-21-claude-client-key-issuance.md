# Claude Client-Key Issuance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Claude to issue UID2 client API keys end-to-end via a `/uid2-client-key` skill, backed by a one-line scope addition to the admin service so the existing `MAINTAINER`-protected endpoints accept machine tokens.

**Architecture:** Two thin deliverables, two repos.
1. `uid2-admin` (Java/Vert.x): add one entry to `OktaCustomScope` so `uid2.admin.client-key-issuance` → `Role.MAINTAINER`, plus parameterised tests mirroring the existing `SS_PORTAL`/`SECRET_ROTATION` pattern. No other backend code changes.
2. `uid2-claude-skills` (Markdown skill): new `skills/uid2-client-key/SKILL.md` that drives the runbook — parses a Jira ticket, ensures site exists, calls `POST /api/client/add`, packages the plaintext key+secret into a 1Password ephemeral share, and posts the metadata-only response back to the Jira ticket.

**Tech Stack:**
- Backend: Java 17, Vert.x, JUnit 5 (parameterised), Mockito, Okta JWT verifier.
- Skill: Markdown frontmatter, Atlassian MCP, 1Password CLI (`op`), `curl`, shell.

**Spec:** [`docs/superpowers/specs/2026-05-21-claude-client-key-issuance-design.md`](../specs/2026-05-21-claude-client-key-issuance-design.md) (commit `80bddd2d`).

---

## File structure

### `uid2-admin` repo (this repo)

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/com/uid2/admin/auth/OktaCustomScope.java` | Modify (add one enum entry) | Map new machine scope to `Role.MAINTAINER`. |
| `src/test/java/com/uid2/admin/auth/OktaCustomScopeTest.java` | Modify (add one row to `testFromNameData`) | Cover `fromName` lookup for the new scope. |
| `src/test/java/com/uid2/admin/auth/AdminAuthMiddlewareTest.java` | Modify (extend two parameterised data providers) | Confirm authorised and unauthorised access for the new scope through the middleware. |

### `uid2-claude-skills` repo (separate repo at `/Users/sophia.chen/ttdsrc/uid2-claude-skills`, origin `gitlab.adsrvr.org:uid2/uid2-claude-skills.git`)

| File | Change | Responsibility |
|---|---|---|
| `skills/uid2-client-key/SKILL.md` | Create | Full end-to-end runbook executed by Claude. |

### Operational artefact (not code)

| Item | Owner | Responsibility |
|---|---|---|
| Okta service accounts `uid2-admin-claude-automation` (one per env) with scope `uid2.admin.client-key-issuance`; credentials stored in 1Password | Okta admin (manual) | One-time provisioning so the skill can obtain access tokens. |

---

## Phase 1 — Backend scope addition (uid2-admin)

The middleware in `src/main/java/com/uid2/admin/auth/AdminAuthMiddleware.java:140-162` already iterates through token scopes and admits the request if any maps to an allowed role. Adding one enum entry is therefore the entire functional change; the tests confirm both the lookup and the end-to-end auth decision.

### Task 1: Failing test for `OktaCustomScope.fromName` on the new scope name

**Files:**
- Modify: `src/test/java/com/uid2/admin/auth/OktaCustomScopeTest.java:12-19`

- [ ] **Step 1: Add the new test row to `testFromNameData`**

Open `src/test/java/com/uid2/admin/auth/OktaCustomScopeTest.java`. The existing `testFromNameData` method (lines 12-19) returns a `Stream<Arguments>` of `(scopeName, expectedEnum)` pairs. Add one row, between the existing `SITE_SYNC` row and the `dummy` row, so the method reads:

```java
private static Stream<Arguments> testFromNameData() {
    return Stream.of(
        Arguments.of("uid2.admin.ss-portal", OktaCustomScope.SS_PORTAL),
        Arguments.of("uid2.admin.secret-rotation", OktaCustomScope.SECRET_ROTATION),
        Arguments.of("uid2.admin.site-sync", OktaCustomScope.SITE_SYNC),
        Arguments.of("uid2.admin.client-key-issuance", OktaCustomScope.CLIENT_KEY_ISSUANCE),
        Arguments.of("dummy", OktaCustomScope.INVALID)
    );
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run from the repo root:

```bash
mvn -pl . -am test -Dtest=OktaCustomScopeTest
```

Expected: compilation failure with `cannot find symbol: variable CLIENT_KEY_ISSUANCE` in `OktaCustomScope`. This is the "red" state.

### Task 2: Add the enum entry to make the test compile and pass

**Files:**
- Modify: `src/main/java/com/uid2/admin/auth/OktaCustomScope.java:10-15`

- [ ] **Step 1: Insert the new enum entry**

The current `OktaCustomScope` enum declaration is at lines 10-15. Add the new entry between `ENCLAVE_REGISTRAR` and `INVALID`:

```java
@Getter
public enum OktaCustomScope {
    SS_PORTAL("uid2.admin.ss-portal", Role.SHARING_PORTAL),
    SECRET_ROTATION("uid2.admin.secret-rotation", Role.SECRET_ROTATION),
    SITE_SYNC("uid2.admin.site-sync", Role.PRIVATE_OPERATOR_SYNC),
    METRICS_EXPORT("uid2.admin.metrics-export", Role.METRICS_EXPORT),
    ENCLAVE_REGISTRAR("uid2.admin.enclave-registrar", Role.ENCLAVE_REGISTRAR),
    CLIENT_KEY_ISSUANCE("uid2.admin.client-key-issuance", Role.MAINTAINER),
    INVALID("invalid", Role.UNKNOWN);
    // ... rest unchanged
```

- [ ] **Step 2: Run the test to verify it passes**

```bash
mvn -pl . -am test -Dtest=OktaCustomScopeTest
```

Expected: `BUILD SUCCESS`, all `testFromName` parameterised cases including the new one pass.

### Task 3: Add the failing authorised-access test for the new scope through the middleware

**Files:**
- Modify: `src/test/java/com/uid2/admin/auth/AdminAuthMiddlewareTest.java:278-284`

- [ ] **Step 1: Extend `testAccessTokenGoodData`**

The existing data provider at lines 278-284 lists `(scope, role)` tuples that should be admitted. Add one row:

```java
private static Stream<Arguments> testAccessTokenGoodData() {
    return Stream.of(
      Arguments.of(OktaCustomScope.SS_PORTAL, OktaCustomScope.SS_PORTAL.getRole()),
      Arguments.of(OktaCustomScope.SECRET_ROTATION, OktaCustomScope.SECRET_ROTATION.getRole()),
      Arguments.of(OktaCustomScope.SITE_SYNC, OktaCustomScope.SITE_SYNC.getRole()),
      Arguments.of(OktaCustomScope.CLIENT_KEY_ISSUANCE, OktaCustomScope.CLIENT_KEY_ISSUANCE.getRole())
    );
}
```

- [ ] **Step 2: Run the parameterised test**

```bash
mvn -pl . -am test -Dtest=AdminAuthMiddlewareTest#testAccessToken_GoodTokenAuthorized
```

Expected: PASS for all four rows. The new row exercises a token carrying scope `uid2.admin.client-key-issuance` being admitted on a route requiring `Role.MAINTAINER`. (It passes immediately because the middleware logic is unchanged — the test confirms behaviour through the middleware, not just the enum.)

### Task 4: Add the failing unauthorised-access test for the new scope

**Files:**
- Modify: `src/test/java/com/uid2/admin/auth/AdminAuthMiddlewareTest.java:252-261`

- [ ] **Step 1: Extend `testAccessTokenUnauthorizedData`**

Add two rows confirming `CLIENT_KEY_ISSUANCE` is rejected on routes requiring unrelated roles:

```java
private static Stream<Arguments> testAccessTokenUnauthorizedData() {
    return Stream.of(
        Arguments.of(OktaCustomScope.SS_PORTAL.getName(), new Role[] {Role.PRIVATE_OPERATOR_SYNC}),
        Arguments.of(OktaCustomScope.SS_PORTAL.getName(), new Role[] {Role.SECRET_ROTATION}),
        Arguments.of(OktaCustomScope.SECRET_ROTATION.getName(), new Role[] {Role.SHARING_PORTAL}),
        Arguments.of(OktaCustomScope.SECRET_ROTATION.getName(), new Role[] {Role.PRIVATE_OPERATOR_SYNC}),
        Arguments.of(OktaCustomScope.SITE_SYNC.getName(), new Role[] {Role.SECRET_ROTATION}),
        Arguments.of(OktaCustomScope.SITE_SYNC.getName(), new Role[] {Role.SHARING_PORTAL}),
        Arguments.of(OktaCustomScope.CLIENT_KEY_ISSUANCE.getName(), new Role[] {Role.SUPER_USER}),
        Arguments.of(OktaCustomScope.CLIENT_KEY_ISSUANCE.getName(), new Role[] {Role.PRIVILEGED})
    );
}
```

These two rows assert that a `client-key-issuance` token is **rejected** on `SUPER_USER`-only routes (e.g. `/api/client/del`) and `PRIVILEGED`-only routes (e.g. `/api/client/reveal`), which matches the spec's threat model.

- [ ] **Step 2: Run the parameterised test**

```bash
mvn -pl . -am test -Dtest=AdminAuthMiddlewareTest#testAccessToken_GoodTokenUnauthorized
```

Expected: PASS for all rows including the two new ones (401 returned, inner handler not invoked).

### Task 5: Run the whole auth-package test suite and full build

- [ ] **Step 1: Run all auth tests**

```bash
mvn -pl . -am test -Dtest='com.uid2.admin.auth.*'
```

Expected: PASS, no regressions.

- [ ] **Step 2: Run the full build**

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS`. This catches anything the focused runs missed.

### Task 6: Commit the backend change

- [ ] **Step 1: Stage and commit**

```bash
git add src/main/java/com/uid2/admin/auth/OktaCustomScope.java \
        src/test/java/com/uid2/admin/auth/OktaCustomScopeTest.java \
        src/test/java/com/uid2/admin/auth/AdminAuthMiddlewareTest.java
git commit -m "$(cat <<'EOF'
feat(auth): add client-key-issuance Okta scope for machine auth

Adds a new OktaCustomScope mapped to Role.MAINTAINER so service-account
access tokens can call MAINTAINER-protected endpoints (POST /api/client/add,
POST /api/site/add, GET /api/site/list, GET /api/client/list/:siteId).

This unblocks the /uid2-client-key Claude skill (see UID2-6903) without
exposing SUPER_USER or PRIVILEGED operations to the same scope.

Tests mirror the existing parameterised SS_PORTAL/SECRET_ROTATION patterns
in OktaCustomScopeTest and AdminAuthMiddlewareTest.

Refs: UID2-6903
Design: docs/superpowers/specs/2026-05-21-claude-client-key-issuance-design.md
EOF
)"
```

- [ ] **Step 2: Push and open PR**

```bash
git push -u origin HEAD
gh pr create --title "feat(auth): add client-key-issuance Okta scope" --body "$(cat <<'EOF'
## Summary
- Add `uid2.admin.client-key-issuance` Okta custom scope mapped to `Role.MAINTAINER`
- Extend `OktaCustomScopeTest` and `AdminAuthMiddlewareTest` parameterised cases (authorised + unauthorised)
- Unblocks the `/uid2-client-key` Claude skill (UID2-6903)

## Test plan
- [x] `mvn -pl . -am test -Dtest='com.uid2.admin.auth.*'` passes
- [x] `mvn clean verify` passes
- [ ] Reviewer confirms the scope→role mapping is appropriate (MAINTAINER only — no SUPER_USER / PRIVILEGED leakage)

Design: `docs/superpowers/specs/2026-05-21-claude-client-key-issuance-design.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL printed. Reviewer needed before merge.

---

## Phase 2 — Claude skill (uid2-claude-skills repo)

The skill is a single `SKILL.md` that Claude reads as a runbook. There is no compile step, no unit test framework — verification is by running the skill end-to-end against the integ admin environment. Each task below adds one section to the file; the file is committed only after the full happy path is validated against integ.

> Working directory for Phase 2 is `/Users/sophia.chen/ttdsrc/uid2-claude-skills` (separate repo, GitLab origin). Switch with `cd /Users/sophia.chen/ttdsrc/uid2-claude-skills` at the start of Task 7. Confirm with `git remote -v` — origin should be `git@gitlab.adsrvr.org:uid2/uid2-claude-skills.git`.

### Task 7: Scaffold the skill directory and frontmatter

**Files:**
- Create: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Create the directory**

```bash
cd /Users/sophia.chen/ttdsrc/uid2-claude-skills
git checkout -b sc-UID2-6903-client-key-skill
mkdir -p skills/uid2-client-key
```

- [ ] **Step 2: Write the frontmatter and title**

Create `skills/uid2-client-key/SKILL.md` with this initial content (subsequent tasks will append sections):

```markdown
---
name: uid2-client-key
description: >
  Issue a UID2 client API key + secret for a partner from a Jira ticket. Reads
  the ticket, ensures the site exists, calls the admin service to create the
  key, packages the plaintext key+secret into a 1Password ephemeral share, and
  comments the metadata back on the ticket. Usage: /uid2-client-key UID2-1234
  [--env integ|prod]
---

# UID2 Client Key Issuance

> **Warning:** This skill performs production writes when `--env prod`. Always run with `--dry-run` first against a new participant pattern.

Issue a UID2 client API key for a partner end-to-end. Replaces the manual
Confluence runbook: [How to provision/create a new client API key/secret](https://thetradedesk.atlassian.net/wiki/spaces/UID2/pages/25235533).

Scope: **UID2 client API keys only**. Operator keys, CSTG keypairs, EUID keys,
and Databricks Cleanroom access are explicitly out of scope — separate skills.

## Arguments

| Position / flag | Required? | Default | Description |
|---|---|---|---|
| `$1` ticket key | yes | — | Jira ticket key, e.g. `UID2-1234`. |
| `--env integ\|prod` | no | `prod` | Admin service environment. |
| `--dry-run` | no | off | Perform steps 1-6 (everything up to `/api/client/add`) and print the planned call without executing. |
| `--name-suffix=<n>` | no | empty | Append ` <n>` to the participant name when an existing key with the same role exists (per runbook: "Acme Corp" → "Acme Corp 2"). |
```

### Task 8: Add the Prerequisites section

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Prerequisites section**

Append to `skills/uid2-client-key/SKILL.md`:

```markdown
## Prerequisites

- UID2 Tailscale connected. Confirm with: `tailscale status | head -1`. If not connected, halt with: "Connect to UID2 Tailscale (https://thetradedesk.atlassian.net/wiki/spaces/UID2/pages/520881958), then re-run."
- `op` CLI signed in (`op whoami` returns an email, not an error).
- Atlassian MCP available (the `mcp__claude_ai_Atlassian__*` tools).
- 1Password item present for the chosen env. Item naming convention:
  - integ: `uid2-admin-claude-automation - integ` in the `UID2` vault
  - prod:  `uid2-admin-claude-automation - prod` in the `UID2` vault
  Each item must carry fields: `okta_client_id`, `okta_client_secret`, `okta_auth_server`, `okta_audience`, `admin_base_url`.
```

### Task 9: Add Step 1 — preflight and Jira ticket read

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 1 section**

Append to `skills/uid2-client-key/SKILL.md`:

````markdown
## Step 1 — Preflight and read the Jira ticket

### 1a. Preflight

Run the preflight commands in order. Halt with a specific message on first failure.

```bash
tailscale status >/dev/null 2>&1 || { echo "Tailscale not connected"; exit 1; }
op whoami >/dev/null 2>&1 || { echo "1Password CLI not signed in — run 'op signin'"; exit 1; }
```

### 1b. Resolve Atlassian cloudId

```text
Call mcp__claude_ai_Atlassian__getAccessibleAtlassianResources.
Use the `id` for `thetradedesk.atlassian.net` as `cloudId` for all later calls.
```

### 1c. Read the ticket

```text
Call mcp__claude_ai_Atlassian__getJiraIssue with the provided ticket key and
contentFormat="markdown". Extract from the response:
  - summary  → participant name (strip "API key request for " prefix if present)
  - description → free-text. Scan for: participant type (publisher / advertiser /
    DSP / data provider / sharer), environment hint (integ/prod), paperwork
    status (signed / pending).
  - reporter.emailAddress → contact email
```

If any of `participant_name`, `participant_type`, `contact_email` cannot be
inferred from the ticket, present what was extracted and ask the engineer to
fill in the missing fields. Do not guess.
````

### Task 10: Add Step 2 — confirm plan and check paperwork

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 2 section**

```markdown
## Step 2 — Confirm the plan with the engineer

Map participant type to the role that will be granted, per the runbook:

| Participant type | Role granted |
|---|---|
| Publisher | `GENERATOR` |
| Advertiser | `MAPPER` |
| Data provider | `MAPPER` |
| DSP | `ID_READER` |
| Sharer | `SHARER` (also surface the runbook's sharing-onboarding prerequisite check; halt if not confirmed) |

Present a confirmation block to the engineer like:

```
Plan:
  Ticket:      UID2-1234
  Participant: Acme Corp (advertiser)
  Env:         prod
  Role:        MAPPER
  Paperwork:   signed (per ticket description)
  Contact:     someone@acme.example

Proceed? [y/N]
```

Halt on `N` or if paperwork is not confirmed signed. The runbook says: "if not
specified and paperwork has been signed by the client, assume Production." For
test/integ requests, the ticket must explicitly say so.
```

### Task 11: Add Step 3 — acquire Okta access token

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 3 section**

````markdown
## Step 3 — Acquire admin-service access token

Read the service-account credentials from 1Password (choose the item per `--env`):

```bash
ITEM="uid2-admin-claude-automation - ${ENV}"
CLIENT_ID=$(op item get "$ITEM" --vault UID2 --fields label=okta_client_id --reveal)
CLIENT_SECRET=$(op item get "$ITEM" --vault UID2 --fields label=okta_client_secret --reveal)
AUTH_SERVER=$(op item get "$ITEM" --vault UID2 --fields label=okta_auth_server --reveal)
ADMIN_BASE_URL=$(op item get "$ITEM" --vault UID2 --fields label=admin_base_url --reveal)
```

Request the token (client_credentials grant):

```bash
TOKEN=$(curl -fsS -X POST "${AUTH_SERVER}/v1/token" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  -d "grant_type=client_credentials" \
  -d "scope=uid2.admin.client-key-issuance" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
```

On `curl` failure: print the HTTP error, do not retry. The most common causes
are: wrong credentials in 1Password (401), wrong scope name (400), wrong auth
server URL (404). Surface these explicitly — do not paper over with a retry.

The token is held in shell variable scope for this skill invocation only.
Never write it to disk.
````

### Task 12: Add Step 4 — resolve or create the site

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 4 section**

````markdown
## Step 4 — Resolve or create the site

```bash
SITES_JSON=$(curl -fsS "${ADMIN_BASE_URL}/api/site/list" \
  -H "Authorization: Bearer ${TOKEN}")
```

Search the response for an exact `name` match against the extracted participant
name (case-insensitive trimmed comparison). Three branches:

1. **Exact match.** Record `site_id` and move on.
2. **No match.** Confirm with the engineer: "No existing site named '<name>'.
   Create a new one? [y/N]". On `y`:

   ```bash
   curl -fsS -X POST "${ADMIN_BASE_URL}/api/site/add?name=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$NAME")&types=${TYPES_CSV}" \
     -H "Authorization: Bearer ${TOKEN}"
   ```

   Record the `id` from the response as `site_id`. `${TYPES_CSV}` is the
   uppercase comma-separated participant type list (e.g. `ADVERTISER`,
   `PUBLISHER,ADVERTISER`).

3. **Multiple similar matches** (case-insensitive substring). Print all matches
   with their `id`s and halt. Engineer picks the right `site_id` and re-runs
   with `--site-id=<id>` (out of scope for the initial skill; tell the
   engineer to use the Admin UI for this case and report which site_id to
   re-run against).
````

### Task 13: Add Step 5 — check for existing key with the requested role

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 5 section**

````markdown
## Step 5 — Check for an existing key with the requested role

```bash
EXISTING_KEYS=$(curl -fsS "${ADMIN_BASE_URL}/api/client/list/${SITE_ID}" \
  -H "Authorization: Bearer ${TOKEN}")
```

If any element has the chosen role in its `roles` array **and** is not
disabled, halt with:

```
Site ${SITE_ID} already has a non-disabled key with role ${ROLE}:
  key_id:  <key_id>
  name:    <name>
  created: <created>

The runbook says: name the new key with a numeric suffix (e.g. "Acme Corp" →
"Acme Corp 2"). Re-run with --name-suffix=2 to proceed, or use the Admin UI
to disable the old key first.
```

(The `--name-suffix` flag is part of the first version of this skill. Default
suffix is empty; the engineer adds it explicitly when needed.)
````

### Task 14: Add Step 6 — create the client key

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 6 section**

````markdown
## Step 6 — Create the client key

If `--dry-run`, print the planned `curl` command and stop here.

```bash
KEY_NAME="${PARTICIPANT_NAME}${NAME_SUFFIX:+ ${NAME_SUFFIX}}"
RESPONSE=$(curl -fsS -X POST \
  "${ADMIN_BASE_URL}/api/client/add?name=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$KEY_NAME")&roles=${ROLE}&site_id=${SITE_ID}" \
  -H "Authorization: Bearer ${TOKEN}")
```

The response is a `RevealedKey<ClientKey>` JSON object with this shape (Jackson
serialisation, confirmed against `RevealedKey.java`):

```json
{
  "authorizable": {
    "key_id":  "UID2-C-P-12345-...",
    "secret":  "<base64>",
    "name":    "...",
    "contact": "...",
    "roles":   ["MAPPER"],
    "site_id": 999,
    "service_id": 0,
    "disabled": false,
    "created": 1747800000
  },
  "plaintext_key": "UID2-C-P-12345-..."
}
```

This is the **only** copy of the plaintext key + secret that will ever be
available. From this point onward, treat the response as sensitive. Capture
two views:

```bash
# Sensitive — for the 1Password share only
KEY=$(echo "$RESPONSE" | python3 -c "import json,sys;print(json.load(sys.stdin)['plaintext_key'])")
SECRET=$(echo "$RESPONSE" | python3 -c "import json,sys;print(json.load(sys.stdin)['authorizable']['secret'])")

# Safe — for the Jira comment (plaintext_key removed top-level, secret removed from authorizable)
SAFE_JSON=$(echo "$RESPONSE" | python3 -c "import json,sys;d=json.load(sys.stdin);d.pop('plaintext_key',None);d.get('authorizable',{}).pop('secret',None);print(json.dumps(d,indent=2))")
```

If anything below this point fails before the key is shared, write the raw
response to `~/uid2-client-key-recovery-${TICKET}-$(date +%s).json` with
mode 0600 and tell the engineer the path. The plaintext key is **not**
retrievable from the admin service again.
````

### Task 15: Add Step 7 — package secrets into a 1Password ephemeral share

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 7 section**

````markdown
## Step 7 — Share via 1Password ephemeral link

Create a transient 1Password item containing the key and secret as plain text
fields (no quotes — the runbook specifies this), then share it with a 7-day
expiry:

```bash
ITEM_TITLE="UID2 client key — ${PARTICIPANT_NAME} — ${TICKET}"
op item create \
  --category="Secure Note" \
  --title="$ITEM_TITLE" \
  --vault=UID2 \
  "client_key=${KEY}" \
  "client_secret=${SECRET}" \
  "site_id=${SITE_ID}" \
  "ticket=${TICKET}"
SHARE_URL=$(op item share "$ITEM_TITLE" --vault=UID2 --expires-in 7d --emails "${CONTACT_EMAIL}" 2>&1 | grep -Eo 'https://share\.1password\.[a-z]+/[^ ]+')
```

If `op item share` fails (it requires a paid 1Password plan with sharing
enabled and may not be available on every account), fall back to:

```bash
echo "1Password share failed. Plaintext key + secret are in the 1Password
item titled '${ITEM_TITLE}' in the UID2 vault. Share it manually following
https://thetradedesk.atlassian.net/wiki/spaces/UID2/pages/403835076."
```

Clear `$KEY` and `$SECRET` from shell variables after this point:

```bash
unset KEY SECRET
```
````

### Task 16: Add Step 8 — post the Jira comment

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 8 section**

````markdown
## Step 8 — Comment back on the Jira ticket

The runbook requires posting the JSON metadata (with key/secret removed) plus
the 1Password share link as a comment. Example final comment body:

```
Issued via /uid2-client-key skill.

Share link (expires in 7 days): ${SHARE_URL}

Metadata:
${SAFE_JSON}
```

Post the comment:

```text
Call mcp__claude_ai_Atlassian__addCommentToJiraIssue with:
  cloudId        = <resolved earlier>
  issueIdOrKey   = ${TICKET}
  body           = <comment body above>
  responseContentFormat = "markdown"
```

If the comment post fails, print the full body to terminal so the engineer
can paste it manually. Do not retry automatically — Atlassian write
operations can succeed silently on the second attempt and produce duplicates.
````

### Task 17: Add Step 9 — final summary for the engineer

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Step 9 section**

```markdown
## Step 9 — Summary and engineer next steps

Print a final summary block:

```
✓ Client key issued for ${PARTICIPANT_NAME} (site_id=${SITE_ID})
  Role:         ${ROLE}
  key_id:       <SAFE_JSON.authorizable.key_id>
  Ticket:       ${TICKET}  (comment posted)
  Share link:   ${SHARE_URL}

Remaining manual steps (per runbook):
  [ ] Reply in the Slack thread with the :approved-check: emoji
  [ ] Update the UID2 Participant Information tracker spreadsheet
      (https://ttdcorp-my.sharepoint.com/:x:/g/personal/luis_chelala_thetradedesk_com/EYkD4Z_1AZJCg_nj3gweFVwBKShBtyjl-jq-fHeY-l7-zQ)
  [ ] Set ticket fields: Type=Task, Sprint=current UID2 sprint,
      Story Points=0.01, Story Points Remaining=0.01, Assignee=you
  [ ] Mark ticket Done
```
```

### Task 18: Add the troubleshooting section

**Files:**
- Modify: `skills/uid2-client-key/SKILL.md`

- [ ] **Step 1: Append the Troubleshooting section**

```markdown
## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| `401` from `${ADMIN_BASE_URL}/api/*` | The `client-key-issuance` scope→`MAINTAINER` mapping is not deployed yet in the target env. | Confirm release tag of uid2-admin in the env contains [the auth change](../specs/2026-05-21-claude-client-key-issuance-design.md). |
| `400 bad scope` from Okta | Service account does not have `uid2.admin.client-key-issuance` granted. | Ask Okta admin to grant the scope to the service-account application in the correct Okta tenant (integ vs prod). |
| `op item share` errors with "sharing not enabled" | 1Password plan does not support item sharing. | Use the manual Confluence-documented ephemeral-share flow. |
| Skill halts at "No existing site named X" but engineer knows the site exists | Name mismatch (whitespace, capitalisation). | Run `curl ${ADMIN_BASE_URL}/api/site/list -H "Authorization: Bearer ${TOKEN}" | jq '.[] | select(.name | test("X"; "i"))'`, then re-run with the exact name from the response. |
| Skill writes a recovery file | Something failed between key creation and share. | The recovery file holds the only copy of the plaintext key. Share it manually via 1Password, then delete the file. |
```

### Task 19: Integration test against the integ environment

> **Blocker:** Phase 1 must be merged and deployed to the integ admin service before this task can succeed. The Okta service account must also be provisioned (see operational handoff below).

**Files:** none (manual verification).

- [ ] **Step 1: Set up a test Jira ticket**

In the UID2 Jira project, create a Task titled "Test client key request — Acme Test" with a description that names a participant type (`advertiser`), explicitly says "for integ", and notes "paperwork signed (test)". Note the ticket key.

- [ ] **Step 2: Dry run**

```text
/uid2-client-key <TEST-TICKET> --env integ --dry-run
```

Expected: skill prints the resolved plan, the `POST /api/client/add` URL that *would* be called, and stops without writing anything. Confirm:
- Tailscale + 1Password preflight passes.
- Ticket fields parsed correctly.
- Site list call succeeds (returns JSON array).
- Existing-key check runs.
- Skill stops before `/api/client/add`.

- [ ] **Step 3: Real run against integ**

```text
/uid2-client-key <TEST-TICKET> --env integ
```

Expected: skill creates the key, creates the 1Password share, posts the comment. Manually verify in the integ Admin UI (`https://admin-integ.uidapi.com/`) that the client key exists with the right role and site.

- [ ] **Step 4: Clean up**

Disable the test client key via the integ Admin UI to avoid noise in future test runs. Delete the 1Password item.

### Task 20: Commit and open MR for the skill

- [ ] **Step 1: Commit**

```bash
cd /Users/sophia.chen/ttdsrc/uid2-claude-skills
git add skills/uid2-client-key/SKILL.md
git commit -m "$(cat <<'EOF'
feat(uid2-client-key): add skill to issue UID2 client API keys

End-to-end automation of the client-API-key issuance runbook:
- reads the Jira ticket
- resolves or creates the participant's site
- calls POST /api/client/add against the admin service
- packages the plaintext key+secret into a 1Password ephemeral share
- posts the metadata (key/secret removed) back to the ticket

Requires uid2-admin to have the client-key-issuance Okta scope deployed
(IABTechLab/uid2-admin PR <link>) and a service-account credentials
1Password item per env.

Refs: UID2-6903
EOF
)"
```

- [ ] **Step 2: Push and open MR**

```bash
git push -u origin HEAD
```

Open the MR via GitLab UI (or `glab mr create` if available). Title: `feat(uid2-client-key): add skill to issue UID2 client API keys`. Body should reference the spec, the integ test ticket key, and the uid2-admin PR.

---

## Phase 3 — Operational handoff (no code)

### Task 21: Document and request Okta service-account provisioning

**Files:** none — this is a handoff to the Okta admin.

- [ ] **Step 1: File a Jira ticket against the Okta admin team**

Create a UID2 ticket (or whatever channel the Okta admin team uses) requesting:

1. New Okta service-account application in the **integ** UID2 Okta tenant named `uid2-admin-claude-automation`, granted the custom scope `uid2.admin.client-key-issuance`.
2. Same in the **prod** UID2 Okta tenant.
3. For each: provide `client_id`, `client_secret`, the OAuth `/v1/token` URL, and the audience to the requester via a 1Password share.

Block on this ticket before Phase 2 Task 19 (integ test).

- [ ] **Step 2: Store credentials in 1Password**

Once the Okta admin returns the credentials, create the two 1Password items in the UID2 vault using the names from Phase 2 Task 8 (`uid2-admin-claude-automation - integ` and `uid2-admin-claude-automation - prod`), with fields:

| Field | Value |
|---|---|
| `okta_client_id` | as provided |
| `okta_client_secret` | as provided |
| `okta_auth_server` | as provided, e.g. `https://uid2.okta.com/oauth2/aus...` |
| `okta_audience` | as provided |
| `admin_base_url` | `https://admin-integ.uidapi.com` (integ) or `https://admin-prod.uidapi.com` (prod) |

---

## Sequencing

- Phase 1 (Tasks 1-6) can be developed standalone. Merge and ship to integ before starting Phase 2 Task 19.
- Phase 2 Tasks 7-18 (writing the skill content) can run in parallel with Phase 1 review.
- Phase 2 Task 19 is blocked on both Phase 1 deploy and Phase 3.
- Phase 3 is operational; kick it off as soon as Phase 1 PR opens so the credentials are ready when the skill is.
