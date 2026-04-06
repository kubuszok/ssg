# ssg-sass Shortcuts and Stubs Tracker

This file tracks all incomplete implementations in `ssg-sass`. The initial port
created compiling skeletons for the entire dart-sass codebase; this document
catalogs what still needs real implementation.

**Status:** 85 distinct stubs across ~50 files. Tests pass (167/167) but only
exercise the fully-ported value/utility/AST layers — not the parser, evaluator,
or serializer.

## Legend

- **CRITICAL** — Foundation methods; nothing works without them
- **HIGH** — Core algorithms; called by critical paths
- **MEDIUM** — Helper methods, less central
- **LOW** — Cosmetic / deferred features
- ✅ — Implemented
- ⚠️  — Partial / approximate
- ❌ — Stub (throws or returns default)

---

## CRITICAL — Parser Foundation (38 items)

### `parse/Parser.scala` — base tokenizer ✅ IMPLEMENTED
- ✅ `whitespace(consumeNewlines)`
- ✅ `whitespaceWithoutComments(consumeNewlines)`
- ✅ `spaces()`
- ✅ `scanComment()` + `silentComment()` + `loudComment()`
- ✅ `identifier(normalize, unit)` + `identifierBody()`
- ✅ `scanIdentifier(text, caseSensitive)`
- ✅ `expectIdentifier(text, name)`
- ✅ `lookingAtIdentifier(forward)` + `lookingAtIdentifierBody()`
- ✅ `string()` with escape sequences
- ✅ `declarationValue(allowEmpty)` with bracket balancing
- ✅ `expectWhitespace()`
- ✅ `escape()` + `escapeCharacter()` (hex escapes, unicode)
- ✅ `naturalNumber()`
- ✅ `variableName()`
- ✅ `Parser.parseIdentifier(text)`
- ✅ `Parser.isIdentifier(text)`
- ✅ `Parser.isVariableDeclarationLike(text)`
- ✅ `wrapSpanFormatException` — now rewrites StringScannerException

### `parse/StylesheetParser.scala` ⚠️  MINIMUM VIABLE
- ✅ `parse()` — full stylesheets with style rules, variables, comments, generic @-rules
- ✅ `parseExpression()` — numbers, strings, booleans, null, variables
- ✅ `parseNumber()` — with units
- ✅ `parseVariableDeclaration()` — with !default/!global flags
- ❌ `parseUseRule()` — still throws (UseRule factory incomplete)
- ❌ `parseSignature(requireParens)` — medium
- ⚠️  Expression parsing is TEXT-BASED — collects raw text then pattern-matches.
  Whitespace-separated arithmetic operators `+ - * / %` and unary minus on
  variables/function calls are now recognized and produce real
  `BinaryOperationExpression` / `UnaryOperationExpression` nodes (with operator
  precedence). A proper tokenizer for tight-binding operators (`10px+5px`),
  function calls, interpolation `#{...}`, and space-separated lists is still TODO.
- ⚠️  Style rule selectors stored as plain Interpolation (no interpolation parsing yet).

### `parse/ScssParser.scala` ✅ IMPLEMENTED
- ✅ `styleRuleSelector()` — collects raw selector text
- ✅ `expectStatementSeparator(name)`
- ✅ `atEndOfStatement()`
- ✅ `lookingAtChildren()`
- ✅ `scanElse(ifIndentation)`
- ✅ `children(child)` — block parsing with `{...}`
- ✅ `statements(statement)` — top-level sequence

### `parse/SassParser.scala` — same 7 methods as ScssParser, indented variants

### `parse/SelectorParser.scala`
- ❌ `parse()` — medium
- ❌ `parseComplexSelector()` — medium
- ❌ `parseCompoundSelector()` — medium
- ❌ `parseSimpleSelector()` — large (many pseudo-class variants)

### `parse/MediaQueryParser.scala`
- ❌ `parse()` — medium

### `parse/KeyframeSelectorParser.scala`
- ❌ `parse()` — small

### `parse/AtRootQueryParser.scala`
- ❌ `parse()` — small

---

## HIGH — Evaluator and Serializer

### `visitor/EvaluateVisitor.scala` ⚠️  MVP IMPLEMENTED
- ✅ `run(stylesheet)` — builds CSS tree via ModifiableCssStylesheet
- ✅ `runExpression(stylesheet, expression)`
- ✅ All 17 expression visitor methods (binary/unary ops, booleans, numbers, strings, lists, maps, variables, functions, if, interpolation)
- ✅ Statement builders: style rules, declarations, variables (guarded + global), control flow (@if/@for/@each/@while), comments, generic @-rules
- ✅ Callables: @function/@mixin/@include/@return/@content with UserDefinedCallable dispatch
- ✅ @media/@supports/@at-root rules building ModifiableCssMediaRule/SupportsRule
- ✅ @import (static)
- ⚠️  @use/@forward — recorded but no module loading (needs ImportCache)
- ⚠️  @extend — no-op (needs ExtensionStore integration)
- ⚠️  Function call dispatch: built-in functions not registered; unknown functions fall back to plain CSS
- ⚠️  Parameter binding: basic; rest/keyword-rest args deferred
- ⚠️  Selector parent expansion (`&`) returns SassNull — needs selector parsing

### `visitor/SerializeVisitor.scala` ✅ MVP IMPLEMENTED
- ✅ `serialize(node)` — expanded + compressed output styles
- ✅ All 9 visit methods (stylesheet, style rule, declaration, comment,
  at-rule, media rule, supports rule, import, keyframe block)
- ⚠️ Source map generation not implemented (returns None)
- ⚠️ Value formatting uses `Value.toCssString` default — needs per-type
  custom formatting (trailing zeros, color shorthand, etc.)

---

## HIGH — Selector Unification & Extend

### Basic @extend ✅ WORKING
- ✅ `visitExtendRule` records target/extender pairs
- ✅ `_applyExtends` walks the CSS tree after evaluation and textually
  rewrites style rule selectors to add extender-replaced variants
- ✅ `@extend .button` appends `.primary` to matching selectors
- ⚠️ **Textual rewrite only** — no selector AST unification

### Still stubbed (not needed for basic use)
- ⚠️ `extend/ExtendFunctions.scala` — `unifyComplex`, `unifyCompound`, `weave`, `paths`
- ⚠️ `extend/ExtensionStore.scala` — full store-based extend with media context
- ⚠️ `ast/selector/*.scala` — `isSuperselector`, `unify` methods
- ⚠️ "Second law of extend" (specificity-based trimming)

---

## HIGH — Environment & Modules (10 items)

### `Environment.scala`
- ⚠️ `closure()` — small (returns `this`; needs deep copy)
- ⚠️ `global()` — trivial

### `Module.scala`
- ❌ `BuiltInModule.css` — intentionally throws
- ⚠️ `ForwardedView` — medium (no shown/hidden/prefix filtering)
- ⚠️ `ShadowedView` — small (no shadowing logic)

### `Callable.scala`
- ⚠️ `function/mixin` factories — small (don't parse arg signatures)
- ⚠️ `overloadedFunction` — medium (just picks first overload)
- ⚠️ `UserDefinedCallable.name` — trivial (returns "user-defined")

### `Configuration.scala`
- ⚠️ `throwErrorForUnknownVariables()` — small
- ⚠️ `implicitConfig(values)` — trivial

### `EvaluationContext.scala`
- ⚠️ `current` — medium (zone-style propagation)

---

## HIGH — Import Resolution (partial)

### `ImportCache.scala`
- ❌ `canonicalize(url, ...)` — ImportCache still unused; direct importer in Evaluator instead
- ❌ `importCanonical(...)` — ImportCache still unused

### `importer/Importer.scala` + `src/main/scala-jvm/.../FilesystemImporter.scala`
- ✅ `FilesystemImporter.canonicalize(url)` — partials, extensions, index files (JVM-only)
- ✅ `FilesystemImporter.load(url)` — file I/O via `java.nio.file` (JVM-only)
- ❌ `PackageImporter` — stub (package config parsing)
- ❌ `NodePackageImporter` — stub (node_modules traversal)

### `StylesheetGraph.scala`
- ⚠️ `addCanonical(...)` — medium (circular dep detection)

### EvaluateVisitor `@import` dynamic loading
- ✅ `_loadDynamicImport(url)` — resolves via importer, parses, evaluates inline
- ✅ Cycle prevention via `_loadedUrls`
- ✅ Variables/functions/mixins propagate across @import boundary

### EvaluateVisitor `@use` module loading ✅ IMPLEMENTED
- ✅ `visitUseRule` loads module via importer, evaluates in fresh environment
- ✅ Default namespace from URL basename (`@use "colors"` → `colors.*`)
- ✅ Explicit namespace (`@use "t" as th` → `th.*`)
- ✅ Flat merge (`@use "vars" as *`) copies variables/functions/mixins
- ✅ `namespace.$var` and `namespace.fn()` parsing in StylesheetParser
- ✅ `Environment.namespaces` + `getNamespacedVariable` / `getNamespacedFunction`
- ⚠️ No `with (...)` configuration support

---

## HIGH — Built-in Functions (6/8 implemented)

### `functions/*.scala`
- ❌ `ColorFunctions` — **still stub** (large, needs color conversion)
- ✅ `MathFunctions` — abs/ceil/floor/round/max/min/percentage/div/unit/unitless/comparable
- ✅ `StringFunctions` — unquote/quote/str-length/to-upper-case/to-lower-case/str-insert/str-index/str-slice
- ✅ `ListFunctions` — length/nth/set-nth/join/append/zip/index/list-separator/is-bracketed
- ✅ `MapFunctions` — map-get/map-merge/map-remove/map-keys/map-values/map-has-key
- ✅ `MetaFunctions` — type-of/inspect/feature-exists/variable-exists/function-exists
- ❌ `SelectorFunctions` — **still stub** (needs selector parser)
- ✅ `Functions.scala` (barrel) — aggregates modules, `lookupGlobal(name)`
- ✅ `Environment.withBuiltins()` — pre-populates environment with global callables
- ✅ StylesheetParser recognizes `name(args)` as `FunctionExpression`

### `visitor/FindDependenciesVisitor.scala`
- ⚠️ `visitIncludeRule` — handles meta.load-css with literal strings (TODO)

---

## LOW — Compile Orchestration (2 items)

### `Compile.scala`
- ❌ `compileString(source)` — trivial once parser/evaluator/serializer exist
- ❌ `compile(path)` — trivial (file I/O + compileString)

---

## Implementation Order

1. **Parser tokenizer methods** (`Parser.scala`) — foundation for everything
2. **StylesheetParser.parse()** + ScssParser overrides — needed to get an AST
3. **SelectorParser.parse()** — selector parsing
4. **EvaluateVisitor.run()** — evaluator (largest piece)
5. **SerializeVisitor.serialize()** — output
6. **Compile.compileString()** — wires it all together
7. **Built-in functions** — math/string first, color last
8. **Extend algorithm** — unifyComplex, weave (parallel to evaluator)
9. **Import resolution** — filesystem importer
