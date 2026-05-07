# Agent Language — English

**Active locale: `en`**

All agent output, status labels, task descriptions, and user-facing messages must be in **English**.

## Action Labels

| Key | Use this text |
|-----|---------------|
| `classify_and_clarify` | Classify and clarify the task |
| `analysis` | Analysis |
| `write_code` | Write code |
| `review_code` | Review code |
| `verify_tests` | Verify tests |
| `fix_bug` | Fix bug |
| `design_ui` | Design UI |
| `traceability_check` | Traceability check |
| `dod_gate` | Definition of Done — gate |
| `checkpoint` | Checkpoint |
| `resume_work` | Resume work |
| `new_feature` | New feature |
| `lint` | Lint |
| `fix` | Fix |
| `review` | Review |
| `tech_debt` | Tech debt |
| `record_tech_debt` | Record tech debt |

## Domain Terms

| Term | Use this text |
|------|---------------|
| module | module |
| source root | source root |
| test root | test root |
| docs | docs |
| feature doc | feature doc |
| acceptance criterion | acceptance criterion |
| edge case | edge case |
| test case | test case |
| step | step |
| plan | plan |
| retro | retro |

## Status Values

Use these exact strings in `.planning/tasks/*.md` checkpoints and test-cases files:

| Status | Text |
|--------|------|
| DONE | `DONE` |
| NEXT | `NEXT` |
| BLOCKED | `BLOCKED` |
| TODO | `TODO` |
| Approved | `Approved` |
| Proposed | `Proposed` |
| In Progress | `In Progress` |

## Commands

Command names are always in English regardless of locale — use the slash-command as-is:
`/kit-new-feature`, `/kit-fix`, `/kit-techdebt`, `/kit-resume`, `/kit-status`, `/kit-approve`, `/kit-review`, `/kit-lint`, `/kit-config`, `/kit-extend`, `/kit-update`, `/kit-uninstall`
