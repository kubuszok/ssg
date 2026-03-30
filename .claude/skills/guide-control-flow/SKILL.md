---
description: Load boundary/break patterns for replacing return, break, and continue statements in SSG code
---

Load the boundary/break control flow guide.

$READ docs/contributing/control-flow-guide.md

Use these patterns to replace:
- `return value` → `scala.util.boundary { ... boundary.break(value) ... }`
- `break` (loop) → `scala.util.boundary { while (cond) { ... boundary.break() } }`
- `continue` → `while (cond) { scala.util.boundary { ... boundary.break() } }`
