// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:manager-setup
// brewtools:manager-setup — Manager mode state resolver/writer.
// State shape: { hard:boolean, level:'strict'|'balanced', mode:'full' }
//   + artifact metadata written by writeState: version/generated_by/last_updated.
//   hard  — HARD wall toggle (PreToolUse guard physically denies main-session tools)
//   level — HARD wall strictness: 'strict' (deny all non-read) | 'balanced' (allow read-only bash/search)
//   metadata — stamped on WRITE only. DEFAULT_STATE deliberately carries no version:
//           it is the answer for "no state file exists", and a version there would
//           claim provenance for a file nothing ever stamped. Same reason a write that
//           cannot resolve the version OMITS the key rather than stamping 'unknown' —
//           see pluginVersion() and its call site in writeState.
//   mode  — vestigial informational field, ALWAYS 'full'. No user action sets it;
//           kept so status/readers of state.mode keep working. planmode is NOT a stored
//           mode — ++m derives it at runtime from permission_mode === 'plan'; planmode
//           stays resolvable in manager-prompts.mjs only.
// project: <cwd>/.claude/brewtools/manager/state.json
// global:  ~/.claude/manager/state.json  (protected for Write tool — only writable here)
// resolveState: hard + level are PROJECT-ONLY (a global state.json must NOT enable the
//   wall in projects lacking their own state.json); mode resolves project -> global -> default.
// Atomic write: lockfile O_CREAT|O_EXCL + stale detection, tmp + rename.

// CLI (see bottom of file): `node manager-state.mjs get|set hard=false|level=strict [--cwd DIR]`.
// This entrypoint is the ONLY Bash command the HARD wall guard self-exempts, so it must stay
// argument-strict: unknown keys/values/flags exit non-zero without writing anything.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { pathToFileURL } from 'node:url';

const DEFAULT_STATE = { hard: false, level: 'balanced', mode: 'full' };
const VALID_SCOPES = new Set(['project', 'global']);

const GENERATED_BY = 'brewtools:manager-setup';

/**
 * Today's date in LOCAL time, `YYYY-MM-DD` — the spec mandates `date +%F`, which is local.
 * `toISOString()` is UTC and would stamp tomorrow's (or yesterday's) date depending on the
 * offset, and this value feeds staleness comparison.
 * @returns {string}
 */
function localDate() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Version of the manager-setup that owns THIS copy of the module, by self-location.
 * Plugin layout (hooks/lib/) -> ../../.claude-plugin/plugin.json.
 * A copy installed into a project has no plugin.json above it, so it falls back to
 * its own baked `brewcode-meta` stamp — which is the version it was copied at.
 * Never a literal: the state file must record the version that actually wrote it.
 *
 * Returns `null`, NEVER the string 'unknown', when both carriers are unreadable.
 * `unknown` is not a version — `sort -V` accepts it and the `PLACEHLD` character test
 * does not catch it, so a consumer would report a confident verdict on a resolver
 * failure. `null` is an internal sentinel that never leaves this module: `writeState`
 * OMITS the `version` key instead of stamping it (see the call site).
 *
 * It returns rather than throws on purpose. This module is the HARD wall's off-switch
 * (`set hard=false`) and the single Bash shape the guard self-exempts; a writer that
 * aborted here would leave the user behind an armed wall with no exit. The other four
 * writers in this repo abort because they are generators — nothing is armed when they
 * refuse. Both hooks (`session-start.mjs`, `manager-prompt.mjs`) import only
 * `resolveState`, so no hook path reaches this function at all.
 * @returns {string|null} semver, or null when unresolvable
 */
function pluginVersion() {
  const here = path.dirname(new URL(import.meta.url).pathname);
  try {
    const pkg = JSON.parse(fs.readFileSync(path.join(here, '..', '..', '.claude-plugin', 'plugin.json'), 'utf8'));
    if (pkg && /^\d+\.\d+\.\d+/.test(pkg.version)) return pkg.version;
  } catch {}
  try {
    const first = fs.readFileSync(new URL(import.meta.url), 'utf8').split('\n', 1)[0];
    const m = /brewcode-meta: version=(\d+\.\d+\.\d+)/.exec(first);
    if (m) return m[1];
  } catch {}
  return null;
}

/**
 * content_version of THIS module — the last release in which its generator/writer logic
 * actually changed, by self-location. Unlike pluginVersion(), there is no plugin.json
 * field for this: the own-header `brewcode-meta` stamp is the only carrier, so this
 * skips the plugin.json attempt entirely and reads straight from the file's own first line.
 * Returns `null`, NEVER 'unknown', when the header carries no `content_version=` token —
 * writeState OMITS the key on null, mirroring pluginVersion()'s call site.
 * @returns {string|null} semver, or null when unresolvable
 */
function resolveContentVersion() {
  try {
    const first = fs.readFileSync(new URL(import.meta.url), 'utf8').split('\n', 1)[0];
    const m = /content_version=(\d+\.\d+\.\d+)/.exec(first);
    if (m) return m[1];
  } catch {}
  return null;
}

function resolveHome(p) {
  if (!p) return p;
  if (p === '~') return process.env.HOME || os.homedir();
  if (p.startsWith('~/')) return path.join(process.env.HOME || os.homedir(), p.slice(2));
  return p;
}

/**
 * Resolve state.json path for a scope.
 * @param {string} scope - 'project' | 'global'
 * @param {string} cwd - project root for project scope
 * @returns {string} absolute state file path
 */
export function resolveStatePath(scope, cwd = process.cwd()) {
  if (!VALID_SCOPES.has(scope)) {
    throw new Error(`invalid scope '${scope}' — must be one of: ${[...VALID_SCOPES].join(', ')}`);
  }
  if (scope === 'global') return resolveHome('~/.claude/manager/state.json');
  return path.join(cwd, '.claude', 'brewtools', 'manager', 'state.json');
}

function clampMode(merged) {
  // mode is vestigial — always 'full'. planmode is hook-internal (driven by
  // permission_mode in manager-prompt.mjs), never stored as state.mode.
  if (merged.mode !== 'full') merged.mode = 'full';
  return merged;
}

function clampLevel(merged) {
  if (!['strict', 'balanced'].includes(merged.level)) merged.level = 'balanced';
  return merged;
}

function readJsonSafe(filePath) {
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    return (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Resolve effective Manager state.
 * SECURITY: `hard` and `level` come ONLY from the PROJECT state.json — a global
 * state.json must never enable the HARD wall in projects without their own state.
 * `mode` resolves project -> global -> default.
 * @param {string} cwd
 * @returns {{hard:boolean, level:string, mode:string, source:'project'|'global'|'default'}}
 */
export function resolveState(cwd = process.cwd()) {
  try {
    const project = readJsonSafe(resolveStatePath('project', cwd));
    const global  = readJsonSafe(resolveStatePath('global', cwd));
    const hard  = (project && typeof project.hard === 'boolean') ? project.hard : DEFAULT_STATE.hard;
    const level = (project && project.level) ? project.level : DEFAULT_STATE.level;
    const mode  = (project && project.mode) ?? (global && global.mode) ?? DEFAULT_STATE.mode;
    const source = project ? 'project' : (global ? 'global' : 'default');
    // Unknown keys of the PROJECT file (version/generated_by/last_updated and anything
    // a future release adds) pass through untouched. Nothing is invented: a file that
    // carries no version resolves without one, so a stale state stays visibly stale.
    const resolved = clampLevel(clampMode({ ...(project || {}), hard, level, mode, source }));
    // Legacy `doc_type` from a pre-spec write is dropped on READ too, not only on write:
    // otherwise every consumer sees it until the next write happens to occur.
    delete resolved.doc_type;
    return resolved;
  } catch {
    return { ...DEFAULT_STATE, source: 'default' };
  }
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

async function acquireLock(lockPath, { retries = 5, delayMs = 100 } = {}) {
  for (let i = 0; i < retries; i++) {
    const token = `${process.pid}:${crypto.randomBytes(8).toString('hex')}`;
    try {
      const fd = fs.openSync(lockPath, fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY, 0o600);
      fs.writeSync(fd, token);
      fs.closeSync(fd);
      return token;
    } catch (e) {
      if (e.code !== 'EEXIST') throw e;
      try {
        const lockContent = fs.readFileSync(lockPath, 'utf8').trim();
        const lockPid = parseInt(lockContent.split(':')[0], 10);
        const ageMs = Date.now() - fs.statSync(lockPath).mtimeMs;
        let stale = false;
        if (!Number.isFinite(lockPid) || lockPid <= 0) {
          stale = true;
        } else {
          try { process.kill(lockPid, 0); } catch (killErr) { if (killErr.code === 'ESRCH') stale = true; }
        }
        if (!stale && ageMs > 60_000) stale = true;
        if (stale) { try { fs.unlinkSync(lockPath); } catch {} continue; }
      } catch {
        // lockfile vanished between EEXIST and stat — race, retry
      }
      if (i === retries - 1) {
        throw new Error(`Could not acquire lock ${lockPath} after ${retries} attempts. Remove it manually if stale.`);
      }
      await sleep(delayMs);
    }
  }
  return null;
}

function releaseLock(lockPath, token) {
  if (!token) return;
  try {
    const current = fs.readFileSync(lockPath, 'utf8').trim();
    if (token && current !== token) {
      process.stderr.write(`[manager-state] lock stolen at ${lockPath}. Not unlinking.\n`);
      return;
    }
    fs.unlinkSync(lockPath);
  } catch {}
}

function writeAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp.${process.pid}.${crypto.randomBytes(4).toString('hex')}`;
  try {
    fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n', { encoding: 'utf8', mode: 0o600 });
    fs.renameSync(tmp, filePath);
  } catch (e) {
    try { fs.unlinkSync(tmp); } catch {}
    throw e;
  }
}

/**
 * Write (merge) Manager state for a scope, atomically.
 * @param {string} scope - 'project' | 'global'
 * @param {object} partial - fields to merge (e.g. { hard:false } or { level:'strict' })
 * @param {string} cwd
 * @returns {{file:string, action:'written', state:object}}
 */
export async function writeState(scope, partial, cwd = process.cwd()) {
  const filePath = resolveStatePath(scope, cwd);
  const lockPath = `${filePath}.lock`;
  fs.mkdirSync(path.dirname(filePath), { recursive: true });

  const token = await acquireLock(lockPath);
  if (!token) throw new Error('could not acquire lock');
  try {
    const existing = readJsonSafe(filePath) || {};
    // Provenance is stamped by the WRITER, never by a reader/merge: the version is the
    // one of the module doing this write, so setup-status reading the raw file sees the
    // real age of the state. No `doc_type`: it is a frontmatter-only field, and JSON
    // carriers never take it — state.json is machine state, not a doc, either way.
    const version = pluginVersion();
    const contentVersion = resolveContentVersion();
    const merged = {
      ...existing,
      ...partial,
      generated_by: GENERATED_BY,
      last_updated: localDate()
    };
    if (version) merged.version = version;
    else {
      // Unresolvable version: DROP the key rather than stamp a fake one. An absent
      // `version` is a documented reader path — setup-status row 8 treats it as the
      // `missing` signal and falls through to the copied guard's `brewcode-meta` line,
      // and manager-setup `status` computes `stale: (stateVersion && pluginVersion) ?
      // ... : null`, so it is never compared as if it were a real version. Deleting a
      // stale inherited key matters: merging over an older state must not let this
      // write keep claiming that older file's version as its own.
      delete merged.version;
      process.stderr.write('[manager-state] plugin version unresolvable — wrote state without a version key\n');
    }
    if (contentVersion) merged.content_version = contentVersion;
    else {
      // Same drop-not-fake rule as version above, for the same reason: the own-header
      // stamp is the only carrier, and until a release run adds `content_version=` to
      // this file's header, resolution legitimately fails — omit rather than invent.
      delete merged.content_version;
      process.stderr.write('[manager-state] content_version unresolvable — wrote state without a content_version key\n');
    }
    delete merged.doc_type; // legacy key from pre-spec writes; JSON carriers never take it
    writeAtomic(filePath, merged);
    return { file: filePath, action: 'written', state: merged };
  } finally {
    releaseLock(lockPath, token);
  }
}

// ---- CLI ---------------------------------------------------------------------
// `node <path>/manager-state.mjs set hard=false` is the documented off-switch for the
// HARD wall and the single Bash shape the guard self-exempts. Keep parsing strict.

const CLI_USAGE = 'usage: node manager-state.mjs get [--cwd DIR] | set hard=<true|false> level=<strict|balanced> [--cwd DIR]';

const SETTABLE = {
  hard: v => (v === 'true' ? true : v === 'false' ? false : undefined),
  level: v => (v === 'strict' || v === 'balanced' ? v : undefined)
};

/**
 * Parse strict CLI argv (everything after the script path).
 * @param {string[]} argv
 * @returns {{command:'get'|'set', patch:object, cwd:string}}
 */
export function parseCliArgs(argv) {
  const command = argv[0];
  if (command !== 'get' && command !== 'set') throw new Error(`unknown command '${command ?? ''}'`);

  const patch = {};
  let cwd = process.cwd();

  for (let i = 1; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--cwd') {
      const next = argv[++i];
      if (!next) throw new Error('--cwd requires a directory');
      cwd = path.resolve(resolveHome(next));
      continue;
    }
    if (arg.startsWith('-')) throw new Error(`unknown flag '${arg}'`);
    if (command !== 'set') throw new Error(`'get' takes no key=value pairs (got '${arg}')`);

    const eq = arg.indexOf('=');
    if (eq < 1) throw new Error(`expected key=value, got '${arg}'`);
    const key = arg.slice(0, eq);
    const raw = arg.slice(eq + 1);
    if (!Object.hasOwn(SETTABLE, key)) throw new Error(`unknown key '${key}' — settable: ${Object.keys(SETTABLE).join(', ')}`);
    const value = SETTABLE[key](raw);
    if (value === undefined) throw new Error(`invalid value '${raw}' for '${key}'`);
    patch[key] = value;
  }

  if (command === 'set' && Object.keys(patch).length === 0) throw new Error('set requires at least one key=value');
  return { command, patch, cwd };
}

async function runCli(argv) {
  let parsed;
  try {
    parsed = parseCliArgs(argv);
  } catch (e) {
    process.stderr.write(`manager-state: ${e.message}\n${CLI_USAGE}\n`);
    process.exitCode = 2;
    return;
  }
  try {
    if (parsed.command === 'get') {
      process.stdout.write(JSON.stringify(resolveState(parsed.cwd)) + '\n');
      return;
    }
    // `hard`/`level` are PROJECT-scope only — there is no global wall.
    const r = await writeState('project', parsed.patch, parsed.cwd);
    process.stdout.write(JSON.stringify(r) + '\n');
  } catch (e) {
    process.stderr.write(`manager-state: ${e.message}\n`);
    process.exitCode = 1;
  }
}

// argv[1] must be realpath'd before comparing: Node resolves ESM specifiers through
// symlinks, so on a path like /var/... -> /private/var/... the raw argv URL never matches
// import.meta.url and the CLI silently no-ops with exit 0 — i.e. the documented HARD-wall
// off-switch `set hard=false` would appear to succeed while writing nothing.
function invokedDirectly() {
  if (!process.argv[1]) return false;
  try {
    return import.meta.url === pathToFileURL(fs.realpathSync(process.argv[1])).href;
  } catch {
    return import.meta.url === pathToFileURL(process.argv[1]).href;
  }
}

if (invokedDirectly()) {
  await runCli(process.argv.slice(2));
}
