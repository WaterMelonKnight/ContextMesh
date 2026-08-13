# Product specification

## Problem

AI conversations are fragmented by provider and session. Transcripts preserve words but not durable state: the same project has several names, decisions are superseded, tasks become complete, and questions remain unresolved. Users cannot reliably ask what they decided, how their thinking changed, or which message supports a claim.

ContextMesh reconstructs an evolving, temporal, evidence-backed context model from conversations and, later, observed AI-agent activity. Conversation import remains the first implementation slice; ContextMesh is not an agent orchestrator.

## Users and positioning

### Primary user

An individual knowledge worker, developer, researcher, founder, or creator who uses several AI tools and has enough history that decisions and projects are hard to track. The MVP is single-user in experience while retaining `workspace_id` boundaries for future SaaS isolation.

### Secondary users

- Privacy-conscious self-hosters who want portable records.
- Developers evaluating personal-memory and context-retrieval systems.

### Positioning

ContextMesh is an AI context reconstruction workspace, not a chat-provider replacement. Its differentiator is the chain **derived state → temporal history → source evidence**.

## Product vocabulary

- **Topic:** something discussed or learned about; it need not have an intended outcome.
- **Project:** an effort the user is trying to accomplish, with a goal and state.
- **Entity:** a canonical concept (including topic/project/person/technology) with aliases.
- **Assertion:** an extracted claim such as a goal, decision, question, task, milestone, or fact.
- **Context source:** an origin of observed context, initially an imported or native conversation and later potentially an agent run or external event.
- **Evidence:** an immutable pointer to the source content or event and the derivation run.
- **Current state:** a projection from still-valid, evidence-backed historical assertions—not a replacement for them.

## User stories

1. As a user, I can import generic JSON or a ChatGPT export and see a deterministic import report.
2. I can browse normalized conversations and messages with provider and timestamps.
3. I can see detected topics and projects and jump to supporting messages.
4. I can see aliases suggested as the same entity, while uncertain matches remain separate.
5. I can inspect a meaningful entity graph without every message becoming a node.
6. I can see a project's goal, state, milestones, tasks, decisions, questions, related topics, and recent evidence.
7. I can see when a decision or project state changed without losing the old state.
8. I can ask a question and receive a concise answer whose claims cite imported messages.
9. I can export or delete my workspace data.

## MVP scope

### Included

- One local account/workspace flow; schema-enforced tenant boundaries.
- Generic JSON schema plus ChatGPT export adapter.
- Conversation/message browser and evidence deep links.
- Asynchronous, versioned, strict-JSON extraction.
- Topics, projects, goals, decisions, open questions, tasks, milestones, facts, technologies, organizations, relevant people, and typed relations.
- Conservative entity resolution with aliasing, confidence, review status, and reversible manual correction.
- Entity-level context graph and temporal edges.
- Explainable project state and milestone-based progress counts.
- Timeline derived from assertions and state transitions.
- Hybrid context retrieval and evidence-bearing answers.
- One extraction/chat provider and one embedding provider behind replaceable ports.

### Non-goals

General-purpose multi-model chat, live provider synchronization, microservices, Kafka, graph databases, teams/RBAC, billing, native mobile, autonomous agents, plugin marketplace, real-time collaboration, self-hosted GPU models, and perfect fully automatic reconciliation.

## Core flows

### Import and reconstruct

1. User uploads a supported file and selects/auto-detects format.
2. Server stores import metadata, validates size/type, and streams normalization.
3. Idempotent normalized conversations/messages are visible immediately.
4. Background jobs chunk conversations, extract strict JSON, validate and persist immutable results.
5. Resolution links high-confidence aliases; ambiguous candidates enter review without merging.
6. Graph and project projections update, retaining evidence and historical validity.
7. UI reports per-conversation success/failure and allows retry.

### Inspect belief

1. User opens a project, decision, relation, or timeline entry.
2. UI shows confidence, validity, extraction version, and evidence snippets.
3. Selecting evidence opens the exact message and highlights the referenced span.

### Ask context

1. User asks a question, optionally scoped to project/time.
2. Retrieval combines structured state, search, graph neighbors, vectors, and recent events.
3. The answer model receives a bounded evidence packet.
4. UI presents answer citations; unsupported claims are omitted or explicitly uncertain.

## Acceptance criteria

The MVP is validated when:

- The two supported formats import repeatably without duplicate conversations/messages.
- A malformed record fails locally and yields an actionable report rather than aborting the entire import.
- Every displayed goal, decision, question, task, milestone, state transition, and graph relation has at least one accessible evidence record.
- Reprocessing unchanged content is skipped; a new extractor version can run alongside old results.
- “Context Graph project,” “ContextMesh,” and a descriptive alias can be proposed as duplicates; low-confidence cases remain separate.
- A superseded decision remains visible historically and the current projection points to the replacement.
- Project progress is shown as milestone/task counts with documented inclusion rules, never an invented percentage.
- A question about a recorded decision returns the current decision and message citation; absent evidence produces an honest “not found.”
- Workspace-scoped API/database tests prove one workspace cannot read another's records.
- A developer can start the eventual stack with documented Compose and application commands.

## Success signals

Measure import completion rate, evidence-link coverage, duplicate suggestion precision from reviewed samples, question-answer citation coverage, and time from import to useful project view. Do not optimize engagement or token volume. MVP quality is primarily trustworthy reconstruction.

## Unresolved product questions

- What exact ChatGPT export variants and attachment types are supported first?
- Should entity merge review ship before Ask My Context or initially be admin/developer-only?
- What minimum evidence quality permits an assertion into current state versus “suggested” state?
- Should deletion remove derived records immediately or use a short recovery window?
- Which single provider offers the best initial structured-output/cost/privacy balance?
