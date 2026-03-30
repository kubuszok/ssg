Convert a source file to idiomatic Scala 3 for SSG.

Argument: `$ARGUMENTS` — path to the original source file (in original-src/)

## Procedure

1. Determine the source library and language from the path:
   - `original-src/flexmark-java/...` → Java, ssg-md module
   - `original-src/liqp/...` → Java, ssg-liquid module
   - `original-src/dart-sass/...` → Dart, ssg-sass module
   - `original-src/jekyll-minifier/...` → Ruby, ssg-html module

2. Read the source file from the local submodule (**NEVER fetch from GitHub**)

3. Load the appropriate conversion skill:
   - Java: `/guide-conversion` with Java rules
   - Dart: `/guide-conversion` with Dart rules
   - Ruby: `/guide-conversion` with Ruby rules

4. Also load `/guide-code-style` for formatting conventions

5. Convert the file following the loaded rules

6. Write the Scala file to the correct module location

7. Update tracking:
   ```
   ssg-dev db migration set <source_path> --status ai_converted
   ```

8. Run compilation check:
   ```
   ssg-dev build compile --module <module>
   ```

## Important

**Do NOT use shell commands directly.** Use `ssg-dev` commands or dedicated tools
(Grep, Glob, Read, Edit) for all operations.
