---
paths:
  - "src/main/jte/**/*.jte"
  - "src/main/frontend/**/*.css"
  - "src/main/resources/static/**"
  - "tailwind.config.js"
---

# Frontend Design Rules

Visual source of truth: **BrewPage design system** — `docs/design/DESIGN.md` + `ASSETS.md` in the sibling `brewpage-app` checkout (`../brewpage-app/` next to this repo; maintained in place, no versioned copies).
Never hand-pick hex colors or one-off tailwind `slate-*`/`gray-*`/`emerald-*` utilities. Use the semantic token layer only.

## Color tokens — single source

Defined in `src/main/frontend/input.css`: `:root` (dark default) + `[data-theme="light"]` overrides.
RGB triples so Tailwind can compose alpha: `rgb(var(--token) / <alpha-value>)`.
Mirror copy for standalone CSS (no Tailwind reach): `static/swagger/theme.css` — keep in lockstep.
Values are ported from `brewpage-app/frontend/src/css/tokens.css` — do not invent new ones.

### Fixed palette (canonical, from brewpage-app tokens.css)

| Token | Dark hex | Dark triple | Light hex | Light triple | Use for |
|---|---|---|---|---|---|
| `bg` | `#141414` | `20 20 20` | `#FAFAF9` | `250 250 249` | page body |
| `bg-elevated` | `#0A0A0A` | `10 10 10` | `#FFFFFF` | `255 255 255` | header + footer |
| `card` | `#1C1A14` | `28 26 20` | `#FFFFFF` | `255 255 255` | elevated panels (`.card`) |
| `surface` | `#272318` | `39 35 24` | `#F5F3EE` | `245 243 238` | filter rows, striping, nav hover |
| `border` | `#8C7039` | `140 112 57` | `#D4C5A9` | `212 197 169` | panel borders |
| `border-subtle` | `#272318` | `39 35 24` | `#E7E5E4` | `231 229 228` | row dividers |
| `primary` | `#EBBB40` | `235 187 64` | `#C65029` | `198 80 41` | brand accent, active nav, CTA, focus ring |
| `primary-hi` | `#FAE96F` | `250 233 111` | `#B5431E` | `181 67 30` | hover / headings / extra contrast |
| `text` | `#D6D3D1` | `214 211 209` | `#1C1A14` | `28 26 20` | primary text |
| `text-muted` | — | `154 149 142` | — | `82 78 70` | secondary labels (flattened alpha) |
| `success` | `#649C67` | `100 156 103` | `#2E7D33` | `46 125 51` | green accent (wordmark "QA", toasts) |
| `warning` | `#DF8D03` | `223 141 3` | inherits dark | inherits dark | amber warn |
| `error` | `#CF6679` | `207 102 121` | inherits dark | inherits dark | red destructive |

Light-theme rules (verbatim brewpage approach):
- Gold `#EBBB40` is unreadable on white → light `--primary` IS the warm orange accent `#C65029` (white text on it ~4.39:1); `--primary-hi #B5431E` carries hover/headings. Every element resolving through `primary` re-themes automatically.
- `warning`/`error` have NO light override — they inherit dark values (brewpage minimal-switch policy).
- `success` takes brewpage's deeper-green light override `#2E7D33` because it doubles as small text.
- The LOGO never follows `--primary`: gold + black are literal pins inside the SVG assets (theme-invariant, see below).

**Brand twist for this project:** wordmark is `Brew<span class="text-primary">.</span><span class="text-success">QA</span>` — gold dot (orange in light), green `QA`. Keep identical in header and footer.

## Logo — canonical mark

Gold `#EBBB40` rounded square + BLACK `#141414` "B" (Montserrat-700, outlined `<path>`, not `<text>`). Proportions replicate the LIVE brewpage.app header lockup — `.logo-square` in `brewpage-app/frontend/src/css/base.css` (36x36px box, 8px radius, Montserrat-700 at 1.25rem):

| Metric | Value | Derivation |
|---|---|---|
| Corner radius | 22.2% of square (`rx 113.78` of 512) | 8px / 36px |
| B cap-height | 38.9% of square (cap 199.11 of 512, centered) | 0.7 (Montserrat capHeight 700 / upm 1000, fontTools-verified) x 20px / 36px |

Source of truth: brewpage.app LIVE header + `base.css` `.logo-square` (NOT brewpage's icon.svg — its proportions differ).

| Rule | Detail |
|---|---|
| Theme-invariant | The mark NEVER changes with theme: no `prefers-color-scheme` styles, no `--primary` reference — gold + black are literal hex inside the SVG in BOTH themes |
| Locations (identical artwork) | `static/icon.svg` = `static/favicon.svg` = swagger badge (`brand.js` renders `<img>` of `icon.svg`) = report favicon + sidebar badge (`BrandingService` copies `static/icon.svg` as `favicon.svg`) = `plugins/custom-logo-plugin/static/custom-logo.svg` (44x44 intrinsic) |
| Header usage | `layout/main.jte`: `<img src="/icon.svg" class="h-9 w-9" alt="" aria-hidden="true">` — never rebuild the badge as a CSS span |
| Report sidebar badge | `brew-brand.js` renders `<img src="favicon.svg">` (28px box sized in `brew-brand.css`) — never a styled CSS span with its own "B"; wordmark accents pin `--brew-gold #EBBB40` / `--brew-green #649c67` |

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
