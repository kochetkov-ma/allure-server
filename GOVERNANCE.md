# Governance

allure-server has one maintainer: [@kochetkov-ma](https://github.com/kochetkov-ma). There is no
committee, no steering group and no company behind the project. This document describes how that
actually works so nobody has to guess.

## The Maintainer

The maintainer holds commit rights on `master`, publishes releases and Docker images, and has the
final say on every change. The project is maintained in his own time, alongside a job. That is the
constraint everything else here follows from.

## How Decisions Get Made

The maintainer decides. Discussion happens in the open, in issues and pull requests, and it
genuinely changes outcomes, but there is no vote and no formal appeal.

The rough order of preference when weighing a change:

1. Does it break existing deployments? The project has over a million Docker pulls. Compatibility of
   the REST API, the config property names and the `/ext` plugin loading mechanism outranks almost
   everything else.
2. Does it fix a real problem someone has, as opposed to an imagined one?
3. Can one person maintain it in five years? A feature that needs constant attention, a new
   external service dependency, or a large surface with no tests is a long-term cost that falls on
   one person.
4. Is it simple? Fewer files, less configuration, less indirection wins.

A change that is right but arrives without tests is not merged until it has them. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## Proposing a Change

Open an issue describing the problem before writing code. The maintainer will say yes, no, or not
in this form. Silence on an issue is not consent to open a large pull request against it.

An unwanted change is declined with a reason. The reason is usually one of: it breaks compatibility,
it belongs in a plugin rather than the core, the maintenance cost outweighs the benefit, or it
solves a problem the project does not have.

An issue with no reply after a month is almost certainly missed rather than rejected. A polite bump
is fine.

## Releases

The maintainer cuts releases. A release is a `vX.Y.Z` tag pushed to the repository, which triggers
the release workflow: a versioned jar attached to the GitHub Release, a CycloneDX SBOM, and
multi-architecture images pushed to Docker Hub as `kochetkovma/allure-server` and to GHCR as
`ghcr.io/kochetkov-ma/allure-server`.

There is no release schedule. Releases happen when there is something worth releasing.

## Becoming a Maintainer

The project would benefit from a second maintainer, and the path is open.

There is no application form and no fixed number of contributions. What earns the invitation is a
track record: several merged pull requests over a period of months, changes that arrive with tests
and do not need to be rewritten in review, useful answers to other people's issues, and judgment
about compatibility that matches the priorities above. Consistency over time matters more than the
size of any single contribution.

The maintainer extends the invitation directly. Asking is fine and will not count against anyone,
but the answer will be based on the record. A new maintainer receives commit rights on `master`;
release credentials for Docker Hub and GHCR stay with the project owner until there is a reason to
change that.

Maintainer status lapses through inactivity rather than being revoked. Someone who stops
participating for a long stretch may have commit rights removed, and they are returned on request
if they come back.

## If the Maintainer Goes Away

The project is Apache-2.0. If it is abandoned, fork it. That is the honest answer for a
single-maintainer project and it is why the license was chosen.

## Changing This Document

Through a pull request, decided the same way as any other change.
