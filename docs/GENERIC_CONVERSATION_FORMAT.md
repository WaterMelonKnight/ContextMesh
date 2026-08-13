# Generic Conversation JSON v1

## Purpose and boundary

Generic Conversation JSON is the public, provider-neutral interchange format for importing a conversation into ContextMesh. The boundary is:

`Provider-specific export -> Provider Adapter -> NormalizedConversation -> persistence/extraction (later)`

Adapters decide how provider data maps into this contract. This version neither persists data nor silently repairs an import. A future `AgentRun` is a sibling **ContextSource**, not a conversation squeezed into this format; agent runs are not supported by v1.

## Envelope and example

The version is the string `"1"`. Timestamps are ISO-8601 instants (an offset or `Z` is required and normalized by Java's `Instant`).

```json
{
  "schemaVersion": "1",
  "conversation": {
    "externalId": "conversation-42",
    "title": "A model-independent conversation",
    "sourceType": "IMPORTED_CONVERSATION",
    "sourceProvider": "example-exporter",
    "createdAt": "2026-01-01T10:00:00Z",
    "messages": [
      {
        "externalId": "message-1",
        "role": "USER",
        "content": [{ "type": "TEXT", "text": "Hello" }],
        "metadata": {}
      },
      {
        "externalId": "message-2",
        "parentExternalId": "message-1",
        "role": "ASSISTANT",
        "content": [{ "type": "TEXT", "text": "Hi" }],
        "generation": { "provider": "openai", "model": "example-model" }
      }
    ]
  }
}
```

## Fields

All fields not marked required are optional and may be omitted or `null`. Present identifiers, titles, provider names, and model names cannot be blank.

- The envelope requires `schemaVersion` and `conversation`.
- A conversation requires `sourceType` (`IMPORTED_CONVERSATION` or `NATIVE_CONVERSATION`) and an ordered `messages` array. It may have `externalId`, `title`, `sourceProvider`, `createdAt`, `updatedAt`, and `metadata`.
- A message requires a provider-neutral `role` (`SYSTEM`, `USER`, `ASSISTANT`, or `TOOL`) and at least one content part. It may have `externalId`, `createdAt`, `parentExternalId`, `generation`, and `metadata`.
- Generation `provider` and `model` are both required when generation is present. Generation belongs to a message, so assistant messages in one conversation may name different providers/models.
- v1 implements only `{ "type": "TEXT", "text": "non-empty text" }`. Whitespace and newlines are preserved. Reserved normalized-domain extension types are `CODE`, `IMAGE_REF`, `FILE_REF`, `TOOL_CALL`, and `TOOL_RESULT`, but v1 rejects them.

## Validation and ordering

Validation is strict and deterministic. Unknown versions, roles, content types, and fields fail. Invalid timestamps, blank present IDs, empty content, and duplicate non-null message external IDs within a conversation fail. Errors include a JSON-style path where practical. Unknown fields are rejected rather than ignored, and malformed values are never coerced or repaired.

The messages list is the imported/default display order and is preserved exactly. `parentExternalId` retains an optional source relationship but does not change list order. Future provider adapters may select a canonical branch or emit multiple normalized conversation views; v1 does not define traversal or build a graph.

Metadata is optional JSON object data (null, booleans, numbers, strings, arrays, and objects). Runtime limits apply independently to conversation and message metadata: at most 50 total object entries, maximum nesting depth 4, and at most 16,384 total string characters. The complete input is limited to 5 MiB. These bounds prevent metadata from becoming an unbounded payload.

## Compatibility and extensions

The runtime parser is authoritative; the companion [JSON Schema](../schemas/generic-conversation-v1.schema.json) describes the wire shape for tooling. Within version 1, field meanings and ordering semantics remain stable. Additive wire changes require an explicit parser/schema decision because v1 rejects unknown fields. Breaking changes require a new `schemaVersion`. New content types must receive typed normalized-domain representations and explicit validation before a format version accepts them; arbitrary provider JSON must not leak into core content.
