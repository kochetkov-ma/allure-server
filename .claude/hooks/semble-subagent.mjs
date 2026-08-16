#!/usr/bin/env node
/**
 * brewcode:semble-setup — SubagentStart hook (self-contained, installed into a project).
 *
 * Registered with NO matcher, so it covers EVERY agent type. `matchQuery` for
 * SubagentStart is the AGENT TYPE, not a tool name; an entry without a matcher
 * key matches all of them (verified live on CC 2.1.226 — a matcher-less
 * SubagentStart entry fired for agent_type "Explore"). Its predecessor,
 * semble-explore.mjs, was pinned to "Explore" alone and is retired for good.
 *
 * A spawned subagent inherits none of the parent's context: it has never seen
 * the semble rule, the session hook's line, or any earlier nudge. So this fires
 * on every spawn with no throttle and no counter — there is nothing to
 * de-duplicate against.
 *
 * ADVISORY ONLY: one `additionalContext` line, never a decision of any kind.
 * Never spawns a process, always prints exactly one JSON object, always exits 0.
 *
 * Pure ESM, Node built-ins only. readStdin/output are inlined on purpose: this
 * file travels alone into a user's .claude/hooks/ and must have no imports.
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

function warn(message) {
  try {
    process.stderr.write('[semble-subagent] ' + message + '\n');
  } catch {
    /* stderr is best-effort */
  }
}
// --- telemetry (best-effort, never throws, never changes hook output) ------
const TELEMETRY_SRC = 'subagent';
const TELEMETRY_MAX_BYTES = 2_000_000;
const TELEMETRY_KEEP_LINES = 1000;

/**
 * Appends one JSONL record to .claude/semble/telemetry.jsonl. Single
 * appendFileSync, never read-modify-write. Every failure is swallowed: a hook
 * that cannot measure itself must still behave exactly as if it had.
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
    /* telemetry must never break a hook */
  }
}
// ---------------------------------------------------------------------------

/** {kind:'missing'|'corrupt'|'ok', state} — same reader as the other semble hooks. */
function readState(cwd) {
  const file = join(cwd, '.claude', 'semble', 'state.json');
  let st;
  try {
    st = statSync(file);
  } catch {
    return { kind: 'missing' };
  }
  if (!st.isFile()) return { kind: 'corrupt' };
  let raw;
  try {
    raw = readFileSync(file, 'utf8');
  } catch {
    return { kind: 'corrupt' };
  }
  if (!raw.trim()) return { kind: 'missing' };
  try {
    const state = JSON.parse(raw);
    if (state === null || typeof state !== 'object' || Array.isArray(state)) return { kind: 'corrupt' };
    return { kind: 'ok', state };
  } catch {
    return { kind: 'corrupt' };
  }
}

/** Identical usability gate to the reminder hook — see its comment for why phase !== 'ready' still fires. */
function gate(read) {
  if (read.kind === 'missing') return { ok: false, why: 'no-state', phase: '', enabled: false };
  if (read.kind !== 'ok') return { ok: false, why: 'corrupt', phase: '', enabled: false };
  const state = read.state;
  const phase = typeof state.phase === 'string' ? state.phase : '';
  const enabled = state.enabled !== false;
  if (!enabled) return { ok: false, why: 'disabled', phase, enabled };
  if (phase === 'disabled') return { ok: false, why: 'disabled', phase, enabled };
  if (phase === 'error') return { ok: false, why: 'error', phase, enabled };
  if (phase === 'prereq_ready') return { ok: false, why: 'not-registered', phase, enabled };
  const completed = Array.isArray(state.completed) ? state.completed : [];
  if (completed.indexOf('mcp') < 0) return { ok: false, why: 'no-mcp', phase, enabled };
  return { ok: true, why: 'ok', phase, enabled };
}

/**
 * Self-sufficient by design: the subagent cannot look anything up in the
 * parent's context, so the line has to carry availability, the exact call and
 * the exact fallback on its own.
 */
function message(cwd) {
  return (
    'semble: mcp__semble_code__search is already available to you — no ToolSearch needed. ' +
    'Start any "where/how/why does X work" question with ONE call: repo="' + cwd +
    '", top_k=5, max_snippet_lines=10, then open the hit at start_line. ' +
    'Use rg only for exact identifiers, literal strings and exhaustive enumeration.'
  );
}

function decide(input, cwd) {
  const sid = typeof input.session_id === 'string' ? input.session_id : '';
  const agentType = typeof input.agent_type === 'string' ? input.agent_type : '';
  // agent_id is the join key: the spawned subagent's own transcript is named
  // after it, which is what makes "was this line actually delivered" auditable.
  const agentId = typeof input.agent_id === 'string' ? input.agent_id : '';
  // A real SubagentStart payload always names the agent type. Its absence means
  // malformed or empty stdin, and a hook that cannot tell what it is talking to
  // says nothing — and writes nothing, since `cwd` is a guess at that point.
  if (!agentType) return {};
  const g = gate(readState(cwd));
  const record = (fired, why) =>
    telemetry(cwd, sid, 'gate', {
      fired, why, phase: g.phase, enabled: g.enabled, agent_type: agentType, agent_id: agentId,
    });

  if (!g.ok) {
    record(false, g.why);
    return {};
  }

  record(true, 'ok');
  telemetry(cwd, sid, 'nudge', {
    matcher: 'SubagentStart',
    agent: 'sub',
    agent_type: agentType,
    agent_id: agentId,
    q: '',
  });
  return {
    hookSpecificOutput: {
      hookEventName: 'SubagentStart',
      additionalContext: message(cwd),
    },
  };
}

async function main() {
  let cwd = process.cwd();
  try {
    let input = {};
    try {
      input = await readStdin();
    } catch {
      input = {}; // malformed/empty stdin: stay silent
    }
    if (!input || typeof input !== 'object' || Array.isArray(input)) input = {};
    if (typeof input.cwd === 'string' && input.cwd) cwd = input.cwd;
    output(decide(input, cwd));
  } catch (e) {
    warn('hook error: ' + (e && e.message));
    output({});
  }
}

main();
