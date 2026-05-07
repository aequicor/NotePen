---
name: session-replay
description: Analyze past agent sessions from SESSIONS.md to find recurring failures, anti-loop triggers, and cost anomalies. Use ONLY when @Main or @PromptEngineer needs data-driven evidence for agent prompt improvements — not for casual browsing.
---

# Session Replay Skill

Agents that loop, escalate to PO unnecessarily, or burn tokens on repetitive tasks leave a trail in SESSIONS.md. This skill reads that trail, identifies patterns, and produces actionable recommendations.

## When to Use

- @Main detects anti-loop trigger fired and wants root cause
- @PromptEngineer is refactoring prompts and needs data on what fails
- PO asks "why did the last 3 sessions cost 3x the average?"
- Before releasing a new agent version — compare with past failure patterns

**Do NOT activate for:**
- Reading a single session's output — just read the session file directly
- General curiosity — this is a diagnostic tool, not a browser
- When SESSIONS.md doesn't exist yet

## Input

| Field | Required | Description |
|-------|----------|-------------|
| `scope` | No | `recent` (last 10), `failed`, `expensive`, or agent name like `@CodeWriter`. Default: `recent` |
| `period` | No | Date range in days. Default: `30` |

## Invocation

```
skill: session-replay
scope: failed
period: 14
```

```
skill: session-replay
scope: @CodeWriter
period: 7
```

## Algorithm

### 1. Load session index

Read `.claude/sessions/SESSIONS.md` — parse the session index table.

If SESSIONS.md doesn't exist → report `NO DATA: SESSIONS.md not found. No session history available.` Stop.

### 2. Select sessions for analysis

Identify based on `scope`:
- `recent` — last 10 sessions
- `failed` — sessions with status "failed" or "blocked"
- `expensive` — top 5 by cost
- `@AgentName` — all sessions for that agent

### 3. Analyze patterns

For each selected session:
- What was the task?
- Which agent(s) were involved?
- Did any anti-loop triggers fire?
- Was HITL escalation needed?
- What was the cost?

### 4. Generate report

```markdown
# Session Analysis Report
**Date:** <today>
**Sessions analyzed:** N
**Period:** <date range>

## Summary
- Success rate: X%
- Avg cost/session: $X.XX
- Most used agent: @Agent (N sessions)
- Most expensive agent: @Agent ($X.XX avg)

## Recurring Issues
| Pattern | Occurrences | Sessions | Fix suggestion |
|----------|------------|----------|----------------|
| @CodeWriter loops on type errors | 4 | s-42, s-45, s-51, s-58 | Add explicit type annotations to stage file |

## Cost Optimization
| Agent | Avg cost | Avg steps | Suggestion |
|-------|----------|-----------|-----------|
| @CornerCaseReviewer | $0.12 | 15 | Reduce CCR iterations when BA has few ACs |

## Anti-Loop Events
| Trigger | Count | Involved agents |
|---------|-------|-----------------|
| Same task 3x | 2 | @CodeWriter |
| Empty result 2x | 1 | @QA |
```

## Error Handling

- If SESSIONS.md empty or has < 3 entries → report `INSUFFICIENT DATA: Only N sessions found. Need at least 3 for pattern analysis.`
- If requested scope has no matching sessions → report `NO MATCH: No [scope] sessions found in the last [period] days.`
- If session files referenced in SESSIONS.md are missing → skip them and report count at the end: `WARNING: N session files referenced but not found.`

## Principles

- **Read-only** — never modify SESSIONS.md or session files
- **Data-driven** — only report what the data shows, don't speculate
- **Actionable** — every finding must have a concrete recommendation
- **No blame** — report patterns, not individual failures