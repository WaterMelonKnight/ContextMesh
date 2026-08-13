# Data model

## Principles

PostgreSQL is the source of truth. UUIDv7 (or application-generated time-sortable UUIDs) are primary keys. Every user-owned row carries `workspace_id`; uniqueness is scoped by it. Timestamps are UTC `timestamptz`. Original normalized messages and extraction results are immutable except for deletion. Derived “current” projections are rebuildable from versioned assertions and history.

Rather than separate tables for every extracted type, canonical concepts use `entities` and claims use `assertions`; projects receive dedicated operational projections because their lifecycle and queries are richer. This prevents a dozen nearly identical evidence/link tables while retaining typed constraints in application code and check constraints. It trades some database-level subtype strictness for a smaller evolvable schema.

## Conceptual model

```mermaid
erDiagram
  USERS ||--o{ WORKSPACES : owns
  WORKSPACES ||--o{ IMPORTS : has
  WORKSPACES ||--o{ CONVERSATIONS : contains
  CONVERSATIONS ||--o{ MESSAGES : contains
  CONVERSATIONS ||--o{ EXTRACTION_RUNS : processedBy
  EXTRACTION_RUNS ||--o{ EXTRACTION_RESULTS : yields
  WORKSPACES ||--o{ ENTITIES : identifies
  ENTITIES ||--o{ ENTITY_ALIASES : namedBy
  ENTITIES ||--o{ ASSERTIONS : subjectOf
  ASSERTIONS ||--o{ EVIDENCE : supportedBy
  MESSAGES ||--o{ EVIDENCE : provides
  ENTITIES ||--o{ ENTITY_RELATIONS : relationSource
  ENTITIES ||--o{ ENTITY_RELATIONS : relationTarget
  ENTITIES ||--o| PROJECTS : projectedAs
  PROJECTS ||--o{ PROJECT_STATE_HISTORY : transitions
  PROJECTS ||--o{ PROJECT_ITEMS : organizes
```

## Context-source evolution (conceptual)

The planned conversation tables are the first concrete context source, not a universal container for every future input. A later agent-ingestion slice may introduce agent runs, steps, tool calls/results, artifacts, and parent-run relationships alongside conversations. Phase 0 deliberately adds neither a polymorphic `context_sources` table nor agent columns: the concrete access patterns and source formats must come first.

Native multi-model conversations should evolve toward `Conversation -> Message -> Generation`. Provider/model execution metadata belongs primarily to `Generation`; imported source-provider metadata may remain on `Conversation`. This avoids a one-conversation/one-model constraint without changing the Phase 1 normalized import contract prematurely.

## Normalized conversation persistence

The synchronous persistence path is `NormalizedConversation -> ConversationIngestionService -> PostgreSQL conversations/messages`. It stores the normalized header and every message atomically; message content parts remain structured JSONB objects (`type` and `text`) rather than being flattened or split into another table. Generation provider/model have explicit message columns.

`sequence_no` is the zero-based position in the original `NormalizedConversation.messages` list. It is persisted directly and is never inferred from source timestamps. `source_created_at` and `source_updated_at` describe time reported by the imported source, while `imported_at` records when ContextMesh accepted the source. Database `created_at`/`updated_at` are persistence audit times.

Source identity is workspace-local. When an external ID is present, identity is `(workspace_id, source_type, nullable source_provider, external_id)`. Without an external ID, the normalized source fingerprint itself is the identity. Consequently, identical anonymous content is skipped while changed anonymous content is a new source; a changed externally identified source is reported as a conflict and is not overwritten. A source identity and matching fingerprint returns `SKIPPED_DUPLICATE`; a matching external identity with another fingerprint returns `CONFLICT`.

`source_fingerprint` is lowercase SHA-256 over UTF-8 JSON for the complete normalized conversation. The canonical document has fixed field order, preserves message/content-list order, renders enums by their stable names and instants in ISO-8601 form, includes nulls, and recursively sorts metadata object keys while preserving metadata arrays. It contains no persistence IDs or import timestamps. Thus unordered Java `Map` iteration and database-generated values cannot change the fingerprint.

## Core tables

All foreign keys use matching `workspace_id` through composite unique constraints where feasible, preventing accidental cross-tenant references. Default deletion is restrictive; the workspace deletion service performs ordered hard deletion.

| Table | Purpose and important columns | Indexes / keys | Lifecycle |
|---|---|---|---|
| `users` | Account: `id`, normalized email, display name, `created_at`, `deleted_at` | PK `id`; unique lower(email) for active users | Soft-disable for login; hard/anonymize with workspace deletion policy. |
| `workspaces` | Isolation/ownership: `id`, `owner_user_id`, name, settings JSON, timestamps | PK; index owner | Soft-delete while deletion job runs, then hard-delete. |
| `provider_credentials` | Encrypted BYOK secret, provider, key version, label, last-used | PK; unique workspace/provider/label; FK workspace | Ciphertext only; hard-delete; rotate by inserting/updating encrypted value. |
| `imports` | Upload job/report: adapter, original filename, content hash, status, counts, errors JSON, timestamps | PK; index workspace/status/created; optional unique workspace/adapter/hash | Mutable state machine; raw staging locator cleared after completion. |
| `conversations` | Normalized header: source type/provider, external ID, title, source created/updated, source fingerprint, import/persistence times, metadata JSONB | PK; workspace-scoped external identity when present; workspace/fingerprint identity otherwise | Immutable in this slice; a changed externally identified source is a conflict rather than an overwrite. |
| `messages` | Ordered normalized record: conversation, external ID, zero-based sequence, role, structured content JSONB, source timestamp, parent external ID, generation provider/model, metadata | PK; unique conversation/sequence; workspace/conversation/sequence index and tenant-safe composite FK | Immutable. Hard-delete with conversation. Content-part tables and native generation records are deferred. |
| `extraction_runs` | One versioned attempt: conversation/chunk scope, extractor/prompt/schema versions, provider/model, source hash, status, started/completed, tokens/cost, supersedes run | PK; unique dedupe fingerprint for successful run; indexes workspace/status/time and conversation | Append-only attempts; status/usage finalize. Never overwrite old successful runs. |
| `extraction_results` | Raw validated structured item: run, item type, schema version, payload JSONB, ordinal, validation status | PK; unique run/ordinal; GIN payload only if measured need | Immutable audit artifact; retained while source exists. Invalid output stored securely with restricted diagnostics, not logs. |
| `entities` | Canonical node: type (`TOPIC`, `PROJECT`, `PERSON`, `ORGANIZATION`, `TECHNOLOGY`, etc.), canonical name, normalized name, description, confidence, review status | PK; unique workspace/type/normalized name only for confirmed canonical values where safe; trigram name; workspace/type | Not hard-merged: canonical survivor plus merge records. Archive via `archived_at`; project rows reference entity. |
| `entity_aliases` | Alias text/normalized form, entity, provider/source scope, confidence, status, valid times | PK; indexes workspace/normalized alias/type and entity; uniqueness only for confirmed scoped alias | Append/close validity. Ambiguous aliases may point through candidates, not multiple confirmed rows. |
| `entity_merge_candidates` | Candidate pair, feature scores JSON, confidence, resolution (`PENDING`, accepted, rejected), resolver/model/run | PK; unique ordered pair + resolver version; pending confidence index | Retained for audit/manual correction. Acceptance creates a merge record. |
| `entity_merges` | Reversible canonical mapping: absorbed entity, survivor, decision source, reason, confidence, `merged_at`, `reversed_at` | PK; indexes absorbed/current and survivor | Never destructive. Reads resolve active mapping; reversal closes mapping and rebuilds projections. |
| `assertions` | Typed extracted state: subject entity, predicate/type (`GOAL`, `DECISION`, `OPEN_QUESTION`, `FACT`, etc.), object entity or value JSON, status, confidence, observed/effective/valid times, extraction result | PK; indexes workspace/subject/type/current, GiST validity range, extraction result | Append-only claim. Close `valid_to`/status transactionally when contradicted/superseded; retain history. |
| `evidence` | Cross-cutting provenance for assertion/relation/state/retrieval output: target kind+ID, message, extraction run/result, quote start/end offsets, optional stored excerpt hash, confidence | PK; indexes target, message, run; FK source objects | Immutable. Prefer offsets into immutable message; excerpt is display convenience and integrity-checkable. At least one evidence row required before derived fact is published. |
| `entity_relations` | Typed directed graph edge, source/target, confidence, derivation source, `valid_from`, `valid_to`, status | PK; indexes workspace/source/type/current and target/type/current; GiST time range; unique active edge fingerprint | Append/close, never overwrite. Self-edge/type checks. Evidence required. |

## Project projections

| Table | Purpose and columns | Indexes / lifecycle |
|---|---|---|
| `projects` | One-to-one with a `PROJECT` entity; current goal assertion ID, summary, current state, state-since, updated timestamp | PK `entity_id`; workspace/state/update index. Rebuildable current projection, no independent truth. |
| `project_state_history` | Project, state enum, `valid_from`, `valid_to`, cause assertion/event, confidence | Current-state partial unique index; project/time index. Append and close; evidence attached to cause. |
| `project_items` | Project membership projection for assertion-backed `GOAL`, `MILESTONE`, `TASK`, `DECISION`, `OPEN_QUESTION`; assertion ID, kind, lifecycle status, ordering/due/completed dates | Unique project/assertion; project/kind/status index. Rebuildable; historical meaning remains in assertion. |
| `project_topics` | Temporal project↔topic relation projection with relation ID | Current project/topic indexes; append/close through underlying relation. |

Separate `project_milestones`, `project_tasks`, etc. are not used initially: their common provenance, temporal behavior, and modest subtype fields fit `assertions` plus `project_items`. If scheduling/query requirements diverge materially, migrate a subtype to a dedicated table without changing assertion identity.

### Explainable progress

The server returns counts, not an LLM score: `completed evidence-backed milestones / actionable evidence-backed milestones`. It also returns excluded counts (suggested, cancelled, no longer valid). If there are no accepted milestones, progress is `not_available`; task counts may be shown separately and never silently substituted.

## Memory projections, retrieval, and operations

Semantic memory (current supported facts/entities), episodic memory (evidence-backed events), and project memory (project assertions and history) are initially read projections over source-of-truth messages, assertions, entities, relations, and project history. Phase 0 does not create a `memories` table. A persisted retrievable-unit table may be added in Phase 6 only if retrieval measurements show a concrete need; it must be rebuildable, point to provenance, and never become duplicate truth.

The following operational tables are future vertical-slice schema, not part of the Phase 0 baseline:

| Table | Purpose and columns | Indexes / lifecycle |
|---|---|---|
| `embeddings` | Polymorphic retrievable source kind/ID, model/version, dimensions, content hash, vector, created time | Immutable cache; unique source/model/version/hash. Added in Phase 6. |
| `context_queries` | Optional privacy-aware retrieval audit metadata | Short retention; no raw query/answer by default. Added with retrieval. |
| `outbox_events` | Minimal durable event envelope and dispatch state | Added only when a concrete asynchronous vertical slice needs it. |
| `background_jobs` | Minimal durable idempotent work record, attempts and sanitized error | Added with import/extraction work; leases/backoff only as demonstrated by that work. |

## Temporal semantics

Three times must not be conflated:

- `observed_at`: source message time (when the user said it).
- `recorded_at`: database time (when ContextMesh learned it).
- `[valid_from, valid_to)`: period the assertion/relation/state is believed applicable.

`valid_to IS NULL` means currently valid, not eternally true. A newer contradicting decision closes the earlier validity interval and links it through `SUPERSEDES`; it does not edit the old value. Late imports may insert historical intervals and trigger deterministic projection rebuilds ordered by effective time, then source time, then stable ID. Uncertain conflicts coexist with status `CONTESTED` until resolved.

## Provenance invariants

1. Every published assertion, entity relation, and state transition has evidence.
2. Initially, evidence identifies workspace, conversation/message, source provider/timestamp, and extraction run/result. Future evidence can identify an agent run/step, tool call/result, or artifact and the exact source event/content that supports the belief.
3. Derived projection rows identify their causative assertion/relation/event.
4. Entity merging rewrites no source evidence; canonical resolution occurs at read/projection time.
5. Manual statements create a distinct `MANUAL` provenance record and never masquerade as imported text.

Enforce what is practical with FKs/checks; enforce the cross-table “evidence before publish” invariant in one application transaction and integration tests.

## Indexing and query strategy

- Always lead tenant queries with `workspace_id`; inspect plans with realistic data.
- B-tree conversation/message/project timelines; partial indexes for `valid_to IS NULL` and pending jobs.
- PostgreSQL full-text for lexical search and `pg_trgm` for aliases.
- HNSW pgvector cosine indexes after enough rows justify them; exact scan is acceptable for tiny workspaces.
- GiST range indexes for temporal overlap only on tables queried temporally.
- Avoid indiscriminate JSONB GIN indexes. Promote frequently queried fields to typed columns.
