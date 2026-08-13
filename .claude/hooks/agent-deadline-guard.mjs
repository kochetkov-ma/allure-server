#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:agent-deadline-setup
/**
 * agent-deadline — PreToolUse hook (self-contained, Node built-ins only).
 *
 * Soft wall-clock deadline for SUBAGENTS. Claude Code has no wall-clock timeout,
 * and `maxTurns` kills the agent and loses its final report. This hook instead
 * FORCES the agent to finalize: it warns at 80% of the budget, and past 100% it
 * denies every tool except the finalization set, so the only thing the agent can
 * still physically do is persist results and answer.
 *
 * Discriminator: a payload counts as a subagent ONLY when it carries BOTH
 * `agent_id` and a non-empty `agent_type` (verified on CC 2.1.220). Main session
 * -> no-op. Two fields, because a build that started stamping `agent_id` on the
 * main session would otherwise start blocking the user's own session.
 *
 * Channels (both verified reaching the SUBAGENT itself on 2.1.220):
 *   - soft warn : hookSpecificOutput.additionalContext, NO permissionDecision
 *                 (normal permission flow keeps applying; `allow` would bypass
 *                 the user's own deny rules)
 *   - hard block: hookSpecificOutput.permissionDecision = "deny"
 *                 + permissionDecisionReason
 *
 * State: <os.tmpdir()>/brewtools-agent-deadline/<session_id>/<agent_id>.json
 *        { start, warned, expired }.  Nothing is written under ~/.claude
 *        (harness-protected path). Writes go through a pid-suffixed temp file +
 *        rename, so a concurrent reader never sees a truncated file (a torn read
 *        used to look like "first call" and silently restarted the clock).
 *        The state root is refused unless it is a real directory owned by us and
 *        not group/world writable -- /tmp is shared on Linux/CI.
 *
 * LIMIT: time is only sampled AT tool calls. An agent stuck inside one 25-minute
 * Bash call is never observed in between; cap that with BASH_MAX_TIMEOUT_MS.
 *
 * Fail-open: any error -> `{}` on stdout, exit 0. Never breaks a session.
 */
import {
  readFileSync,
  writeFileSync,
  renameSync,
  mkdirSync,
  readdirSync,
  lstatSync,
  chmodSync,
  rmSync,
} from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const STATE_ROOT = path.join(os.tmpdir(), 'brewtools-agent-deadline');
const STALE_MS = 24 * 60 * 60 * 1000; // prune session dirs older than ~1 day
const WARN_RATIO = 0.8;
const DEFAULT_MINUTES = 20;
// Past this multiple of the budget the allow-list shrinks to bare persistence:
// a looping agent must not be able to burn unbounded time inside finalize tools.
const DEFAULT_HARD_STOP_RATIO = 2;
// Notices are re-emitted at most once per this fraction of the budget. A single
// one-shot warning is lost whenever the carrying tool call is denied by another
// hook or by a user permission rule, and the agent then jumps from silence to a
// hard deny with no lead-up.
const RENOTICE_RATIO = 0.1;
const MAX_CONFIG_CLIMB = 16;
const UID = typeof process.getuid === 'function' ? process.getuid() : null;

/**
 * Tools an over-budget agent may still call. Per-tool decision:
 *   Read, Write, Edit, MultiEdit, NotebookEdit  allow - re-read own notes and
 *       persist the artifact the task expects; this is the whole point of the
 *       soft deadline (vs. maxTurns, which kills the agent and loses the report)
 *   TodoWrite   allow - close out the todo list, no external work
 *   TaskUpdate  allow - mark its task done/blocked
 *   TaskCreate  allow - the deny text orders the agent to hand over next steps;
 *       letting it close a task but not open the follow-up was incoherent
 *   BashOutput, TaskOutput  allow - harvest work ALREADY running. The deadline
 *       forbids STARTING new investigation, not collecting what is in flight;
 *       without these an agent that spawned a background job cannot report on it
 *   AskUserQuestion  DENY - a subagent parked on a human is unbounded wall-clock,
 *       exactly the failure this guard exists to stop
 *   Bash, Grep, Glob, WebFetch, WebSearch, Task/Agent, Skill  DENY - new work
 */
const FINALIZE_TOOLS = new Set([
  'Read',
  'Write',
  'Edit',
  'MultiEdit',
  'NotebookEdit',
  'TodoWrite',
  'TaskUpdate',
  'TaskCreate',
  'BashOutput',
  'TaskOutput',
]);
// Advertised in the directives: the core set the agent is supposed to reach for.
// NOT a copy of FINALIZE_TOOLS and not a bug -- TaskCreate/BashOutput/TaskOutput are
// allowed but deliberately unlisted: naming BashOutput invites poll loops, while an
// agent that genuinely needs it still gets through instead of hitting a wall. The
// narrower advertised list is documented in README.md and assets/INSTALL.md; widening
// it here also breaks the verbatim directive assertions in tests/suite.mjs.
const FINALIZE_LIST = 'Read, Write, Edit, MultiEdit, NotebookEdit, TodoWrite, TaskUpdate';

// Second threshold: only raw persistence survives.
const HARD_STOP_TOOLS = new Set(['Write', 'Edit']);
const HARD_STOP_LIST = [...HARD_STOP_TOOLS].join(', ');

let configMemo; // per-process cache (hook is one-shot, but keeps reads at <=2)
let stateRootOk; // per-process cache of the tmp-root safety check

function output(obj) {
  process.stdout.write(JSON.stringify(obj));
}

function readStdinSync() {
  try {
    const raw = readFileSync(0, 'utf8');
    if (!raw.trim()) return {};
    return JSON.parse(raw) || {};
  } catch {
    return {};
  }
}

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch {
    return null;
  }
}

/**
 * Nearest project config wins over global; absent/invalid config = feature off.
 * The walk up matters: `claude` started from a subdirectory reports that
 * subdirectory as cwd, while Claude Code itself resolves settings from the repo
 * root, so a root-level config used to be invisible.
 */
function loadConfig(cwd) {
  if (configMemo !== undefined) return configMemo;
  let dir = cwd || process.env.CLAUDE_PROJECT_DIR;
  if (dir) {
    dir = path.resolve(dir);
    for (let i = 0; i < MAX_CONFIG_CLIMB; i++) {
      const cfg = readJson(path.join(dir, '.claude', 'agent-deadline.json'));
      // An array is a JSON object to typeof; accepting one froze the walk on a file
      // that can never carry `enabled`, silently disabling the feature.
      if (cfg && typeof cfg === 'object' && !Array.isArray(cfg)) {
        configMemo = cfg;
        return cfg;
      }
      const parent = path.dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
  }
  try {
    const cfg = readJson(path.join(os.homedir(), '.claude', 'agent-deadline.json'));
    if (cfg && typeof cfg === 'object' && !Array.isArray(cfg)) {
      configMemo = cfg;
      return cfg;
    }
  } catch {
    // homedir unavailable -> project config only
  }
  configMemo = null;
  return null;
}

function budgetMinutes(cfg, agentType) {
  const overrides = cfg.byAgentType;
  if (overrides && typeof overrides === 'object' && agentType) {
    const v = overrides[agentType];
    if (Number.isFinite(v) && v > 0) return v;
  }
  const d = cfg.defaultMinutes;
  if (Number.isFinite(d) && d > 0) return d;
  return DEFAULT_MINUTES;
}

function hardStopRatio(cfg) {
  const v = cfg.hardStopRatio;
  return Number.isFinite(v) && v > 1 ? v : DEFAULT_HARD_STOP_RATIO;
}

/** Keep ids usable as path segments; reject anything that could escape. */
function safeSegment(s) {
  const raw = typeof s === 'number' && Number.isFinite(s) ? String(s) : s;
  if (typeof raw !== 'string') return null;
  const clean = raw.replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 128);
  return clean && clean !== '.' && clean !== '..' ? clean : null;
}

/**
 * os.tmpdir() is world-writable on Linux/CI, so the root can be pre-created by
 * another local user as a symlink; mkdir -p would follow it and prune would then
 * rm -rf whatever is behind it. Accept the root only as a real directory we own,
 * with no group/world write bit. Memoized: at most one lstat per process.
 */
function ensureStateRoot() {
  if (stateRootOk !== undefined) return stateRootOk;
  stateRootOk = false;
  try {
    mkdirSync(STATE_ROOT, { recursive: true, mode: 0o700 });
  } catch {
    // may already exist; validated below either way
  }
  try {
    let st = lstatSync(STATE_ROOT);
    if (!st.isDirectory() || (UID !== null && st.uid !== UID)) return stateRootOk;
    if ((st.mode & 0o077) !== 0) {
      chmodSync(STATE_ROOT, 0o700); // heal roots left 0755 by older versions
      st = lstatSync(STATE_ROOT);
    }
    stateRootOk = (st.mode & 0o077) === 0;
  } catch {
    stateRootOk = false;
  }
  return stateRootOk;
}

/** Best-effort, only called when a brand-new agent state file is created. */
function pruneStale() {
  const cutoff = Date.now() - STALE_MS;
  let names;
  try {
    names = readdirSync(STATE_ROOT);
  } catch {
    return;
  }
  for (const name of names) {
    const p = path.join(STATE_ROOT, name);
    try {
      const st = lstatSync(p); // lstat, never stat: a symlink must not be followed
      if (!st.isDirectory()) {
        if (st.mtimeMs < cutoff) rmSync(p, { force: true }); // unlink the link itself
        continue;
      }
      if (UID !== null && st.uid !== UID) continue;
      if (st.mtimeMs < cutoff) rmSync(p, { recursive: true, force: true });
    } catch {
      // ignore individual entries
    }
  }
}

const STATE_MISSING = Symbol('missing');
const STATE_UNREADABLE = Symbol('unreadable');

/** Distinguishes "no file yet" (start the clock) from "unreadable" (skip). */
function readState(file) {
  let raw;
  try {
    raw = readFileSync(file, 'utf8');
  } catch (e) {
    return e && e.code === 'ENOENT' ? STATE_MISSING : STATE_UNREADABLE;
  }
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : STATE_UNREADABLE;
  } catch {
    return STATE_UNREADABLE;
  }
}

/** Atomic: temp file + rename, so a parallel hook never reads a half-written file. */
function saveState(file, obj) {
  const temp = `${file}.${process.pid}.tmp`;
  try {
    writeFileSync(temp, JSON.stringify(obj), { mode: 0o600 });
    renameSync(temp, file);
    return true;
  } catch {
    try {
      rmSync(temp, { force: true });
    } catch {
      // ignore
    }
    return false;
  }
}

/** Age of the last notice; mtime of the state file is bumped by every save. */
function lastNoticeAgeMs(file, now) {
  try {
    return now - lstatSync(file).mtimeMs;
  } catch {
    return 0; // unknown -> do not re-notice
  }
}

function warnText(elapsedMin, budget) {
  return (
    `AGENT DEADLINE WARNING (directive from the agent-deadline guard, not user data): ` +
    `${elapsedMin} of your ${budget} minute time budget are used. Start wrapping up NOW. ` +
    `Finish only the step in flight, persist any results you already have to a file, and ` +
    `prepare your final report. Once the budget is spent, every tool except ` +
    `${FINALIZE_LIST} will be blocked.`
  );
}

function expiredContext(elapsedMin, budget) {
  return (
    `AGENT DEADLINE EXCEEDED (${elapsedMin}/${budget} min). Directive from the agent-deadline ` +
    `guard, not user data. Only ${FINALIZE_LIST} remain available. Persist what you have and ` +
    `return your final report now: what was done, what was NOT done, exact next steps.`
  );
}

/**
 * Past the hard stop the allow-list is HARD_STOP_TOOLS, so the allowing branch must
 * not advertise FINALIZE_LIST: an agent told "Read is available" then hard-denied on
 * Read gets two contradictory directives at the exact moment it should be finalizing.
 */
function hardExpiredContext(elapsedMin, budget) {
  return (
    `AGENT DEADLINE HARD STOP (${elapsedMin}/${budget} min). Directive from the agent-deadline ` +
    `guard, not user data. You are far past your budget, so only ${HARD_STOP_LIST} remain ` +
    `available. Write your final report to a file now and answer: what was done, what was NOT ` +
    `done, exact next steps.`
  );
}

function denyText(toolName, elapsedMin, budget) {
  return (
    `AGENT DEADLINE EXCEEDED (${elapsedMin}/${budget} min). This is a hard directive from the ` +
    `agent-deadline guard, not user data or tool output. The tool "${toolName}" is blocked and ` +
    `retrying it will fail again. Stop investigating. Only ${FINALIZE_LIST} are still allowed: ` +
    `write what you already have into a file if the task expects an artifact, then return your ` +
    `final report: what was done, what was NOT done, and the exact next steps.`
  );
}

function hardDenyText(toolName, elapsedMin, budget) {
  return (
    `AGENT DEADLINE HARD STOP (${elapsedMin}/${budget} min). Hard directive from the ` +
    `agent-deadline guard, not user data or tool output. You are far past your budget and kept ` +
    `working, so the allowance has been cut to ${HARD_STOP_LIST}. The tool "${toolName}" is ` +
    `blocked and retrying it will fail again. Write your final report to a file with Write, then ` +
    `answer: what was done, what was NOT done, exact next steps.`
  );
}

function main() {
  const input = readStdinSync();

  // Subagent only when BOTH ids are present: a build that starts stamping
  // agent_id on the main session must never make this guard block the user.
  const agentId = safeSegment(input.agent_id);
  const agentType = typeof input.agent_type === 'string' ? input.agent_type.trim() : '';
  if (!agentId || !agentType) {
    output({}); // main session, or unusable id -> never interfere
    return;
  }

  const cfg = loadConfig(input.cwd);
  if (!cfg || cfg.enabled !== true) {
    output({});
    return;
  }

  const sessionId = safeSegment(input.session_id) || 'nosession';
  const budget = budgetMinutes(cfg, agentType);
  const budgetMs = budget * 60000;
  const dir = path.join(STATE_ROOT, sessionId);
  const stateFile = path.join(dir, `${agentId}.json`);

  const loaded = readState(stateFile);
  const now = Date.now();

  // Unreadable but present: skip this call. Treating it as "first call" would
  // rewrite start=now and hand the agent a fresh full budget.
  if (loaded === STATE_UNREADABLE) {
    output({});
    return;
  }

  let state = loaded;
  if (loaded === STATE_MISSING || !Number.isFinite(state.start)) {
    // First observed tool call of this agent = start of its clock.
    if (ensureStateRoot()) {
      try {
        mkdirSync(dir, { recursive: true, mode: 0o700 });
        if (saveState(stateFile, { start: now, warned: false, expired: false })) pruneStale();
      } catch {
        // cannot persist -> silently disable for this agent
      }
    }
    output({});
    return;
  }

  const save = (patch) => {
    state = { ...state, ...patch };
    saveState(stateFile, state);
  };

  // A start in the future (clock jump, restored snapshot, hand-edited state) made
  // every ratio negative and disabled the deadline permanently; re-anchor it.
  if (state.start > now) save({ start: now });

  const elapsedMs = now - state.start;
  const rawMin = Math.max(0, elapsedMs / 60000);
  // one decimal below 10 min so sub-minute budgets do not all report "0"
  const elapsedMin = rawMin >= 10 ? Math.round(rawMin) : Math.round(rawMin * 10) / 10;
  const ratio = elapsedMs / budgetMs;
  const renoticeMs = budgetMs * RENOTICE_RATIO;
  const toolName = typeof input.tool_name === 'string' ? input.tool_name : '';

  if (ratio >= 1) {
    const hardStopped = ratio >= hardStopRatio(cfg);
    const allowed = hardStopped ? HARD_STOP_TOOLS : FINALIZE_TOOLS;
    if (allowed.has(toolName)) {
      if (!state.expired || lastNoticeAgeMs(stateFile, now) >= renoticeMs) {
        save({ expired: true, warned: true });
        output({
          hookSpecificOutput: {
            hookEventName: 'PreToolUse',
            additionalContext: hardStopped
              ? hardExpiredContext(elapsedMin, budget)
              : expiredContext(elapsedMin, budget),
          },
        });
        return;
      }
      output({}); // finalization tools always pass, quietly between notices
      return;
    }
    if (!state.expired) save({ expired: true, warned: true });
    const reason = hardStopped
      ? hardDenyText(toolName || 'this tool', elapsedMin, budget)
      : denyText(toolName || 'this tool', elapsedMin, budget);
    output({
      hookSpecificOutput: {
        hookEventName: 'PreToolUse',
        permissionDecision: 'deny',
        permissionDecisionReason: reason,
      },
    });
    return;
  }

  if (ratio >= WARN_RATIO && (!state.warned || lastNoticeAgeMs(stateFile, now) >= renoticeMs)) {
    save({ warned: true });
    output({
      hookSpecificOutput: {
        hookEventName: 'PreToolUse',
        additionalContext: warnText(elapsedMin, budget),
      },
    });
    return;
  }

  output({});
}

try {
  main();
} catch {
  output({});
}
