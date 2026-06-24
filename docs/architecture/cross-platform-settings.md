# Cross-Platform Build Settings

## Validated Toolchain

| Tool | Version | Purpose |
|------|---------|---------|
| Scala | 3.8.4 | Language version |
| sbt | 2.0.0 | Build tool |
| sbt-kubuszok | 0.2.3 | Meta-plugin (bundles sbt-scalafmt, sbt-scoverage, sbt-scalajs, sbt-scala-native, sbt-commandmatrix, and more) |
| sbt-projectmatrix | (merged into sbt 2.0) | Cross-platform compilation (no longer a separate plugin) |
| sbt-scalajs | (bundled by sbt-kubuszok) | Scala.js compiler plugin |
| sbt-scala-native | (bundled by sbt-kubuszok) | Scala Native compiler plugin |
| sbt-scalafmt | (bundled by sbt-kubuszok) | Code formatting |
| MUnit | 1.3.3 | Test framework |
| MUnit ScalaCheck | 1.3.0 | Property-based testing |

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
- No special dependencies for most modules; ssg-highlight uses tree-sitter via platform-specific providers (pnm-provider-tree-sitter-desktop on JVM)

### Scala.js
- No DOM dependencies (SSG is not a browser application)
- May need `@JSExport` annotations if publishing as npm packages

### Scala Native
- Most modules have no C library dependencies; ssg-highlight uses sn-provider-tree-sitter for tree-sitter integration via Scala Native FFI
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

5. **JavaScript interop**: ssg-highlight uses wasm-provider-tree-sitter on Scala.js for
   tree-sitter integration. Platform-specific implementations are behind common traits.
