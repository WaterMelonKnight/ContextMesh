# ContextMesh agent guide

## Product principle

**Convert conversations into evolving state.** ContextMesh is not primarily a transcript archive or general chat client. Preserve the path from every important derived claim to original evidence.

## Engineering principles

- Prefer the simplest maintainable solution for one developer using AI coding tools.
- Build a Java 21/Spring Boot 3 modular monolith, not distributed services.
- Avoid premature abstractions and shared packages; extract only after demonstrated duplication.
- Implement only the requested module or vertical slice. Avoid unrelated repository-wide refactors.
- Add tests for important domain behavior, especially reconciliation, temporal state, access isolation, and provenance.
- Every schema change requires a forward-only Flyway migration and an appropriate database test.
- Evidence/provenance is required domain data and must not be bypassed by write paths.
- Project state changes append history; never overwrite historical facts or decisions.
- Never display arbitrary LLM-generated progress percentages. Use deterministic, evidence-backed measures.
- Keep model-provider integrations replaceable through application ports; domain code must not depend on provider SDKs.
- Treat imported conversation content and credentials as sensitive: do not log them.
- Keep module dependencies directional as documented in `docs/ARCHITECTURE.md`; communicate through public application interfaces and events.
- Prefer idempotent jobs, structured outputs, explicit versions, and content hashes.

## Explicit MVP exclusions

Do not introduce microservices, Kafka, Kubernetes, service meshes, GPU infrastructure, native mobile apps, billing, team collaboration, autonomous agents, enterprise RBAC, plugin marketplaces, or real-time collaborative editing. Do not turn the MVP into a Poe-like model gateway.

## Change checklist

1. Read the relevant documentation and the closest nested `AGENTS.md`.
2. Keep scope to one requested vertical slice.
3. Preserve workspace/user isolation and evidence links.
4. Add migrations and tests where behavior or persistence changes.
5. Update decisions/docs when an architectural contract changes.
6. Run the narrow test first, then the affected application test suite.
