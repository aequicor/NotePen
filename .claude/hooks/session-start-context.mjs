#!/usr/bin/env node
// ai-agent-kit — Claude Code hook: SessionStart
// v6.1+: emits additionalContext with branch + active task + pending step,
// AND prints a stderr reminder for the PO-visible channel (per
// manifest.session_isolation.prompt_style: silent | stderr | both).
// Also writes .planning/.session-bootstrap.md as a per-session briefing
// file so OpenCode (via session-bootstrap.ts plugin) and any non-CC tooling
// has a parallel artifact.
// Cross-platform: Node.js only, no shell-specific commands.
// Fail-soft: any error → empty context, never blocks the session.

import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";

function safeRead(p) {
  try { return fs.readFileSync(p, "utf8"); } catch { return ""; }
}

function safeWrite(p, content) {
  try {
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.writeFileSync(p, content, "utf8");
    return true;
  } catch { return false; }
}

function safeExec(cmd) {
  try {
    return execSync(cmd, {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
      timeout: 2000
    }).trim();
  } catch {
    return "";
  }
}

// Parse a single field from CURRENT.md or task file (key: value).
// Tolerant: ignores lines starting with # and trims whitespace.
function parseField(text, key) {
  const re = new RegExp(`^${key}:\\s*([^\\n\\r#]+?)\\s*(?:#.*)?$`, "m");
  const m = text.match(re);
  return m ? m[1].trim() : "";
}

// Parse step_commits[] from task file. Returns array of {step, sha, goal, superseded}.
// Line-by-line parser (avoids relying on \Z which is not supported in JS regex).
function parseStepCommits(taskBody) {
  const entries = [];
  let cur = null;
  for (const line of taskBody.split(/\r?\n/)) {
    const stepM = line.match(/^-\s*step:\s*(\d+)/);
    if (stepM) {
      if (cur) entries.push(cur);
      cur = { step: parseInt(stepM[1], 10), sha: "", goal: "", superseded: false };
      continue;
    }
    if (!cur) continue;
    const shaM = line.match(/^\s+sha:\s*([^\s]+)/);
    if (shaM) cur.sha = shaM[1];
    const goalM = line.match(/^\s+goal:\s*(.+)/);
    if (goalM) cur.goal = goalM[1].trim();
    if (/^\s+superseded:\s*true\s*$/.test(line)) cur.superseded = true;
  }
  if (cur) entries.push(cur);
  return entries;
}

const cwd = process.cwd();
const branch = safeExec("git rev-parse --abbrev-ref HEAD") || "(no git)";
const lastCommit = safeExec("git log -1 --oneline --no-color") || "(no commits)";

const currentMdPath = path.join(cwd, ".planning", "CURRENT.md");
const currentMd = safeRead(currentMdPath);
const activeTask = parseField(currentMd, "active_task") || "(none)";
const mode = parseField(currentMd, "mode") || "interactive";
const taskStatus = parseField(currentMd, "status") || "";
const awaitingPo = (parseField(currentMd, "awaiting_po") || "false") === "true";

// session_isolation.prompt_style — read from manifest if available; default "both".
// We don't have a manifest path constant here, so try common locations.
let promptStyle = "both";
const manifestCandidates = [
  path.join(cwd, "manifest.yaml"),
  path.join(cwd, "manifest.yml"),
  path.join(cwd, ".kit-manifest.yaml")
];
for (const mp of manifestCandidates) {
  const m = safeRead(mp);
  if (m) {
    const styleM = m.match(/prompt_style:\s*(silent|stderr|both)/);
    if (styleM) promptStyle = styleM[1];
    break;
  }
}

let taskLine = "";
let pendingStepLine = "";
let bootstrapBody = "";

if (activeTask !== "(none)" && !activeTask.startsWith("(")) {
  const taskFilePath = path.join(cwd, ".planning", "tasks", `${activeTask}.md`);
  const body = safeRead(taskFilePath);
  const typeM = body.match(/^\*\*Type:\*\*\s*(.*)$/m);
  const lastCheckpoint = (body.match(/^([0-9]{4}-[0-9]{2}-[0-9]{2}T[^\s]+)\s+—\s+(.*)$/m) || [])[2]?.trim() || "";
  taskLine = ` | type=${typeM?.[1]?.trim() || "?"} | last_checkpoint: ${lastCheckpoint.slice(0, 100) || "(none)"}`;

  // v6.1: detect pending step from current_step_idx + step_commits[].
  const currentStepIdx = parseInt(parseField(body, "current_step_idx") || "0", 10);
  const commits = parseStepCommits(body);
  const lastEntry = commits.length ? commits[commits.length - 1] : null;

  // Pending step heuristic:
  // - if last entry is superseded → step is being re-executed (defect path) → pending = currentStepIdx (same step)
  // - else → pending = currentStepIdx + 1 (next step)
  const pending = (lastEntry && lastEntry.superseded) ? currentStepIdx : currentStepIdx + 1;

  if (taskStatus === "SLEEP_BLOCKED") {
    pendingStepLine = ` | sleep BLOCKED — read MORNING_REPORT.md`;
  } else if (currentStepIdx === 0 && commits.length === 0) {
    pendingStepLine = ` | EXECUTE not started yet`;
  } else {
    pendingStepLine = ` | pending step ${pending} | last_green sha=${lastEntry?.sha?.slice(0, 8) || "?"}`;
  }

  // Build bootstrap body (used by OpenCode and as a side-channel).
  const lines = [
    `# Session bootstrap — written by SessionStart hook`,
    `# Generated: ${new Date().toISOString()}`,
    ``,
    `active_task: ${activeTask}`,
    `mode: ${mode}`,
    `status: ${taskStatus || "(active)"}`,
    `current_step_idx: ${currentStepIdx}`,
    `pending_step: ${pending}`,
    `last_green_sha: ${lastEntry?.sha || "(none)"}`,
    ``,
    `## Pending action`,
    taskStatus === "SLEEP_BLOCKED"
      ? `Read .planning/MORNING_REPORT.md (sleep mode hit BLOCKED-shutdown). Then /kit-resume to continue.`
      : (currentStepIdx === 0
        ? `Run /kit-resume to continue analysis/plan/execute from current state.`
        : `Run /kit-step-resume to enter step ${pending}.`),
    ``,
    `## Last step runbook (for regression check at next 5.6)`,
    lastEntry?.goal
      ? `Step ${lastEntry.step} — ${lastEntry.goal} — sha ${lastEntry.sha?.slice(0, 8) || "?"}${lastEntry.superseded ? " (superseded)" : ""}`
      : `(no completed steps yet)`,
    ``,
    `> This file is regenerated on every session start. Safe to ignore if you've already loaded context.`
  ];
  bootstrapBody = lines.join("\n");
} else {
  bootstrapBody = `# No active task — run /kit-new-feature to start.\n# Generated: ${new Date().toISOString()}\n`;
}

// Always write the bootstrap file (also for "no active task" case so the file's mtime reflects this session).
safeWrite(path.join(cwd, ".planning", ".session-bootstrap.md"), bootstrapBody);

const ctx = [
  "[ai-agent-kit session boot]",
  `branch=${branch}`,
  `last_commit=${lastCommit.slice(0, 100)}`,
  `active_task=${activeTask}${taskLine}${pendingStepLine}`,
  `mode=${mode}${awaitingPo ? " awaiting_po=true" : ""}`
].join(" | ");

// Channel A: additionalContext (silent injection).
const additionalContext = (promptStyle === "stderr") ? "" : ctx;

process.stdout.write(JSON.stringify({
  hookSpecificOutput: {
    hookEventName: "SessionStart",
    additionalContext
  }
}));

// Channel B: stderr reminder (visible to PO before first input).
if ((promptStyle === "stderr" || promptStyle === "both") && pendingStepLine) {
  if (taskStatus === "SLEEP_BLOCKED") {
    process.stderr.write(`🌙 sleep BLOCKED on task ${activeTask} — read .planning/MORNING_REPORT.md, then /kit-resume\n`);
  } else if (mode === "sleep") {
    process.stderr.write(`🌙 sleep mode active for task ${activeTask} — pipeline runs autonomously; do not interrupt unless needed\n`);
  } else {
    // Strip leading " | " from pendingStepLine for clean stderr output.
    const cleaned = pendingStepLine.replace(/^\s*\|\s*/, "").trim();
    process.stderr.write(`🔄 ${cleaned} — run /kit-step-resume to continue\n`);
  }
}

process.exit(0);
