# Software architecture

## Architectural drivers

Optimize for one AI-assisted developer, rapid vertical slices, low cost, privacy, evidence integrity, and a future hosted offering. The MVP is a **modular monolith** with one PostgreSQL database and database-backed workers. Maven is chosen over Gradle because its declarative lifecycle, wrapper, dependency convergence tooling, and conventional Spring layout give humans and coding agents fewer build-language choices.

## System context

```mermaid
flowchart LR
  BROWSER["Browser"]
  WEB["Next.js web application"]
  SERVER["Spring Boot modular monolith"]
  DATABASE[("PostgreSQL with pgvector")]
  MODELS["External model APIs"]
  STORAGE["Object and file staging"]

  BROWSER --> WEB
  WEB -->|"REST API"| SERVER
  SERVER --> DATABASE
  SERVER -->|"Minimal required content"| MODELS
  SERVER --> STORAGE
```

Next.js is a presentation/BFF-free client for the MVP; authorization and domain rules live in Spring. Upload bodies may pass directly to Spring. PostgreSQL is the system of record for normalized content, assertions, temporal relations, jobs, full-text indexes, and vectors. Uploaded archives are deleted after processing by default; long-term raw-archive retention is opt-in.

## Backend style and dependency rules

Each module contains `domain`, `application`, and `adapter` packages as needed. Domain code is plain Java. Public application ports/DTOs form module APIs; internal repositories and provider SDK types are not exported. Synchronous calls are used when a caller needs an immediate result; committed domain events trigger projections/jobs.

Conversation source adapters are an ingestion boundary. Generic Conversation JSON and ChatGPT official `conversations.json` importers produce only provider-neutral `NormalizedConversation` values and enter the shared `ConversationImportService -> ConversationIngestionService -> PostgreSQL` path. Provider mapping/tree types do not enter conversation persistence. The ChatGPT canonical-branch contract is documented in [ChatGPT official export import](CHATGPT_EXPORT_IMPORT.md).

```mermaid
flowchart TD
  USER["user"]
  SHARED["shared"]
  CONVERSATION["conversation"]
  PROVIDER["provider"]
  INGESTION["ingestion"]
  EXTRACTION["extraction"]
  PROVENANCE["provenance"]
  ENTITY["entity"]
  GRAPH["graph"]
  PROJECT["project"]
  RETRIEVAL["retrieval"]

  USER --> SHARED
  CONVERSATION --> USER
  CONVERSATION --> SHARED
  PROVIDER --> USER
  PROVIDER --> SHARED
  INGESTION --> USER
  INGESTION --> CONVERSATION
  INGESTION --> PROVIDER
  INGESTION --> SHARED
  EXTRACTION --> CONVERSATION
  EXTRACTION --> PROVIDER
  EXTRACTION --> SHARED
  PROVENANCE --> CONVERSATION
  PROVENANCE --> EXTRACTION
  PROVENANCE --> SHARED
  ENTITY --> CONVERSATION
  ENTITY --> PROVENANCE
  ENTITY --> SHARED
  GRAPH --> ENTITY
  GRAPH --> PROVENANCE
  GRAPH --> SHARED
  PROJECT --> ENTITY
  PROJECT --> CONVERSATION
  PROJECT --> PROVENANCE
  PROJECT --> SHARED
  RETRIEVAL --> CONVERSATION
  RETRIEVAL --> ENTITY
  RETRIEVAL --> GRAPH
  RETRIEVAL --> PROJECT
  RETRIEVAL --> PROVENANCE
  RETRIEVAL --> PROVIDER
  RETRIEVAL --> SHARED
```

Arrows mean “may depend on.” `graph` and `project` consume entity/extraction facts rather than calling each other. Retrieval composes their read APIs. Events must not create hidden circular command flows.

## Modules

| Module | Responsibility and entities | Public interfaces | Depends on | Emits | Consumes |
|---|---|---|---|---|---|
| `user` | Users, workspaces, membership/ownership, provider credentials | `WorkspaceAccess`, `CredentialVault` | `shared` | `WorkspaceDeletionRequested` | — |
| `ingestion` | Import batch/file lifecycle, adapter selection, normalization jobs | `ImportService`, `ConversationImportAdapter` | `user`, `conversation`, `provider`, `shared` | `ConversationImported`, `ConversationNormalized`, `ImportCompleted` | workspace deletion |
| `conversation` | Provider-independent conversations/messages and source identity | `ConversationCatalog`, `MessageEvidenceReader` | `user`, `shared` | `ConversationStored` | workspace deletion |
| `provider` | Model/embedding clients, routing, credentials, usage metadata; no domain truth | `StructuredGenerationPort`, `EmbeddingPort`, `AnswerGenerationPort` | `user`, `shared` | `ProviderCallRecorded` | — |
| `extraction` | Runs, immutable results, schemas/prompts, chunking and validation | `ExtractionScheduler`, `ExtractionResultReader` | `conversation`, `provider`, `shared` | `ExtractionCompleted`, `EntityDetected`, typed assertion detection events | `ConversationNormalized` |
| `provenance` | Cross-cutting evidence records and validation; extraction-backed and manual provenance | `EvidenceWriter`, `EvidenceReader` | `conversation`, `extraction`, `shared` | — | — |
| `entity` | Canonical entities, aliases, candidate matches, merge/reversal, assertions | `EntityCatalog`, `ResolutionService`, `AssertionReader` | `conversation`, `provenance`, `shared` | `EntityMerged`, `EntitySplit`, `TopicDetected`, `ProjectDetected`, `DecisionDetected` | extraction detection events |
| `graph` | Temporal typed entity relations and graph read projection | `GraphQuery`, `RelationWriter` | `entity`, `provenance`, `shared` | `GraphUpdated` | entity/merge/assertion events |
| `project` | Project goal/state history, milestone/task/decision/question projections and explainable progress | `ProjectQuery`, `ProjectStateReconciler` | `entity`, `conversation`, `provenance`, `shared` | `ProjectStateChanged`, `ProjectProjectionUpdated` | project/assertion/merge events |
| `retrieval` | Query planning, hybrid candidates, ranking, evidence packet, grounded answer; composes derived memory views | `ContextQueryService`, `SearchService` | read interfaces of conversation/entity/graph/project/provenance; `provider`, `shared` | `ContextQueryAnswered` (audit metadata only) | — |
| `shared` | IDs, clock, pagination, domain-event/outbox primitives, errors; no business entities | small technical types | — | — | — |

`topics` and `projects` are typed canonical entities but projects additionally have a dedicated state projection. Evidence is a cross-cutting capability in the small `provenance` package: it persists stable links near extraction and exposes narrow application interfaces to entity, graph, project, and retrieval. It is not owned by canonical entities, because manual assertions, project transitions, and grounded answers also need provenance. The dependencies above keep that package acyclic.

Semantic, episodic, and project memory initially are derived read projections over messages, assertions, entities, relations, and project history. There is no implemented `memory` module or duplicate memory truth in the MVP foundation. Phase 6 may introduce persisted retrieval units only when measured Ask My Context requirements justify them; such units remain rebuildable and reference their sources.

## Context sources and future execution histories

A **Context Source** is the conceptual origin of material ContextMesh can understand. It is not a Phase 0 Java interface or database table. Initial source kinds are `IMPORTED_CONVERSATION` and, later, `NATIVE_CONVERSATION`; evolution may add `AGENT_RUN`, `MULTI_AGENT_RUN`, and `EXTERNAL_EVENT`. This boundary prevents extraction, provenance, and projections from permanently assuming all evidence is a chat message while avoiding a generic event framework before concrete formats exist.

Conversation remains a first-class, simple domain concept. Future agent histories form a sibling model: `Context Source -> Conversation | Agent Run`. Candidate execution primitives include `AgentRun`, `AgentStep`, `ToolCall`, `ToolResult`, `Plan`, `Subtask`, `Artifact`, `Agent`, and parent/child runs. Runs, steps, tool activity, and plans are primarily source/event records; stable artifacts can also become meaningful entities; all may anchor provenance. Extracted goals, decisions, tasks, and facts remain assertions. Verified completion and artifact/status events may feed project timelines and deterministic state reconciliation.

Multi-agent sources may preserve parent/child execution relationships and distinct agent identities while contributing evidence to the same workspace graph and project projections. ContextMesh observes these records; orchestration, autonomous agents, and a multi-agent framework remain outside the MVP. No agent persistence or module is introduced until an agent-ingestion vertical slice demonstrates the needed boundary.

A future native conversation separates **Conversation**, **Message**, and **Generation**. A conversation is not permanently bound to one model: provider/model, finish reason, usage, and provider request identity belong primarily to a generation, allowing different or parallel model responses to one user message. Imported conversations retain source-provider and per-message generation metadata in the initial normalized persistence model; dedicated generation records remain deferred until the native-conversation slice.

## Processing pipelines

The first synchronous import slice follows this provider-neutral boundary:

```text
Source-specific representation
        -> ConversationImporter (parse + validate + normalize)
        -> NormalizedConversation
        -> ConversationImportService (ordered batch + summary)
        -> ConversationIngestionService (identity + fingerprint + idempotency + conflict + per-conversation transaction)
        -> PostgreSQL (conversation and message storage)
```

Importers have no persistence dependency and do not reproduce ingestion rules. Generic JSON is the first adapter; future provider exports, browser extraction, and third-party exporter connectors implement the same normalization seam. Dynamic loading and plugin infrastructure are deliberately outside this boundary.

```mermaid
sequenceDiagram
  participant INGEST as Ingestion worker
  participant CONV as Conversation module
  participant EXTRACT as Extraction worker
  participant RESOLVE as Entity resolution
  participant GRAPH as Graph projector
  participant PROJECT as Project projector
  INGEST->>CONV: Store normalized conversation idempotently
  CONV-->>EXTRACT: Publish ConversationNormalized event
  EXTRACT->>EXTRACT: Chunk, generate, and validate
  EXTRACT-->>RESOLVE: Send immutable detections and evidence
  RESOLVE->>RESOLVE: Canonicalize or create review candidate
  RESOLVE-->>GRAPH: Publish entity and assertion events
  RESOLVE-->>PROJECT: Publish project and assertion events
  GRAPH->>GRAPH: Append or close temporal relations
  PROJECT->>PROJECT: Append state and rebuild current projection
```

### Jobs and transactions

The following is the target direction for the import/extraction vertical slices, not a Phase 0 scheduler deliverable. Phase 0 creates neither jobs nor outbox tables because no durable background work exists yet. Add their minimal schema and polling behavior with the first concrete job. Do not build a generalized distributed scheduler, dashboards, dependency DAGs, advanced priorities, or speculative worker infrastructure.

- API writes domain data and an `outbox_events` row in one transaction.
- A polling dispatcher claims rows with `FOR UPDATE SKIP LOCKED`, publishes Spring application events after commit, and marks dispatch status.
- Durable work lives in `background_jobs`, with type, JSON payload, deduplication key, attempts, lease, next attempt, and dead-letter status.
- Handlers are idempotent. In-process Spring events are notification mechanics, not durability.
- Projection updates use the source event ID as an idempotency key.

This is intentionally more reliable than bare `@Async` without adding Kafka. Later, the outbox can feed a broker and high-volume workers can be split along existing module ports.

## Frontend architecture

`apps/web` uses Next.js App Router, TypeScript, React Server Components for initial read pages where practical, and client components for uploads/graph interaction. A generated or hand-maintained thin API client maps server DTOs; domain logic is not duplicated in the browser. Suggested feature folders are `imports`, `conversations`, `projects`, `graph`, `search`, and `context-query`.

Use React Flow initially: it supports controlled nodes/edges and custom evidence-aware entity cards with less graph-specific API surface than Cytoscape. The API supplies bounded subgraphs; never load the full workspace graph. Accessibility requires a list/table alternative to the canvas.

## Internal event envelope

Every event has `eventId`, `eventType`, `schemaVersion`, `workspaceId`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, and a minimal payload. Never put raw conversations in events. Initial events include:

- `ConversationImported`, `ConversationNormalized`, `ExtractionCompleted`
- `EntityDetected`, `EntityMerged`, `EntitySplit`
- `TopicDetected`, `ProjectDetected`, `DecisionDetected`
- `ProjectStateChanged`, `GraphUpdated`

Events describe committed facts. Commands requesting expensive work are durable jobs, not misleading past-tense events.

## Provider abstraction

Separate capabilities because not every provider/model supports all of them:

```java
public interface StructuredGenerationPort {
  StructuredGeneration generate(StructuredGenerationRequest request);
}
public interface EmbeddingPort {
  List<Embedding> embed(EmbeddingBatch request);
}
public interface AnswerGenerationPort {
  GeneratedAnswer answer(GroundedAnswerRequest request);
}
```

Requests contain provider-neutral messages, model policy/cost tier, schema identifier + JSON Schema, timeout, and idempotency/correlation metadata. Results include parsed values or typed failures, usage, model identity, finish reason, and provider request ID. Adapters translate OpenAI/Anthropic/etc. Domain modules select a capability and policy, never a vendor model name. Streaming chat can be a later interface rather than contaminating extraction.

## Deployment

MVP Compose runs `web`, `server`, and a pgvector-enabled PostgreSQL image. The server process can execute both HTTP and worker roles; a profile permits a second identical worker process if needed. TLS terminates at a simple reverse proxy/hosting platform. Cloudflare may host/proxy the frontend later but is not required by backend contracts.

## Security and privacy

- TLS in transit; encrypted database/storage volumes in hosted deployment.
- Credentials encrypted at application level using a master key supplied outside the database; never returned after creation or logged. BYOK is workspace-scoped.
- Log IDs, timings, sizes, hashes, and error categories—not prompts, message bodies, answer bodies, or keys.
- Enforce `workspace_id` in every aggregate/query; repository APIs require workspace context. Add cross-workspace integration tests. Consider PostgreSQL RLS as defense-in-depth after query patterns stabilize.
- Minimize provider disclosure: send only selected chunks/evidence; show which provider is used; support redaction and future local adapters.
- Export includes normalized content, derived state, and provenance in documented JSON. Deletion cascades/queues embeddings and staged objects, with auditable completion but no retained sensitive payload.
- Validate upload type/size, zip paths and expansion ratio; rate-limit expensive endpoints; sanitize rendered Markdown.

## Cost controls

- **Local/SQL first:** hashes, parsing, exact aliasing, full-text search, state projection.
- **Cheap/background:** batched embeddings, classification, summary, structured extraction, metadata.
- **Capable/on demand:** ambiguous entity reconciliation and final grounded answers.
- Hash `(source content, extractor, prompt, schema, model policy)` to skip unchanged work; batch within token limits; cache embeddings and retrieval plans where safe.
- Record tokens and estimated cost per provider call/run; impose workspace budgets and job backpressure.

## Evolution without a rewrite

First scale PostgreSQL indexes, bounded queries, worker concurrency, and read projections. Then separate web/worker processes. If job throughput warrants it, publish the transactional outbox to a managed queue. Partition embeddings or use a specialist vector store only after measured pgvector limits. Introduce a graph database only for proven traversal needs; module interfaces already isolate graph reads. Split a module into a service only when ownership/scale/reliability demands outweigh operational cost.
