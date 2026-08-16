#!/usr/bin/env node
// brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewcode:semble-setup
/**
 * brewcode:semble-setup — UserPromptSubmit hook (self-contained, installed into
 * a project). It runs alongside the advisory hooks, it does not replace them.
 *
 * WHY IT EXISTS. Prefetch runs the search itself and hands over the RESULT:
 * measured 5/6 sessions opened an injected path, at fewer tool calls than
 * control in 5/6 questions. That is a different mechanism from advice, aimed at
 * the same goal.
 *
 * NOT A REPLACEMENT FOR THE ADVISORY HOOKS. 5.0.0 deleted `semble-reminder.mjs`
 * and `semble-explore.mjs` citing "0/18 and 0/11 conversion". Both hooks are
 * back, because that rationale did not survive review: the delivery channel was
 * never broken (PreToolUse and SubagentStart both accept and deliver
 * `additionalContext`), and `0/18` had an empty numerator AND an empty
 * denominator — the reminder fired zero times in those 18 sessions (its own gate
 * suppressed 74/113, 37 were `disabled`, 2 throttled; lifetime 14/2718 = 0.52%).
 * The `0/11` figure was real but covered a single agent type on text that
 * undercut itself. Per-channel conversion on the accumulated log is reminder
 * 2/10 sessions and explore 1/7 — not zero. The restored hooks' own conversion
 * is NOT yet measured.
 *
 * WHAT IT SHIPS: paths, never snippets. With 5 hits carrying path+lines+snippet
 * and no directive, conversion was 2/6 and in 2/6 sessions the model answered
 * with ZERO tool calls straight off the snippets — the snippet SUBSTITUTES for
 * verification. Three bare paths plus a directive provoke the read instead.
 * Prefetch buys turns and citation precision, NOT accuracy: all 18 answers were
 * correct in all three arms.
 *
 * IT RUNS ON EVERY PROMPT THE USER TYPES. A crash or a hang here is the worst
 * failure in this skill, so every path is fail-open and silent: `{}` on stdout,
 * exit 0, no matter what. The search is spawned with a hard 3 s cap (the hook's
 * own registered timeout is 5 s); see the cooldown block below for what a
 * failure and a timeout each cost.
 *
 * IT NEVER BUILDS AN INDEX. semble builds lazily inside the call and a cold
 * build takes minutes, so a 3 s child can only ever kill it half-done — burning
 * the cap on every prompt and making no progress, forever. The hook therefore
 * stats the cache directory first and stays silent (`why=cold-index`, cost ~4
 * stat calls) until something that is ALLOWED to take minutes has built it: the
 * MCP server, or `semble-project.sh warm`/`smoke` during install.
 *
 * Pure ESM, Node built-ins only. Helpers are inlined on purpose: this file
 * travels alone into a user's .claude/hooks/ and must have no imports.
 */
import {
  appendFileSync, existsSync, readFileSync, realpathSync, statSync, writeFileSync,
} from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

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
    process.stderr.write('[semble-prefetch] ' + message + '\n');
  } catch {
    /* stderr is best-effort */
  }
}
// --- telemetry (best-effort, never throws, never changes hook output) ------
const TELEMETRY_SRC = 'prefetch';
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

/** Anti-storm guard, not a rate limiter: gate v3 already suppresses ~64% of prompts. */
const THROTTLE_MS = 30_000;
/**
 * A search that FAILED — no uvx, non-zero exit, unparseable output — is a
 * standing condition: nothing about the next prompt will change it, so it parks
 * the mechanism for ten minutes rather than paying the cap on every keystroke.
 */
const COOLDOWN_MS = 600_000;
/**
 * A search that TIMED OUT is a different event and gets a tenth of the penalty.
 * Against a warm index a search is ~0.6 s, so hitting a 3 s cap means transient
 * load, not a broken install — and the ten-minute park was measured silencing
 * prefetch for whole sessions off one slow call. The cold-index case, which is
 * what used to make this fire on every fresh repo, no longer reaches the child
 * at all: `indexReady` gates it out for free.
 */
const TIMEOUT_COOLDOWN_MS = 60_000;
/** Hard cap on the child. The registered hook timeout is 5 s; measured median is 0.6 s. */
const SEARCH_TIMEOUT_MS = 3_000;
const TOP_K = 3;

/**
 * The searcher. MUST stay byte-identical to the MCP registration written by
 * semble-mcp.sh (`SEMBLE_PIN_SPEC` and `SEMBLE_CONTENT_ARGS` in
 * scripts/lib/semble-common.sh) — semble keys its cache directory by project
 * path ALONE but rejects a cached index whose content-type set differs, so a
 * mismatched set here would make the hook and the server evict each other's
 * index on every alternation. tests/suite-hooks.mjs asserts the two agree.
 */
const PIN_SPEC = 'semble[mcp]==0.5.4';
const CONTENT_ARGS = ['code', 'docs', 'config'];

// ── gate v3 ────────────────────────────────────────────────────────────────
// INTENT and (DOMAIN or repo-reference), minus four suppressors. Measured on 61
// real user prompts: fires 36%, precision 55%, recall 71%, F1 0.62. The old v1
// rule required a code-domain noun, which a vocabulary-mismatch question by
// construction never has — that one clause was what capped recall.
// 55% precision is the honest ceiling of lexical rules; roughly half of the
// firings are pure overhead, which is affordable only because a firing costs
// one 0.6 s search and ~90 tokens.

const META = /^(да|нет|ок|окей|ага|угу|yes|no|ok|okay|sure|thanks|спасибо|go|next|стоп|stop|y|n|\d+)[\s.!?)]*$/i;
const CODEWORD_ONLY = /^(\+\+[a-z]{1,3}\s*)+$/i;

const INTENT = new RegExp([
  '\\b(where|how|why|what|which|who|when)\\b',
  '\\b(find|show|explain|check|look|search|locate|list|describe|trace|inspect|review|audit|analyz|understand|figure out|walk me)\\w*\\b',
  '\\b(is|are|does|do|can|could|should)\\s+\\w+',
  // JS \b is ASCII-only, so Cyrillic needs explicit boundaries.
  '(?<![\\p{L}])(где|как|почему|зачем|что|какой|какая|какие|какое|каком|чем|кто|когда|куда|откуда)(?![\\p{L}])',
  '(найд|посмотр|покаж|объясн|проверь|расскаж|разбер|изуч|проанализ|глянь|поищ|опиш|отследи)\\w*',
].join('|'), 'iu');

const DOMAIN = new RegExp([
  '\\b(hook|skill|agent|plugin|function|class|method|module|config|setting|script|test|api|endpoint|schema|handler|parser|command|variable|import|export|interface|type|error|exception|log|cache|index|query|route|middleware|component|repo|branch|commit|file|code|implement|regex|flag|arg|param|json|yaml|env)\\w*\\b',
  '(хук|скилл|скил|агент|плагин|функци|класс|метод|модул|конфиг|настройк|скрипт|тест|апи|эндпоинт|схем|хендлер|парсер|команд|переменн|импорт|интерфейс|ошибк|лог|кэш|кеш|индекс|запрос|роут|компонент|репозитор|ветк|коммит|файл|код|реализац|регулярк|флаг|аргумент)\\w*',
  '`[^`]+`',
  '\\b\\w+_\\w+\\b',
  '\\b[a-z]+[A-Z]\\w*\\b',
  '\\b\\w+\\.(mjs|js|ts|tsx|py|json|md|sh|yml|yaml|java|kt|go|rs|rb|toml)\\b',
  '\\b\\w+\\(\\)',
  '(^|\\s)[.~]?/[\\w./-]+',
].join('|'), 'u');

// P1 — the prompt is anchored to THIS repo even when it names no code noun.
// This clause is the whole of v3: it readmits the vocabulary-mismatch class.
const REPOREF = /(у\s+нас|в\s+проекте|в\s+репо|в\s+коде|здесь|тут\s+|нам\s+)|\b(this|our|the)\s+(project|repo|repository|codebase|code)\b|\bin\s+(this|the)\s+(project|repo|codebase)\b/iu;

// S1 — about THIS conversation / what the assistant just did. The answer is in
// context, not on disk. "как ты думаешь" solicits an opinion and is exempted.
const SELF = /(?!как\s+ты\s+думаешь)((что|чё|чего|почему|зачем)\s+(же\s+)?ты\s|ты\s+(сделал|делаешь|нашёл|нашел|решил|проверял|говоришь|предлагаешь|заебал|пиздишь|хочешь)|расскажи,?\s+что\s+(ты|мы)|что\s+мы\s+(в\s+этой\s+фиче\s+)?сделали|what\s+(did|have)\s+you\b|тебя\s+прос)/iu;
// S2 — an exact path, filename or quoted literal is rg territory (rg won 2/2 there, 20x faster).
const LITERAL = /(^|\s)[~.]?\/?[\w.-]+\/[\w./-]+|(^|\s)[\w-]+\.(yml|yaml|json|mjs|js|ts|tsx|py|sh|md|toml)\b|"[^"]{2,}"|«[^»]{2,}»/u;
// S3 — exhaustive enumeration: rg won 5/5, semble 2/5.
const ENUM = /\b(every|all\s+the|complete\s+(list|set)|list\s+all|how\s+many|count)\b|(все\s+(места|файлы|случаи)|полный\s+список|перечисли|сколько\s+(их|всего))/iu;
// S4 — a numbered task or section from the tracker, not code.
const TASKREF = /(§\s*\d|\bзадач\w*\s*#?\s*\d|\b\d{1,3}-(й|ой|ая|ую|ые)\b|\bПП-\d|#\d{1,4}\b)/u;

function suppressor(p) {
  if (SELF.test(p)) return 'self-reference';
  if (TASKREF.test(p)) return 'task-reference';
  if (LITERAL.test(p)) return 'exact-literal';
  if (ENUM.test(p)) return 'enumeration';
  return null;
}

/**
 * @returns {{fire:boolean, why:string}} — `why` is the telemetry token, so every
 * suppressed prompt is attributable to the exact clause that suppressed it.
 */
function gateV3(prompt) {
  const p = String(prompt || '').trim();
  if (!p) return { fire: false, why: 'empty' };
  if (p.startsWith('/')) return { fire: false, why: 'slash-command' };
  if (CODEWORD_ONLY.test(p)) return { fire: false, why: 'codeword-only' };
  if (META.test(p)) return { fire: false, why: 'meta-reply' };

  const body = p.replace(/^(\s*\+\+[a-z]{1,3}\s*)+/i, '').trim();
  if (body.length < 30) return { fire: false, why: 'too-short' };
  if (body.length > 2000) return { fire: false, why: 'too-long' };
  if (/^https?:\/\/\S+$/.test(body)) return { fire: false, why: 'bare-url' };
  if (/^[~./][\w./-]+$/.test(body)) return { fire: false, why: 'bare-path' };

  if (!INTENT.test(body)) return { fire: false, why: 'no-intent' };
  if (!DOMAIN.test(body) && !REPOREF.test(body)) return { fire: false, why: 'no-domain-no-reporef' };
  // Suppressors read the WHOLE prompt, codewords included — that is how the 61-prompt
  // precision/recall was measured, and a `++m` prefix must not change a verdict.
  const s = suppressor(p);
  if (s) return { fire: false, why: s };
  return { fire: true, why: 'behaviour-or-vocab' };
}

// ── distiller ──────────────────────────────────────────────────────────────
// Measured against handing semble the raw prompt: hit@3 11/16 vs 9/16,
// MRR 0.674 vs 0.398, paired 8 wins / 3 losses / 5 ties. The assumption that
// rewriting the query would hurt is refuted; the distiller stays.
const STOP = new Set(`
a an the this that these those there here it its is are was were be been being do does did done
what where when why how which who whom whose can could should would will shall may might must
i you he she we they me him her us them my your our their mine yours
and or but if then else so because as of in on at to for from with without by about into over under
not no yes just only also very much many more most some any all every each other another same
find show tell give want need let make take get go come know think see look use using used
across whole entire everything anything something please thanks okay ok well now then still yet
file files place places way ways thing things stuff part parts list complete summarise summarize
different inside vs versus current legacy across repo repository project
и в во не что он на я с со как а то все она так его но да ты к у же вы за бы по только ее мне было
вот от меня еще нет о из ему теперь когда даже ну вдруг ли если уже или ни быть был него до вас
нибудь опять уж вам ведь там потом себя ничего ей может они тут где есть надо ней для мы тебя их
чем была сам чтоб без будто чего раз тоже себе под будет ж тогда кто этот того потому этого какой
совсем ним здесь этом один почти мой тем чтобы нее сейчас были куда зачем всех никогда можно при
наконец два об другой хоть после над больше тот через эти нас про всего них какая много разве три
эту моя впрочем хорошо свою этой перед иногда лучше чуть том нельзя такой им более всегда конечно
всю между нужно давай слушай смотри окей значит просто вообще типа блять блядь ебать нахуй хуй сука
ёпта ёкта ебаный ебучий погоди скажи расскажи покажи сделай сделать нужен нужна нужны имеешь виду
`.trim().split(/\s+/).filter(Boolean));

/** Keyword query: code-shaped tokens first (highest signal), then content words. */
function distill(prompt, max = 9) {
  const p = String(prompt || '')
    .replace(/^(\s*\+\+[a-z]{1,3}\s*)+/i, '')
    .replace(/```[\s\S]*?```/g, ' ');

  const strong = [];
  for (const re of [
    /`([^`]{2,40})`/g,
    /\b([\w-]+\.(?:mjs|js|ts|tsx|py|json|md|sh|yml|yaml|java|kt|go|rs|toml))\b/g,
    /\b([a-z]+(?:[-_][a-z0-9]+)+)\b/gi,
    /\b([a-z]+[A-Z][A-Za-z]*)\b/g,
  ]) {
    for (const m of p.matchAll(re)) strong.push(m[1]);
  }

  const words = [];
  for (const raw of p.toLowerCase().match(/[\p{L}][\p{L}\d]{2,}/gu) || []) {
    if (!STOP.has(raw)) words.push(raw);
  }

  const seen = new Set();
  const out = [];
  for (const t of [...strong, ...words]) {
    const k = t.toLowerCase();
    if (seen.has(k)) continue;
    seen.add(k);
    out.push(t);
    if (out.length >= max) break;
  }
  return out.join(' ');
}

// ── state, throttle, cooldown ──────────────────────────────────────────────

/** {kind:'missing'|'corrupt'|'ok', state} — same reader as the session hook. */
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

/**
 * Is semble USABLE in this repo — not "has verification finished".
 *
 * `phase === 'ready'` would deadlock: semble builds its index lazily inside a
 * tool call, so nothing advances the phase until something calls semble.
 * `completed` containing "mcp" is the registration proxy — semble-mcp.sh writes
 * it in the same checkpoint patch that registers the server. Reading
 * ~/.claude.json on every prompt to check for real would cost megabytes of
 * parse per keystroke. `prereq_ready` is denied because the add-failed rollback
 * lands there with `completed` still holding "mcp".
 */
function stateGate(read) {
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
  return { ok: true, why: 'ok', phase, enabled, state };
}

// ── cache location ─────────────────────────────────────────────────────────
// The MCP server is registered with an explicit SEMBLE_CACHE_LOCATION. semble
// keys its cache directory by project path ALONE, so it cannot notice that two
// consumers disagree about the ROOT those directories live under: each one just
// finds nothing and builds its own copy. Before this was passed through, the
// hook indexed into semble's default root while the server used the registered
// one — 40 MB of duplicated index across two repos, and every first firing was
// a from-scratch build that the 3 s cap killed.

/** Mirrors sc_cache_root_code in scripts/lib/semble-common.sh. */
function defaultCacheRoot() {
  const home = process.env.HOME || homedir();
  if (process.platform === 'darwin') return join(home, 'Library', 'Caches', 'semble-code');
  return join(process.env.XDG_CACHE_HOME || join(home, '.cache'), 'semble-code');
}

/** state.json carries `cacheRoot`, written by semble-mcp.sh from the same helper. */
function cacheRootOf(state) {
  const r = state && typeof state.cacheRoot === 'string' ? state.cacheRoot : '';
  return r || defaultCacheRoot();
}

/**
 * Mirrors sc_repo_hash / semble cache.py: sha256 of the resolved project path.
 *
 * Always computed from the path we are about to hand the child, never read from
 * state.json. A hash carried over from another checkout — .claude/ copied into a
 * fork, a moved or renamed repo, a git worktree, claude started in a
 * subdirectory — would vouch for a FOREIGN index, and the readiness check would
 * wave through a spawn against a genuinely cold path: the 3 s timeout this guard
 * exists to prevent. A mismatch must read as cold (silent, free) instead.
 */
function repoHashOf(_state, cwd) {
  let path = cwd;
  try {
    path = realpathSync(cwd);
  } catch { /* unresolvable: hash what we were given, same as semble would */ }
  try {
    return createHash('sha256').update(path).digest('hex');
  } catch {
    return '';
  }
}

/** semble writes all four; a partial set is a build that never finished. */
const INDEX_FILES = ['chunks.json', 'metadata.json', 'bm25_index', 'semantic_index'];

/**
 * Is there an index to search? Four existsSync calls, no child process — the
 * cheapest possible answer to the question that used to cost a 3 s timeout and
 * a ten-minute cooldown on every fresh repo.
 */
function indexReady(root, hash) {
  if (!root || !hash) return false;
  try {
    const dir = join(root, hash, 'index');
    return INDEX_FILES.every((n) => existsSync(join(dir, n)));
  } catch {
    return false;
  }
}

const markerFile = (cwd) => join(cwd, '.claude', 'semble', '.prefetch-ts');

/**
 * `{t, cool}` epoch-ms in ONE file so the install needs exactly one .gitignore
 * line. Unreadable or malformed reads as "no marker": a corrupt throttle must
 * fail OPEN, the same as every other input here.
 */
function readMarker(cwd) {
  try {
    const m = JSON.parse(readFileSync(markerFile(cwd), 'utf8'));
    if (m === null || typeof m !== 'object' || Array.isArray(m)) return {};
    return m;
  } catch {
    return {};
  }
}

function writeMarker(cwd, patch) {
  try {
    writeFileSync(markerFile(cwd), JSON.stringify({ ...readMarker(cwd), ...patch }));
  } catch (e) {
    warn('marker write failed: ' + e.message); // ignored on purpose
  }
}

const fresh = (v, window) => typeof v === 'number' && Number.isFinite(v)
  && Date.now() - v >= 0 && Date.now() - v < window;

/**
 * How long the armed cooldown lasts, written alongside it by whoever armed it.
 * Clamped into [0, COOLDOWN_MS]: a corrupt or hostile marker must never be able
 * to park the mechanism for longer than the hook's own maximum.
 */
function coolWindow(marker) {
  const w = marker && marker.coolMs;
  if (typeof w !== 'number' || !Number.isFinite(w) || w < 0) return COOLDOWN_MS;
  return Math.min(w, COOLDOWN_MS);
}

// ── search ─────────────────────────────────────────────────────────────────

/**
 * `{hits, why}`. `hits === null` is the signal to park the mechanism, and `why`
 * says for how long: `search-timeout` (transient, one minute) or
 * `search-failed` (standing condition, ten minutes). `hits === []` — a search
 * that ran and found nothing — is not a failure: it arms the ordinary 30 s
 * throttle, never a cooldown.
 *
 * SEMBLE_CACHE_LOCATION is the load-bearing env entry: without it the child
 * silently uses semble's default root, which is NOT the root the MCP server was
 * registered with, and the two build separate copies of the same index.
 */
function search(cwd, cacheRoot, query) {
  let out;
  try {
    out = execFileSync(
      'uvx',
      // Options FIRST, then `--`, then the positionals. The distiller puts backticked
      // text at the front, so a prompt like ``what does `-k` do`` distils to exactly
      // `-k`, and argparse reads a lone leading-dash argv as an option: rc!=0 ->
      // search-failed -> the ten-minute cooldown, for a well-formed question. `--`
      // has to come after the flags — argparse stops parsing options at it too, so
      // trailing flags would be swallowed as positionals.
      ['--from', PIN_SPEC, 'semble', 'search',
        '--content', ...CONTENT_ARGS, '-k', String(TOP_K), '--max-snippet-lines', '0',
        '--', query, cwd],
      {
        cwd,
        encoding: 'utf8',
        maxBuffer: 4 << 20,
        timeout: SEARCH_TIMEOUT_MS,
        killSignal: 'SIGKILL',
        stdio: ['ignore', 'pipe', 'ignore'],
        env: { ...process.env, SEMBLE_CACHE_LOCATION: cacheRoot },
      },
    );
  } catch (e) {
    // ETIMEDOUT is set by spawnSync itself, so it is not confusable with a
    // child that merely exited non-zero (ENOENT, rc!=0 — no `code` or a
    // different one).
    return { hits: null, why: (e && e.code === 'ETIMEDOUT') ? 'search-timeout' : 'search-failed' };
  }
  try {
    const parsed = JSON.parse(out);
    const results = Array.isArray(parsed && parsed.results) ? parsed.results : null;
    if (results === null) return { hits: null, why: 'search-failed' };
    return {
      hits: results
        .filter((h) => h && typeof h.file_path === 'string' && h.file_path)
        .slice(0, TOP_K),
      why: 'ok',
    };
  } catch {
    return { hits: null, why: 'search-failed' };
  }
}

/**
 * The framing that converted 5/6. Three parts, all load-bearing:
 * named PROVENANCE (what produced this and from what), bare PATHS with no
 * snippet (a snippet substitutes for the read; a path provokes it), and an
 * explicit DIRECTIVE including permission to reject the candidates.
 */
function render(hits) {
  return [
    'Retrieval note (automatic, from a semantic index of THIS repository, built by semble over the working tree).',
    'These candidate locations were ranked for the question above before you started:',
    ...hits.map((h, i) => '  ' + (i + 1) + '. ' + h.file_path
      + (typeof h.start_line === 'number' ? ':' + h.start_line : '')),
    '',
    'Open the candidates that look right BEFORE running any search of your own; they are already ranked.',
    'If none of them answers the question, say so and search normally.',
    'Name the file you actually used in your answer.',
  ].join('\n');
}

function decide(input, cwd) {
  const sid = typeof input.session_id === 'string' ? input.session_id : '';
  const prompt = typeof input.prompt === 'string' ? input.prompt : '';
  const t0 = Date.now();
  const skip = (why, extra) => {
    telemetry(cwd, sid, 'prefetch', { fired: false, why, ...(extra || {}) });
    return {};
  };

  const g = stateGate(readState(cwd));
  if (!g.ok) return skip(g.why, { phase: g.phase, enabled: g.enabled });

  const marker = readMarker(cwd);
  if (fresh(marker.cool, coolWindow(marker))) return skip('cooldown');
  if (fresh(marker.t, THROTTLE_MS)) return skip('throttled');

  const gate = gateV3(prompt);
  if (!gate.fire) return skip(gate.why);

  const query = distill(prompt);
  if (!query) return skip('empty-query');

  // Cheap and non-punitive: no index yet means nothing to search, not a
  // failure. No child is spawned and no cooldown is armed, so the very next
  // prompt after the MCP server (or `semble-project.sh warm`) builds the index
  // fires normally.
  const cacheRoot = cacheRootOf(g.state);
  if (!indexReady(cacheRoot, repoHashOf(g.state, cwd))) return skip('cold-index');

  const r = search(cwd, cacheRoot, query);
  const hits = r.hits;
  const ms = Date.now() - t0;
  if (hits === null) {
    writeMarker(cwd, {
      cool: Date.now(),
      coolMs: r.why === 'search-timeout' ? TIMEOUT_COOLDOWN_MS : COOLDOWN_MS,
    });
    return skip(r.why, { q: query.slice(0, 120), ms });
  }
  if (!hits.length) {
    // The search ran, so the normal 30 s throttle applies — not the failure cooldown.
    // Without it an empty result set armed nothing and the next prompt spawned another
    // child immediately: the throttle only ever existed after a firing.
    writeMarker(cwd, { t: Date.now() });
    return skip('no-hits', { q: query.slice(0, 120), ms });
  }

  writeMarker(cwd, { t: Date.now() });
  telemetry(cwd, sid, 'prefetch', {
    fired: true,
    why: gate.why,
    q: query.slice(0, 120),
    n: hits.length,
    ms,
    paths: hits.map((h) => h.file_path),
  });
  return {
    hookSpecificOutput: {
      hookEventName: 'UserPromptSubmit',
      additionalContext: render(hits),
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

// Run only when executed as the hook. Importing the file (tests, corpus
// replays) must not consume stdin.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();

export {
  COOLDOWN_MS, CONTENT_ARGS, INDEX_FILES, PIN_SPEC, THROTTLE_MS, TIMEOUT_COOLDOWN_MS,
  cacheRootOf, defaultCacheRoot, distill, gateV3, indexReady, render, repoHashOf,
};
