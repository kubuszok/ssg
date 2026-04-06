# SSG Shortcuts

Scratchpad for cross-agent coordination on the `sass-port` branch.

## Recent work

### Import infrastructure (ImportCache / StylesheetGraph / PackageImporter)

- `ssg-sass/src/main/scala/ssg/sass/ImportCache.scala` — working cache:
  - `canonicalize(url, baseImporter?, baseUrl?, forImport?)` walks
    `baseImporter :: importers` and returns the first resolver.
  - `importCanonical(importer, canonicalUrl)` loads + parses via `ScssParser`
    and memoizes by canonical URL so repeat loads don't re-parse.
- `ssg-sass/src/main/scala/ssg/sass/StylesheetGraph.scala` — now tracks
  directed edges between canonical URLs with `addEdge(from, to)` and
  `wouldCycle(from, to)`; returns false instead of introducing a cycle.
- `ssg-sass/src/main/scala/ssg/sass/importer/Importer.scala` — `PackageImporter`
  accepts `Map[String, String]` (package name -> root path) and a delegate
  `Importer`; rewrites `pkg:name/rest` -> `<root>/rest` and delegates.
  `NodePackageImporter` is left as a stub.
- `ssg-sass/src/main/scala/ssg/sass/visitor/EvaluateVisitor.scala` —
  `_loadDynamicImport`, `_visitFileUseRule`, and `visitForwardRule` now go
  through an `_effectiveImportCache` (supplied or lazily-built) so multiple
  `@use` of the same URL parse exactly once. An `_activeImports` set provides
  silent cycle-breaking for `@import` chains (matches existing `_loadedUrls`
  semantics).
- Tests: cross-platform `ssg-sass/src/test/scala/ssg/sass/ImportSuite.scala`
  now hosts `ImportCacheSuite` with a `CountingMemoryImporter` for dedupe /
  cycle / `pkg:` coverage. JVM-only `ImportSuite` filesystem tests unchanged.

All 3 platforms: JVM 392, JS 375 (+2 ignored), Native 375 (+2 ignored), green.

### Cleanup pass: extend specificity, env closures, callable name, configuration

- `ssg-sass/src/main/scala/ssg/sass/extend/ExtensionStore.scala` —
  `extendComplex` now applies the "second law of extend": a generated
  complex selector is dropped unless `merged.specificity >= original.specificity`.
  Uses the existing `ComplexSelector.specificity` lazy val.
- `ssg-sass/src/main/scala/ssg/sass/Environment.scala` —
  - `closure()` is now a real snapshot: clones variables, variableNodes,
    functions, mixins, namespaces, globalVarNames, and `_content` into a
    fresh `Environment`. Mutations after the snapshot do not leak.
  - `global()` returns `Environment.withBuiltins()` populated with any
    variables tracked in the new `globalVarNames` set.
  - New `setGlobalVariable(name, value, nodeWithSpan?)` records the name
    in `globalVarNames` so it survives `global()`.
- `ssg-sass/src/main/scala/ssg/sass/Callable.scala` —
  `UserDefinedCallable.name` is now a cached `val` (was `def`); still
  pulls from `CallableDeclaration.name` when the declaration is one,
  with `"user-defined"` as the unreachable fallback.
- `ssg-sass/src/main/scala/ssg/sass/Configuration.scala` —
  - Added `isImplicit: Boolean` to the primary constructor.
  - `throwErrorForUnknownVariables()` throws a `SassException` listing
    the unused `$names`; implicit configs are silently allowed (these
    come from forwarded `with` clauses).
  - `Configuration.implicitConfig(values)` now sets `isImplicit = true`.
- `ssg-sass/src/main/scala/ssg/sass/visitor/SerializeVisitor.scala` —
  `visitCssDeclaration` records two source-map entries per declaration:
  one for the property name and one for the value, so debuggers can
  highlight either side.
- Tests: `ssg-sass/src/test/scala/ssg/sass/CleanupSuite.scala` covers
  closure isolation (vars + functions), `global()` behavior, both
  branches of `throwErrorForUnknownVariables` (explicit/implicit/empty),
  `UserDefinedCallable.name` via `@function` end-to-end, and the second
  law of extend round-trip. 8/8 green on JVM, JS, Native.

## Areas under parallel work — do not touch

- `extend/ExtensionStore`, `extend/Extension`, `extend/ExtendMode`
- `ast/selector/*`
- `parse/SelectorParser`
- `EvaluateVisitor` `_applyExtends` / `visitExtendRule`
