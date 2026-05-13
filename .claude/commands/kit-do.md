---
description: "Run Session 2 (Execute) of the AI-Kit v3 pipeline. Argument: $ARGUMENTS — the plan id (e.g. `2026-05-11-registration`), optionally followed by `--resume`."
---
# /kit-do

Run Session 2 (Execute) of the AI-Kit v3 pipeline. Argument: $ARGUMENTS — the plan id (e.g. `2026-05-11-registration`), optionally followed by `--resume`.

<project>NotePen</project>
<stack>kotlin / compose-multiplatform, android, desktop</stack>

<communication_language>
Communicate with the user in Russian (ru). All prose — questions, explanations, status updates, summaries, and reasoning addressed to the user — must be in Russian. Keep code, file paths, shell commands, identifiers, manifest keys, error codes, and other technical tokens verbatim in their original form.
</communication_language>




<workflow>
Run Session 2 (Execute) of the AI-Kit v3 pipeline. Argument: $ARGUMENTS — the plan id (e.g. `2026-05-11-registration`), optionally followed by `--resume`.

You are running Session 2 of the AI-Kit v3 pipeline.

**Args:** $ARGUMENTS

Follow the Session 2 protocol from your project instructions:

1. **Initialization.** Read `.aikit/plans/<plan-id>.md`. If not found → STOP. Find the plan-commit (`git log --grep="kit: plan for <slug>"`). Walk commits since to identify last completed step and any external `kit: fix *` commits. Set `last_known_hash = HEAD`. State out loud what you found.

2. **Stage 3 — Steps loop.** For each step from current to last:
   - Execute, then `git add -A && git commit -m "kit: step <N>/<total> — <slug>"`.
   - Output STEP SUMMARY. AWAIT.
   - On user reply: ALWAYS rehydrate first (`git log <last_known>..HEAD` and `git show` of any new commits, narrate findings out loud), then act on `next` / `revert` / pasted FIX SUMMARY block.

3. **Stage 4 — Ship** (auto-enters after the last step):
   - Run tests. If failed → STOP, AWAIT decision (`fix` / `push as-is` / `abort`).
   - List commits since `<plan-commit>~1` to the user.
   - Propose squash with a derived message. AWAIT user reply: `ok` / new message / `keep` / `cancel`.
   - On `ok`: `git reset --soft <plan-commit>~1 && git commit -m "<msg>"`. Update `last_known_hash`. AWAIT push gate.
   - On `push`: detect if branch was previously pushed (`git rev-parse --verify origin/<branch>`). If yes AND squash rewrote history → `git push --force-with-lease` AFTER stating the warning verbatim. Otherwise `git push -u origin <branch>` or plain push.
   - On `local` (or after `keep` + `local`): END without push.

Hard rules:

- ALWAYS run rehydration as your first action after every AWAIT. Never silently proceed.
- NEVER `git push --force` (only `--force-with-lease`, only after warning).
- NEVER push without an explicit `push` reply from the user.
- NEVER push if Stage 4 tests failed, unless user explicitly typed `push as-is`.
- NEVER use `--no-verify` on any commit or push.
- NEVER `git commit --amend` on a commit already in `last_known_hash`.
- NEVER modify `.aikit/plans/<plan-id>.md` — the plan is frozen at end of Session 1.

Trust git, not paste blocks. When the user pastes a `## FIX SUMMARY · commit <hash>` block, validate the hash via `git log <last_known>..HEAD` and read the actual diff via `git show <hash>` — never trust the block's "Solution:" section as fact.

If the plan id is missing or `.aikit/plans/<id>.md` doesn't exist, STOP. Output: `Plan <id> not found at .aikit/plans/<id>.md. Did Session 1 commit it? Try: git log --grep="kit: plan".`
</workflow>
