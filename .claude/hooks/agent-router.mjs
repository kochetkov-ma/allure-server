#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:agent-router-setup
/**
 * agent-router - PreToolUse hook for the `Agent` tool (Node built-ins only, ESM).
 *
 * The main loop habitually spawns a generic agent (`general-purpose`) when a real
 * expert exists: a project agent in `<root>/.claude/agents/*.md`, or a plugin
 * specialist. This hook catches that and redirects. Tier 1 = deterministic only:
 * no network, no LLM, single-digit milliseconds on the common path.
 *
 * Decision order (allow silently = exit 0, no stdout, as early as possible):
 *   1. tool_name !== 'Agent'                     -> allow  (`Task` normalizes to `Agent`)
 *   2. agent_id present (subagent-issued call)   -> allow  (only the main loop is policed)
 *   3. config enabled:false / config unparsable  -> allow
 *   4. subagent_type is a project agent          -> allow  (an expert was already picked)
 *   5. subagent_type not generic / in neverFlag  -> allow
 *   6. intent rule fires + picked is generic     -> DENY, naming the expert
 *      (a project agent covering the same intent outranks the plugin specialist)
 *   7. roster scoring: one clear winner          -> DENY, naming it
 *      several plausible / weak best             -> additionalContext nudge, allow
 *      nothing scores                            -> allow silently
 *   8. anti-loop: a given (session, task) is denied AT MOST ONCE; the retry is
 *      allowed with additionalContext instead. A deny returns to the model as a
 *      tool error; denying the retry too would loop forever.
 *   9. fail open, always: any throw / bad stdin / bad config / bad roster ->
 *      exit 0, no stdout. Never breaks the user's tool call.
 *
 * Channels:
 *   deny  : hookSpecificOutput.permissionDecision = "deny" + permissionDecisionReason
 *   nudge : hookSpecificOutput.additionalContext, NO permissionDecision (an `allow`
 *           here would bypass the user's own deny rules)
 *
 * State: <os.tmpdir()>/brewtools-agent-router/<session_id>/<sha1(root+task)[0..32]>
 *        one empty-ish marker file per (session, root, task) already denied once; nothing
 *        else lives there. Nothing is written under ~/.claude (harness-protected
 *        path). If the state root is unusable (read-only, foreign-owned tmp) EVERY
 *        deny degrades to a nudge - without a marker we cannot guarantee the loop
 *        terminates.
 */
import {
  readFileSync,
  writeFileSync,
  renameSync,
  readdirSync,
  mkdirSync,
  lstatSync,
  statSync,
  chmodSync,
  rmSync,
} from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { createHash } from 'node:crypto';

const EVENT = 'PreToolUse';
const STATE_ROOT = path.join(os.tmpdir(), 'brewtools-agent-router');
const STALE_MS = 24 * 60 * 60 * 1000; // prune markers older than ~1 day
const MAX_ROOT_CLIMB = 16;
const PROMPT_SCAN_CHARS = 2000;
const DESC_SCAN_CHARS = 500;
const DESC_WORD_CAP = 3; // description-word overlap is noisy; bound its contribution
const UID = typeof process.getuid === 'function' ? process.getuid() : null;

/**
 * Intent rules: data, not control flow. `match` is a regex source string (compiled
 * case-insensitive, run over the RAW task text), `expert` the agent to redirect to,
 * `label` the human phrase used in the deny reason. First match wins. A config
 * `intents` array REPLACES this table wholesale.
 */
const DEFAULT_INTENTS = [
  {
    label: 'skill authoring',
    expert: 'brewcode:skill-creator',
    match:
      '\\bSKILL\\.md\\b' +
      '|\\bslash[ -]command\\b' +
      '|\\bskill activation\\b' +
      '|\\b(?:creat|writ|author|scaffold|improv|refactor|fix|updat|debug|add)\\w*\\s+' +
      '(?:a\\s+|an\\s+|the\\s+|new\\s+|our\\s+|this\\s+)*(?:claude(?:\\s+code)?\\s+)?skills?(?![\\w-])' +
      '|\\bskills?\\s+(?:creation|authoring|scaffold\\w*|definition)\\b',
  },
  {
    label: 'agent authoring',
    expert: 'brewcode:agent-creator',
    match:
      '\\.claude/agents/' +
      '|\\bagent (?:definition|frontmatter|roster|file)\\b' +
      '|\\bsubagent definition\\b' +
      '|\\b(?:creat|writ|author|scaffold|improv|refactor|fix|updat|add)\\w*\\s+' +
      '(?:a\\s+|an\\s+|the\\s+|new\\s+|our\\s+|this\\s+)*(?:sub[- ]?)?agents?(?![\\w-])',
  },
  {
    label: 'hook authoring',
    expert: 'brewcode:hook-creator',
    match:
      '\\bPreToolUse\\b|\\bPostToolUse\\b|\\bSessionStart\\b|\\bUserPromptSubmit\\b' +
      '|\\bSubagentStart\\b|\\bSubagentStop\\b|\\bhookSpecificOutput\\b|\\bhooks\\.json\\b' +
      '|\\b(?:creat|writ|author|debug|fix|improv|updat|instal|regist|add)\\w*\\s+' +
      '(?:a\\s+|an\\s+|the\\s+|new\\s+|our\\s+|this\\s+)*(?:claude(?:\\s+code)?\\s+)?hooks?(?![\\w-])' +
      '|\\bsettings\\.json\\b[^\\n]{0,40}\\bhooks?\\b' +
      '|\\bhooks?\\b[^\\n]{0,40}\\bsettings\\.json\\b',
  },
  {
    label: 'shell scripting',
    expert: 'brewcode:bash-expert',
    match:
      '\\bshell\\s?script\\b' +
      '|\\b(?:bash|zsh|sh)\\s+script\\b' +
      '|\\bshellcheck\\b' +
      '|#!/(?:usr/bin/env\\s+)?(?:ba|z)?sh\\b' +
      '|\\b(?:writ|creat|fix|debug|refactor|harden|port|updat)\\w*\\s+' +
      '(?:a\\s+|an\\s+|the\\s+|new\\s+|our\\s+|this\\s+)*[\\w./-]*\\.(?:sh|bash|zsh)\\b',
  },
];

const DEFAULTS = {
  enabled: true,
  level: 'fast',
  genericTypes: ['general-purpose', 'worker'],
  // Explore is the right tool for search, Plan for planning - never flagged by
  // roster scoring. Only an explicit intent rule may override that. The four
  // intent experts are also here explicitly, and normalizeConfig() below unions
  // this list with every configured intent's `expert` - a router redirect target
  // can never itself be flagged, built-in or user-defined.
  neverFlag: [
    'Explore', 'Plan', 'statusline-setup', 'output-style-setup',
    'brewcode:agent-creator', 'brewcode:skill-creator', 'brewcode:hook-creator', 'brewcode:bash-expert',
  ],
  minScore: 3,
  margin: 2,
  intents: DEFAULT_INTENTS,
};

const STOPWORDS = new Set([
  'a', 'an', 'the', 'and', 'or', 'of', 'to', 'in', 'on', 'for', 'with', 'from', 'by',
  'is', 'are', 'be', 'was', 'were', 'this', 'that', 'these', 'those', 'it', 'its',
  'as', 'at', 'into', 'not', 'no', 'all', 'any', 'we', 'you', 'your', 'our', 'i',
  'use', 'using', 'used', 'make', 'made', 'do', 'does', 'done', 'need', 'needs',
  'should', 'must', 'can', 'will', 'please', 'task', 'work', 'then', 'than', 'if',
  'new', 'add', 'run', 'get', 'set', 'via', 'over', 'up', 'out', 'so', 'but',
  'only', 'also', 'more', 'most', 'each', 'per', 'about', 'after', 'before',
]);

const TAIL = '(Deliberate? retry once and it passes.)';

let stateRootOk;

function output(obj) {
  process.stdout.write(JSON.stringify(obj));
}

function readStdinSync() {
  try {
    const raw = readFileSync(0, 'utf8');
    if (!raw.trim()) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function nonEmpty(v) {
  return typeof v === 'string' && v.trim() !== '';
}

function sha1(s) {
  return createHash('sha1').update(s).digest('hex');
}

// ── project root / config ────────────────────────────────────────────────────

/**
 * Nearest ancestor holding a `.claude` directory; `claude` started from a
 * subdirectory reports that subdirectory as cwd while Claude Code itself resolves
 * from the repo root. Falls back to cwd unchanged.
 */
function findRoot(cwd) {
  let dir = cwd || process.env.CLAUDE_PROJECT_DIR || process.cwd();
  try {
    dir = path.resolve(dir);
  } catch {
    return null;
  }
  let cur = dir;
  for (let i = 0; i < MAX_ROOT_CLIMB; i++) {
    try {
      if (statSync(path.join(cur, '.claude')).isDirectory()) return cur;
    } catch {
      // keep climbing
    }
    const parent = path.dirname(cur);
    if (parent === cur) break;
    cur = parent;
  }
  return dir;
}

const CONFIG_BROKEN = Symbol('config-broken');

/** Absent config = defaults. Present-but-unparsable = fail open (allow, no output). */
function loadConfig(root) {
  const file = path.join(root, '.claude', 'brewtools', 'agent-router.json');
  let raw;
  try {
    raw = readFileSync(file, 'utf8');
  } catch {
    return { ...DEFAULTS };
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return CONFIG_BROKEN;
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return CONFIG_BROKEN;
  return normalizeConfig(parsed);
}

function strList(v, fallback) {
  if (!Array.isArray(v)) return fallback;
  const out = v.filter((s) => typeof s === 'string' && s.trim() !== '');
  return out.length || v.length === 0 ? out : fallback;
}

function normalizeConfig(raw) {
  const cfg = { ...DEFAULTS };
  if (raw.enabled === false) cfg.enabled = false;
  if (typeof raw.level === 'string') cfg.level = raw.level; // tier-2 switch; tier 1 ignores it
  cfg.genericTypes = strList(raw.genericTypes, DEFAULTS.genericTypes);
  cfg.neverFlag = strList(raw.neverFlag, DEFAULTS.neverFlag);
  if (Number.isFinite(raw.minScore) && raw.minScore > 0) cfg.minScore = raw.minScore;
  if (Number.isFinite(raw.margin) && raw.margin >= 0) cfg.margin = raw.margin;
  if (Array.isArray(raw.intents)) {
    cfg.intents = raw.intents.filter(
      (r) => r && typeof r === 'object' && nonEmpty(r.match) && nonEmpty(r.expert),
    );
  }
  // An intent's redirect target is by definition already the right expert - it
  // can never be something this hook itself flags, built-in table or user override.
  cfg.neverFlag = [...new Set([...cfg.neverFlag, ...cfg.intents.map((r) => r.expert)])];
  return cfg;
}

// ── roster ───────────────────────────────────────────────────────────────────

/** Lowercase, unicode-safe; punctuation collapses to single spaces. */
function normalizeText(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

/** Both fields are bounded: a multi-megabyte prompt must not turn into a regex bill. */
function taskText(toolInput) {
  const desc = typeof toolInput.description === 'string' ? toolInput.description : '';
  const prompt = typeof toolInput.prompt === 'string' ? toolInput.prompt : '';
  return `${desc.slice(0, DESC_SCAN_CHARS)}\n${prompt.slice(0, PROMPT_SCAN_CHARS)}`;
}

/** Frontmatter `name` + `description`; anything malformed yields null (file skipped). */
function parseAgentFile(file, rel) {
  let raw;
  try {
    raw = readFileSync(file, 'utf8');
  } catch {
    return null;
  }
  const m = /^---\r?\n([\s\S]*?)\r?\n---/.exec(raw);
  if (!m) return null;
  const fm = m[1];
  const nameM = /^name:\s*(.+)$/m.exec(fm);
  if (!nameM) return null;
  const name = nameM[1].trim().replace(/^["']|["']$/g, '').trim();
  if (!name) return null;
  const descM = /^description:\s*(.+)$/m.exec(fm);
  const description = descM ? descM[1].trim().replace(/^["']|["']$/g, '') : '';

  const trigM = /triggers:\s*([\s\S]*)$/i.exec(description);
  const triggers = [];
  if (trigM) {
    for (const part of trigM[1].split(/[,;]/)) {
      const t = normalizeText(part);
      if (t) triggers.push(t);
    }
  }
  const body = trigM ? description.slice(0, trigM.index) : description;
  const words = new Set(
    normalizeText(body)
      .split(' ')
      .filter((w) => w.length >= 4 && !STOPWORDS.has(w)),
  );
  return { name, rel, triggers, words: [...words] };
}

function readRoster(dir) {
  let names;
  try {
    names = readdirSync(dir);
  } catch {
    return []; // no roster dir: intents still apply, scoring simply finds nothing
  }
  const agents = [];
  for (const n of names.sort()) {
    if (!n.endsWith('.md')) continue;
    const a = parseAgentFile(path.join(dir, n), `.claude/agents/${n}`);
    if (a) agents.push(a);
  }
  return agents;
}

/** A missing dir is an EMPTY roster, not an error: intent rules still fire. */
function loadRoster(root) {
  return readRoster(path.join(root, '.claude', 'agents'));
}

// ── scoring ──────────────────────────────────────────────────────────────────

function scoreAgent(agent, textNorm, tokens) {
  let score = 0;
  for (const t of agent.triggers) {
    if (t.includes(' ')) {
      if (textNorm.includes(t)) score += 3;
    } else if (tokens.has(t)) {
      score += 2;
    }
  }
  let descHits = 0;
  for (const w of agent.words) if (tokens.has(w)) descHits++;
  score += Math.min(descHits, DESC_WORD_CAP);

  const nameNorm = normalizeText(agent.name);
  const parts = nameNorm.split(' ').filter(Boolean);
  if (nameNorm && textNorm.includes(nameNorm)) score += 4;
  else if (parts.length > 1 && parts.every((p) => tokens.has(p))) score += 2;
  return score;
}

/** Descending by score, then by name for a deterministic order. */
function rankRoster(roster, text) {
  const textNorm = normalizeText(text);
  const tokens = new Set(textNorm.split(' ').filter(Boolean));
  return roster
    .map((a) => ({ agent: a, score: scoreAgent(a, textNorm, tokens) }))
    .sort((x, y) => y.score - x.score || x.agent.name.localeCompare(y.agent.name));
}

// ── tmp state (anti-loop markers + roster cache) ─────────────────────────────

/**
 * os.tmpdir() is world-writable, so the root can be pre-created by another user as
 * a symlink. Accept it only as a real directory we own with no group/world write.
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
      chmodSync(STATE_ROOT, 0o700);
      st = lstatSync(STATE_ROOT);
    }
    stateRootOk = (st.mode & 0o077) === 0;
  } catch {
    stateRootOk = false;
  }
  return stateRootOk;
}

function writeAtomic(file, data) {
  const tmp = `${file}.${process.pid}.tmp`;
  try {
    writeFileSync(tmp, data, { mode: 0o600 });
    renameSync(tmp, file);
    return true;
  } catch {
    try {
      rmSync(tmp, { force: true });
    } catch {
      // ignore
    }
    return false;
  }
}

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
      const st = lstatSync(p); // lstat, never stat: do not follow a planted symlink
      if (!st.isDirectory()) {
        if (st.mtimeMs < cutoff) rmSync(p, { force: true }); // unlink the link itself
        continue;
      }
      if (UID !== null && st.uid !== UID) continue;
      if (st.mtimeMs >= cutoff) continue;
      rmSync(p, { recursive: true, force: true });
    } catch {
      // ignore individual entries
    }
  }
}

function safeSegment(s) {
  const raw = typeof s === 'number' && Number.isFinite(s) ? String(s) : s;
  if (typeof raw !== 'string') return null;
  const clean = raw.replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 128);
  return clean && clean !== '.' && clean !== '..' ? clean : null;
}

/**
 * True the FIRST time this (session, root, task) is seen; false afterwards, and false
 * whenever the marker cannot be persisted - a deny we cannot record is a deny that
 * could loop, so it degrades to a nudge instead.
 */
function claimDeny(sessionId, root, text) {
  if (!ensureStateRoot()) return false;
  const session = safeSegment(sessionId) || 'nosession';
  const dir = path.join(STATE_ROOT, session);
  const marker = path.join(dir, sha1(`${root}\0${normalizeText(text)}`).slice(0, 32));
  try {
    lstatSync(marker);
    return false; // already denied once
  } catch {
    // not yet marked
  }
  pruneStale();
  try {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
  } catch {
    return false;
  }
  return writeAtomic(marker, `${Date.now()}`);
}

// ── message builders ─────────────────────────────────────────────────────────

function rosterDenyReason(agent, picked) {
  return (
    `agent-router: '${agent.name}' (${agent.rel}) matches this task better than ${picked}` +
    ` - retry with subagent_type: ${agent.name}. ${TAIL}`
  );
}

function intentPluginDenyReason(intent, picked) {
  return (
    `agent-router: this looks like ${intent.label} - '${intent.expert}' is the expert for it,` +
    ` not ${picked} - retry with subagent_type: ${intent.expert}. ${TAIL}`
  );
}

function intentProjectDenyReason(intent, agent, picked) {
  return (
    `agent-router: this looks like ${intent.label} - '${agent.name}' (${agent.rel}) is the project` +
    ` expert for it, not ${picked} - retry with subagent_type: ${agent.name}. ${TAIL}`
  );
}

/** Also the wording when the anti-loop marker could not be written - do not claim a cause. */
function repeatContext(expert, picked) {
  return (
    `agent-router: '${expert}' still looks like a better fit than ${picked} for this task,` +
    ' but this spawn is not being blocked (already flagged once, or the anti-loop marker' +
    ' could not be recorded) - proceeding as requested.'
  );
}

function nudgeContext(candidates, picked) {
  const list = candidates.map((c) => `${c.agent.name} (${c.agent.rel})`).join('; ');
  return (
    `agent-router: project agents that may fit this task better than ${picked}: ${list}.` +
    ` Consider re-spawning with one of them; proceeding with ${picked}.`
  );
}

function emitDeny(reason, expert, picked, sessionId, root, text) {
  if (claimDeny(sessionId, root, text)) {
    output({
      hookSpecificOutput: {
        hookEventName: EVENT,
        permissionDecision: 'deny',
        permissionDecisionReason: reason,
      },
    });
    return;
  }
  output({
    hookSpecificOutput: { hookEventName: EVENT, additionalContext: repeatContext(expert, picked) },
  });
}

function emitContext(text) {
  output({ hookSpecificOutput: { hookEventName: EVENT, additionalContext: text } });
}

// ── main ─────────────────────────────────────────────────────────────────────

function matchIntent(intents, text) {
  for (const rule of intents) {
    let re;
    try {
      re = new RegExp(rule.match, 'i');
    } catch {
      continue; // a bad user regex must not disable the rest of the table
    }
    if (re.test(text)) {
      return { label: rule.label || 'specialist work', expert: rule.expert, match: rule.match };
    }
  }
  return null;
}

function main() {
  const input = readStdinSync();

  // 1 - not an Agent spawn (`Task` is an alias that normalizes to `Agent`).
  if (input.tool_name !== 'Agent') return;
  // 2 - a subagent issued this call; only the main loop is policed.
  if (nonEmpty(input.agent_id)) return;

  const root = findRoot(input.cwd);
  if (!root) return;

  // 3 - disabled, or a config file that exists but cannot be trusted.
  const cfg = loadConfig(root);
  if (cfg === CONFIG_BROKEN || cfg.enabled === false) return;

  const ti = input.tool_input && typeof input.tool_input === 'object' ? input.tool_input : {};
  const picked = typeof ti.subagent_type === 'string' ? ti.subagent_type.trim() : '';
  if (!picked) return;

  const roster = loadRoster(root);
  // 4 - the model already picked a project expert.
  if (roster.some((a) => a.name === picked)) return;
  // 5 - a plugin specialist / built-in, or explicitly never flagged.
  if (cfg.neverFlag.includes(picked)) return;
  if (!cfg.genericTypes.includes(picked)) return;

  const text = taskText(ti);
  if (!text.trim()) return;

  const ranked = rankRoster(roster, text);
  const best = ranked[0];
  const runnerUp = ranked[1];

  // 6 - deterministic intent rules; a project agent covering the same intent wins.
  const intent = matchIntent(cfg.intents, text);
  if (intent && intent.expert !== picked) {
    if (best && best.score >= cfg.minScore) {
      emitDeny(
        intentProjectDenyReason(intent, best.agent, picked),
        best.agent.name,
        picked,
        input.session_id,
        root,
        text,
      );
      return;
    }
    emitDeny(
      intentPluginDenyReason(intent, picked),
      intent.expert,
      picked,
      input.session_id,
      root,
      text,
    );
    return;
  }

  // 7 - roster scoring.
  if (!best || best.score <= 0) return;
  const clearWinner =
    best.score >= cfg.minScore && best.score - (runnerUp ? runnerUp.score : 0) >= cfg.margin;
  if (clearWinner) {
    emitDeny(
      rosterDenyReason(best.agent, picked),
      best.agent.name,
      picked,
      input.session_id,
      root,
      text,
    );
    return;
  }
  const nudgeFloor = Math.max(1, Math.ceil(cfg.minScore / 2));
  if (best.score >= nudgeFloor) {
    emitContext(nudgeContext(ranked.filter((c) => c.score > 0).slice(0, 3), picked));
  }
}

try {
  main();
} catch {
  // 9 - fail open: no stdout, no stack trace, exit 0.
}
