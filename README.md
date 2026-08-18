# ContextMesh

ContextMesh converts AI conversations and future agent activity into **evolving, evidence-backed state**. It is not a general chat client: the product goal is to reconstruct topics, projects, decisions, and changes while preserving the path back to original evidence.

> **Status — Local conversation MVP:** ChatGPT JSON import, immutable imported history, native continuations, and provider-neutral streaming chat are usable in the browser. Context graphs and extraction remain planned.

## Implemented now

- Java 21 / Spring Boot 3 modular-monolith foundation with Maven Wrapper.
- `GET /api/v1/health`, reporting application liveness and database availability without exposing diagnostics.
- PostgreSQL 16 with pgvector through Docker Compose.
- Flyway baseline containing only users and workspaces.
- Testcontainers database/migration smoke test and an ArchUnit cycle rule.
- Next.js/React/TypeScript status page that calls the real health API.
- Backend and frontend GitHub Actions checks.

The health endpoint deliberately returns HTTP `200 OK` when the process is alive, including when its payload is `DEGRADED` with `database: DOWN`. This keeps it usable as a liveness check and lets clients distinguish a reachable backend from a database outage. Readiness-sensitive deployment checks should inspect the payload (or use a future dedicated readiness endpoint).

## Planned product

Future vertical slices will add conversation normalization/import, versioned structured extraction, conservative entity resolution, evidence-aware graph and project projections, temporal history, and grounded retrieval. None of those features is claimed as implemented in Phase 0. See the [roadmap](docs/ROADMAP.md).

## Prerequisites

- Docker with Compose
- Java 21 (the Maven distribution is downloaded by `mvnw`)
- Node.js 22 LTS and npm (also recorded in `.nvmrc`)

## Development

The portable development launcher starts PostgreSQL with Compose, then runs Spring Boot and
Next.js as native processes. It activates Spring's `dev` profile and cleans up both application
processes on exit. From the repository root, run:

```bash
./scripts/dev.sh
```

Open <http://localhost:3000>. Every browser API call is same-origin: Next.js rewrites `/api/**` to
the backend, so the browser never needs a public Spring Boot hostname or port.

```text
browser -> http://localhost:3000 -> Next.js -> /api/** rewrite -> http://127.0.0.1:8080 -> Spring Boot
```

In a remote cloud IDE, only the frontend needs a public HTTPS origin (with no path). The backend
port does not have to be exposed at all:

```bash
CONTEXTMESH_PUBLIC_WEB_ORIGIN=https://workspace--3000.example-cloud-ide.com ./scripts/dev.sh
```

The remote browser topology is:

```text
browser -> https://workspace--3000.example-cloud-ide.com -> Next.js -> /api/** rewrite -> http://127.0.0.1:8080 -> Spring Boot
```

The launcher derives `CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST` from the public web URL for Next.js
`allowedDevOrigins`; localhost remains allowed and arbitrary hosts are not. Because the browser
only ever talks to its own origin, no request to the API is cross-origin and the server carries no
development CORS policy.

Set `CONTEXTMESH_INTERNAL_API_ORIGIN` only when Spring Boot does not listen on
`http://127.0.0.1:8080`. It is read by `next.config.mjs` on the server, is never exposed to the
browser, and must be a bare HTTP(S) origin without a path, query, fragment, or credentials.

Turn generation stays incremental through the proxy: the backend serves `text/event-stream` with
`Cache-Control: no-cache, no-transform`, which stops the Next.js rewrite and any cloud IDE HTTPS
proxy from gzipping — and therefore buffering — the deltas into a single burst at completion.

The existing `compose.yaml` intentionally supplies PostgreSQL infrastructure only. The launcher
uses it rather than adding invasive development containers for Maven and Next.js; Docker does not
remove the browser public-URL requirement for the frontend. PostgreSQL remains running after the
launcher exits and can be stopped with `docker compose down` (add `-v` only to delete local
database data).

### Manual startup

From the repository root, start pgvector PostgreSQL:

```bash
docker compose up -d postgres
```

Start the backend in a second terminal:

```bash
cd services/server
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

To enable a trusted OpenAI-compatible endpoint, configure it before starting the backend. No
provider credential is required while the adapter is disabled:

```bash
export CONTEXTMESH_OPENAI_ENABLED=true
export CONTEXTMESH_OPENAI_BASE_URL=http://localhost:11434/v1
export CONTEXTMESH_OPENAI_API_KEY=replace-with-your-key
cd services/server && ./mvnw spring-boot:run
```

The stable request provider ID is `openai-compatible`; each turn still selects its model. Endpoint
configuration is trusted local-user/administrator configuration in this release.

Start the frontend in a third terminal:

```bash
cd apps/web
npm ci
npm run dev
```

The web application reaches the backend through the Next.js `/api/**` rewrite, which targets
`http://127.0.0.1:8080` by default. Override it before starting Next.js when the backend listens
elsewhere:

```bash
CONTEXTMESH_INTERNAL_API_ORIGIN=http://127.0.0.1:9090 npm run dev
```

Development database defaults are `contextmesh` for database, user, and password on port `5432`. Override Compose with `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_PORT`; configure the server with `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. These defaults are local-only and are not production secrets.

### Verify the browser workflow

The `dev` Spring profile enables `GET /api/v1/development/workspace`. The endpoint idempotently creates and returns the deterministic local development workspace; the browser calls it automatically through the same-origin `/api` proxy. It is absent outside the `dev` profile.

1. Open <http://localhost:3000>.
2. Choose **Import ChatGPT JSON** and select a real `conversations.json` export (the browser sends its JSON contents, never its path).
3. Select the imported conversation and inspect its ordered messages.
4. Choose **Continue full conversation** or **Continue from here** beside a message.
5. In the new native conversation, use `fake` / `fake-model` for the built-in deterministic local provider, or `openai-compatible` and the server-configured model.
6. Send a message and observe the incremental assistant response. Reopen or refresh the conversation to confirm PostgreSQL persistence.

Provider API keys remain backend environment configuration and are never entered in or returned to the browser. The local workspace bootstrap is development-only; authentication and production workspace selection are intentionally deferred.

## Checks

```bash
cd services/server && ./mvnw verify
cd apps/web && npm ci && npm run lint && npm run typecheck && npm run build && npm audit
```

## Architecture and documentation

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Context engine](docs/CONTEXT_ENGINE.md)
- [API design](docs/API_DESIGN.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)

## License

ContextMesh is licensed under the [Apache License 2.0](LICENSE).
