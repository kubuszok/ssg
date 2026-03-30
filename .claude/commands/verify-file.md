Verify an SSG Scala file after conversion.

Argument: `$ARGUMENTS` — path to the SSG Scala file

## Procedure

1. Read the file with the Read tool

2. Quick checks:
   - License header present?
   - No `return` keyword?
   - No raw `null` (should use `Nullable[A]`)?
   - `final case class` used?
   - Split packages?

3. Compile check:
   - Determine module from path
   - `ssg-dev build compile --module <module>`

4. If tests exist, run them:
   - `ssg-dev test unit --module <module>`

5. Report status

## Important

**Do NOT use shell commands directly.** Use `ssg-dev` commands or dedicated tools.
