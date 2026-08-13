---
id: M-UI-BRAND-POLISH
title: Canonical BrewPage brand rollout + light palette + UI wrap/charset fixes
status: closed
priority: P2
owner: manager
created: 2026-07-17
updated: 2026-07-17
tags: [ui, branding, jte, tailwind, swagger]
links: []
---

## Context

Live local run on the new JTE+HTMX UI surfaced 5 user-reported visual defects
(badge/path mid-word wrapping, Swagger title mojibake, placeholder logo, off-brand
light palette, broken generated-report sidebar branding). Batch-fixed and verified
in one session.

## Acceptance

- [x] Reports grid ACTIVE badge no longer wraps mid-word ("ACTIV/E"); path cell no
      longer break-all mid-word wraps -- `grid.jte` cell/badge classes.
- [x] Swagger tab title mojibake ("&acirc;&euro;&rdquo;"-style garbling) gone --
      `SwaggerBrandingFilter` now force-emits UTF-8.
- [x] Placeholder logo replaced with canonical brewpage-app mark (gold #EBBB40 +
      black #141414 outlined Montserrat-700 B, theme-invariant) in `static/icon.svg`,
      `favicon.svg`, header img in `layout/main.jte`, swagger `brand.js` badge,
      custom-logo-plugin SVG.
- [x] Light-theme brown primary #7A5800 replaced with brewpage light palette
      verbatim (primary #C65029, primary-hi #B5431E, border #D4C5A9, success
      #2E7D33, warning/error inherit dark); `--on-primary` token removed.
- [x] Generated-report sidebar double-mark + blue wordmark fixed in `brew-brand.css`
      (background/padding reset, explicit white link color) + custom-logo plugin
      `background-size: contain`.
- [x] Full palette + logo spec recorded in `.claude/rules/frontend-design.md`.
- [x] Build GREEN, 251 tests; reviewed (developer + reviewer agents, verdict after
      fixes: clean); visually verified via Playwright screenshots.

## Notes

- 2026-07-17: all 5 defects fixed, reviewed by developer + reviewer agents (verdict
  after fixes: clean). Visual verification via Playwright screenshots saved under
  `.claude/reports/20260717-165500_ui-visual-verify/`. Build GREEN, 251 tests.
- Residual (deliberate, no new board rows): filled accent buttons use `text-white`
  in 11 template spots -- flagged for a deliberate on-accent token decision later;
  Swagger `brand.js` fallback `/icon.svg` is not context-path aware (matches the
  whole-UI root-absolute pattern).
- Closing state: branch `feature/phase-1-vaadin-removal`, working tree NOT yet
  committed (awaiting user); sits on top of pushed commit `da03c1d`.
