#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewdoc:docsync-setup
/**
 * docsync-watch — PostToolUse:Read hook (self-contained, project-local)
 *
 * When a tracked .md file is read: record it in the session touched-set. Silent
 * BY DESIGN — a Read fires constantly and mid-turn context injection on every one
 * would be noise. A read-only doc with no `last_updated` still produces a signal:
 * the Stop gate reports undated touched docs alongside stale ones.
 *
 * SELF-CONTAINED: helpers inlined, Node built-ins only, pure ESM. Reads project
 * state from <cwd>/.claude/docsync/ at runtime. Never throws, always exits 0.
 */
import { readFileSync, writeFileSync, mkdirSync, renameSync } from 'fs';
import { join, relative, isAbsolute, dirname } from 'path';

// --- inlined helpers -------------------------------------------------------
async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString('utf8');
  try { return JSON.parse(raw); } catch { return {}; }
}
function output(r) { try { console.log(JSON.stringify(r)); } catch { console.log('{}'); } }
function readJson(p, fb) { try { return JSON.parse(readFileSync(p, 'utf8')); } catch { return fb; } }
function writeJsonAtomic(p, o) {
  try {
    mkdirSync(dirname(p), { recursive: true });
    const tmp = p + '.tmp';
    writeFileSync(tmp, JSON.stringify(o, null, 2));
    renameSync(tmp, p);
  } catch {}
}
function statePath(cwd) { return join(cwd, '.claude', 'docsync', 'state.json'); }

function loadConfig(cwd) {
  const c = readJson(join(cwd, '.claude', 'docsync', 'config.json'), {});
  return {
    // `disable` flips this to false and leaves everything else in place. Absent = on,
    // so a config written before the toggle existed keeps working.
    enabled: c.enabled !== false,
    threshold_days: Number.isInteger(c.threshold_days) && c.threshold_days > 0 ? c.threshold_days : 7,
    exclude: Array.isArray(c.exclude) ? c.exclude : []
  };
}
// Re-read + union at write time so concurrent track/watch never drop entries.
function recordTouched(cwd, sessionId, rel) {
  const disk = readJson(statePath(cwd), null);
  const st = (!disk || disk.session_id !== sessionId) ? { session_id: sessionId, touched: [], asked: false } : disk;
  if (!Array.isArray(st.touched)) st.touched = [];
  if (st.touched.includes(rel)) return; // already recorded, skip write
  st.touched.push(rel);
  writeJsonAtomic(statePath(cwd), st);
}

function globToRegex(g) {
  let re = '';
  for (let i = 0; i < g.length; i++) {
    const c = g[i];
    if (c === '*') {
      if (g[i + 1] === '*') { i++; if (g[i + 1] === '/') { re += '(?:.*/)?'; i++; } else re += '.*'; }
      else re += '[^/]*';
    } else if (c === '?') re += '[^/]';
    else if ('.+^${}()|[]\\'.includes(c)) re += '\\' + c;
    else re += c;
  }
  return new RegExp('^' + re + '$');
}
function isExcluded(rel, globs) {
  return globs.some(g => { try { return globToRegex(g).test(rel); } catch { return false; } });
}
function parseFm(abs) {
  try {
    const txt = readFileSync(abs, 'utf8').replace(/^﻿/, ''); // strip UTF-8 BOM
    if (!txt.startsWith('---')) return { present: false, fields: {} };
    const m = txt.match(/^---\r?\n([\s\S]*?)\r?\n---/);
    if (!m) return { present: false, fields: {} };
    const fields = {};
    for (const line of m[1].split(/\r?\n/)) {
      const mm = line.match(/^\s*([A-Za-z_][A-Za-z0-9_-]*)\s*:\s*(.*)$/);
      if (!mm) continue;
      let v = mm[2].trim();
      if (v[0] === '"' || v[0] === "'") v = v.replace(/^["']|["']$/g, '');
      else v = v.replace(/\s+#.*$/, '').trim();
      fields[mm[1]] = v;
    }
    return { present: true, fields };
  } catch { return { present: false, fields: {} }; }
}
function relOf(cwd, fp) {
  const abs = isAbsolute(fp) ? fp : join(cwd, fp);
  return relative(cwd, abs);
}
// doc_type default: absent or unrecognized => 'user'. Only 'skip' removes a file from scope.
function docTypeOf(fields) {
  const v = String(fields.doc_type || '').trim().toLowerCase();
  return (v === 'llm' || v === 'user' || v === 'skip') ? v : 'user';
}
function isTracked(cwd, fp, cfg) {
  if (!fp || !fp.endsWith('.md')) return false;
  const rel = relOf(cwd, fp);
  if (!rel || rel.startsWith('..') || isAbsolute(rel)) return false;
  if (isExcluded(rel, cfg.exclude)) return false;
  if (docTypeOf(parseFm(join(cwd, rel)).fields) === 'skip') return false;
  return true;
}
// ---------------------------------------------------------------------------

async function main() {
  try {
    const input = await readStdin();
    const cwd = input.cwd || process.cwd();
    const sessionId = input.session_id;
    const fp = input.tool_input && input.tool_input.file_path;

    const cfg = loadConfig(cwd);
    if (!cfg.enabled) { output({}); return; } // `disable`: registered but inert
    if (isTracked(cwd, fp, cfg)) recordTouched(cwd, sessionId, relOf(cwd, fp));
    output({});
  } catch (err) {
    try { console.error(`[docsync-watch] ${err.message}`); } catch {}
    output({});
  }
}

main();
