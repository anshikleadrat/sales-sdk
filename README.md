# AI Query SDK

An embeddable Spring Boot SDK that adds a natural-language query interface over a host
application's existing PostgreSQL/JPA model. It introspects the host's JPA entities,
lets an admin configure what is queryable through a built-in web UI, and exposes one
business-query endpoint that uses OpenRouter to plan and summarize answers.

Everything runs inside the host process. No customer data, database credentials or LLM
API key leaves the deployment; the only outbound vendor call is a metadata-only license
check, and it is skipped unless a license key and server URL are configured.

## Install

```xml
<dependency>
    <groupId>com.leadrat</groupId>
    <artifactId>ai-query-sdk</artifactId>
    <version>1.0.0</version>
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

Auto-configuration picks the SDK up; no `@Import` or component scan change is needed.

## Configure

```yaml
ai-sdk:
  enabled: true

  security:
    otp: ${AI_SDK_SETUP_OTP}
    jwt-secret: ${AI_SDK_JWT_SECRET}
    jwt-expiry-minutes: 60

  storage:
    sqlite-path: ${AI_SDK_SQLITE_PATH:./ai-sdk-data/sdk-config.db}

  datasource:
    readonly:
      url: ${AI_SDK_RO_DB_URL:}
      username: ${AI_SDK_RO_DB_USER:}
      password: ${AI_SDK_RO_DB_PASSWORD:}

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
```

`jwt-secret` must be at least 256 bits. If `datasource.readonly.url` is blank the SDK
falls back to the application's primary `DataSource` and logs a startup warning; the
recommended posture is a dedicated `SELECT`-only PostgreSQL role:

```sql
CREATE ROLE ai_sdk_ro LOGIN PASSWORD '...';
GRANT CONNECT ON DATABASE app TO ai_sdk_ro;
GRANT USAGE ON SCHEMA public TO ai_sdk_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ai_sdk_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO ai_sdk_ro;
```

## Use

The UI pages link to each other in setup order:

1. `GET /ai-sdk/setup` — exchange the OTP for a permanent admin password (one time only).
2. `GET /ai-sdk/auth` — exchange the password for a JWT held in the browser tab.
3. `GET /ai-sdk/configure` — enable entities, fields, relationships and guardrails.
4. `GET /ai-sdk/console` — run a question against the single query endpoint.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/ai-sdk/setup` | OTP (one-time) | Set admin password |
| POST | `/ai-sdk/auth/token` | Password | Issue JWT |
| GET | `/ai-sdk/status` | None | Setup and datasource status |
| GET | `/ai-sdk/setup`, `/ai-sdk/auth`, `/ai-sdk/configure`, `/ai-sdk/console` | None (shell) | UI pages |
| GET | `/ai-sdk/configure/schema` | JWT | Introspected schema + current config |
| POST | `/ai-sdk/configure/entities` | JWT | Save entity/field/relationship config |
| POST | `/ai-sdk/configure/guardrails` | JWT | Save guardrails + prompt instructions |
| POST | `/ai-sdk/configure/rescan` | JWT | Re-run introspection |
| GET | `/ai-sdk/configure/audit` | JWT | Recent query audit entries |
| POST | `/ai-sdk/query` | JWT | The single business query endpoint |

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

## Operations

- The SQLite store holds configuration, the bcrypt admin password hash and the audit log.
  It never holds business rows. Keep it out of version control; the SDK creates it `0600`.
- Password recovery: stop the app, delete the single row in `admin_setup`, redeploy with a
  fresh OTP, then call `/ai-sdk/setup` once more.
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
git tag v1.0.0
git push origin v1.0.0
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
