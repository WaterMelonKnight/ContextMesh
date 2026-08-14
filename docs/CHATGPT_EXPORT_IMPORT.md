# ChatGPT official export import

ContextMesh accepts the `conversations.json` file from a user-requested official ChatGPT data export. Extract that file from the export ZIP and send its JSON content directly; ZIP upload, account login, cookies, private web APIs, and synchronization are intentionally not supported.

```http
POST /api/v1/workspaces/{workspaceId}/imports/chatgpt
Content-Type: application/json
```

The body is the standard top-level array of conversation records. Each record requires `id` and `mapping`; `title`, `create_time`, `update_time`, and `current_node` are used when present. The response is the same ordered `ConversationImportResult` summary as the generic importer.

## Normalization policy

- The ChatGPT conversation `id` becomes `externalId`, the source provider is `chatgpt`, and the source type is `IMPORTED_CONVERSATION`. Unix timestamps are retained as instants.
- A mapping is never read in JSON object order. With `current_node`, the importer walks its parent chain to the logical root and emits visible messages in root-to-leaf order. Null-message nodes remain traversal links but are not emitted.
- Without `current_node`, the canonical endpoint is the deepest leaf. Ties select the lexicographically greatest mapping node ID. This deterministic fallback keeps one branch and does not combine regenerated alternatives.
- Parent and child references are checked and parent cycles are rejected. A normalized message's `parentExternalId` is the external ID of its nearest retained ancestor message, never a mapping-node ID; omitted traversal nodes are skipped when linking parents. Raw mapping-node and parent-node IDs remain in bounded message metadata. The selected endpoint, explicit current node, and mapping-node count are retained as conversation metadata for provenance and future branch-aware import.
- `system`, `user`, `assistant`, and `tool` map directly to ContextMesh roles. Every other role fails with a source path rather than being guessed.
- Only `content_type: "text"` with string `parts` is normalized. Parts, Unicode, Markdown, code, whitespace, and newlines are preserved exactly. A system text node whose parts are all empty is treated as a traversal-only node; empty user and assistant text still fails validation. The known internal content types `user_editable_context` and `execution_output` are also traversal-only and are not flattened into text. Images, files, audio, structured tool payloads, and every other unsupported type fail validation; no placeholder text is invented.
- An assistant with `metadata.model_slug` receives generation metadata `{provider: "openai", model: <model_slug>}`. Model metadata is not inferred for other roles.

The complete payload is normalized before ingestion. Limits are 5 MiB per request, 1,000 conversations, 10,000 mapping nodes per conversation, and 1,000,000 text characters per message. Invalid exports return HTTP 400 Problem Details with a location such as `conversation[12].mapping["abc"].message.author.role`.

## Identity behavior

The adapter does not fingerprint, query the database, or decide duplicates. The shared ingestion service owns those operations. Importing an unchanged ChatGPT conversation ID again returns `SKIPPED_DUPLICATE`; changing normalized content while retaining the ID returns `CONFLICT`. Identity remains workspace-scoped.
