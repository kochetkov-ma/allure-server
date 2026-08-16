#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:agent-deadline-setup
/**
 * agent-deadline — SubagentStop hook (self-contained, Node built-ins only).
 *
 * Drops the state file of the subagent that just finished, and the session dir
 * once it is empty. Also prunes session dirs older than ~1 day, so the tmp tree
 * stays self-cleaning even if a session dies without firing SubagentStop.
 *
 * SubagentStop carries `agent_id` + `agent_type` (verified on CC 2.1.220).
 *
 * os.tmpdir() is shared on Linux/CI, so nothing here follows a symlink: every
 * candidate is lstat-ed and skipped unless it is a real entry we own. Otherwise
 * a symlink planted at the state root turns this pruner into `rm -rf` on
 * whatever it points at.
 *
 * Fail-open: any error -> `{}` on stdout, exit 0. Never blocks a stop.
 */
import { readFileSync, readdirSync, lstatSync, rmSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const STATE_ROOT = path.join(os.tmpdir(), 'brewtools-agent-deadline');
const STALE_MS = 24 * 60 * 60 * 1000;
const UID = typeof process.getuid === 'function' ? process.getuid() : null;

function output(obj) {
  process.stdout.write(JSON.stringify(obj));
}

function safeSegment(s) {
  const raw = typeof s === 'number' && Number.isFinite(s) ? String(s) : s;
  if (typeof raw !== 'string') return null;
  const clean = raw.replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 128);
  return clean && clean !== '.' && clean !== '..' ? clean : null;
}

/** True only for a real directory owned by this user (never a symlink). */
function ownedDir(p) {
  try {
    const st = lstatSync(p);
    return st.isDirectory() && (UID === null || st.uid === UID);
  } catch {
    return false;
  }
}

function pruneStale() {
  // readdir would happily follow a symlinked root and we would then prune the
  // victim's entries; refuse anything that is not a real directory we own.
  if (!ownedDir(STATE_ROOT)) return;
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

function main() {
  let input = {};
  try {
    const raw = readFileSync(0, 'utf8');
    if (raw.trim()) input = JSON.parse(raw) || {};
  } catch {
    // unreadable payload -> still worth pruning
  }

  const agentId = safeSegment(input.agent_id);
  const sessionId = safeSegment(input.session_id) || 'nosession';

  const dir = path.join(STATE_ROOT, sessionId);
  if (agentId && ownedDir(dir)) {
    try {
      rmSync(path.join(dir, `${agentId}.json`), { force: true });
      // guard writes are temp-file + rename; drop any temp left by a killed hook
      for (const name of readdirSync(dir)) {
        if (name.startsWith(`${agentId}.json.`) && name.endsWith('.tmp')) {
          rmSync(path.join(dir, name), { force: true });
        }
      }
      if (readdirSync(dir).length === 0) rmSync(dir, { recursive: true, force: true });
    } catch {
      // ignore
    }
  }

  pruneStale();
  output({});
}

try {
  main();
} catch {
  output({});
}
