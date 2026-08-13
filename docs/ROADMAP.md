# Implementation roadmap

Each phase is a deployable vertical slice. Tasks are deliberately narrow enough for a coding agent, and each should update tests/docs without unrelated refactors.

## Phase 0 — Skeleton and infrastructure

**Goal:** reproducible, boring local foundation.

**Deliverables:** Maven Java 21/Spring Boot service, Next.js TypeScript app, Compose PostgreSQL/pgvector, Flyway baseline, health endpoints, CI format/test/build, workspace/request skeleton, secret/logging policy.

**Dependencies:** Java/Node/Docker versions. Apache-2.0 is selected in ADR-017.

**Acceptance:** documented one-command infrastructure start; server and web smoke tests pass; migration applies to empty DB; CI builds both; no product feature claims.

**Likely coding-agent tasks:** scaffold server with Maven wrapper; add Compose and DB health; add first Flyway migration; scaffold web with one status page; add CI workflows and developer commands.

## Recommended next PR

Implement **Generic Conversation JSON v1 + Normalized Conversation contract** only. Keep the contract compatible with future native multi-model conversations and sibling agent context sources, but do not implement agent support, provider generations, persistence, or import workers in that PR.

## Phase 1 — Conversation import and browser

**Goal:** safely import and inspect normalized source material.

**Deliverables:** import lifecycle/job table, generic JSON v1 schema/adapter, ChatGPT adapter with fixtures, idempotent conversation/message persistence, import report API, conversation/message API and minimal browser/evidence anchors.

**Dependencies:** Phase 0; exact initial ChatGPT fixture variants; upload limits.

**Acceptance:** both formats import; retries do not duplicate; malformed entries report locally; source order/provider/time retained; cross-workspace tests pass; user can browse exact messages.

**Likely coding-agent tasks:** define generic JSON contract and fixtures; implement normalization domain types; implement generic adapter; implement ChatGPT tree adapter; implement DB-backed import worker; add import API; add browser pages and pagination.

## Phase 2 — Topic extraction

**Goal:** prove conversation → versioned structured extraction → evidence-backed topics.

**Deliverables:** provider capability ports and one adapter, extraction schema/prompt registry, chunking, run/result/evidence persistence, strict validation, job retry/deduplication, initial entity/alias exact resolution, topic chips.

**Dependencies:** provider selection/BYOK secret handling; Phase 1 messages.

**Acceptance:** unchanged content is skipped; provider-invalid JSON cannot publish state; every topic links to a message/run; model/prompt/schema/tokens recorded; reprocessing retains old run; cheap-model budget configurable.

**Likely coding-agent tasks:** implement run schema/migration; define JSON Schema and validators; create provider port/test fake; build stable chunker; persist results/evidence atomically; implement exact alias resolution; show topic evidence in browser.

## Phase 3 — Context graph visualization

**Goal:** navigate entity relationships without transcript-node noise.

**Deliverables:** typed temporal entity relations, conservative candidate scoring, merge candidates/reversal foundations, bounded graph API, React Flow view plus accessible list, evidence drawer.

**Dependencies:** reliable topic/entity extraction and evaluation fixtures.

**Acceptance:** graph has entity nodes only; every edge has evidence; low-confidence duplicates remain separate; API enforces depth/node bounds; selecting edge/node reaches message; time filter changes valid edges.

**Likely coding-agent tasks:** relation migration/invariants; candidate generator/scorer; graph projection handler; bounded recursive SQL/API; React Flow canvas; graph list/evidence panel; merge review tests.

## Phase 4 — Project extraction and state

**Goal:** distinguish efforts from topics and produce explainable current project views.

**Deliverables:** project/assertion schema extensions, goal/task/milestone/decision/question extraction, deterministic state reconciler, project projection/API/UI, milestone count progress.

**Dependencies:** extraction precision baseline; state transition rules agreed.

**Acceptance:** project/topic remain distinct; all project items cite evidence; state transitions are append-only; uncertain transitions are suggestions; progress enumerates included/excluded milestones and is unavailable without denominator.

**Likely coding-agent tasks:** add project migrations; extend extraction schema/fixtures; implement assertion validator; implement transition rules/table tests; build projection replay; project API; project detail page.

## Phase 5 — Timeline and provenance

**Goal:** make change and “why” first-class.

**Deliverables:** temporal reconciliation, supersession/contradiction rules, late-import replay, project/entity timeline API/UI, evidence inspector, export/delete basics.

**Dependencies:** Phase 4 assertions; agreed effective-time semantics.

**Acceptance:** old decisions remain visible; current decision resolves correctly after out-of-order import; evidence shows provider/message/time/run; deletion removes source and derived data; export is documented and round-trippable where promised.

**Likely coding-agent tasks:** implement interval/supersession service; add deterministic replay tests; timeline query/API; timeline component; evidence inspector/deep link; workspace export; deletion job.

## Phase 6 — Ask My Context

**Goal:** grounded questions over accumulated state with bounded cost.

**Deliverables:** pgvector embeddings, lexical/entity/project/temporal retrievers, query planner/ranker, evidence packet composer, answer provider, citation validation, question UI and cost telemetry.

**Dependencies:** sufficiently trustworthy evidence/state; evaluation question set.

**Acceptance:** database-decision evaluation returns structured current state and correct source; no-evidence questions do not hallucinate; citations resolve; token budgets enforced; entire transcript history is never sent; retrieval/version/usage observable without raw-content logs.

**Likely coding-agent tasks:** embedding migration/repository; batch embedding jobs; implement each retriever independently; rank/deduplicate; context budgeter; grounded-answer adapter; citation validator; query UI; evaluation harness.

## Phase 7 — Multi-model capability (post-MVP candidate)

**Goal:** validate provider replaceability, not build a Poe competitor.

**Deliverables:** second provider adapter, workspace policy/routing, model capability discovery/configuration, optional minimal context-aware chat experiment.

**Dependencies:** demonstrated user need and stable provider ports.

**Acceptance:** extraction/answer provider can switch by configuration without domain changes; credentials isolated; failures/costs normalized; gateway scope remains intentionally narrow.

**Likely coding-agent tasks:** add second adapter contract tests; routing policy; credential settings UI; fallback behavior; provider usage comparison; document compatibility matrix.

## First five assignments

1. Scaffold `services/server` with Java 21, Spring Boot 3, Maven wrapper, module package boundaries, ArchUnit dependency test, and smoke test.
2. Add Docker Compose PostgreSQL/pgvector plus a deliberately small Flyway baseline for users/workspaces and a Testcontainers migration test. Add later tables only with their vertical slices.
3. Specify generic conversation JSON v1 with JSON Schema and representative valid/invalid fixtures; implement only parser-to-domain contract tests.
4. Implement idempotent conversation normalization/persistence application service with workspace-isolation integration tests.
5. Scaffold `apps/web` and implement a real health-status page; defer all import and conversation pages.

Assignments 3 and 5 may proceed after API/domain contracts stabilize; assignments touching shared migrations should remain sequential to minimize conflicts.

## Scope risks and guardrails

- **Chat gateway gravity:** reject rich chat/provider features until reconstruction/retrieval criteria pass.
- **Ontology explosion:** keep a small typed vocabulary; add types only with a user-visible query/view.
- **Premature graph infrastructure:** measure PostgreSQL traversal before considering Neo4j.
- **Extraction perfectionism:** ship confidence/review/evaluation loops; do not chase flawless automation.
- **Provider proliferation:** one provider until ports are proven, then exactly one comparison adapter.
- **Frontend polish before trust:** prioritize evidence navigation and correctness over elaborate graph animation.
- **Distributed systems temptation:** scale workers/database first; require measurements and an ADR for new infrastructure.
- **Privacy backlog:** credential handling, isolation tests, content-safe logs, export/deletion are release gates, not “later hardening.”
- **Unbounded reprocessing cost:** fingerprint, budget, batch, and require explicit selective reruns.
- **Solo-maintainer overload:** vertical slices, conventional tooling, minimal packages, and small PRs.
