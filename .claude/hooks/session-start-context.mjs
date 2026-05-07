#!/usr/bin/env node
/**
 * SessionStart hook — injects current branch, last commit, and active task context.
 * ai-agent-kit v5 — Claude Code hooks.
 * Fails open: any error produces an empty context rather than blocking the session.
 */

import { execSync } from 'child_process';
import { readFileSync } from 'fs';

function safeExec(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf8', timeout: 3000 }).trim();
  } catch {
    return '';
  }
}

function safeRead(path) {
  try {
    return readFileSync(path, 'utf8');
  } catch {
    return '';
  }
}

try {
  const branch = safeExec('git rev-parse --abbrev-ref HEAD');
  const lastCommit = safeExec('git log -1 --pretty=format:%s');

  const currentMd = safeRead('.planning/CURRENT.md');
  const activeTaskMatch = currentMd.match(/active_task:\s*(.+)/);
  const activeTask = activeTaskMatch ? activeTaskMatch[1].trim() : '(none)';

  let taskType = '';
  let nextStep = '';

  if (activeTask && activeTask !== '(none)') {
    const taskMd = safeRead(`.planning/tasks/${activeTask}.md`);
    const typeMatch = taskMd.match(/^##?\s*Type[:\s]+(.+)/m);
    taskType = typeMatch ? typeMatch[1].trim() : '';

    // Find the last NEXT line
    const nextMatches = taskMd.match(/- NEXT:\s*(.+)/g);
    if (nextMatches && nextMatches.length > 0) {
      const lastNext = nextMatches[nextMatches.length - 1];
      const nextMatch = lastNext.match(/- NEXT:\s*(.+)/);
      nextStep = nextMatch ? nextMatch[1].trim() : '';
    }
  }

  const context = [
    branch ? `branch=${branch}` : '',
    lastCommit ? `last_commit=${lastCommit}` : '',
    `active_task=${activeTask}`,
    taskType ? `type=${taskType}` : '',
    nextStep ? `next: ${nextStep}` : '',
  ].filter(Boolean).join(' | ');

  console.log(JSON.stringify({
    type: 'SessionStart',
    additionalContext: context,
  }));
} catch {
  // Fail open — empty context
  console.log(JSON.stringify({ type: 'SessionStart', additionalContext: '' }));
}
