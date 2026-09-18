# AI Query SDK

A Spring Boot starter that adds a natural-language query interface over your existing JPA model,
plus optional meeting links whose transcripts are stored against the lead and fed back into answers.

Everything runs in your process. No data, DB credentials or LLM key leaves the deployment.

Requires **Spring Boot 4.1+ and Java 21** (built against Framework 7 / Hibernate 7 / Jackson 3 — it
will not load in a Boot 3 app), and a host with a `DataSource` and an `EntityManagerFactory`.

## Use it in a project

Add the dependency. That is the entire integration — no `@Import`, no component scan change, no YAML,
no security config, no environment variable.

```xml
<dependency>
    <groupId>com.leadrat</groupId>
    <artifactId>ai-query-sdk</artifactId>
    <version>1.2.0</version>
</dependency>

<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/leadrat/java-sdk</url>
    </repository>
</repositories>
```

It is on GitHub Packages, so `~/.m2/settings.xml` needs a `github` server with a `read:packages` token.
Add `ai-sdk-data/` to `.gitignore`.

Start the app and follow the pages in order:

| | Page | What you do |
|---|---|---|
| 1 | `/ai-sdk/setup` | Exchange the OTP (logged at `WARN` on first start) for an admin password — the page is served only until this is done, after which it redirects to sign-in |
| 2 | `/ai-sdk/auth` | Sign in with that password; the session is kept in the browser until it expires |
| 3 | `/ai-sdk/settings` | Paste the OpenRouter key, pick the models |
| 4 | `/ai-sdk/configure` | Choose entities, fields, relationships, guardrails |
| 5 | `/ai-sdk/meetings` | Optional — connect one Google account |
| 6 | `/ai-sdk/console` | Ask a question |
| 7 | `/ai-sdk/embed` | Build the widget snippet |

Then ask, from your own frontend:

```js
const client = AiSdkWidget.client({ baseUrl: '/ai-sdk', tokenUrl: '/my-app/ai-sdk-token' });
await client.query({ question: 'Summarize this client and flag churn risk',
                     targets: [{ entity: 'Client', id: '4521' }] });
```

Or drop in the bundled widget, which renders an Ask button (and a meeting button) itself:

```html
<script src="/ai-sdk/assets/widget.js"
        data-ai-sdk-base="/ai-sdk"
        data-ai-sdk-entity="Client" data-ai-sdk-id="4521"
        data-ai-sdk-token-url="/my-app/ai-sdk-token"
        data-ai-sdk-meetings="true"></script>
```

The recommended token shape is a route in your app that calls `POST /ai-sdk/auth/token` server-side for
signed-in users and returns `{ "token": "..." }`, so the admin password never reaches a browser.

`extension/` holds a Chrome MV3 extension that does the same against any page marked up with
`data-ai-sdk-entity` / `data-ai-sdk-id`.

## Configure it

**Everything is configured on `/ai-sdk/settings` and stored in the SDK's own SQLite file.** Changes are
live — no restart. A value you already set as an environment variable or in YAML is picked up and shown
as *from host*; what you save on the page wins.

| Setting | Default |
|---|---|
| `llm.api-key` | `OPENROUTER_API_KEY` — **the only thing the SDK cannot invent** |
| `llm.provider`, `llm.base-url`, `llm.timeout-seconds` | `openrouter`, OpenRouter's URL, `30` |
| `llm.planner-model`, `llm.summarizer-model` | `anthropic/claude-sonnet-4.5` — both fields offer every model your account can reach, read live from the provider |
| `meeting.google.client-id` / `client-secret` | unset — fill them in and meeting links turn on |
| `meeting.google.redirect-uri` | derived from the request; the page prints the URL to register with Google |
| `meeting.recall.api-key` / `webhook-secret` | unset — fill them in and the notetaker bot turns on |
| `meeting.lead-entity` | `Lead` |
| `security.allowed-origins` | empty (no CORS headers); needed only for a cross-origin frontend |
| `security.admin-password` | unset — the one-time OTP flow via `/ai-sdk/setup` instead |

Nothing else needs setting. The admin OTP, the JWT signing secret and the Google token-encryption key are
generated and persisted on first start; the SQLite store falls back to a temp directory if its path is not
writable; and each feature enables itself once its credentials are present — `ai-sdk.meeting.enabled`,
`...google.enabled` and `...recall.enabled` exist only to force a feature **off**.

`security.admin-password` is the alternative to the OTP dance: set it and the admin password is
synced from it on every boot (at least 12 characters), so a fresh deployment authenticates
immediately with no `/ai-sdk/setup` visit at all. It stays authoritative across restarts — if
someone later sets a different password through the setup page, the next boot puts this one back.
Unset it to go back to the OTP flow and manage the password through the page instead.

Anything above can still be set the usual Spring way if you prefer, under the `ai-sdk` prefix
(`AI_SDK_LLM_API_KEY`, `ai-sdk.llm.api-key`, …), along with the tuning knobs that have sane defaults:
`storage.sqlite-path`, `security.jwt-expiry-minutes`, `query.*` (cache TTL, depths, row caps,
`rate-limit-per-minute`, `db-timeout-seconds`) and `license.*`.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/ai-sdk/query` | JWT | **The business endpoint** — answer a question about records |
| POST | `/ai-sdk/setup` | OTP | Set the admin password (once) |
| POST | `/ai-sdk/auth/token` | Password | Issue a JWT |
| GET | `/ai-sdk/status` | None | Setup state, readiness, read-only enforcement mode |
| GET | UI pages above | None (shell) | The admin pages |
| GET/POST | `/ai-sdk/configure/settings` | JWT | Read / save keys and models (secrets masked) |
| GET | `/ai-sdk/configure/settings/models` | JWT | Models the configured provider offers |
| GET | `/ai-sdk/configure/schema` | JWT | Introspected schema + current config |
| POST | `/ai-sdk/configure/entities`, `/configure/guardrails`, `/configure/rescan` | JWT | Save config, re-introspect |
| GET | `/ai-sdk/configure/audit` | JWT | Recent query audit entries |
| POST | `/ai-sdk/meetings` | JWT | Generate a meeting link for a lead |
| GET | `/ai-sdk/meetings/list?leadId=`, `/meetings/{id}`, `/meetings/{id}/discussions`, `/meetings/discussions?leadId=`, `/meetings/status` | JWT | Read meetings and discussions |
| PATCH/POST | `/ai-sdk/meetings/{id}`, `/{id}/cancel`, `/{id}/complete` | JWT | Reschedule, cancel, complete |
| GET/DELETE | `/ai-sdk/meetings/google/connect`, `/meetings/google` | JWT | Connect / disconnect Google |
| GET | `/ai-sdk/meetings/google/callback` | OAuth | Consent callback |
| POST | `/ai-sdk/webhooks/recall-ai` | Signature | Recall.ai bot and transcript events |

### Query request

```json
{
  "question": "Summarize these clients and flag anyone at churn risk",
  "targets": [{ "entity": "Client", "id": "4521" }, { "entity": "Lead", "id": "8890" }],
  "options": { "childDepth": 1, "parentDepth": 2, "maxChildrenPerRelation": 20 }
}
```

Options are clamped to the per-entity guardrails, themselves clamped to global ceilings. Relations that
are not traversable are dropped from the plan even if the model asks for them, and fields marked sensitive
or not exposed are stripped in code before anything is serialized for the LLM.

## Safety

The SDK takes **no separate database credentials** — it reuses your `DataSource` and enforces read-only
access itself:

- A private `EntityManagerFactory` (`aiSdkReadOnly`) over a `ReadOnlyDataSource` wrapper, isolated from
  your persistence context; sessions open with `defaultReadOnly` and `FlushMode.MANUAL`.
- Every connection is put in JDBC read-only mode and, on PostgreSQL, gets a read-only transaction
  characteristic, a `statement_timeout` and an `idle_in_transaction_session_timeout`, reset before
  returning to the pool.
- Every SQL string is checked by `ReadOnlySqlGuard`: `SELECT`/`WITH` only, no batches, no DML/DDL/TCL/`COPY`
  /sequence/large-object keywords, `CallableStatement` refused. A violation surfaces as `403`.
- Traversal is built with the Criteria API and bound parameters, so no LLM text ever reaches SQL.

The SQLite store holds config, the bcrypt password hash, generated secrets and the audit log — never
business rows. It is created `0600`; keep it out of version control.

To reset the password: stop the app, delete the row in `admin_setup` and the `setup-otp` row in
`sdk_secret`, restart, and run `/ai-sdk/setup` again. Deleting the `jwt-secret` row invalidates every
issued token. If `security.admin-password` is set, deleting `admin_setup` does not reset anything —
the next boot resyncs it right back; change or unset that config instead.

## Publishing

`.github/workflows/publish.yml` publishes on any `v*` tag using the workflow's own `GITHUB_TOKEN`:

```bash
git tag v1.2.0 && git push origin v1.2.0
```

From a workstation, with a `write:packages` token in the `github` server entry of `~/.m2/settings.xml`:

```bash
GITHUB_TOKEN=ghp_... mvn -B deploy -DskipTests
```

Release versions are immutable once published, so bump `<version>` for each release.
