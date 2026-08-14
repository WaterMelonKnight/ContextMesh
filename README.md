# ContextMesh

ContextMesh converts AI conversations and future agent activity into **evolving, evidence-backed state**. It is not a general chat client: the product goal is to reconstruct topics, projects, decisions, and changes while preserving the path back to original evidence.

> **Status — Phase 0:** the runnable development foundation is implemented. Product functionality such as importing conversations, extraction, graphs, projects, and Ask My Context remains planned and is not available yet.

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

## Run locally

From the repository root, start pgvector PostgreSQL:

```bash
docker compose up -d postgres
```

Start the backend in a second terminal:

```bash
cd services/server
./mvnw spring-boot:run
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

Open <http://localhost:3000>. The web application calls the backend at `http://localhost:8080` by default. Override it before starting Next.js when needed:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

Development database defaults are `contextmesh` for database, user, and password on port `5432`. Override Compose with `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_PORT`; configure the server with `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. These defaults are local-only and are not production secrets.

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
