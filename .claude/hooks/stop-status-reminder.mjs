#!/usr/bin/env node
/**
 * Stop hook — reminds the user of the active task when a session ends.
 * ai-agent-kit v5 — Claude Code hooks.
 * Non-blocking: never forces the agent to keep working.
 * Fails open on any error.
 */

import { readFileSync } from 'fs';

function safeRead(path) {
  try {
    return readFileSync(path, 'utf8');
  } catch {
    return '';
  }
}

try {
  const currentMd = safeRead('.planning/CURRENT.md');
  const activeTaskMatch = currentMd.match(/active_task:\s*(.+)/);
  const activeTask = activeTaskMatch ? activeTaskMatch[1].trim() : '';

  if (!activeTask || activeTask === '(none)') {
    process.exit(0);
  }

  // Check if task is blocked
  const taskMd = safeRead(`.planning/tasks/${activeTask}.md`);
  if (taskMd.includes('BLOCKED:') || taskMd.includes('ЗАБЛОКИРОВАНО:')) {
    process.exit(0);
  }

  process.stderr.write(
    `\n[kit] Active task: ${activeTask}\n` +
    `      Run /kit-status to see progress or /kit-resume to continue.\n\n`
  );
} catch {
  // Fail open — no output
}

process.exit(0);
