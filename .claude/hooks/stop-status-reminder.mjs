#!/usr/bin/env node
// ai-agent-kit — Claude Code hook: Stop
// On session stop, if an active task remains, prints a one-line reminder to stderr.
// Non-blocking: never forces the agent to keep working.
// Cross-platform: Node.js only.

import fs from "node:fs";
import path from "node:path";

function safeRead(p) {
  try { return fs.readFileSync(p, "utf8"); } catch { return ""; }
}

const cwd = process.cwd();
const currentMd = safeRead(path.join(cwd, ".planning", "CURRENT.md"));
const taskMatch = currentMd.match(/active_task:\s*([^\n\r]+)/);
const activeTask = taskMatch ? taskMatch[1].trim() : "";

if (!activeTask || activeTask === "(none)" || activeTask.startsWith("(")) {
  process.exit(0);
}

const taskFile = path.join(cwd, ".planning", "tasks", `${activeTask}.md`);
const body = safeRead(taskFile);
const blocked = /^- BLOCKED:/m.test(body);
const flag = blocked ? " [BLOCKED]" : "";

process.stderr.write(
  `[ai-agent-kit] Session stopped with active_task=${activeTask}${flag}. Run /kit-status to inspect or /kit-resume to continue.\n`
);
process.exit(0);
