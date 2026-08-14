# REST API design (v1)

## Conventions

Base path is `/api/v1`. JSON uses camelCase and ISO-8601 UTC timestamps. IDs are opaque strings. Collection endpoints use cursor pagination (`items`, `nextCursor`) and bounded `limit` (default 25, max 100). Errors use RFC 9457 Problem Details with stable `type`, `title`, `status`, `detail`, `instance`, and optional `errors`/`correlationId`.

Phase 0 implements only `GET /api/v1/health`, returning non-sensitive application and database status. It returns `200 OK` whenever the backend can answer: `status: UP`/`database: UP` when the database query succeeds, or `status: DEGRADED`/`database: DOWN` when it does not. This liveness-oriented choice lets the frontend distinguish an unavailable backend from an unavailable database; no exception or connection detail is returned. Every product endpoint below is a planned contract for its corresponding future vertical slice, not currently implemented behavior.

Authentication is same-site secure session cookie for the initial web app; unsafe requests require CSRF protection. The authenticated workspace is selected through `/workspaces/{id}` in authorization context or a validated `X-Workspace-Id`; clients can never submit ownership fields in bodies. All endpoints enforce workspace isolation.

## Native Talk generation

`POST /api/v1/workspaces/{workspaceId}/conversations/{conversationId}/turns` accepts a provider,
model, and a non-empty list of text content parts. It is available only for native conversations
and returns `text/event-stream`. The provider-neutral SSE sequence is `started`, one or more
`delta`, then `completed`; completed data includes the persisted assistant message ID, provider,
and model. Errors detected before streaming use Problem Details. A provider failure after streaming
begins emits an `error` event with a stable sanitized code/message and terminates the stream.

The user message is committed before provider execution. Deltas are not database rows. On normal
completion their text is aggregated into one assistant message; on failure the user message stays
and no assistant message is written. The initial `fake` provider deterministically emits `Fake `
and `response` chunks for development and integration testing and never contacts a network.

Long work returns `202 Accepted` with a resource URL. `Idempotency-Key` is required for imports and context queries that may be retried. Optimistic `ETag`/`If-Match` is used for future manual resolution.

## Imports

### `POST /api/v1/workspaces/{workspaceId}/imports/conversations`

Synchronously accepts the documented Generic Conversation JSON v1 single or batch envelope. The workspace path parameter is the explicit isolation boundary. The response is an ordered summary with `totalReceived`, `importedCount`, `skippedDuplicateCount`, `conflictCount`, and per-conversation ingestion results. Duplicate and conflict statuses are successful business results; invalid schema or malformed JSON returns HTTP 400 Problem Details. Each conversation is ingested in its own transaction.

### `POST /api/v1/workspaces/{workspaceId}/imports/chatgpt`

Synchronously accepts the top-level JSON array from an official ChatGPT export `conversations.json`. The provider adapter selects one deterministic canonical mapping branch and delegates normalized conversations to the same import and ingestion services as the generic endpoint. Response and duplicate/conflict semantics are identical. Invalid mapping relationships, roles, timestamps, or unsupported non-text content return HTTP 400 Problem Details. See [ChatGPT official export import](CHATGPT_EXPORT_IMPORT.md).

### `POST /api/v1/imports`

Multipart fields: `file` and optional `adapter` (`auto`, `generic-json-v1`, `chatgpt-export`). Validates compressed/uncompressed limits and streams to staging.

```json
{
  "id": "imp_…", "status": "QUEUED", "adapter": "chatgpt-export",
  "createdAt": "2026-08-12T10:00:00Z",
  "links": {"self": "/api/v1/imports/imp_…"}
}
```

### `GET /api/v1/imports/{id}`

Returns status (`QUEUED`, `NORMALIZING`, `EXTRACTING`, `COMPLETED`, `PARTIAL`, `FAILED`), counts, sanitized per-record warnings/errors, timestamps, and retry eligibility. `POST /imports/{id}/retry` retries failed idempotent stages without re-upload. List imports with `GET /imports?cursor=&limit=`.

## Conversations

### Native Talk lifecycle

`POST /api/v1/workspaces/{workspaceId}/conversations` creates a native conversation and returns
`201 Created`. The JSON body is `{ "title": "Optional title" }`; the returned conversation has
`sourceType: "NATIVE_CONVERSATION"`, ContextMesh identity, timestamps, metadata, and an empty
`messages` array.

`POST /api/v1/workspaces/{workspaceId}/conversations/{conversationId}/messages` appends one native
message and returns `201 Created`. Its body contains `role`, a non-empty array of `{ "type":
"TEXT", "text": "..." }` parts, and optional `generation: { "provider": "...", "model": "..." }`.
The result includes the stable ID, zero-based `sequenceNo`, parent stable ID, content, timestamps,
and generation metadata. Imported targets return `409`; missing or cross-workspace targets return
`404`; invalid enums/content return Problem Details with `400`.

`GET /api/v1/workspaces/{workspaceId}/conversations/{conversationId}` returns the common header and
all messages ordered by sequence for either an imported or native conversation. This first Native
Talk slice intentionally returns the bounded conversation aggregate; pagination can be added when
measured conversation sizes require it.

Imported history cannot be extended through the native append API. A future continuation flow will
assemble selected imported conversation/graph/project context into a **new** native conversation;
that context assembly is not part of this API slice.

- `GET /conversations?query=&provider=&topicId=&projectId=&from=&to=&cursor=&limit=` returns summary cards, detected entity chips, and extraction status.
- `GET /conversations/{id}` returns header/source metadata and derived links, not all messages.
- `GET /conversations/{id}/messages?cursor=&limit=` returns ordered messages with role/time and stable evidence anchors.
- `GET /messages/{id}` returns one message plus conversation/provider metadata; optional `evidenceId` adds validated highlight offsets.

Message bodies are returned only from conversation/message endpoints, never embedded wholesale in graph responses.

## Topics and projects

- `GET /topics?query=&cursor=&limit=` and `GET /topics/{id}` return canonical name, aliases, related projects/topics, confidence, and evidence summaries.
- `GET /projects?state=&query=&cursor=&limit=` lists current projections.
- `GET /projects/{id}` returns goal, summary, state, `stateSince`, related topics, recent conversations, and explainable progress.
- `GET /projects/{id}/timeline?from=&to=&cursor=&limit=` returns state transitions and assertion events with evidence references.

Project response excerpt:

```json
{
  "id": "ent_project_1",
  "name": "ContextMesh",
  "state": {"value": "BUILDING", "since": "2026-08-20T09:00:00Z", "evidenceIds": ["ev_20"]},
  "goal": {"text": "Reconstruct evolving context", "evidenceIds": ["ev_1"]},
  "progress": {
    "method": "ACCEPTED_MILESTONES",
    "completed": 1, "total": 4, "excludedSuggested": 2,
    "completedItemIds": ["as_m1"]
  },
  "decisions": [{"id": "as_d1", "text": "Use PostgreSQL for MVP", "validFrom": "2026-08-10T12:00:00Z", "validTo": null, "evidenceIds": ["ev_10"]}],
  "openQuestions": [], "tasks": [], "milestones": []
}
```

There is deliberately no endpoint to set an arbitrary progress percentage.

## Graph

### `GET /api/v1/graph`

Parameters: one required seed (`entityId` or `projectId`), `depth` (default 1, max 2), optional entity/relation types and `at` timestamp, plus `limit` (max 250 nodes). Returns typed nodes, temporal edges, confidence, evidence summaries, and `truncated`. The server performs bounded traversal; a full workspace dump is not offered.

```json
{"nodes":[{"id":"ent_1","type":"PROJECT","label":"ContextMesh"}],"edges":[{"id":"rel_1","source":"ent_1","target":"ent_pg","type":"USES","validFrom":"2026-08-10T12:00:00Z","validTo":null,"evidenceIds":["ev_10"]}],"truncated":false}
```

## Search and context query

### `GET /api/v1/search?q=…&types=conversation,entity,project&cursor=&limit=`

Low-cost hybrid lexical/vector search over permitted workspace records. Results have `resultType`, title/snippet, score band (not misleading raw cross-index score), timestamp, entity links, and evidence/message link.

### `POST /api/v1/context/query`

```json
{
  "question": "What database did I decide to use for ContextMesh?",
  "scope": {"projectId": "ent_project_1", "from": null, "to": null},
  "answerMode": "CONCISE"
}
```

Returns `202` for asynchronous generation. `GET /context/queries/{id}` returns status and eventually:

```json
{
  "id": "cq_1", "status": "COMPLETED",
  "answer": "You selected PostgreSQL with pgvector for the MVP.",
  "citations": [{
    "id": "cite_1", "evidenceId": "ev_10",
    "conversationId": "conv_2", "messageId": "msg_8",
    "provider": "CHATGPT", "sourceTimestamp": "2026-08-10T12:00:00Z",
    "excerpt": "Use PostgreSQL + pgvector for the MVP.",
    "messageUrl": "/conversations/conv_2?message=msg_8&evidence=ev_10"
  }],
  "limitations": []
}
```

If evidence is insufficient, status still completes with an explicit not-found answer and empty citations; it does not improvise.

## Evidence and resolution

- `GET /evidence/{id}` returns target, immutable source identifiers, provider/timestamp, extraction run metadata, excerpt/offset, and message URL.
- Manual entity candidate review is intentionally small: `GET /entity-merge-candidates?status=PENDING` and `POST /entity-merge-candidates/{id}/resolution` with `{"decision":"ACCEPT"|"REJECT","reason":"…"}`. It requires `If-Match` and creates audit history; no destructive delete/merge API exists.

## API evolution

OpenAPI is generated/checked in CI once server scaffolding exists. Additive fields are allowed; clients ignore unknown fields. Breaking changes require `/v2` or an explicit compatibility plan. Internal extraction payloads are not exposed as stable public APIs.
