Audit an SSG Scala file against its original source.

Argument: `$ARGUMENTS` — path to the SSG Scala file

## Procedure

1. Read the SSG Scala file with the Read tool

2. Identify the original source file:
   - Check the license header for source attribution
   - Or use `ssg-dev compare file` to find the mapping

3. Read the original source file from the local submodule

4. Compare for completeness:
   - All public methods present?
   - All constants/enums present?
   - All inner classes/types present?

5. Check conventions:
   - License header with original source attribution
   - No `return`, `null`, Java/Dart/Ruby syntax remnants
   - Uses `Nullable[A]`, `boundary`/`break` where needed
   - `final case class`, split packages, braces

6. Check tests:
   - Does the original have tests?
   - Are they ported?

7. Record the audit result:
   ```
   ssg-dev db audit set <file_path> --status <pass|minor_issues|major_issues> [--tested yes|no|partial] [--notes "..."]
   ```

## Important

**Do NOT use shell commands directly.** Use `ssg-dev` commands or dedicated tools.
