#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:agent-return-setup
/**
 * agent-return-budget — shared config, thresholds and return contract
 * (Node built-ins only). Never invoked directly; imported by the pair.
 *
 * Single source of truth:
 *   agent-return-contract.mjs (SubagentStart) states the contract,
 *   agent-return-guard.mjs    (SubagentStop)  enforces the same numbers.
 * Both import from here so the announced budget and the enforced budget can
 * never drift apart.
 *
 * Sizing is a length heuristic, NOT a tokenizer: the measured distribution the
 * thresholds were derived from (80 real subagent returns) was computed with
 * chars/4, so anything more accurate would move the boundaries off the data.
 * Moving away from chars/4 means re-fitting PASS/FILE in the same change.
 *
 * ENABLE GATE: `enabled` must be exactly `true` in the config; a missing config
 * file means the feature is OFF. Same contract as agent-deadline.
 *
 * THRESHOLD PRECEDENCE, per threshold, first hit wins:
 *   1. config key   `passTokens` / `fileTokens`   (positive integer only)
 *   2. env var      AGENT_RETURN_PASS / AGENT_RETURN_FILE   (via intEnv)
 *   3. built-in     1000 / 2500
 *
 * Everything resolves ONCE at module load, not per call: a hook process lives
 * for exactly one invocation, so a per-call resolver would buy nothing and
 * would force RETURN_CONTRACT to become a builder that both hooks could call
 * with different inputs — the one drift this module exists to prevent.
 * Config discovery therefore keys on process.cwd() (the dir Claude Code spawns
 * the hook in) rather than the payload `cwd`, which the SubagentStart hook
 * never reads.
 */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const MAX_CONFIG_CLIMB = 16;
const CONFIG_NAME = 'agent-return.json';

/** ~4 chars per token. Deliberately crude; see the module note. */
export function estimateTokens(text) {
  return Math.ceil(text.length / 4);
}

/**
 * Positive integer from env, anything else -> fallback. Number(), not
 * parseInt(): parseInt("1.7") is 1 and parseInt("12abc") is 12, so a typo'd
 * threshold used to be accepted as a plausible number instead of ignored.
 */
function intEnv(name, fallback) {
  const raw = process.env[name];
  if (typeof raw !== 'string' || !raw.trim()) return fallback;
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : fallback;
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
 * root, so a root-level config used to be invisible. A malformed project file
 * is not an object and is skipped, so the global config takes over.
 */
function loadConfig() {
  let dir = process.cwd() || process.env.CLAUDE_PROJECT_DIR;
  if (dir) {
    dir = path.resolve(dir);
    for (let i = 0; i < MAX_CONFIG_CLIMB; i++) {
      const cfg = readJson(path.join(dir, '.claude', CONFIG_NAME));
      // An array is a JSON object to typeof; accepting one froze the walk on a file
      // that can never carry `enabled`, silently disabling the feature.
      if (cfg && typeof cfg === 'object' && !Array.isArray(cfg)) return cfg;
      const parent = path.dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
  }
  try {
    const cfg = readJson(path.join(os.homedir(), '.claude', CONFIG_NAME));
    if (cfg && typeof cfg === 'object' && !Array.isArray(cfg)) return cfg;
  } catch {
    // homedir unavailable -> project config only
  }
  return null;
}

/** Config key > env var > built-in default. Validation lives here, not in intEnv. */
function threshold(cfg, key, envName, fallback) {
  const v = cfg ? cfg[key] : undefined;
  if (Number.isInteger(v) && v > 0) return v;
  return intEnv(envName, fallback);
}

let CONFIG = null;
try {
  CONFIG = loadConfig();
} catch {
  CONFIG = null; // unreadable filesystem -> feature off, never a throw at import
}

/** Exactly `true` enables; no config file at all = disabled. */
export const ENABLED = !!CONFIG && CONFIG.enabled === true;
/** Grace line: p25 of real returns (761) already sits under it. */
export const PASS = threshold(CONFIG, 'passTokens', 'AGENT_RETURN_PASS', 1000);
/** Past this, compression is not enough — the detail belongs in a file (~p78). */
export const FILE = threshold(CONFIG, 'fileTokens', 'AGENT_RETURN_FILE', 2500);

/**
 * Injected verbatim into every subagent at SubagentStart. Mirrors the return
 * section of the project's agent protocol on purpose — one shape, stated at the
 * moment it applies instead of buried at the top of context.
 */
export const RETURN_CONTRACT =
  `RETURN CONTRACT (agent-return guard, mechanical): Verdict first, <=30 lines, \`path:line\`. ` +
  `!=bodies/output/log/preamble. Over ~${PASS} tokens your return is blocked for compression; ` +
  `over ~${FILE} write the detail to \`.claude/reports/YYYYMMDD-HHMMSS_<name>/\` and return ` +
  `that path + verdict + <=3 lines.`;
