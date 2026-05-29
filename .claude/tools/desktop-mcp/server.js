#!/usr/bin/env node
/**
 * notepen-desktop-mcp
 *
 * A self-contained macOS desktop-GUI automation MCP server for driving the
 * NotePen Compose Desktop app during UX testing. It shells out only to tools
 * that ship with macOS:
 *   - `screencapture`  (screenshots)          -> needs Screen Recording permission
 *   - `sips`           (downscale preview)
 *   - `osascript`      (JXA/CoreGraphics mouse, System Events keys, window list)
 *                                              -> needs Accessibility permission
 *
 * No Homebrew, no Swift, no AI provider key, no third-party native binaries.
 *
 * Coordinate model: the screenshot tool downsizes the captured image to the
 * display's *logical* size (points), so a pixel position you read off the
 * returned preview equals the (x, y) you pass to click/move/drag — origin is
 * top-left of the main display, units are points.
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { execFile } from "node:child_process";
import { readFile, mkdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

function sh(cmd, args, { input } = {}) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      cmd,
      args,
      { maxBuffer: 64 * 1024 * 1024 },
      (err, stdout, stderr) => {
        if (err) {
          err.stdout = stdout;
          err.stderr = stderr;
          return reject(err);
        }
        resolve({ stdout, stderr });
      },
    );
    if (input != null) {
      child.stdin.write(input);
      child.stdin.end();
    }
  });
}

const osa = (script, lang = "AppleScript") =>
  sh("osascript", lang === "JavaScript" ? ["-l", "JavaScript"] : [], {
    input: script,
  });

/** Logical (points) size of the main display, origin top-left. */
async function logicalScreenSize() {
  try {
    const { stdout } = await osa(
      'tell application "Finder" to get bounds of window of desktop',
    );
    // -> "0, 0, 1512, 982"
    const n = stdout.trim().split(",").map((s) => parseInt(s.trim(), 10));
    if (n.length === 4 && n[2] > 0 && n[3] > 0) {
      return { width: n[2], height: n[3] };
    }
  } catch {
    /* fall through */
  }
  return null;
}

// ---- mouse via JXA + CoreGraphics --------------------------------------
function mouseScript({ kind, x, y, x2, y2, button = "left", clickCount = 1 }) {
  return `
ObjC.import('CoreGraphics');
ObjC.import('stdlib');
var TAP=0; // kCGHIDEventTap
var MOVED=5, L_DOWN=1, L_UP=2, R_DOWN=3, R_UP=4, L_DRAG=6;
var BTN = ${button === "right" ? 1 : 0};
function ev(type, px, py){
  var p=$.CGPointMake(px,py);
  var e=$.CGEventCreateMouseEvent($(), type, p, BTN);
  return e;
}
function post(e){ $.CGEventPost(TAP, e); }
function sleep(ms){ var end=$.NSDate.dateWithTimeIntervalSinceNow(ms/1000.0); $.NSRunLoop.currentRunLoop.runUntilDate(end); }
var kind=${JSON.stringify(kind)};
if (kind==='move'){
  post(ev(MOVED, ${x}, ${y}));
} else if (kind==='click'){
  post(ev(MOVED, ${x}, ${y})); sleep(30);
  var down=${button === "right" ? "R_DOWN" : "L_DOWN"}, up=${button === "right" ? "R_UP" : "L_UP"};
  for (var i=1;i<=${clickCount};i++){
    var d=ev(down, ${x}, ${y}); $.CGEventSetIntegerValueField(d,1,i); post(d); sleep(20);
    var u=ev(up, ${x}, ${y}); $.CGEventSetIntegerValueField(u,1,i); post(u); sleep(20);
  }
} else if (kind==='drag'){
  post(ev(MOVED, ${x}, ${y})); sleep(30);
  var d=ev(L_DOWN, ${x}, ${y}); $.CGEventSetIntegerValueField(d,1,1); post(d); sleep(40);
  var steps=24;
  for (var s=1;s<=steps;s++){
    var cx=${x}+(${x2}-${x})*s/steps, cy=${y}+(${y2}-${y})*s/steps;
    post(ev(L_DRAG, cx, cy)); sleep(12);
  }
  var u=ev(L_UP, ${x2}, ${y2}); $.CGEventSetIntegerValueField(u,1,1); post(u);
}
"ok";
`;
}

// ---- keyboard via System Events ----------------------------------------
const KEYCODES = {
  return: 36, enter: 76, tab: 48, space: 49, delete: 51, forwarddelete: 117,
  escape: 53, esc: 53, left: 123, right: 124, down: 125, up: 126,
  home: 115, end: 119, pageup: 116, pagedown: 121,
  f1: 122, f2: 120, f3: 99, f4: 118, f5: 96, f6: 97, f7: 98, f8: 100,
  f9: 101, f10: 109, f11: 103, f12: 111,
};
function modifierClause(mods) {
  if (!mods || !mods.length) return "";
  const map = { command: "command down", cmd: "command down", control: "control down", ctrl: "control down", option: "option down", alt: "option down", shift: "shift down" };
  const parts = mods.map((m) => map[m.toLowerCase()]).filter(Boolean);
  return parts.length ? ` using {${parts.join(", ")}}` : "";
}
function pressKeyScript(key, mods) {
  const m = modifierClause(mods);
  const lower = String(key).toLowerCase();
  if (lower in KEYCODES) {
    return `tell application "System Events" to key code ${KEYCODES[lower]}${m}`;
  }
  if (String(key).length === 1) {
    const ch = String(key).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
    return `tell application "System Events" to keystroke "${ch}"${m}`;
  }
  throw new Error(`Unknown key: ${key} (use a single char or one of: ${Object.keys(KEYCODES).join(", ")})`);
}
function typeTextScript(text) {
  // Split on newlines: type segments, press Return between them.
  const segs = String(text).split("\n");
  const lines = [`tell application "System Events"`];
  segs.forEach((seg, i) => {
    if (seg.length) {
      const esc = seg.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
      lines.push(`  keystroke "${esc}"`);
    }
    if (i < segs.length - 1) lines.push(`  key code 36`);
  });
  lines.push(`end tell`);
  return lines.join("\n");
}

// ---- tool definitions ---------------------------------------------------
const TOOLS = [
  {
    name: "screenshot",
    description:
      "Capture the macOS screen and return it as an image. The image is downsized to the display's logical size, so pixel positions in it map 1:1 to click/move coordinates (origin top-left, units = points). Optionally save the full-resolution PNG to `path`. Requires Screen Recording permission for the host terminal app.",
    inputSchema: {
      type: "object",
      properties: {
        path: { type: "string", description: "Optional absolute path to also save the full-resolution PNG (e.g. a .claude/ux-reports/.../shot.png file)." },
        region: {
          type: "object",
          description: "Optional region in points {x,y,w,h}. Omit for full screen.",
          properties: { x: { type: "number" }, y: { type: "number" }, w: { type: "number" }, h: { type: "number" } },
          required: ["x", "y", "w", "h"],
        },
        maxPreview: { type: "number", description: "Max longest-side px for the returned inline preview. Default = logical screen size (1:1 coords). Lower it to save tokens.", },
      },
    },
  },
  {
    name: "click",
    description: "Click at logical screen coordinates (points, top-left origin). Use coordinates read from a `screenshot` preview.",
    inputSchema: {
      type: "object",
      properties: {
        x: { type: "number" }, y: { type: "number" },
        button: { type: "string", enum: ["left", "right"], description: "Default left." },
        count: { type: "number", description: "Click count (2 = double click). Default 1." },
      },
      required: ["x", "y"],
    },
  },
  {
    name: "double_click",
    description: "Double-click at logical screen coordinates (points).",
    inputSchema: { type: "object", properties: { x: { type: "number" }, y: { type: "number" } }, required: ["x", "y"] },
  },
  {
    name: "move",
    description: "Move the mouse pointer to logical screen coordinates (points) without clicking.",
    inputSchema: { type: "object", properties: { x: { type: "number" }, y: { type: "number" } }, required: ["x", "y"] },
  },
  {
    name: "drag",
    description: "Press at (x,y), drag to (x2,y2), release. Coordinates in points. Useful for drawing strokes or swiping pages.",
    inputSchema: { type: "object", properties: { x: { type: "number" }, y: { type: "number" }, x2: { type: "number" }, y2: { type: "number" } }, required: ["x", "y", "x2", "y2"] },
  },
  {
    name: "type_text",
    description: "Type a literal UTF-8 string into the focused control. Newlines become Return presses. Requires Accessibility permission.",
    inputSchema: { type: "object", properties: { text: { type: "string" } }, required: ["text"] },
  },
  {
    name: "press_key",
    description: 'Press a single key, optionally with modifiers. key = one char (e.g. "a") or a named key (return, tab, escape, left, right, up, down, pageup, pagedown, home, end, space, delete, f1-f12). modifiers = subset of [command, control, option, shift].',
    inputSchema: { type: "object", properties: { key: { type: "string" }, modifiers: { type: "array", items: { type: "string" } } }, required: ["key"] },
  },
  {
    name: "activate_app",
    description: 'Bring an app to the foreground by name, e.g. "NotePen" (release .app bundle) or "java" (gradle runDesktop JVM).',
    inputSchema: { type: "object", properties: { name: { type: "string" } }, required: ["name"] },
  },
  {
    name: "list_windows",
    description: "List on-screen windows (title, position, size in points) for a process name. Requires Accessibility permission. Use to find click targets and the app window bounds.",
    inputSchema: { type: "object", properties: { name: { type: "string", description: 'Process name, e.g. "NotePen" or "java".' } }, required: ["name"] },
  },
];

const text = (s) => ({ content: [{ type: "text", text: s }] });
const errText = (s) => ({ content: [{ type: "text", text: s }], isError: true });

async function handleScreenshot(args = {}) {
  await mkdir(join(tmpdir(), "notepen-mcp"), { recursive: true });
  const full = args.path || join(tmpdir(), "notepen-mcp", `shot-${process.pid}.png`);
  const capArgs = ["-x", "-t", "png"];
  if (args.region) {
    const r = args.region;
    capArgs.push("-R", `${r.x},${r.y},${r.w},${r.h}`);
  }
  capArgs.push(full);
  try {
    await sh("screencapture", capArgs);
  } catch (e) {
    return errText(
      `screencapture failed (${e.code}). Most likely the terminal app running Claude lacks Screen Recording permission. Grant it in System Settings ▸ Privacy & Security ▸ Screen Recording, then restart the terminal.\n${e.stderr || e.message}`,
    );
  }
  // Build a coordinate-aligned preview.
  const logical = args.region
    ? { width: Math.round(args.region.w), height: Math.round(args.region.h) }
    : await logicalScreenSize();
  const preview = join(tmpdir(), "notepen-mcp", `preview-${process.pid}.png`);
  let note = "";
  try {
    if (args.maxPreview) {
      await sh("sips", ["-Z", String(Math.round(args.maxPreview)), full, "--out", preview]);
      note = `Preview downscaled to max ${args.maxPreview}px; scale coordinates accordingly.`;
    } else if (logical) {
      await sh("sips", ["-z", String(logical.height), String(logical.width), full, "--out", preview]);
      note = `Preview is ${logical.width}x${logical.height} pt — pixel positions map 1:1 to click coordinates.`;
    } else {
      await sh("sips", ["-Z", "1600", full, "--out", preview]);
      note = "Could not read logical screen size; preview capped at 1600px.";
    }
  } catch {
    // sips failed: just return the raw capture.
    note = "sips downscale failed; returning raw capture (coords may be 2x retina pixels).";
  }
  const previewPath = note.startsWith("sips") ? full : preview;
  const data = await readFile(previewPath);
  const parts = [];
  parts.push({ type: "text", text: `${note}${args.path ? `\nFull-res saved: ${full}` : ""}` });
  parts.push({ type: "image", data: data.toString("base64"), mimeType: "image/png" });
  return { content: parts };
}

const server = new Server(
  { name: "notepen-desktop", version: "1.0.0" },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: TOOLS }));

server.setRequestHandler(CallToolRequestSchema, async (req) => {
  const { name, arguments: a = {} } = req.params;
  try {
    switch (name) {
      case "screenshot":
        return await handleScreenshot(a);
      case "click":
        await osa(mouseScript({ kind: "click", x: a.x, y: a.y, button: a.button || "left", clickCount: a.count || 1 }), "JavaScript");
        return text(`clicked (${a.x}, ${a.y})${a.count > 1 ? ` x${a.count}` : ""}`);
      case "double_click":
        await osa(mouseScript({ kind: "click", x: a.x, y: a.y, clickCount: 2 }), "JavaScript");
        return text(`double-clicked (${a.x}, ${a.y})`);
      case "move":
        await osa(mouseScript({ kind: "move", x: a.x, y: a.y }), "JavaScript");
        return text(`moved to (${a.x}, ${a.y})`);
      case "drag":
        await osa(mouseScript({ kind: "drag", x: a.x, y: a.y, x2: a.x2, y2: a.y2 }), "JavaScript");
        return text(`dragged (${a.x},${a.y}) -> (${a.x2},${a.y2})`);
      case "type_text":
        await osa(typeTextScript(a.text));
        return text(`typed ${a.text.length} chars`);
      case "press_key":
        await osa(pressKeyScript(a.key, a.modifiers));
        return text(`pressed ${a.modifiers ? a.modifiers.join("+") + "+" : ""}${a.key}`);
      case "activate_app":
        await osa(`tell application "${String(a.name).replace(/"/g, '\\"')}" to activate`);
        return text(`activated ${a.name}`);
      case "list_windows": {
        const nm = String(a.name).replace(/"/g, '\\"');
        const script = `tell application "System Events"
  set out to ""
  repeat with p in (every process whose name is "${nm}")
    repeat with w in windows of p
      set out to out & (name of w) & " | pos " & (position of w as string) & " | size " & (size of w as string) & linefeed
    end repeat
  end repeat
end tell
return out`;
        const { stdout } = await osa(script);
        return text(stdout.trim() || `No on-screen windows found for process "${a.name}".`);
      }
      default:
        return errText(`Unknown tool: ${name}`);
    }
  } catch (e) {
    const hint = /not allowed|assistive|accessibility|1002|-1719|-25211/i.test(String(e.stderr || e.message))
      ? "\nHint: this needs Accessibility permission. Grant the terminal app in System Settings ▸ Privacy & Security ▸ Accessibility, then restart it."
      : "";
    return errText(`${name} failed: ${e.stderr || e.message}${hint}`);
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
