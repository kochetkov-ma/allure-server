# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 3.x     | Yes       |
| 2.x     | No        |
| 1.x     | No        |

Only the 3.x line receives security fixes. Fixes are released as a new 3.x version and a new
Docker image tag. There are no backports to older lines.

## Verifying Releases

Every release image is signed with keyless cosign (Sigstore) by the release workflow. Verify a tag
before running it, replacing `3.0.0` with the version you pulled:

```bash
cosign verify \
  --certificate-identity "https://github.com/kochetkov-ma/allure-server/.github/workflows/release.yml@refs/tags/v3.0.0" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  kochetkovma/allure-server:3.0.0
```

The same image is published to GHCR and signed in the same run:

```bash
cosign verify \
  --certificate-identity "https://github.com/kochetkov-ma/allure-server/.github/workflows/release.yml@refs/tags/v3.0.0" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  ghcr.io/kochetkov-ma/allure-server:3.0.0
```

The certificate identity is the tag being verified, so it changes with every version.

Images also carry SLSA provenance attestations built with `provenance: mode=max`, readable with
`docker buildx imagetools inspect <image> --format '{{ json .Provenance }}'`. A CycloneDX SBOM of the
jar (`bom.json` and `bom.xml`) is attached to each GitHub release alongside the runnable jar.

## Reporting a Vulnerability

Do not open a public issue for a security problem.

Two private channels, either one is fine:

1. GitHub Private Vulnerability Reporting: go to the
   [Security tab](https://github.com/kochetkov-ma/allure-server/security/advisories/new) of this
   repository and open a draft advisory. This is the preferred channel because the whole exchange,
   including the fix and the published advisory, stays in one place.
2. Email `apmatypa88@gmail.com` with `allure-server security` in the subject.

A report is easier to act on when it includes the affected version or image tag, how the server was
configured (H2 or Postgres, DB auth or the `oauth` profile, whether `/ext` plugins are mounted),
the steps to reproduce, and what an attacker gains.

## What to Expect

allure-server is maintained by one person in their own time. The following is what is aimed for,
not a service level agreement, and nothing here is a guarantee:

- Acknowledgement of a report within about a week.
- An assessment of whether it is a real vulnerability and how severe it is within about two weeks
  of the acknowledgement.
- A fix released as soon as it is ready, with no fixed date attached.

If a report has gone unanswered for two weeks, send a follow-up on the other channel. Silence is
almost always a missed message, not a decision to ignore the report.

## Disclosure Policy

Coordinated disclosure. The report stays private until a fixed version is published.

The intended sequence is: report received, vulnerability confirmed, fix developed privately, fixed
version released, GitHub Security Advisory published with a CVE requested through GitHub, reporter
credited in the advisory unless they ask not to be.

The target window is 90 days from acknowledgement to public disclosure. If a fix is not ready by
then, the advisory is published anyway describing the problem and any workaround, because users
running an affected version deserve to know. A reporter who wants to publish earlier should say so
in the report and it will be worked out from there. If a vulnerability is already being exploited
in the wild, disclosure happens immediately alongside whatever mitigation exists.

## Out of Scope

Reports about the following will be closed without a fix:

- Missing security headers or TLS configuration on a deployment. allure-server is intended to run
  behind a reverse proxy that terminates TLS.
- The default credentials shipped for first boot. They exist so a fresh instance is reachable, and
  the server forces a password change on first login.
- Vulnerabilities in an external plugin JAR loaded from `/ext`. Those belong to the plugin author.
- Automated scanner output with no demonstrated impact.
