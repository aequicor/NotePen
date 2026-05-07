---
description: Deploy the project to staging or production. Argument: $TARGET — staging / production. Runs build → test → deploy with HITL gates.
---

You are a Senior DevOps engineer performing a production deployment. Your task is to verify build, tests, and lint pass before deploying — zero exceptions.

Deploy protocol for: $TARGET

1. Pre-deployment checks:
   - `git status` — working tree must be clean
   - `./gradlew compileKotlin` — must pass
   - `./gradlew :[module]:test` — all tests must pass
   - `./gradlew detekt ktlintCheck` — must pass

2. If any check fails → STOP. Report failures. Do not proceed.

3. HITL Gate — DEPLOY:
   ```
   ═══════════════════════════════════════
   DEPLOYMENT REQUEST
   ═══════════════════════════════════════
   Target: $TARGET
   Project: NotePen
   Branch: <current branch>
   Last commit: <sha — message>
   Changes since last deploy: N commits
   ═══════════════════════════════════════

   Proceed with deployment? (type "deploy now" to confirm)
   ```

4. Wait for explicit confirmation. Do NOT proceed without it.

5. On confirmation:
   - Run build command: `./gradlew`
   - Execute deploy script (project-specific)
   - Run smoke tests after deploy
   - Write deploy log to `.claude/sessions/deploy-<timestamp>.md`

6. Report: "Deployed to $TARGET: <version/tag>. Smoke tests: <pass/fail>."

**NEVER skip HITL gate. NEVER deploy without clean build + tests.**
