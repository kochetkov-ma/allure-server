#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewdoc:docsync-setup
/**
 * docsync-track — PostToolUse:Write|Edit|MultiEdit hook (self-contained, project-local)
 *
 * When a tracked .md file is written/edited: record it in the session touched-set
 * and, if it lacks docsync frontmatter (`last_updated`), nudge Claude to add it.
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
// Atomic write: temp file + rename, so a crash never leaves a half-written state.
function writeJsonAtomic(p, o) {
  try {
    mkdirSync(dirname(p), { recursive: true });
    const tmp = p + '.tmp';
    writeFileSync(tmp, JSON.stringify(o, null, 2));
    renameSync(tmp, p);
  } catch {}
}
// Local-time YYYY-MM-DD (matches `date +%F` used by the sync flow).
function today() {
  const d = new Date(), p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
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
// Re-read from disk at write time and union, so concurrent track/watch calls never
// drop each other's entries. Reset touched/asked when the session id changes.
function recordTouched(cwd, sessionId, rel) {
  const disk = readJson(statePath(cwd), null);
  const st = (!disk || disk.session_id !== sessionId) ? { session_id: sessionId, touched: [], asked: false } : disk;
  if (!Array.isArray(st.touched)) st.touched = [];
  if (st.touched.includes(rel)) return; // already recorded, skip write
  st.touched.push(rel);
  writeJsonAtomic(statePath(cwd), st);
}

// Minimal glob -> RegExp: supports **/ (any dir prefix), **, *, ?.
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
      else v = v.replace(/\s+#.*$/, '').trim(); // drop trailing comment on unquoted scalar
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
// A path is tracked when it is an in-project .md, not excluded, not doc_type: skip.
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
    if (!isTracked(cwd, fp, cfg)) { output({}); return; }

    const rel = relOf(cwd, fp);
    recordTouched(cwd, sessionId, rel);

    const fm = parseFm(join(cwd, rel));
    if (!fm.present || !fm.fields.last_updated) {
      output({
        hookSpecificOutput: {
          hookEventName: 'PostToolUse',
          additionalContext: `docsync: \`${rel}\` has no \`last_updated\` frontmatter. Add YAML frontmatter with \`last_updated: "${today()}"\` (quoted — unquoted a real YAML parser types it as a Date), optionally \`doc_type: llm|user|skip\` (bare, unquoted; absent = user) and \`sync_procedure: "..."\`, so staleness tracking works.`
        }
      });
      return;
    }
    output({});
  } catch (err) {
    try { console.error(`[docsync-track] ${err.message}`); } catch {}
    output({});
  }
}

main();
