---
paths:
  - "**/*"
description: semble-first — one semantic search, then read the exact line; rg stays for exact matching
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewcode:semble-setup"
---

# semble-first

Semantic search = the `semble_code` MCP. Code is authoritative.

| Ask | Tool |
|-----|------|
| behaviour/intent, or wording absent from the code | `mcp__semble_code__search` (8/9) |
| neighbours of a KNOWN hit | `mcp__semble_code__find_related` |
| "every/all/how many", identifier, literal, regex, path, verifying a hit | `rg -l`/`-c` (semble lost 2/5) |

top-k is a ranked sample, not a list: "every/all" is unanswerable in principle.

```json
{"query":"how sessions are persisted","repo":"/abs/root","top_k":5,"max_snippet_lines":10}
```

`repo` REQUIRED on both — absolute project root or `https://` URL, never inferred. Hits carry
`file_path,start_line,end_line,score,content`; no `line` field — open at `start_line`, and pass it to
`find_related` as `{file_path, line, repo}` (repo-relative, 1-indexed).

ONE search per question, then read the file; full chunk = re-call with `max_snippet_lines=null`.
!=re-running an equivalent query through `rg`/Grep.

Indexed (`--content code docs config`): source, config, prose (`.md .rst .adoc .org .tex .html`).
NEVER indexed: `.json .json5 .csv .tsv .psv .mdx .txt` -> `rg`. `.json` matters most — hook registrations,
manifests, `package.json`, `settings.json`, OpenAPI: semble returns prose *about* them, never them.

Duplicate trees are not deduplicated (same text 3x -> `.sembleignore`); no watcher, index built in-call and
cached; !=shell `semble search` with another `--content` set — cache keyed by path alone, consumers rebuild
over each other.
