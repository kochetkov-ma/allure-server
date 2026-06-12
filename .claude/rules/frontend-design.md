---
paths:
  - "src/main/jte/**/*.jte"
  - "src/main/frontend/**/*.css"
  - "src/main/resources/static/**"
  - "tailwind.config.js"
---

# Frontend Design Rules

Visual source of truth: **BrewPage design system** — `/Users/maximus/IdeaProjects/brewpage-app/docs/design/DESIGN.md` + `ASSETS.md` (sibling repo; maintained in place, no versioned copies).
Never hand-pick hex colors or one-off tailwind `slate-*`/`gray-*`/`emerald-*` utilities. Use the semantic token layer only.

## Color tokens — single source

Defined in `src/main/frontend/input.css`: `:root` (dark default) + `[data-theme="light"]` overrides.
RGB triples so Tailwind can compose alpha: `rgb(var(--token) / <alpha-value>)`.

| Semantic | Use for |
|---|---|
| `bg` / `bg-elevated` | page body / header+footer |
| `surface` | filter rows, subtle striping, inactive nav hover |
| `card` | elevated panels (`.card`) |
| `border` / `border-subtle` | panel borders / row dividers |
| `primary` / `primary-hi` | gold brand, active nav, primary CTA, focus ring |
| `text` / `text-muted` | primary text / secondary labels |
| `success` | green brand accent (wordmark "QA", success toast, drop-zone loaded) |
| `warning` / `error` | amber warn / red destructive |

**Brand twist for this project:** wordmark is `Brew<span class="text-primary">.</span><span class="text-success">QA</span>` — gold dot, green `QA`. Keep identical in header and footer.

## Utility classes

Prefer semantic Tailwind utilities: `bg-bg`, `bg-surface`, `bg-card`, `border-border`, `text-text`, `text-text-muted`, `text-primary`, `text-success`, `text-error`. Combine with opacity modifiers (`bg-primary/20`, `text-text-muted/60`) instead of new tokens.

## Component classes (`@layer components` in `input.css`)

| Class | When |
|---|---|
| `.card` | any bordered panel with `::after` glow line |
| `.btn-primary` | single primary action per view |
| `.btn-bordered` | secondary actions |
| `.btn-text` | tertiary / inline actions |
| `.drop-zone` (+ `--active`, `--loaded`) | file uploads |
| `.toast` (+ `--success`, `--warning`, `--error`) | transient notifications |
| `.form-field` | labelled input row |

Do NOT re-implement these inline in a template. Extend the `@layer components` block if a new reusable pattern emerges.

## Theme

- Default: **dark**. Light opt-in via `[data-theme="light"]` on `<html>`.
- Theme selection: pre-paint IIFE in `<head>` (inlined in `layout/main.jte`) reads `localStorage['allure-server-theme']` then falls back to `prefers-color-scheme`. Never move this to a deferred script — causes flash.
- Toggle handler: `src/main/resources/static/js/theme.js` on `#theme-toggle` only.

## Table / list pages

- Data grid fragment is the htmx swap target — put `<tfoot>` totals row inside the fragment so filter updates refresh it. Use OOB (`hx-swap-oob="true"`) only when the totals live outside the swapped fragment.
- Size formatting: server-side via `HumanSize.format(long)`; if mirroring in Alpine JS, keep the same 1024-based, 1-decimal thresholds so values match across render paths.

## What NOT to do

- No Google Fonts / external font imports — system-ui stack only.
- No hardcoded `#rrggbb` in templates or CSS — go through a CSS var.
- No `dark:` Tailwind variants — we invert via `[data-theme="light"]` selector, so `dark:*` is a no-op. Use semantic tokens that re-theme themselves.
- No `style="…"` inline overrides — extend tokens or component layer.
- No `slate-*`, `gray-*`, `emerald-*`, `sky-*`, `amber-*`, `red-*`, `indigo-*`, `white`, `black` utility classes — always semantic.
