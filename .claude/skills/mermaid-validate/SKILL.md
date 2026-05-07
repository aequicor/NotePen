---
name: mermaid-validate
description: Validate that all Mermaid diagram blocks in a diagrams file render correctly. Uses mmdc (via npx) when npm is available; falls back to AI syntax self-check otherwise. Called by @SystemAnalyst after writing a diagrams file.
---

# Mermaid Validate Skill

Input (already in context when this skill is invoked):
- `<diagrams-file>` — absolute path to the `.md` file containing the Mermaid blocks just written.

---

## Step V1 — Detect npm

Run:
```bash
npm --version 2>&1
```

- Exit-code = 0 → **Variant A (mmdc render check)**
- Exit-code ≠ 0 → **Variant B (AI syntax self-check)**

---

## Variant A — mmdc render check

Run once against the whole diagrams file:
```bash
npx --yes @mermaid-js/mermaid-cli mmdc \
  -i <diagrams-file> \
  -o /tmp/kit_mermaid_validate.svg 2>&1
```

**Exit-code = 0** — all diagrams valid. Record in result block:
```
**Validation:** all diagrams rendered OK (mmdc)
```

**Exit-code ≠ 0:**
1. Read stderr to identify the failing block (error usually includes diagram type or line number).
2. Fix the offending block in the file:
   - Spaces in node IDs → replace with `snake_case` or `PascalCase`
   - Wrong arrow type for diagram type → correct the syntax
   - Undeclared participants / states → declare them before first use
3. Re-run mmdc once.
   - Still failing → remove that diagram block entirely. Append to `## Notes` in the file (create the section if absent):
     `⚠ [Diagram block title] removed — failed to render: <brief error>`
4. Record in result block:
   - All pass after fix: `**Validation:** N/M diagrams rendered OK after 1 fix (mmdc)`
   - One removed: `**Validation:** N/M diagrams rendered OK; 1 removed — see ## Notes (mmdc)`

---

## Variant B — AI syntax self-check

Re-read every ` ```mermaid ``` ` block in the file. For each block, verify and fix in-place:

| Rule | What to check | Fix |
|------|---------------|-----|
| Node IDs | No spaces allowed | Replace spaces with `_` |
| `classDiagram` | Relationships only: `<\|--` `*--` `o--` `-->` `--` `..` `..>` | Correct invalid arrows |
| `sequenceDiagram` | All participants used in messages must be declared at the top; no unclosed `loop`/`alt`/`opt` | Add missing declarations; close open blocks |
| `stateDiagram-v2` | Every state in transitions (`A --> B`) must be declared or be `[*]` | Add missing state declarations |
| `flowchart` | Every node ID in an edge (`A --> B`) must appear in at least one node definition | Add missing node definitions |
| Fenced block tag | Must be exactly ` ```mermaid ` with no extra characters | Correct tag |

Record in result block:
```
**Validation:** AI syntax-check applied (npm unavailable — open in a Mermaid viewer to verify rendering)
```
