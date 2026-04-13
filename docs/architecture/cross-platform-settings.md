# Cross-Platform Build Settings

## Validated Toolchain

| Tool | Version | Purpose |
|------|---------|---------|
| Scala | 3.8.2 | Language version |
| sbt | 1.12.6 | Build tool |
| sbt-projectmatrix | 0.11.0 | Cross-platform compilation |
| sbt-scalajs | 1.20.2 | Scala.js compiler plugin |
| sbt-scala-native | 0.5.10 | Scala Native compiler plugin |
| sbt-scalafmt | 2.5.4 | Code formatting |
| sbt-scalafix | 0.14.6 | Linting and refactoring |
| MUnit | 1.2.3 | Test framework |
| MUnit ScalaCheck | 1.0.0 | Property-based testing |

## Compiler Flags

All platforms share the same flags:

```
-deprecation -feature -no-indent -Werror
-Wimplausible-patterns -Wrecurse-with-default -Wenum-comment-discard
-Wunused:imports,privates,locals,patvars,nowarn
-Wconf:cat=deprecation:info
```

## Platform-Specific Notes

### JVM
- Fork enabled for test isolation
- No special dependencies (all modules are pure Scala)

### Scala.js
- No DOM dependencies (SSG is not a browser application)
- May need `@JSExport` annotations if publishing as npm packages

### Scala Native
- No C library dependencies (all modules are pure Scala)
- Future: tree-sitter integration may require native libs
- No multithreading assumptions in core modules

## Platform-Specific Source Directories

If platform-specific code is needed, use:
```
ssg-md/src/main/scalajvm/     # JVM-specific
ssg-md/src/main/scalajs/      # Scala.js-specific
ssg-md/src/main/scalanative/  # Scala Native-specific
```

sbt-projectmatrix automatically includes these based on the platform axis.

## Known Cross-Platform Concerns

1. **Regex**: `java.util.regex` works on JVM and Scala.js (polyfill). For Scala Native,
   ensure patterns use POSIX-compatible syntax.

2. **File I/O**: Not available on Scala.js. SSG modules should accept `String` inputs
   rather than file paths. File I/O goes in the top-level `ssg` module (JVM/Native only).

3. **Reflection**: Not available on Scala Native or Scala.js. Avoid `Class.forName`,
   `TypeTag`, etc. Use compile-time mechanisms (inline, macros) instead.

4. **Threads**: Limited on Scala Native. Use single-threaded algorithms.

5. **JavaScript interop**: Future `ssg-highlight` module may use tree-sitter-wasm on
   Scala.js. Plan for platform-specific implementations behind a common trait.
