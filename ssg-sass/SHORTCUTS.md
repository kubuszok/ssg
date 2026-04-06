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
  variables/function calls are recognized and produce real
  `BinaryOperationExpression` / `UnaryOperationExpression` nodes (with operator
  precedence). Tight-binding operators (`10px+5px`, `$a*2`, `10px-5px`) are
  now handled via a small arithmetic tokenizer that splits operand/operator
  boundaries even without surrounding spaces. Identifier hyphens (`border-color`,
  `a-b`) are preserved. A proper tokenizer for function calls, interpolation
  `#{...}`, and space-separated lists is still TODO.
- ✅ `@mixin` / `@function` / `@include` — parsed with positional parameters,
  default values, and a trailing rest parameter (`$args...`). `@include` call
  sites accept a trailing rest argument (`$list...`) that is splatted into
  positional parameters when the value is a `SassList`. **Keyword arguments**
  (`foo($name: value, $other: 10)`) are now supported at call sites for
  both `@include` and text-based function calls, including mixed
  positional + keyword. `_bindParameters` binds named args by parameter
  name after filling positional slots. `$kwargs...` is still TODO.
- ✅ `#{expr}` interpolation in expression values — declaration values like
  `width: #{$base * 2}px`, property names like `#{$prefix}-color: red` (and
  mid-name `margin-#{$side}: ...`), and string concatenation
  `"foo-#{$x}-bar"` all parse the inner expression and splice its CSS
  string form into surrounding literal text. Property-name interpolation
  goes through a dedicated `_readInterpolatedName` scanner.
- ⚠️  Style rule selectors stored as plain Interpolation (no interpolation parsing yet).
- ✅ `@media <query> { body }` — parsed in `_atRule`. The query text is
  collected up to the opening `{` while respecting balanced parens,
  `#{...}` interpolations, and string literals, then fed through
  `_parseInterpolatedString` so `#{expr}` inside the query is
  evaluated (e.g. `@media (max-width: #{$bp})`). Child statements are
  parsed via `_children()` and the result becomes a `MediaRule` AST
  node. Nested media rules parse recursively; nested media inside a
  style rule bubbles out in `visitMediaRule` — a clone of the enclosing
  style rule is placed inside the media rule, and the media rule
  attaches to the nearest non-style parent, producing the expected
  Sass output `@media (q) { .a { color: red; } }`.
- ✅ `@supports <condition> { body }` — parsed in `_atRule` reusing
  the same bracket/interpolation-aware condition scanner as `@media`.
  One balanced outer `(...)` layer is stripped before wrapping the
  result in `SupportsAnything(Interpolation)` so
  `_visitSupportsCondition` re-adds a single pair of parens at
  serialize time. Handles `@supports (display: grid)`,
  `@supports (a) and (b)`, and `#{...}` interpolation in the
  condition. `visitSupportsRule` mirrors the media bubbling pattern:
  when nested inside a style rule, the supports rule attaches to the
  nearest non-style parent and a clone of the enclosing style rule
  is placed inside it.
- ✅ `@keyframes <name> { <block>* }` (plus `-webkit-`/`-moz-`/`-o-`/
  `-ms-` prefixed variants) — parsed in `_atRule`. The body is a
  sequence of keyframe blocks where each block is a comma-separated
  selector list followed by a declaration block parsed via
  `_children()`. Selectors `from`/`to` are normalized to
  `0%`/`100%`. The whole rule is represented as a generic `AtRule`
  whose `childStatements` are `StyleRule` nodes with the
  (normalized) keyframe selector text, which the existing
  evaluator/serializer handle without changes.

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
- ✅ @use — module loading via importer (see `@use module loading` below)
- ⚠️  @forward — text-based MVP: load + merge into current env, with `show`/`hide` filtering and `as prefix-*` rename for variables/functions/mixins. No `with (...)` config; no module-level isolation; built-in callables not re-forwarded.
- ⚠️  @extend — no-op (needs ExtensionStore integration)
- ⚠️  Function call dispatch: built-in functions not registered; unknown functions fall back to plain CSS
- ⚠️  Parameter binding: basic; rest/keyword-rest args deferred. Built-in
  callables resolve named arguments against their declared parameter names
  (parsed from the textual signature on `BuiltInCallable`).
- ✅  `@return` inside `@function` bodies — parsed by StylesheetParser and
  propagated via a ReturnSignal caught by `_runUserDefinedFunction`.
- ✅  `@function`/`@mixin` parameter defaults — default expressions are
  parsed with a raw-text collector that stops at the next top-level `,` or
  `)`, so `($a: 1, $b: 2)` no longer over-consumes.
- ✅ Selector parent expansion (`&`) — text-based: `visitSelectorExpression` returns the active style rule's selector as an unquoted SassString, and nested style rules substitute `&` against the parent selector via `_expandSelector`. Full SelectorList value type still deferred.

### `visitor/SerializeVisitor.scala` ✅ MVP IMPLEMENTED
- ✅ `serialize(node)` — expanded + compressed output styles
- ✅ All 9 visit methods (stylesheet, style rule, declaration, comment,
  at-rule, media rule, supports rule, import, keyframe block)
- ⚠️ Source map generation: minimal v3 source map. Opt-in via
  `Compile.compileString(..., sourceMap = true)` and
  `new SerializeVisitor(sourceMap = true)`. The serializer records one
  mapping per emitted style rule and per declaration using the source
  span carried on each `CssNode` (`AstNode.span: FileSpan`), then emits
  a JSON object of the form
  `{"version":3,"sources":[...],"names":[],"mappings":"<vlq>"}` via a
  small inline base64 VLQ encoder (`SerializeVisitor.vlqEncode`).
  `CompileResult.sourceMap` and `SerializeResult.sourceMap` are
  `Nullable[String]`. When `sourceMap=false` (default) the field is
  empty and serialization is unchanged. Limitations: mappings are
  per-rule/declaration only (not per token); selector spans are taken
  from the style rule's own span (not yet from a parsed selector AST);
  there is no `sourcesContent`, `sourceRoot`, or `file` field. Source
  files without a known URL fall back to the literal name `"stdin"`.
- ✅ Value formatting: SassColor (rgb space) emits `#fff`/`#abc` shorthand,
  named colors when shorter (`#ff0000` → `red`), full 6-digit hex otherwise.
  SassNumber strips trailing zeros (`1.50px` → `1.5px`, `3.0` → `3`) via
  `SassNumber.formatNumber`, and compressed mode strips the leading `0` from
  fractional values (`0.5` → `.5`). Non-opaque colors and other value types
  fall back to `Value.toCssString`.

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
- ✅ `with ($var: value, ...)` configuration — pre-sets variables in the module
  environment so `!default` declarations honor overrides

---

## HIGH — Built-in Functions (6/8 implemented)

### `functions/*.scala`
- ✅ `ColorFunctions` — rgb/rgba/hsl/hsla, accessors (red/green/blue/hue/saturation/lightness/alpha), manipulations (lighten/darken/saturate/desaturate/mix/invert/grayscale/complement)
- ✅ `MathFunctions` — abs/ceil/floor/round/max/min/percentage/div/unit/unitless/comparable
- ✅ `StringFunctions` — unquote/quote/str-length/to-upper-case/to-lower-case/str-insert/str-index/str-slice
- ✅ `ListFunctions` — length/nth/set-nth/join/append/zip/index/list-separator/is-bracketed
- ✅ `MapFunctions` — map-get/map-merge/map-remove/map-keys/map-values/map-has-key
- ✅ `MetaFunctions` — type-of/inspect/feature-exists/variable-exists/function-exists
- ⚠️  `SelectorFunctions` — text-based MVP: `selector-append`, `selector-nest`, `selector-extend` (string replace), `selector-unify` (returns null stub). String args only; lists/non-strings return null. No selector AST.
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
