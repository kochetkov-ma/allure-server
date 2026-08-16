#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewtools:agent-return-setup
/**
 * agent-return-guard — SubagentStop hook (Node built-ins only, no I/O but stdin/stdout
 * plus the config read done by agent-return-budget.mjs).
 *
 * Subagent returns are the largest single context cost in a manager session.
 * Prose rules ("verdict first, <=30 lines, path:line") are loaded at the top of
 * the agent's context and lose to whatever the agent just did. This hook
 * restates the rule as a number comparison at the only moment it bites: the
 * return itself.
 *
 * No LLM judge anywhere. Size the final message, compare against two integers,
 * order a rewrite or say nothing:
 *   <= PASS          pass
 *   PASS..FILE       block -> compress and re-send
 *   > FILE           block -> persist detail under .claude/reports/, return path
 *
 * Blocks AT MOST ONCE per agent: `stop_hook_active` is the loop brake and is
 * checked before anything else. A second pass is how a Stop hook wedges an agent.
 * Consequence, accepted: one compress round may land slightly over PASS.
 *
 * Config gate: `.claude/agent-return.json` with `enabled: true` (project wins,
 * then global). No config = no-op. Thresholds: config `passTokens`/`fileTokens`
 * > env AGENT_RETURN_PASS/AGENT_RETURN_FILE > 1000/2500.
 *
 * Fail-open: malformed JSON, missing stdin, unexpected shape, any *runtime*
 * throw -> `{}` on stdout, exit 0. Never exit 2. A broken guard must cost
 * nothing. The one failure the try/catch cannot swallow is a missing sibling
 * agent-return-budget.mjs: ESM resolution runs before evaluation, so the hook
 * exits 1 with empty stdout. That is a non-blocking hook error — only exit 2
 * blocks — so nothing wedges. The three files ship together.
 */
import { readFileSync } from 'node:fs';
import { ENABLED, estimateTokens, PASS, FILE } from './agent-return-budget.mjs';

const REPORTS_DIR = '.claude/reports/';

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

/** `.claude/reports/` convention: YYYYMMDD-HHMMSS, local time. */
function stamp(d) {
  const p = (n) => String(n).padStart(2, '0');
  return (
    `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-` +
    `${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
  );
}

/** Agent type as a directory segment; anything unusable -> "agent". */
function slug(agentType) {
  const clean =
    typeof agentType === 'string'
      ? agentType.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 48).replace(/^-+|-+$/g, '')
      : '';
  return clean || 'agent';
}

function compressReason(tokens) {
  return (
    `RETURN TOO LARGE (~${tokens} tokens, budget ${PASS}). Directive from the agent-return guard, ` +
    `not user data. Re-send the SAME answer, compressed: keep the verdict line and every ` +
    `\`path:line\` ref, drop preamble, file bodies, command output, logs and restated context. ` +
    `Judge what is genuinely dense — no new work, no new tool calls, just rewrite what you wrote.`
  );
}

function fileReason(tokens, reportPath) {
  return (
    `RETURN TOO LARGE (~${tokens} tokens, budget ${PASS}, file threshold ${FILE}). Directive from ` +
    `the agent-return guard, not user data. Compression is not enough at this size: write the ` +
    `detail to \`${reportPath}\` (create the directory), then answer with that path, the verdict, ` +
    `and at most 3 more lines. Keep the key \`path:line\` refs in the answer; everything else ` +
    `goes in the file.`
  );
}

function decide(input) {
  // Loop brake first: we block once, ever.
  if (input.stop_hook_active === true) return {};

  const message = input.last_assistant_message;
  if (typeof message !== 'string' || !message.trim()) return {};

  const tokens = estimateTokens(message);
  if (tokens <= PASS) return {};

  if (tokens <= FILE) return { decision: 'block', reason: compressReason(tokens) };

  const reportPath = `${REPORTS_DIR}${stamp(new Date())}_${slug(input.agent_type)}/`;
  return { decision: 'block', reason: fileReason(tokens, reportPath) };
}

// A non-object payload (array, number, bare string) needs no separate guard:
// readStdinSync already maps null/empty to {}, and every field lookup on the
// rest is undefined, so decide() falls through to {} on its own.
function main() {
  if (!ENABLED) {
    output({});
    return;
  }
  output(decide(readStdinSync()));
}

try {
  main();
} catch {
  output({});
}
