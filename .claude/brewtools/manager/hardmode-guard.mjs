#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:manager-setup
// brewtools:manager-setup — HARD wall guard (PreToolUse, matcher "*").
//
// SELF-CONTAINED — copied into <project>/.claude/brewtools/manager/ by
// `/brewtools:manager-setup install` and registered in .claude/settings.local.json
// (PreToolUse "*"). No external imports. Project-only state.
//
// When state.hard === true, physically DENIES tool calls in the MAIN session,
// leaving only delegation (Task/Agent/Skill), reading, and task tracking.
// Subagents stay fully free.
//
// LINCHPIN: this PreToolUse hook fires inside subagents too, and subagent
// tool-call stdin carries `agent_id`; the MAIN session stdin does NOT.
// session_id is identical for both. => Discriminator is `agent_id` ALONE: DENY
// whenever it is ABSENT (main session), pass through when present.
// `agent_type` is NOT a discriminator — CC 2.1.228 sets it on the MAIN thread of
// a `claude --agent <name>` session too (without `agent_id`), so treating it as
// one disarms the wall for every such session. A `--agent` main session IS walled.
//
// Strictness levels:
//   strict   — deny all non-read tools (no bash, no web).
//   balanced — additionally allow read-only Bash (whitelist classifier), WebSearch,
//              and read-only MCP tools.
// Fail-open: ANY thrown error / unreadable state -> output({}) so a guard bug never
// bricks the session.
//
// PreToolUse stdin fields used: tool_name, tool_input.command, cwd, agent_id.
//
// Provenance: NOT-IN-DOC (HOOKS-REFERENCE.md lists only subagent_type/subagent_id),
// but the CC 2.1.228 binary's own schema text states it outright — agent_id is
// "Present only when the hook fires from within a subagent... Absent for the main
// thread, even in --agent sessions. Use this field (not agent_type) to distinguish
// subagent calls from main-thread calls." Established 2026-08-11 by reading the
// 2.1.228 binary, NOT by a live probe; earlier live checks (2.1.177, 2.1.195)
// covered agent_id only. Re-verify live if PreToolUse stdin shape ever looks off.
// The undocumented `effort` payload key (used by other brewtools hooks) is
// irrelevant here and intentionally NOT read by this guard.

import { readFileSync } from 'node:fs';
import { join } from 'node:path';

// ---- inlined stdin/stdout helpers (no plugin lib) ---------------------------

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function output(obj) {
  process.stdout.write(JSON.stringify(obj));
}

// ---- project-only state read ------------------------------------------------
// Reads <cwd>/.claude/brewtools/manager/state.json. Global ~/.claude state is
// NEVER consulted: the wall is strictly project-scoped. Missing/unreadable/invalid
// file => hard:false (no-op).
function readProjectState(cwd) {
  try {
    const p = join(cwd, '.claude', 'brewtools', 'manager', 'state.json');
    const parsed = JSON.parse(readFileSync(p, 'utf8'));
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { hard: false, level: 'balanced' };
    }
    const hard = parsed.hard === true;
    const level = parsed.level === 'strict' ? 'strict' : 'balanced';
    return { hard, level };
  } catch {
    return { hard: false, level: 'balanced' };
  }
}

// ---- guard tables -----------------------------------------------------------

// Tools always permitted in the main session under the hard wall.
// Audited bucket-by-bucket: nothing here can mutate the workspace on its own. Tools that
// merely SPAWN work (Task/Agent/Skill/SlashCommand/SendMessage) are safe because every tool
// call they cause in the main session comes back through this same guard.
const ALWAYS_ALLOW = new Set([
  // read — inspect files, never write
  'Read', 'Grep', 'Glob', 'NotebookRead',
  // delegate / orchestrate — hands the work to a subagent, which is where mutation belongs
  'Task', 'Agent', 'Skill', 'SlashCommand', 'ListAgents', 'SendMessage', 'Monitor',
  // plan mode — without these an armed wall traps a plan-mode session forever
  'EnterPlanMode', 'ExitPlanMode',
  // tool discovery — with ENABLE_TOOL_SEARCH the Task* tools below are DEFERRED, so
  // denying ToolSearch would make the tracking bucket unreachable
  'ToolSearch',
  // track / report — task graph + findings, no filesystem side effects
  'TaskCreate', 'TaskUpdate', 'TaskList', 'TaskGet', 'TodoWrite', 'ReportFindings',
  // background shells — read output of / stop a shell started before arming; neither writes
  'BashOutput', 'KillShell', 'KillBash',
  // MCP resource introspection — read-only by protocol definition
  'ListMcpResourcesTool', 'ReadMcpResourceTool',
  // ask the human
  'AskUserQuestion'
]);
// Deliberately NOT allowed: Artifact (publishes a page), WebFetch, Write/Edit/NotebookEdit.

// Tools never permitted in the main session under the hard wall (any level).
const ALWAYS_BLOCK = new Set(['Write', 'Edit', 'NotebookEdit', 'WebFetch']);

// MCP tool names whose verb implies mutation.
const MCP_WRITE_VERB = /mcp__.*(write|create|update|delete|put|post|send|comment|merge|move|upload|publish|export|resize|duplicate)/i;
// MCP tool names whose verb implies read-only access.
const MCP_READ_VERB = /mcp__.*(search|get|list|read|fetch|query|trace|status|describe)/i;

const EXIT_HINT = 'Manager HARD wall is ON — delegate via Task/Agent. To exit run `/brewtools:manager-setup disable`; the only Bash it needs — `node <project>/.claude/brewtools/manager/manager-state.mjs set hard=false` — is self-exempt at every level.';

function deny(reason) {
  output({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'deny',
      permissionDecisionReason: `${reason} ${EXIT_HINT}`
    }
  });
}

// Self-exempt: ONLY the genuine `node <path>/manager-state.mjs get|set ...` CLI invocation
// used by `/brewtools:manager-setup disable` / `level`.
//
// The exemption is anchored on the SCRIPT NODE ACTUALLY EXECUTES (argv[1]), never on a
// substring of the command line. A substring anchor was an arbitrary-code-execution hole:
// `node -e "<payload>" manager-state.mjs` and `node -e "console.log('manager-state.mjs')"`
// both satisfied it and ran before the Bash classifier ever saw them.
//
// Four independent conditions, all required:
//   1. the command starts with `node ` (no env prefix, no other binary),
//   2. no shell operator outside quotes and no `$` expansion anywhere,
//   3. no eval/print/input-type flag — those make node a shell,
//   4. argv[1] (the first token after `node`, i.e. the script) is the helper at one of its
//      two shipped locations, and the remaining tokens are the helper's own CLI shape.
function noShellOpsOutsideQuotes(s) {
  let inDQ = false, inSQ = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === '"' && !inSQ) { inDQ = !inDQ; continue; }
    if (c === "'" && !inDQ) { inSQ = !inSQ; continue; }
    if (!inDQ && !inSQ) {
      if (c === '>' || c === '|' || c === '&' || c === ';') return false;
      if (s.slice(i, i + 2) === '$(') return false;
      if (c === '`') return false;
    }
  }
  return true;
}

// The two locations the helper is ever shipped to: the project copy written by
// `manager-setup install`/`upgrade`, and the plugin's own hooks/lib. Anchoring on the
// full tail (not just the basename) means an arbitrary `/tmp/evil/manager-state.mjs`
// is not exempt.
const HELPER_PATH = /(^|\/)(\.claude\/brewtools\/manager|hooks\/lib)\/manager-state\.mjs$/;

// Any flag that turns `node` into an evaluator. Rejected outright.
const NODE_EVAL_FLAG = /(^|\s)(-e|--eval|-p|--print|--input-type|--experimental-loader|--import|--require|-r|--loader)(\s|=|$)/;

// Split into argv-ish tokens honouring quotes. null when quoting is unbalanced.
function tokenizeCommand(s) {
  const out = [];
  let cur = '', started = false, inDQ = false, inSQ = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === '\\' && !inSQ && i + 1 < s.length) { cur += s[++i]; started = true; continue; }
    if (c === '"' && !inSQ) { inDQ = !inDQ; started = true; continue; }
    if (c === "'" && !inDQ) { inSQ = !inSQ; started = true; continue; }
    if (!inDQ && !inSQ && /\s/.test(c)) { if (started) { out.push(cur); cur = ''; started = false; } continue; }
    cur += c; started = true;
  }
  if (inDQ || inSQ) return null;
  if (started) out.push(cur);
  return out;
}

// The helper's own CLI grammar: get|set [key=value ...] [--cwd DIR]. Mirrors parseCliArgs
// in lib/manager-state.mjs — the guard must never allow a shape the helper would not.
function isStateCliArgs(args) {
  if (args[0] !== 'get' && args[0] !== 'set') return false;
  for (let i = 1; i < args.length; i++) {
    const a = args[i];
    if (a === '--cwd') { if (!args[++i]) return false; continue; }
    if (a.startsWith('-')) return false;
    if (args[0] !== 'set') return false;
    if (!/^(hard=(true|false)|level=(strict|balanced))$/.test(a)) return false;
  }
  return true;
}

function isStateWriteCommand(cmd) {
  if (typeof cmd !== 'string' || !cmd) return false;
  const s = cmd.trim();
  if (!/^node\s/.test(s)) return false;                   // must be a bare node invocation
  if (!noShellOpsOutsideQuotes(s)) return false;          // no operators outside quotes
  if (s.includes('$')) return false;                      // no variable/command expansion
  if (NODE_EVAL_FLAG.test(s)) return false;               // node must not act as an evaluator
  const argv = tokenizeCommand(s);
  if (!argv || argv.length < 3) return false;
  if (!HELPER_PATH.test(argv[1])) return false;           // the SCRIPT must be the shipped helper
  return isStateCliArgs(argv.slice(2));
}

// Read-only base commands allowed under `balanced`.
const READONLY_BASE = new Set([
  'ls', 'cat', 'pwd', 'which', 'head', 'tail', 'wc', 'grep', 'rg',
  'env', 'date', 'whoami', 'basename', 'dirname', 'realpath', 'test', '[',
  'jq', 'echo'
]);

// Tokens that, appearing ANYWHERE in a command, force a deny (mutation/eval risk).
const MUTATING_TOKENS = [
  '>', '>>', 'rm ', 'mv ', 'cp ', 'tee', 'sed -i', 'perl -i', 'truncate',
  'dd ', 'git commit', 'git push', 'git add', 'git reset', 'git checkout',
  'git restore', 'git rm', 'npm i', 'npm install', 'yarn add', 'pip install',
  'mkdir', 'touch', 'chmod', 'chown', 'ln ', 'python -c', 'python3 -c',
  'node -e', 'node --eval', '--eval'
];

// Classify a single shell segment as read-only-safe. Default-deny.
function isReadonlySegment(seg) {
  const s = seg.trim();
  if (!s) return true; // empty segment from a trailing operator — harmless
  // No command substitution.
  if (/\$\(/.test(s) || /`/.test(s)) return false;
  // Any redirection or mutating token anywhere -> deny.
  for (const t of MUTATING_TOKENS) {
    if (s.includes(t)) return false;
  }
  // `:` immediately before `>` (truncation idiom) — already covered by '>' token, but be explicit.
  if (/:\s*>/.test(s)) return false;

  const words = s.split(/\s+/);
  const base = words[0];

  // git: only read-only subcommands.
  if (base === 'git') {
    const sub = words[1] || '';
    const allowedGit = new Set(['status', 'log', 'diff', 'show', 'branch', 'rev-parse', 'describe', 'stash']);
    if (sub === 'remote') return words[2] === '-v';
    if (sub === 'tag') return words[2] === '-l';
    if (sub === 'stash') return words[2] === 'list';
    return allowedGit.has(sub);
  }
  // gh: only read-only verbs.
  if (base === 'gh') {
    return words.includes('list') || words.includes('view') || words.includes('status');
  }
  // node --check only (node -e/--eval already rejected above).
  if (base === 'node') {
    return words.includes('--check');
  }
  // find: reject mutating actions.
  if (base === 'find') {
    if (words.some(w => w === '-delete' || w === '-exec' || w === '-execdir' || w === '-fprint')) return false;
    return true;
  }
  // python/python3 without -c already passed token check (python -c rejected); deny anything else to be safe.
  if (base === 'python' || base === 'python3') return false;

  return READONLY_BASE.has(base);
}

// Classify a full Bash command (handles chaining) as read-only. Default-deny.
function isReadonlyCommand(cmd) {
  if (typeof cmd !== 'string' || !cmd.trim()) return false;
  // Reject command substitution outright before splitting.
  if (/\$\(/.test(cmd) || /`/.test(cmd)) return false;
  // Split on chaining operators; classify each segment. Pipe counts as chaining too.
  const segments = cmd.split(/&&|\|\||;|\|/);
  for (const seg of segments) {
    if (!isReadonlySegment(seg)) return false;
  }
  return true;
}

(async () => {
  try {
    let input;
    try {
      input = await readStdin();
    } catch {
      // (i) Fail-open on unreadable/invalid stdin.
      output({});
      return;
    }
    const cwd = input.cwd || process.cwd();

    // (a) Hot path: hard wall off -> near-zero-overhead no-op.
    const state = readProjectState(cwd);
    if (state.hard !== true) { output({}); return; }

    // (b) LINCHPIN: only agent_id marks a subagent -> pass through. agent_type is NOT a
    // discriminator: CC 2.1.228 sets it on the MAIN thread of a `claude --agent <name>`
    // session too (without agent_id), so an OR here disarms the wall for those sessions.
    if (Object.prototype.hasOwnProperty.call(input, 'agent_id')) {
      output({});
      return;
    }

    const level = state.level === 'strict' ? 'strict' : 'balanced';
    const tool = input.tool_name || '';
    const toolInput = input.tool_input || {};

    // (c) Always-allow set (delegation, reading, tracking).
    if (ALWAYS_ALLOW.has(tool)) { output({}); return; }

    // (d) Self-exempt: Bash that writes Manager state (anchored by path). Survives strict.
    if (tool === 'Bash' && isStateWriteCommand(toolInput.command)) { output({}); return; }

    // (e) Always-block tools + mutating MCP verbs.
    if (ALWAYS_BLOCK.has(tool)) {
      deny(`Hard wall: ${tool} is blocked in the main session — delegate to a subagent.`);
      return;
    }
    if (MCP_WRITE_VERB.test(tool)) {
      deny(`Hard wall: mutating MCP tool ${tool} is blocked in the main session — delegate to a subagent.`);
      return;
    }

    // (f) Bash.
    if (tool === 'Bash') {
      if (level === 'strict') {
        deny('Hard wall (strict): Bash is blocked in the main session — delegate execution to a subagent.');
        return;
      }
      // balanced: allow only fully read-only commands.
      if (isReadonlyCommand(toolInput.command)) { output({}); return; }
      deny('Hard wall (balanced): only read-only Bash is allowed in the main session — delegate execution to a subagent.');
      return;
    }

    // (g) WebSearch + read-only MCP.
    if (tool === 'WebSearch') {
      if (level === 'balanced') { output({}); return; }
      deny('Hard wall (strict): WebSearch is blocked in the main session — delegate to a subagent.');
      return;
    }
    if (tool.startsWith('mcp__')) {
      if (level === 'balanced' && MCP_READ_VERB.test(tool)) { output({}); return; }
      deny(`Hard wall: MCP tool ${tool} is blocked in the main session — delegate to a subagent.`);
      return;
    }

    // (h) Default-deny everything else.
    deny(`Hard wall: ${tool || 'this tool'} is blocked in the main session — delegate to a subagent.`);
  } catch {
    // (i) Fail-open: never brick the session on a guard bug.
    output({});
  }
})();
