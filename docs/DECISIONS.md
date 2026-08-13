# Architecture decision record

Decisions are accepted unless marked otherwise. Amend a decision with a new ADR; do not silently rewrite history once implementation begins.

## ADR-001 — Modular monolith for MVP

**Status:** Accepted. **Decision:** one Spring Boot deployable organized into enforced modules, plus one web app. **Why:** lowest operational and cognitive cost; transactional consistency is valuable for evidence/state. **Consequences:** module APIs and dependency tests are required; modules may be extracted only after measured pressure. Microservices are rejected.

## ADR-002 — PostgreSQL + pgvector is the primary store

**Status:** Accepted. **Decision:** normalized relational truth, temporal edges, full-text search, jobs, and vectors share PostgreSQL. **Why:** one backup/transaction/query system is adequate for MVP scale. **Consequences:** dimension/model-specific vector indexes and careful tenant indexes; specialist stores require benchmarks.

## ADR-003 — No graph database in MVP

**Status:** Accepted. **Decision:** represent the meaningful graph with `entities` and temporal `entity_relations`; bounded recursive SQL supplies graph views. **Why:** expected traversals are shallow and PostgreSQL is already required. **Consequences:** cap depth/size and isolate graph queries behind a port so later migration is possible.

## ADR-004 — Evidence is first-class domain data

**Status:** Accepted. **Decision:** published assertions, relations, and state transitions require immutable evidence linking message and extraction run. **Why:** “why do you believe this?” is core value. **Consequences:** slightly more write complexity and storage; derived state without evidence remains a suggestion/debug result, not truth.

## ADR-005 — Project and Topic are separate concepts

**Status:** Accepted. **Decision:** both are canonical entity types; projects additionally have goals, state, items, and timeline projection. **Why:** discussion is not intent to accomplish. **Consequences:** extraction schema and UI must not promote every recurring topic into a project.

## ADR-006 — Project progress must be explainable

**Status:** Accepted. **Decision:** report accepted completed milestones over accepted actionable milestones, with IDs/evidence and exclusions; unavailable when denominator is absent. **Why:** LLM percentages imply false precision. **Consequences:** LLMs may suggest milestones but cannot write progress; task counts remain separate.

## ADR-007 — LLM extraction uses versioned structured output

**Status:** Accepted. **Decision:** JSON Schema-constrained, validated results are keyed by content/extractor/prompt/schema/model-policy versions and retained across reruns. **Why:** extraction is probabilistic and improves. **Consequences:** storage and replay logic are required; invalid output cannot affect state.

## ADR-008 — No self-hosted GPU infrastructure in MVP

**Status:** Accepted. **Decision:** use BYOK external APIs through provider ports; prepare for future Ollama/OpenAI-compatible adapters without operating GPUs. **Why:** GPU operations conflict with solo-maintainer/cost goals. **Consequences:** disclose/minimize external data sharing and make provider replacement real.

## ADR-009 — Maven is the backend build tool

**Status:** Accepted. **Decision:** Maven wrapper and conventional multi-package single-module build initially. **Why:** explicit lifecycle, mature Spring/Testcontainers/quality tooling, dependency convergence, and less programmable build surface help coding agents. **Consequences:** accept more XML; introduce Maven submodules only if build boundaries later justify them.

## ADR-010 — Durable database jobs plus transactional outbox

**Status:** Accepted. **Decision:** expensive/retryable work uses a PostgreSQL job table; domain data and outbox event are committed together; Spring events dispatch in-process. **Why:** bare application events are not durable, while Kafka is unnecessary. **Consequences:** add only the minimum polling, idempotency, retry, and cleanup behavior demonstrated by an import or extraction job. Phase 0 has no job/outbox schema. General schedulers, dashboards, DAGs, advanced priority queues, and speculative distributed infrastructure are rejected. A broker can consume the outbox later if measured need arises.

## ADR-011 — Unified assertions, dedicated project projection

**Status:** Accepted. **Decision:** goals, decisions, facts, questions, tasks, and milestones share typed `assertions` + evidence; `projects`/`project_items` are query projections. **Why:** common provenance/temporal lifecycle outweighs sparse subtype differences. **Consequences:** application/schema validation must enforce type shapes; split tables only after concrete query/domain divergence.

## ADR-012 — Historical truth is append/close, current state is rebuildable

**Status:** Accepted. **Decision:** temporal assertions/relations/state use half-open validity intervals and supersession links. Current project and memory views are projections. **Why:** newer context must not erase how thinking evolved. **Consequences:** late imports trigger ordered replay; projection handlers must be idempotent.

## ADR-013 — React Flow for the first graph UI

**Status:** Accepted, revisit after prototype. **Decision:** controlled React Flow subgraphs plus accessible list view. **Why:** custom domain nodes/evidence interactions and React integration are primary; sophisticated graph analysis is not. **Consequences:** server bounds graph size; replace if measured layout/performance/accessibility needs favor Cytoscape.

## ADR-014 — Provider interfaces are capability-specific

**Status:** Accepted. **Decision:** separate structured generation, embeddings, and grounded answers instead of one broad `ModelProvider`. **Why:** providers/models have different capabilities and streaming/extraction failure semantics. **Consequences:** routing is per capability; vendor SDK types remain adapter-local.

## ADR-015 — Memory starts as derived read projections

**Status:** Accepted. **Decision:** semantic, episodic, and project memory initially derive from source messages, assertions, entities, relations, and append-only project history. Do not create a standalone `memory` implementation or `memories` table before Phase 6. **Why:** assertions and temporal history are already the evidence-backed truth; copying them creates reconciliation risk without a demonstrated retrieval need. **Consequences:** Ask My Context may later materialize versioned, rebuildable retrieval units after measurement, but those units must reference provenance and cannot become independent truth.

## ADR-016 — Provenance is a small cross-cutting capability

**Status:** Accepted. **Decision:** a `provenance` package owns stable evidence persistence and narrow read/write interfaces. It depends toward conversation/extraction sources; entity, graph, project, and retrieval consume its interfaces. **Why:** evidence supports more than canonical entities, including manual assertions, state transitions, and grounded answers. **Consequences:** keep the package small and the graph acyclic; do not grow it into a generalized workflow subsystem.

## ADR-017 — Apache License 2.0

**Status:** Accepted. **Decision:** license ContextMesh under the standard Apache License, Version 2.0. **Why:** it is OSI-approved, permissive, and includes explicit patent terms. **Consequences:** retain the root `LICENSE` and required notices when distributing the software; no custom license terms apply.

## ADR-018 — Context sources include conversations and future agent runs

**Status:** Accepted. **Decision:** treat `Context Source` as a conceptual boundary whose first-class children include Conversation and, in a future slice, Agent Run. Preserve raw execution records as evidence/timeline detail and graph only meaningful concepts. Model native multi-provider output at a future Generation layer rather than binding Conversation to one model. **Why:** conversation import should stay simple without blocking agent histories, multi-agent parent/child runs, artifacts, or multi-model native conversations. **Consequences:** add no generic source table, agent module, Generation persistence, or orchestration now. Extend provenance through concrete source-specific references when an implemented slice proves their shape; every derived claim must still resolve to original source content/event.

## Important unresolved decisions

1. **Initial model provider/model policy:** evaluate structured-schema reliability, cost, privacy terms, rate limits, and embedding availability with a fixed corpus.
2. **Authentication for local-first MVP:** simple local account versus external OIDC; preserve workspace-scoped server authorization either way.
3. **Raw archive retention:** delete staging immediately after normalization by default; confirm whether opt-in retention is worth privacy/storage complexity.
4. **ChatGPT branch semantics:** decide how alternate branches appear as conversations/messages using real export fixtures.
5. **Entity auto-merge thresholds:** require an evaluation corpus and precision-first target before enabling automatic merge.
6. **Embedding model migration:** initially allow one configured dimension per deployment or support multiple model-specific columns/tables; decide after provider selection.
