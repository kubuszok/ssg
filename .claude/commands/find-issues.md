Find code quality issues in the SSG codebase.

## Procedure

1. Run quality scans:
   ```
   ssg-dev quality scan --all --summary
   ```

2. Check for specific patterns:
   - `ssg-dev quality scan --return` — find remaining `return` statements
   - `ssg-dev quality scan --null` — find null comparisons
   - `ssg-dev quality scan --java-syntax` — find Java/Dart syntax remnants
   - `ssg-dev quality scan --todo` — find TODOs and FIXMEs

3. Check issues database:
   ```
   ssg-dev db issues list --status open
   ```

4. Summarize findings and suggest fixes.

## Important

**Do NOT use shell commands directly.** Use `ssg-dev quality` and `ssg-dev db issues` commands.
