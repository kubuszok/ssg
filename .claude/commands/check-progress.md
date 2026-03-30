Show migration progress for the SSG project, optionally filtered by library or module.

## Procedure

1. Run the status command:

   Overall summary:
   ```
   ssg-dev db migration stats
   ```

   Filtered by library (e.g., `flexmark`):
   ```
   ssg-dev db migration list --lib flexmark
   ```

   Filtered by module (e.g., `ssg-md`):
   ```
   ssg-dev db migration list --module ssg-md
   ```

2. If `$ARGUMENTS` is provided, use it as a filter:
   ```
   ssg-dev db migration list --lib $ARGUMENTS
   ```

3. Summarize:
   - Total files per status (not_started, in_progress, ai_converted, verified, idiomatized, skipped)
   - Percentage complete per library
   - List any files with notes indicating issues

## Important

**Do NOT use shell commands directly.** Use `ssg-dev db migration` commands or Read the TSV file.
