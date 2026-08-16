# Support

allure-server is maintained by one person. Answers come when they come. Filing in the right place
is the single biggest thing that makes a response likely.

## Questions and Help

Use [GitHub Discussions](https://github.com/kochetkov-ma/allure-server/discussions) for questions:
how to configure something, how to deploy it, whether a thing is possible, what an option does.

Discussions may not be enabled on this repository yet. If that link returns a 404, open a
[GitHub Issue](https://github.com/kochetkov-ma/allure-server/issues) instead and start the title
with `question:`. Once Discussions is enabled, existing question issues will be converted and this
document will drop the fallback.

Before asking, check the [README](README.md), the Swagger UI on a running instance at
`/swagger-ui.html`, and the closed issues. A large share of questions are already answered there.

## Bugs

Open an [Issue](https://github.com/kochetkov-ma/allure-server/issues). A bug report is actionable
when it states the version or Docker image tag, how the server runs (jar, Docker, Helm), the
database (H2 or Postgres), what was expected, what happened, and the exact steps to reproduce.
Attach the relevant log lines rather than a screenshot of them.

A report of the form "reports do not generate" with nothing else attached cannot be acted on and
will be closed.

## Feature Requests

Open an Issue describing the problem you have, not only the feature you want. The problem is what
determines whether the feature is the right answer. See [GOVERNANCE.md](GOVERNANCE.md) for how a
request is accepted or declined.

## Security

Do not use Discussions or Issues for a vulnerability. Follow [SECURITY.md](SECURITY.md).

## Commercial Support

There is none. There is no company behind this project, no paid tier and no support contract.

## What Is Not Supported

- Debugging your CI pipeline, your test framework, or the Allure results it produces. If the
  results zip is invalid before it reaches the server, that is upstream of this project.
- Questions about Allure Report itself. Those belong at
  [allurereport.org/docs](https://allurereport.org/docs).
- Versions before 3.x. Upgrade first, then report if the problem persists.
