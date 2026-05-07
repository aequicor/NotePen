# DECISIONS.md — NotePen

> Architectural Decision Records (ADR). Each decision is numbered and dated.
> Reference this file before making architectural choices.

---

## Template

```markdown
## ADR-NNN: <title>

**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-NNN
**Date:** YYYY-MM-DD

### Context
Why this decision was needed.

### Decision
What was decided.

### Alternatives considered
What else was considered and why rejected.

### Consequences
Trade-offs and implications.
```

---

## ADR-001: Use DECISIONS log

**Status:** Accepted
**Date:** (fill in project start date)

### Context
Architectural decisions made without documentation are rediscovered expensively.
Agents and team members need a reliable source of "why we did it this way".

### Decision
All architectural decisions — library choices, patterns, key trade-offs — are documented here in ADR format.
Agents must check this file at startup and before making architectural recommendations.
Any TODO/FIXME left in code must have a corresponding entry here.

### Alternatives considered
- Inline code comments: too scattered, not searchable, lost in refactors.
- External wiki: not co-located with code, agents can't read it directly.

### Consequences
+: Decisions survive team changes and session resets.
+: Agents can avoid re-litigating settled decisions.
-: Requires discipline to actually write entries.
