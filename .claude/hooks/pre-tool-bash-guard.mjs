#!/usr/bin/env node
// ai-agent-kit — Claude Code hook: PreToolUse for Bash
// Three checks the permission-table cannot enforce alone:
//   1. Hard-deny `git push --force` to main/master regardless of pattern allow-list.
//   2. Pre-commit sync: if src/ is staged without test-cases.md update — ask PO
//      (in interactive mode) or allow with stderr note (in sleep mode — sleep is
//      autonomous; PO chose this trade-off).
//   3. v6.1+: --no-verify and amend-of-published commits — hard deny.
// Cross-platform: Node.js + git only. Fail-soft (default allow on error).

import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

function readStdin() {
  return new Promise((resolve) => {
    let data = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (chunk) => { data += chunk; });
    process.stdin.on("end", () => resolve(data));
    process.stdin.resume();
  });
}

function safeExec(cmd) {
  try {
    return execSync(cmd, {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
      timeout: 3000
    });
  } catch {
    return "";
  }
}

function emit(decision, reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: decision,
      permissionDecisionReason: reason
    }
  }));
}

let raw = "";
try { raw = await readStdin(); } catch { process.exit(0); }

let input;
try { input = JSON.parse(raw || "{}"); } catch { process.exit(0); }

if (input.tool_name !== "Bash") process.exit(0);

const cmd = (input.tool_input?.command || "").trim();
if (!cmd) process.exit(0);

// ── Hard deny: git push --force to main/master ─────────────────────────
if (/git\s+push\s+(--force(?:-with-lease)?|-f|-fwl)\b/.test(cmd) && /\b(main|master|prod|production)\b/.test(cmd)) {
  emit("deny", "git push --force to main/master/prod is permanently denied. Use a feature branch and a regular push.");
  process.exit(0);
}

// ── Hard deny: writing to credential files ─────────────────────────────
if (/(>|tee|cp|mv)\s+[^|]*?(\.env(?:\.\w+)?|~?\/?\.ssh\/|~?\/?\.aws\/|credentials\.json)/i.test(cmd)) {
  emit("deny", "Writing to credential files is forbidden. Set the value via the appropriate manifest field or your shell environment.");
  process.exit(0);
}

// ── Hard deny: --no-verify on git commit/push (v6.1+) ──────────────────
if (/^git\s+(commit|push|merge|rebase)\b/.test(cmd) && /--no-verify\b/.test(cmd)) {
  emit("deny", "--no-verify is forbidden in ai-agent-kit. If a hook fails, fix the underlying issue or escalate to PO. Sleep mode also forbids this — the BLOCKED-shutdown procedure is the correct response.");
  process.exit(0);
}

// ── Hard deny: amending a commit that's already pushed ─────────────────
if (/^git\s+commit\b.*--amend\b/.test(cmd)) {
  emit("deny", "git commit --amend is forbidden in ai-agent-kit pipelines. v6.1 per-step commits are append-only history; corrections go through /kit-defect (re-open step) or /kit-revert-step (drop step). Amending a step commit silently breaks step_commits[] integrity.");
  process.exit(0);
}

// ── Sleep-mode awareness: read .planning/CURRENT.md for mode ───────────
let sleepMode = false;
try {
  const cm = fs.readFileSync(path.join(process.cwd(), ".planning", "CURRENT.md"), "utf8");
  sleepMode = /^mode:\s*sleep\b/m.test(cm);
} catch { /* ignore */ }

// ── Pre-commit: source-vs-tests sync check ─────────────────────────────
if (/^git\s+commit\b/.test(cmd)) {
  const staged = safeExec("git diff --cached --name-only").split(/\r?\n/).filter(Boolean);
  if (staged.length === 0) process.exit(0);

  const isSrc = (p) => /(^|\/)src\//i.test(p) && !/\/test\//i.test(p) && !/Test\.[a-z]+$/i.test(p);
  const isTestCases = (p) => /vault\/features\/[^/]+\/[^/]+\/test-cases\.md$/i.test(p);
  // v6 — accept spec.md or plan.md updates as a sync signal (replaces v5's feature.md)
  const isFeatureDoc = (p) => /vault\/features\/[^/]+\/[^/]+\/(spec|plan|feature)\.md$/i.test(p);
  const isTestFile = (p) => /\/test\//i.test(p) || /Test\.[a-z]+$/i.test(p) || /\.test\.[a-z]+$/i.test(p);

  const srcFiles = staged.filter(isSrc);
  if (srcFiles.length > 0) {
    const hasTestCases = staged.some(isTestCases);
    const hasFeatureDoc = staged.some(isFeatureDoc);
    const hasTests = staged.some(isTestFile);

    if (!hasTestCases && !hasFeatureDoc && !hasTests) {
      if (sleepMode) {
        // v6.1+: sleep mode is autonomous; PO chose this trade-off. Allow with stderr note.
        process.stderr.write(`⚠️  pre-commit sync: src staged without tests in sleep mode (${srcFiles.slice(0, 3).join(", ")}). Logged for MORNING_REPORT.\n`);
        // Fall through to allow (no emit).
      } else {
        emit(
          "ask",
          `Source files staged (${srcFiles.slice(0, 3).join(", ")}${srcFiles.length > 3 ? ", …" : ""}) without any test, test-cases.md, or plan.md update. Likely missing @Verifier MODE=RECONCILE or regression test. Proceed anyway?`
        );
        process.exit(0);
      }
    }
  }
}

// Default: allow (no JSON output → permission table decides)
process.exit(0);
