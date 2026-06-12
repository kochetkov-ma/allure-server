# backlog/ -- ungroomed inbox

Dump zone. Drop raw ideas, pasted error logs, "look into X later", half-thoughts here
as `*.md` files. No gate, no format required -- just get it out of your head and into a file.

These are NOT tasks yet. They become tasks (or get trashed) during **grooming**.

## Grooming (periodic)

Run a groom pass at the start of a session or when this folder exceeds ~10 items. For each file:

- **Promote** -> a real `todo` task: mint an id, create a task file from `../TASK_TEMPLATE.md`
  under `../todo/`, add it to `../board.md`, delete the backlog file.
- **Merge** -> fold into an existing task's `## Notes`, delete the backlog file.
- **Trash** -> noise / already done / out of scope: delete the backlog file.

Never leave a groomed item here. Full procedure: `../TRACKER.md` §7.

The task-board dashboard skill can run a groom pass for you.

(This README is the only permanent file here; everything else is transient.)
