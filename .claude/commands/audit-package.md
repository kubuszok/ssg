Audit all SSG Scala files in a package against their original sources.

Argument: `$ARGUMENTS` — package path (e.g., `md/core` or `liquid/nodes`)

## Procedure

1. Find all Scala files in the package:
   - Use Glob to find `ssg-*/src/main/scala/ssg/$ARGUMENTS/*.scala`

2. For each file, run the audit-file procedure

3. Summarize results:
   - Files audited
   - Pass / minor_issues / major_issues counts
   - Test coverage

## Important

**Do NOT use shell commands directly.** Use `ssg-dev` commands or dedicated tools.
