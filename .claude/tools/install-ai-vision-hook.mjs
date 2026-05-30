#!/usr/bin/env node
// Install the "ai-vision" UserPromptSubmit hook into .claude/settings.local.json (macOS / POSIX).
//
//   node .claude/tools/install-ai-vision-hook.mjs
//
// Idempotent: merges into existing local settings, preserves other keys, and won't add a duplicate.
// settings.local.json is gitignored (per-machine), so this is the right place for a machine-specific
// absolute path that survives AI-Kit regeneration of the committed settings.json.
import { readFileSync, writeFileSync, existsSync, chmodSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url)); // .../.claude/tools
const repoRoot = resolve(here, '..', '..');
const hookScript = join(repoRoot, '.claude', 'tools', 'ai-vision-hook.sh');
const settingsPath =
  process.env.AIVISION_SETTINGS_OVERRIDE || join(repoRoot, '.claude', 'settings.local.json');
const command = `bash "${hookScript}"`;

// Load existing local settings (or start fresh).
let settings = {};
if (existsSync(settingsPath)) {
  const raw = readFileSync(settingsPath, 'utf8').trim();
  if (raw) {
    try {
      settings = JSON.parse(raw);
    } catch (e) {
      console.error(`Refusing to overwrite malformed JSON at ${settingsPath}: ${e.message}`);
      process.exit(1);
    }
  }
}

settings.hooks ??= {};
settings.hooks.UserPromptSubmit ??= [];

const already = settings.hooks.UserPromptSubmit.some((group) =>
  (group?.hooks ?? []).some((h) => typeof h?.command === 'string' && h.command.includes('ai-vision-hook.sh')),
);

if (already) {
  console.log(`ai-vision hook already present in ${settingsPath} — nothing to do.`);
} else {
  settings.hooks.UserPromptSubmit.push({
    hooks: [
      {
        type: 'command',
        command,
        timeout: 15,
        statusMessage: 'Checking for ai-vision trigger',
      },
    ],
  });
  mkdirSync(dirname(settingsPath), { recursive: true });
  writeFileSync(settingsPath, JSON.stringify(settings, null, 2) + '\n', 'utf8');
  console.log(`Installed ai-vision hook -> ${settingsPath}`);
  console.log(`  command: ${command}`);
}

// Make the hook script executable (no-op effect on Windows checkouts).
try {
  chmodSync(hookScript, 0o755);
} catch {
  /* best-effort */
}

console.log('Done. Open /hooks once (or restart Claude Code) so the new hook is picked up.');
