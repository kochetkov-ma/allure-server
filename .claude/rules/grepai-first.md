---
paths:
  - "**/*"
---

> **FIRST** `grepai` mcp for code exploration. Params → MCP descriptions.

## Examples

**search** `query:"user auth"` → `{s:0.89, f:"auth/Login.java", l:"15-45", content:"..."}`
**search+compact** `query:"error handling", limit:10, compact:true` → `{s:0.82, f:"ErrorHandler.java", l:"23-55"}` (no content)
**callers** `symbol:"validateToken"` → `[{f:"AuthFilter.java", l:42, fn:"doFilter"}]`
**callees** `symbol:"processOrder"` → `[{f:"PaymentService.java", l:88, fn:"charge"}]`
**graph** `symbol:"main", depth:2` → `{n:"main", c:[{n:"init", c:[{n:"loadConfig"}]}]}`

## When

| Need | Tool | Params |
|------|------|--------|
| Explore (≤5 results) | search | `limit:5` → read content directly |
| Explore (>5 results) | search | `limit:10, compact:true` → then Read top files |
| Who calls X? | trace_callers | `symbol:"X"` |
| What X calls? | trace_callees | `symbol:"X"` |
| Full dependency tree | trace_graph | `symbol:"X", depth:2` |

## Query Tips

English · 3-7 words · intent not syntax · ✅`"validate credentials"` ❌`"validateUser"`