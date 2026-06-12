---
id: M-ENV-SECRETS
title: Remove hardcoded secrets/defaults from application.yaml (`my-token`, admin/admin)
status: todo
priority: P2
owner:
created: 2026-06-11
updated: 2026-06-11
tags: [security, config, tech-debt]
links:
  - src/main/resources/application.yaml
---

## Context
`application.yaml` ships weak hardcoded defaults: bootstrap admin `basic.auth.username: admin`
/ `basic.auth.password: admin`, and a literal TMS token `tms.token: "my-token"`. These are
committed defaults in an open-source artifact -- anyone deploying without overriding gets a
known admin credential. Secrets must come from environment variables / externalized config
with safe placeholder behavior (fail or warn on default, never silently run with `admin/admin`
in non-dev profiles).

## Acceptance
- [ ] `basic.auth.*` defaults read from env vars (`${BASIC_AUTH_USERNAME:...}` style); no literal production credential in the file
- [ ] `tms.token` has no literal default; empty/unset disables TMS calls cleanly (tms.enabled already false)
- [ ] Startup warning (or refusal in prod profile) when bootstrap admin password is left at the default
- [ ] README + docker-compose examples updated to pass credentials via environment
- [ ] No other hardcoded credentials remain (grep sweep of `src/main/resources/`)

## Notes
Coordinates with M-APP-PROFILES (dev/prod profile split would host the fail-on-default rule).
