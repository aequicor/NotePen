---
description: "Start Session 1 (Plan) of the AI-Kit v3 pipeline. Argument: $ARGUMENTS — the user's task description."
---
# /kit

Start Session 1 (Plan) of the AI-Kit v3 pipeline. Argument: $ARGUMENTS — the user's task description.

<project>NotePen</project>
<stack>kotlin / kotlin-multiplatform, compose-multiplatform</stack>

<communication_language>
Communicate with the user in Russian (ru). All prose — questions, explanations, status updates, summaries, and reasoning addressed to the user — must be in Russian. Keep code, file paths, shell commands, identifiers, manifest keys, error codes, and other technical tokens verbatim in their original form.
</communication_language>




<workflow>
Start Session 1 (Plan) of the AI-Kit v3 pipeline. Argument: $ARGUMENTS — the user's task description.

You are running Session 1 of the AI-Kit v3 pipeline.

**Task:** $ARGUMENTS

Follow the Session 1 protocol from your project instructions:

1. **Stage 1 — Context.** Identify what needs to be understood.
   Dispatch a Researcher subagent for heavy reads with a focused brief.
   Output a CONTEXT SUMMARY. AWAIT user reply.

2. **Stage 2 — Plan.** Compose 3–10 MVP-style steps (each runnable, independently committable, bounded). Write `.aikit/plans/<id>.md` (id = `<YYYY-MM-DD>-<slug>`). Commit with `kit: plan for <slug>`. Output PLAN SUMMARY pointing the user at `/kit-do <id>`. END the session.

Hard rules:

- Do NOT start executing any step in this session. Session 1 ends after PLAN SUMMARY.
- Do NOT modify the plan file once it's committed. If a refinement is needed before END, revise BEFORE the commit.
- Use the CONTEXT and PLAN SUMMARY formats exactly. No narrative substitutes.
- Do NOT push, do NOT run tests in Session 1.

If the task description is empty or only whitespace, ask: `What task should this kit plan cover? Provide a one-sentence description and any constraints.` Wait for the user before scanning anything.
</workflow>
