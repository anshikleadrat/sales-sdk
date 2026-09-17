# AI Query SDK

An embeddable Spring Boot SDK that adds a natural-language query interface over a host
application's existing PostgreSQL/JPA model. It introspects the host's JPA entities,
lets an admin configure what is queryable through a built-in web UI, and exposes one
business-query endpoint that uses OpenRouter to plan and summarize answers.

Everything runs inside the host process. No customer data, database credentials or LLM
API key leaves the deployment; the only outbound vendor call is a metadata-only license
check, and it is skipped unless a license key and server URL are configured.

Requires Spring Boot 4.1+ and Java 21 in the host application — it is built against Boot 4
(Spring Framework 7, Hibernate 7, Jackson 3) and will not load in a Boot 3 app.

## Install

Add the dependency. That is the whole integration — the SDK auto-configures itself, mounts its
own endpoints and UI under `/ai-sdk`, and needs no `@Import`, component scan change, YAML block
or security configuration in the host application.

```xml
<dependency>
    <groupId>com.leadrat</groupId>
    <artifactId>ai-query-sdk</artifactId>
    <version>1.2.0</version>
</dependency>
```

The SDK is published to GitHub Packages, so a consuming project also needs the repository
and a token with `read:packages`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/leadrat/java-sdk</url>
    </repository>
</repositories>
```

```xml
<servers>
    <server>
        <id>github</id>
        <username>${env.GITHUB_ACTOR}</username>
        <password>${env.GITHUB_TOKEN}</password>
    </server>
</servers>
```

Add the SQLite store to `.gitignore`:

```
ai-sdk-data/
```

### What the SDK wires up for you

- **Its own Spring Security chain.** When Spring Security is on the classpath the SDK
  contributes a `SecurityFilterChain` scoped to `/ai-sdk/**`, ordered ahead of the host's
  chains. Without it a host resource server would try to decode the SDK's own tokens as its
  own and reject them before the SDK's `JwtAuthFilter` ever ran. The host's chains are left
  untouched and continue to guard every other path.
- **An admin password OTP.** If `ai-sdk.security.otp` is unset, a one-time OTP is generated on
  first start, stored in the SQLite store and logged at `WARN` until setup completes. Set the
  property explicitly to keep it out of the logs.
- **A JWT signing secret.** If `ai-sdk.security.jwt-secret` is unset, a 384-bit secret is
  generated and persisted in the SQLite store, so issued tokens survive restarts. An explicitly
  configured secret must be at least 256 bits.
- **The LLM key.** If `ai-sdk.llm.api-key` is unset, the SDK falls back to the host's
  `OPENROUTER_API_KEY` (and `OPENROUTER_BASE_URL`) environment variables, so an application
  already calling OpenRouter needs no new credential.

## Configure

Every setting is optional and has a default. The SDK binds under `ai-sdk`, so plain environment
variables work through Spring's relaxed binding without any YAML at all:

| Environment variable | Default |
|---|---|
| `AI_SDK_ENABLED` | `true` |
| `AI_SDK_SECURITY_OTP` | generated and stored on first start |
| `AI_SDK_SECURITY_JWT_SECRET` | generated and stored on first start |
| `AI_SDK_SECURITY_JWT_EXPIRY_MINUTES` | `60` |
| `AI_SDK_SECURITY_ALLOWED_ORIGINS` | empty (no CORS headers) |
| `AI_SDK_STORAGE_SQLITE_PATH` | `./ai-sdk-data/sdk-config.db` |
| `AI_SDK_LLM_API_KEY` | `OPENROUTER_API_KEY` |
| `AI_SDK_LLM_BASE_URL` | `OPENROUTER_BASE_URL`, else the OpenRouter default |
| `AI_SDK_LLM_PLANNER_MODEL` | `anthropic/claude-sonnet-4.5` |
| `AI_SDK_LLM_SUMMARIZER_MODEL` | `anthropic/claude-sonnet-4.5` |
| `AI_SDK_QUERY_RATE_LIMIT_PER_MINUTE` | `30` |
| `AI_SDK_QUERY_DB_TIMEOUT_SECONDS` | `5` |
| `AI_SDK_MEETING_ENABLED` | `false` |
| `AI_SDK_MEETING_LEAD_ENTITY` | `Lead` |
| `AI_SDK_MEETING_GOOGLE_CLIENT_ID` | `GOOGLE_CLIENT_ID` |
| `AI_SDK_MEETING_GOOGLE_CLIENT_SECRET` | `GOOGLE_CLIENT_SECRET` |
| `AI_SDK_MEETING_GOOGLE_REDIRECT_URI` | `GOOGLE_REDIRECT_URI` |
| `AI_SDK_MEETING_GOOGLE_TOKEN_ENCRYPTION_KEY` | `GOOGLE_TOKEN_ENCRYPTION_KEY` |
| `AI_SDK_MEETING_RECALL_API_KEY` | `RECALL_API_KEY` |
| `AI_SDK_MEETING_RECALL_WEBHOOK_SECRET` | `RECALL_WEBHOOK_SECRET` |

The equivalent YAML, for a deployment that prefers to pin everything explicitly:

```yaml
ai-sdk:
  enabled: true

  security:
    otp: ${AI_SDK_SETUP_OTP}
    jwt-secret: ${AI_SDK_JWT_SECRET}
    jwt-expiry-minutes: 60
    allowed-origins: []

  storage:
    sqlite-path: ${AI_SDK_SQLITE_PATH:./ai-sdk-data/sdk-config.db}

  llm:
    provider: openrouter
    base-url: https://openrouter.ai/api/v1
    api-key: ${OPENROUTER_API_KEY}
    planner-model: ${AI_SDK_PLANNER_MODEL:anthropic/claude-sonnet-4.5}
    summarizer-model: ${AI_SDK_SUMMARIZER_MODEL:anthropic/claude-sonnet-4.5}
    timeout-seconds: 30

  query:
    cache-ttl-minutes: 20
    max-targets-per-request: 25
    default-child-depth: 1
    default-parent-depth: 2
    max-child-depth: 3
    max-parent-depth: 3
    max-children-per-relation: 50
    db-timeout-seconds: 5
    rate-limit-per-minute: 30

  license:
    key: ${AI_SDK_LICENSE_KEY:}
    server-url: ${AI_SDK_LICENSE_URL:}
    check-interval-hours: 24

  meeting:
    enabled: true
    lead-entity: Lead
    max-discussions-per-lead: 5
    discussion-char-limit: 6000
    google:
      enabled: true
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      redirect-uri: https://app.example.com/ai-sdk/meetings/google/callback
      token-encryption-key: ${GOOGLE_TOKEN_ENCRYPTION_KEY}
      post-connect-redirect: https://app.example.com/ai-sdk/configure
    recall:
      enabled: true
      base-url: https://us-east-1.recall.ai
      api-key: ${RECALL_API_KEY}
      webhook-secret: ${RECALL_WEBHOOK_SECRET}
      bot-name: AI SDK Notetaker
      store-transcript: true
      language: auto
      auto-transcribe: true
      transcript-mode: prioritize_accuracy
      join-early-minutes: 2
      join-grace-minutes: 30
      direct-bot-window-minutes: 15
      reconcile-seconds: 600
```

`jwt-secret` must be at least 256 bits.

### Host requirements

The SDK builds a private read-only persistence unit over the host's own entities, so the host
needs a Spring Data JPA setup with an `EntityManagerFactory` and a `DataSource`. No
`persistence.xml` is required — managed types and their `@Converter` classes are discovered
from the host's live metamodel.

## Read-only guardrail

The SDK does not take separate database credentials. It reuses the application's own
`DataSource` — the one already mounted into the Spring app — and enforces read-only
access in the SDK instead of relying on a `SELECT`-only role:

- Queries run through a private `EntityManagerFactory` (`aiSdkReadOnly`) built over a
  `ReadOnlyDataSource` wrapper, isolated from the application's persistence context.
- Every connection handed to that factory is put in JDBC read-only mode and, on
  PostgreSQL, gets `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY`, a
  `statement_timeout` and an `idle_in_transaction_session_timeout` derived from
  `query.db-timeout-seconds`. The session state is reset before the connection returns
  to the pool, and discarded if the reset fails.
- Every SQL string reaching `prepareStatement`/`execute` is checked by
  `ReadOnlySqlGuard`: the statement must start with `SELECT`/`WITH`, may not be a
  multi-statement batch, and may not contain DML, DDL, transaction-control, `COPY`,
  sequence-mutating or file/large-object keywords. `CallableStatement` is refused
  outright.
- Hibernate sessions are opened with `defaultReadOnly` and `FlushMode.MANUAL`, so no
  entity change can ever be flushed.
- Attempts to turn the guardrail off (`setReadOnly(false)`) throw, and any violation
  surfaces on `POST /ai-sdk/query` as `403` with the guardrail message.

Traversal itself is built with the JPA Criteria API and bound parameters, so no
LLM-produced text is ever concatenated into SQL; the guardrail is the second line of
defence behind that.

## Use

The UI pages link to each other in setup order:

1. `GET /ai-sdk/setup` — exchange the OTP for a permanent admin password (one time only).
2. `GET /ai-sdk/auth` — exchange the password for a JWT held in the browser tab.
3. `GET /ai-sdk/configure` — enable entities, fields, relationships and guardrails.
4. `GET /ai-sdk/console` — run a question against the single query endpoint.
5. `GET /ai-sdk/embed` — build the snippet for embedding the widget in your own frontend.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/ai-sdk/setup` | OTP (one-time) | Set admin password |
| POST | `/ai-sdk/auth/token` | Password | Issue JWT |
| GET | `/ai-sdk/status` | None | Setup status and read-only enforcement mode |
| GET | `/ai-sdk/setup`, `/ai-sdk/auth`, `/ai-sdk/configure`, `/ai-sdk/meetings`, `/ai-sdk/console`, `/ai-sdk/embed` | None (shell) | UI pages |
| GET | `/ai-sdk/configure/schema` | JWT | Introspected schema + current config |
| POST | `/ai-sdk/configure/entities` | JWT | Save entity/field/relationship config |
| POST | `/ai-sdk/configure/guardrails` | JWT | Save guardrails + prompt instructions |
| POST | `/ai-sdk/configure/rescan` | JWT | Re-run introspection |
| GET | `/ai-sdk/configure/audit` | JWT | Recent query audit entries |
| POST | `/ai-sdk/query` | JWT | The single business query endpoint |
| POST | `/ai-sdk/meetings` | JWT | Generate a meeting link for a lead — the button's endpoint |
| GET | `/ai-sdk/meetings/list?leadId=` | JWT | Meetings for a lead, newest first |
| GET | `/ai-sdk/meetings/{id}` | JWT | One meeting |
| PATCH | `/ai-sdk/meetings/{id}` | JWT | Reschedule or edit |
| POST | `/ai-sdk/meetings/{id}/cancel` | JWT | Cancel, drop the bot, delete the calendar event |
| POST | `/ai-sdk/meetings/{id}/complete` | JWT | Mark completed |
| GET | `/ai-sdk/meetings/{id}/discussions` | JWT | Discussions captured from that meeting |
| GET | `/ai-sdk/meetings/discussions?leadId=` | JWT | A lead's discussions, latest first |
| GET | `/ai-sdk/meetings/status` | JWT | Google/Recall connection state |
| GET | `/ai-sdk/meetings/google/connect` | JWT | Google consent URL |
| GET | `/ai-sdk/meetings/google/callback` | None (OAuth) | Consent callback |
| DELETE | `/ai-sdk/meetings/google` | JWT | Disconnect Google and the Recall calendar |
| POST | `/ai-sdk/webhooks/recall-ai` | Signature | Recall.ai calendar, bot and transcript events |

### Query request

```json
{
  "question": "Summarize these clients and flag anyone at churn risk",
  "targets": [
    { "entity": "Client", "id": "4521" },
    { "entity": "Lead", "id": "8890" }
  ],
  "options": { "childDepth": 1, "parentDepth": 2, "maxChildrenPerRelation": 20 }
}
```

Options are clamped server-side to the per-entity guardrails, themselves clamped to the
global ceilings. Relations that are not marked traversable are dropped from the plan even
if the model asks for them, and fields marked sensitive or not exposed are stripped in
code before anything is serialized for the LLM.

## Meetings and lead discussions

With `ai-sdk.meeting.enabled`, a button in your app asks the SDK for a meeting link, the SDK
generates it, a notetaker bot joins the call, and what was discussed is stored against the lead —
so the next answer the SDK writes knows what was already said. Nobody pastes a link anywhere.

**One Google account, connected on the SDK's own pages.** Step 4 of the setup flow
(`/ai-sdk/meetings`, also reachable from the setup page) connects a single Google account for the
whole deployment; its refresh token is AES-GCM encrypted in the SDK's SQLite file. That account
backs every link the SDK generates, so reps never connect anything themselves. The redirect URI
registered with the Google OAuth client must be `https://your-host/ai-sdk/meetings/google/callback`.

**The button.** `POST /ai-sdk/meetings` with a lead id creates a Google Calendar event with a
`hangoutsMeet` conference request and returns the Meet link in the same response — nothing is
async, so the button either gets a link or gets an error explaining why. `scheduledAt` is optional
and defaults to now, which is what an "start a meeting with this lead" button wants.

```json
POST /ai-sdk/meetings
{ "leadId": "8890", "leadEntity": "Lead", "title": "Pricing walkthrough", "durationMinutes": 45 }

201 { "id": "…", "leadId": "8890", "meetingLink": "https://meet.google.com/abc-defg-hij", "status": "SCHEDULED", … }
```

The bundled widget renders that button for you with `data-ai-sdk-meetings="true"`, or call
`AiSdkWidget.meetingLink()` from your own UI. The lead is taken from the widget's first target,
so the same `data-ai-sdk-entity`/`data-ai-sdk-id` that powers the Ask button powers this one.

**Capture.** The connected Google account is registered with Recall.ai as a calendar. When the
event syncs, a bot is scheduled against it; inside the direct-bot window — or when the event has
not synced yet, which is the normal case for a meeting starting now — the bot is sent straight at
the meeting URL. Bot status, recording and transcript events arrive on `/ai-sdk/webhooks/recall-ai`,
verified with the Standard Webhooks HMAC signature. A background reconciler re-syncs the calendar
and picks up any meeting whose bot never got scheduled.

**Storage.** Transcripts are flattened to `speaker: text` lines and written to
`meeting_discussion`, keyed by `lead_id` — one lead, many discussions, read back newest first.
Each row keeps its meeting, participants, timestamps and the Recall bot id it came from, so a
re-delivered webhook updates the row rather than duplicating it.

**In the answer.** Every `/ai-sdk/query` target is looked up by id against `meeting_discussion`;
matching discussions are attached to that record as `meetingDiscussions` (newest first, capped by
`max-discussions-per-lead` and truncated at `discussion-char-limit`) and the summarizer is told to
ground its answer in them — what was promised, what was objected to, what the next step is. The
query cache keys on the discussion fingerprint, so a new transcript invalidates stale answers.

## Frontend clients

Two ready-made frontends consume the business endpoint. Both send the JWT as a bearer token
and neither needs a build step.

### Injected widget

A single script served by the deployment at `/ai-sdk/assets/widget.js`. It renders a
launcher and panel inside a shadow DOM, so it cannot collide with the host page's styles.

```html
<script src="https://app.example.com/ai-sdk/assets/widget.js"
        data-ai-sdk-base="https://app.example.com/ai-sdk"
        data-ai-sdk-entity="Client"
        data-ai-sdk-id="4521"
        data-ai-sdk-token-url="/my-app/ai-sdk-token"
        data-ai-sdk-position="bottom-right"
        data-ai-sdk-label="Ask AI"
        data-ai-sdk-meetings="true"
        data-ai-sdk-meeting-label="Get meeting link"
        data-ai-sdk-meeting-minutes="45"></script>
```

`data-ai-sdk-meetings="true"` adds the meeting-link button beside Ask. Pressing it calls
`POST /ai-sdk/meetings` for the current target and shows the generated Meet link with a copy
button; `onMeeting` receives the created meeting if you would rather render it yourself.

Or drive it from code, which suits single-page apps where the current record changes:

```js
const widget = AiSdkWidget.init({
  baseUrl: 'https://app.example.com/ai-sdk',
  getToken: async () => (await fetch('/my-app/ai-sdk-token')).json().then(r => r.token),
  targets: [{ entity: 'Client', id: '4521' }],
  options: { parentDepth: 2, childDepth: 1 },
  meetings: true,
  onAnswer: response => console.log(response.answer),
  onMeeting: meeting => console.log(meeting.meetingLink)
});

widget.setTargets([{ entity: 'Lead', id: '8890' }]);
```

The same script exposes a headless client when you want to render answers yourself:

```js
const client = AiSdkWidget.client({ baseUrl: '/ai-sdk', tokenUrl: '/my-app/ai-sdk-token' });
const response = await client.query({
  question: 'Summarize this client and flag churn risk',
  targets: [{ entity: 'Client', id: '4521' }]
});

const meeting = await client.createMeeting({ leadId: '8890', leadEntity: 'Lead', durationMinutes: 45 });
console.log(meeting.meetingLink);
```

Your own button, with no widget at all:

```js
const meeting = await AiSdkWidget.meetingLink();
window.open(meeting.meetingLink, '_blank');
```

With no targets configured, the widget picks up any element on the page carrying
`data-ai-sdk-entity` and `data-ai-sdk-id`.

**Tokens.** The recommended shape is a route in your own application that calls
`POST /ai-sdk/auth/token` server-side for signed-in users and returns `{ "token": "..." }`,
so the admin password never reaches a browser. Failing that the widget reuses a token from
session storage, and as a last resort prompts for the admin password on a 401.

`GET /ai-sdk/embed` builds the snippet for you from the entities you have enabled, and can
mount a live preview on the page.

### Chrome extension

`extension/` holds an unpacked Manifest V3 extension: a popup for asking about any record,
and a content script that mounts a panel on pages that mark records up with
`data-ai-sdk-entity` / `data-ai-sdk-id`. The token lives in `chrome.storage.local`; the
password is never stored. See `extension/README.md` for loading and packaging.

### Cross-origin access

A frontend on a different origin — including the extension — must have its origin
allow-listed, otherwise the browser blocks the call:

```yaml
ai-sdk:
  security:
    allowed-origins:
      - https://frontend.example.com
      - chrome-extension://<extension id>
```

Leaving `allowed-origins` empty (the default) sends no CORS headers at all, which is the
right setting when everything is same-origin.

## Operations

- The SQLite store holds configuration, the bcrypt admin password hash, the generated OTP and
  JWT signing secret, and the audit log. It never holds business rows. Keep it out of version
  control; the SDK creates it `0600`.
- Password recovery: stop the app, delete the single row in `admin_setup`, and either configure
  a fresh `ai-sdk.security.otp` or delete the `setup-otp` row in `sdk_secret` so a new one is
  generated and logged. Then call `/ai-sdk/setup` once more.
- Deleting the `jwt-secret` row in `sdk_secret` rotates the signing key and invalidates every
  issued token on the next start.
- Results are cached in-process for 20 minutes, keyed on targets, question, effective
  options and the configuration version, so saving configuration invalidates stale answers.

## Publishing

Artifacts go to GitHub Packages at `https://maven.pkg.github.com/<owner>/<repo>`. The owner
and repo are `github.owner` / `github.repo` properties in `pom.xml` (defaults: `leadrat` and
`java-sdk`); override them on the command line if the repository lives elsewhere.

### From CI

`.github/workflows/publish.yml` publishes on any `v*` tag, or on manual dispatch, using the
workflow's own `GITHUB_TOKEN`. No secret needs to be created:

```bash
git tag v1.2.0
git push origin v1.2.0
```

`.github/workflows/build.yml` builds every push to master and every pull request.

### From a workstation

Add the server entry to `~/.m2/settings.xml` (the `id` must be `github`, matching
`distributionManagement`), using a personal access token with `write:packages`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>your-github-username</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

```bash
GITHUB_TOKEN=ghp_... mvn -B deploy -DskipTests
```

`mvn deploy` attaches the jar, a sources jar and a javadoc jar. Release versions are
immutable once published, so publish iterations as `-SNAPSHOT` and bump `<version>` in
`pom.xml` for each release.
