# ContextMesh

ContextMesh is a planned open-source AI workspace that converts imported AI conversations into **evolving, evidence-backed state**. Instead of being another general chat client, it will reconstruct a user's topics, projects, goals, decisions, questions, tasks, milestones, and their changes over time.

> **Status:** product specification and architecture only. No application is implemented yet, and the commands below describe the intended developer experience.

## Why it exists

Useful context is fragmented across ChatGPT, Claude, Gemini, local models, and many sessions. Searchable transcripts do not answer what was decided, what changed, or why the system believes it. ContextMesh will normalize those transcripts, extract structured context, reconcile repeated concepts, and preserve links to the original messages.

## MVP

The first release validates one pipeline:

```text
conversation imports -> structured extraction -> entity resolution
  -> context graph -> project state and timeline -> retrieval with evidence
```

It includes generic JSON and ChatGPT export import, a conversation browser, versioned LLM extraction, conservative entity resolution, project/topic views, timelines, provenance, and evidence-bearing “Ask My Context.” It deliberately excludes a general multi-model chat gateway, collaboration, billing, mobile apps, and distributed infrastructure.

## Planned architecture

```text
Browser -> Next.js/TypeScript web app -> Spring Boot 3/Java 21 modular monolith
                                      -> PostgreSQL + pgvector
                                      -> external model/embedding APIs (BYOK)
```

The backend is one deployable with directional domain modules. PostgreSQL stores transactional, temporal, graph-edge, full-text, and vector data. Database-backed jobs provide resumable background work; no Kafka or graph database is required. Maven is selected for its explicit, predictable lifecycle and widespread Spring tooling support—useful to a solo developer and coding agents.

## Repository layout (planned)

```text
apps/web/             # Next.js application (created in Phase 0)
services/server/      # Spring Boot modular monolith (created in Phase 0)
docs/                 # Product and technical specifications
```

Shared packages will be added only when actual duplication justifies them.

## Intended local development

After Phase 0, developers will use Docker Compose for PostgreSQL/pgvector and run the two applications independently:

```bash
docker compose up -d postgres
cd services/server && ./mvnw spring-boot:run
cd apps/web && npm run dev
```

These commands do not work yet because scaffolding is intentionally outside this specification task. Start with [the product specification](docs/PRODUCT.md), [architecture](docs/ARCHITECTURE.md), and [roadmap](docs/ROADMAP.md).

## Guiding rule

**Convert conversations into evolving state, and make every important belief explainable from evidence.**

## Documentation

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Context engine](docs/CONTEXT_ENGINE.md)
- [API design](docs/API_DESIGN.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)

ContextMesh is intended to be open-source and self-hostable; a license and contribution policy will be selected before implementation begins.
