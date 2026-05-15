---
genre: index
title: Vault Index — NotePen
topic: overview
triggers:
  - "index"
  - "overview"
  - "orientation"
confidence: high
source: human
updated: 2026-05-07T17:37:42Z
---
# NotePen — Knowledge Vault

> **Entry point for AI agents.** Search this vault before any action.
> ai-agent-kit v6 — flat layout, one folder per feature, spec/plan split.

---

## Layout

```
vault/
├── _INDEX.md
├── _templates/
│   ├── spec.md                       ← FROZEN portion (Why/ACs/ECs/How/Test plan)
│   └── plan.md                       ← MUTABLE portion (Implementation plan / DoD / step diff stats)
│
├── features/<module>/<feature>/      ← one folder per feature
│   ├── spec.md                       ← frozen at CONFIRM (replaces v5 feature.md § spec sections)
│   ├── plan.md                       ← mutable across EXECUTE (replaces v5 feature.md § plan + DoD)
│   ├── test-cases.md                 ← live test state
│   └── retro.md                      ← optional, accumulates bug retros
│
├── reference/common/architecture.md  ← архитектура проекта (модули, слои, зависимости)
├── guidelines/<module>/<topic>.md    ← coding patterns (optional, project-specific)
├── guidelines/libs/<lib>-<version>.md ← cached external API documentation
│
└── tech-debt/<module>/               ← deferred non-critical findings, drained by `/kit-techdebt`
    ├── <slug>.md                     ← open / in-progress
    └── done/<slug>.md                ← archived
```

v4 used five Diátaxis subtrees (one file per concern → 7–10 files per feature). v5 collapsed them into a single `feature.md` per feature. v6 splits that one file again — but along the **frozen-vs-mutable** boundary, not the document-genre boundary: `spec.md` is the contract (untouched after CONFIRM) and `plan.md` carries everything that legitimately changes during EXECUTE (steps, replans, diff stats, DoD verdict). This stops replans from rotting the spec.

---

## Modules

| Module | Gradle module | Docs | Responsibility |
|--------|---------------|------|----------------|
| `common` | `:app:byCompose:common` | `vault/common/` | Compose Multiplatform UI: PDF rendering, drawing surface, page management |
| `shared` | `:shared` | `vault/shared/` | Cross-platform business logic and domain models |

---

## Agent Workflow

1. **Search**: `search_docs("topic")` — find relevant rules
2. **Read**: open the linked spec.md / plan.md / guideline files
3. **Execute**: implement by the rules
4. **Write**: `write_guideline(...)` / `update_doc(...)` — record learnings

---

## Quick Links

- Build: `./gradlew`
- Compile: `./gradlew compileKotlin`
- Test: `./gradlew :[module]:test`
- Lint: `./gradlew detekt ktlintCheck`
- Architecture: `reference/common/architecture.md`
- **PDF Editor Roadmap (M1–M8):** `concepts/common/plans/pdf-editor-roadmap.md`
- **ADR-001 — PDF rendering strategy:** `reference/shared/spec/adr-001-pdf-rendering.md`
- **M1 smoke-test report:** `guidelines/common/reports/test-runs/m1-pdf-infrastructure-run-01.md`
