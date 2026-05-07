#!/usr/bin/env node
/**
 * PreToolUse hook for Bash — enforces destructive-action gates.
 * ai-agent-kit v5 — Claude Code hooks.
 *
 * Hard denies:
 *   1. git push --force to main/master/prod
 *   2. writes to credential files (.env, .ssh, .aws, credentials.json)
 *
 * Soft check (asks):
 *   3. commit staging src/ files without test-cases update
 *
 * Fails open on any error (allows by default).
 */

import { readFileSync } from 'fs';
import { execSync } from 'child_process';

function safeExec(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf8', timeout: 3000 }).trim();
  } catch {
    return '';
  }
}

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => { input += chunk; });
process.stdin.on('end', () => {
  try {
    const data = JSON.parse(input);
    if (data.tool_name !== 'Bash') {
      // Allow all non-Bash tools
      console.log(JSON.stringify({ decision: 'allow' }));
      process.exit(0);
    }

    const cmd = (data.tool_input && data.tool_input.command) || '';

    // Hard deny: force push to protected branches
    if (/git push.*(--force|-f)/.test(cmd)) {
      const branchMatch = cmd.match(/origin\s+(\S+)/);
      const target = branchMatch ? branchMatch[1] : '';
      if (!target || /^(main|master|prod|production)$/.test(target)) {
        console.log(JSON.stringify({
          decision: 'deny',
          reason: 'git push --force to main/master/prod is permanently denied. Use a feature branch and a regular push.',
        }));
        process.exit(0);
      }
    }

    // Hard deny: writes to credential files
    const credentialPatterns = [
      /\.env(\b|$|\.)/,
      /\/(\.ssh|\.aws)\//,
      /credentials\.json/,
    ];
    if (credentialPatterns.some(p => p.test(cmd))) {
      if (/\b(echo|cat|tee|write|>>|>)\b/.test(cmd)) {
        console.log(JSON.stringify({
          decision: 'deny',
          reason: 'Writing to credential files (.env, .ssh, .aws, credentials.json) is blocked.',
        }));
        process.exit(0);
      }
    }

    // Soft check: commit with src/ changes but no test-cases update
    if (/git commit/.test(cmd)) {
      const staged = safeExec('git diff --cached --name-only');
      if (staged) {
        const hasSrcChange = staged.split('\n').some(f => f.startsWith('src/') || f.includes('/src/'));
        const hasTestCasesChange = staged.split('\n').some(f => f.includes('test-cases.md'));
        if (hasSrcChange && !hasTestCasesChange) {
          console.log(JSON.stringify({
            decision: 'ask',
            reason: 'Source files in src/ are staged but test-cases.md has no updates. Confirm commit is intentional?',
          }));
          process.exit(0);
        }
      }
    }

    // Default: allow
    console.log(JSON.stringify({ decision: 'allow' }));
  } catch {
    // Fail open
    console.log(JSON.stringify({ decision: 'allow' }));
  }
  process.exit(0);
});

setTimeout(() => {
  console.log(JSON.stringify({ decision: 'allow' }));
  process.exit(0);
}, 3000);
