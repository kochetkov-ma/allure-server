#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewcode:semble-setup
/**
 * brewcode:semble-setup — PostToolUse / PostToolUseFailure hook (self-contained,
 * installed into a project). PURE OBSERVER.
 *
 * It answers one question the other three hooks cannot: did a real semble call
 * actually happen, and how does that compare to the ordinary search-shaped tool
 * uses it was supposed to displace. It appends JSONL to
 * `<cwd>/.claude/semble/telemetry.jsonl` and NOTHING else — no
 * `additionalContext`, no `permissionDecision`, no `systemMessage`. The reply is
 * always the neutral `{}` and the exit code is always 0, so wiring it can never
 * change what any tool call does.
 *
 * Registered on BOTH post-tool events, same matcher list:
 *   PostToolUse         -> a call that succeeded (`ok:true`)
 *   PostToolUseFailure  -> a call that errored or was interrupted (`ok:false`)
 * On 2.1.226 a failed call does NOT fire PostToolUse, so without the second
 * registration every failure would silently vanish from the denominator.
 *
 * Pure ESM, Node built-ins only. Helpers are inlined on purpose: this file
 * travels alone into a user's .claude/hooks/ and must have no imports.
 */
import { appendFileSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// --- inlined helpers -------------------------------------------------------
async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function output(response) {
  let text = '{}';
  try {
    text = JSON.stringify(response === undefined ? {} : response);
  } catch {
    text = '{}';
  }
  process.stdout.write(text + '\n');
}

// --- telemetry (best-effort, never throws, never changes hook output) ------
const TELEMETRY_SRC = 'stats';
const TELEMETRY_MAX_BYTES = 2_000_000;
const TELEMETRY_KEEP_LINES = 1000;

/**
 * Appends one JSONL record to .claude/semble/telemetry.jsonl. Single
 * appendFileSync, never read-modify-write. Every failure is swallowed: a hook
 * that cannot measure itself must still behave exactly as if it had.
 * Byte-identical in shape to the writer in semble-prefetch.mjs — the two files
 * append to the SAME log and must stay in sync.
 */
function telemetry(cwd, sid, ev, extra) {
  try {
    const file = join(cwd, '.claude', 'semble', 'telemetry.jsonl');
    try {
      if (statSync(file).size > TELEMETRY_MAX_BYTES) {
        const kept = readFileSync(file, 'utf8').split('\n').filter((l) => l).slice(-TELEMETRY_KEEP_LINES);
        writeFileSync(file, kept.join('\n') + '\n');
      }
    } catch {
      /* no file yet, or the trim failed - append anyway */
    }
    const rec = {
      ts: new Date().toISOString(),
      ev,
      src: TELEMETRY_SRC,
      sid: typeof sid === 'string' ? sid : '',
      ...(extra || {}),
    };
    appendFileSync(file, JSON.stringify(rec) + '\n');
  } catch {
    /* telemetry must never break a tool call */
  }
}
// ---------------------------------------------------------------------------

/** The numerator: the two semble MCP tools. */
const SEMBLE_TOOLS = ['mcp__semble_code__search', 'mcp__semble_code__find_related'];

/** The denominator: search-shaped tools that semble is meant to displace. */
const SEARCH_TOOLS = ['Bash', 'Grep', 'Glob'];

/**
 * Prefetch conversion, and the only reason this hook watches `Read`.
 * `semble-prefetch.mjs` logs the candidate paths it injected; a conversion is an
 * injected path being OPENED afterwards in the same session. Without an `open`
 * record there is no way to compute that from the log without replaying the
 * transcripts, so the number would only ever exist while someone was measuring.
 * `Read` is never counted as a search: it is not a tool semble displaces.
 */
const OPEN_TOOLS = ['Read'];
const PATHMAX = 200;

// The definition of "search-shaped" for the denominator. It outlived the
// retired advisory hook that first carried it; keep it in step with any other
// copy that appears. A search binary at a command boundary
// (start, |, ;, &, &&, ||, subshell); the `m` flag covers heredocs and
// multi-line scripts, where `^` alone only ever matched offset 0.
const SEARCH_RE = /(?:^|[|;&(]|&&|\|\|)\s*(?:command\s+)?(grep|egrep|fgrep|ugrep|rg|ag|ack|find|bfs)\b/m;

const QMAX = 120;

/**
 * `main` | `sub`, decided on `agent_id` ALONE. CC 2.1.226 documents the two
 * fields verbatim:
 *   agent_id   - "Present only when the hook fires from within a subagent.
 *                 Absent for the main thread, even in --agent sessions. Use
 *                 this field (not agent_type) to distinguish subagent calls
 *                 from main-thread calls."
 *   agent_type - "Present when the hook fires from within a subagent
 *                 (alongside agent_id), OR on the main thread of a session
 *                 started with --agent (without agent_id)."
 * The payload builder confirms it: `agent_type` falls back to the session-wide
 * `mainThreadAgentType`, so testing it labelled every main-thread call in an
 * `--agent` session as `sub`. Undefined values are dropped by the JSON
 * serialisation, so a plain key test on the parsed input is exact.
 */
function agentOf(input) {
  if (!input || typeof input !== 'object') return 'unknown';
  return typeof input.agent_id === 'string' && input.agent_id ? 'sub' : 'main';
}

/** Max stored length of the raw agent_id. Real ones are 36-char UUIDs. */
const AIDMAX = 64;

/**
 * The evidence behind `agent:"sub"`, stored so the label is checkable rather
 * than trusted. A `sub` record WITHOUT `aid` can only have been written by the
 * pre-fix `agentOf`, which is what lets a reader quarantine mislabelled history
 * instead of silently averaging over it. It is also the join key to the
 * `agent_id` the SubagentStart hook already logs.
 */
function aidOf(input) {
  const id = input && input.agent_id;
  return typeof id === 'string' && id ? { aid: id.slice(0, AIDMAX) } : {};
}

/** Non-negative integer milliseconds, or null when the payload had none. */
function msOf(input) {
  const d = input && input.duration_ms;
  if (typeof d !== 'number' || !Number.isFinite(d) || d < 0) return null;
  return Math.round(d);
}

/**
 * Did a PostToolUse response carry an error flag anyway? MCP results are
 * delivered as `{content, isError}`; only the explicit error booleans count, a
 * plain `error` key is too common a legitimate field name to trust.
 */
function responseFailed(resp) {
  if (!resp || typeof resp !== 'object' || Array.isArray(resp)) return false;
  return resp.isError === true || resp.is_error === true;
}

/** The query text worth logging for a search-shaped tool, or null. */
function queryOf(toolName, toolInput) {
  const ti = toolInput && typeof toolInput === 'object' && !Array.isArray(toolInput) ? toolInput : {};
  if (toolName === 'Bash') {
    const cmd = typeof ti.command === 'string' ? ti.command : '';
    if (!cmd || !SEARCH_RE.test(cmd)) return null; // not search-shaped: do not log
    return cmd.slice(0, QMAX);
  }
  // Grep / Glob ARE search tools; the pattern is the whole query.
  const pat = typeof ti.pattern === 'string' ? ti.pattern : '';
  return pat.slice(0, QMAX);
}

/**
 * `{fail:true}` on a PostToolUseFailure, `{}` otherwise - spread into the extras
 * so a success record stays byte-identical to what earlier versions wrote. Without
 * it an `open` emitted for a Read that FAILED is indistinguishable from a real
 * open, and a failed Read of an injected path inflates prefetch conversion.
 */
function failFlag(ev) {
  return ev === 'PostToolUseFailure' ? { fail: true } : {};
}

function record(input, cwd) {
  const ev = typeof input.hook_event_name === 'string' ? input.hook_event_name : '';
  if (ev !== 'PostToolUse' && ev !== 'PostToolUseFailure') return;

  const tool = typeof input.tool_name === 'string' ? input.tool_name : '';
  if (!tool) return;

  const sid = typeof input.session_id === 'string' ? input.session_id : '';
  const agent = agentOf(input);
  const aid = aidOf(input);
  const ms = msOf(input);

  if (SEMBLE_TOOLS.indexOf(tool) >= 0) {
    const ok = ev === 'PostToolUse' && !responseFailed(input.tool_response);
    telemetry(cwd, sid, 'call', { tool, ok, ...(ms === null ? {} : { ms }), agent, ...aid });
    return;
  }
  if (OPEN_TOOLS.indexOf(tool) >= 0) {
    const ti = input.tool_input && typeof input.tool_input === 'object' ? input.tool_input : {};
    const p = typeof ti.file_path === 'string' ? ti.file_path : '';
    if (!p) return;
    // Both absolute and repo-relative forms are logged. The prefetch hook records
    // semble's repo-relative `file_path`; Claude Code reads with an absolute path.
    // Storing both spares the reader a cwd it does not have at read time.
    const rel = p.indexOf(cwd + '/') === 0 ? p.slice(cwd.length + 1) : p;
    telemetry(cwd, sid, 'open', {
      f: rel.slice(0, PATHMAX), abs: p.slice(0, PATHMAX), ...failFlag(ev), agent, ...aid,
    });
    return;
  }
  if (SEARCH_TOOLS.indexOf(tool) < 0) return;

  // A failed grep is still a search: `grep foo` with no match exits 1 and lands
  // on PostToolUseFailure. Dropping those would gut the denominator.
  const q = queryOf(tool, input.tool_input);
  if (q === null) return;
  telemetry(cwd, sid, 'search', { tool, q, ...failFlag(ev), agent, ...aid });
}

async function main() {
  let cwd = process.cwd();
  try {
    let input = {};
    try {
      input = await readStdin();
    } catch {
      input = {}; // malformed/empty stdin: record nothing, stay neutral
    }
    if (!input || typeof input !== 'object' || Array.isArray(input)) input = {};
    if (typeof input.cwd === 'string' && input.cwd) cwd = input.cwd;
    record(input, cwd);
  } catch {
    /* an observer that throws is worse than an observer that misses a sample */
  }
  output({});
}

main();
