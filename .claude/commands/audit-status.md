Show audit progress for the SSG project.

## Procedure

1. Run the audit stats:
   ```
   ssg-dev db audit stats
   ```

2. If `$ARGUMENTS` is provided, filter by package:
   ```
   ssg-dev db audit list --package $ARGUMENTS
   ```

3. Summarize:
   - Files per status (pass, minor_issues, major_issues)
   - Test coverage percentage
   - Packages with most outstanding issues

## Important

**Do NOT use shell commands directly.** Use `ssg-dev db audit` commands.
