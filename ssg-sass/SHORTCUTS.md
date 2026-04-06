# ssg-sass Shortcuts and Stubs Tracker

This file tracks all incomplete implementations in `ssg-sass`. The initial port
created compiling skeletons for the entire dart-sass codebase; this document
catalogs what still needs real implementation.

**Status:** Parser, evaluator, serializer, and most built-in function modules
are working end-to-end. Tests pass (478 JVM / 457 JS / 457 Native) and
exercise the full Compile → AST → Evaluate → Serialize pipeline including
@import/@use/@forward (with `with (...)` config), @mixin/@function/@include
(positional + named + `$args...` + `$kwargs...` rest), control flow,
interpolation, lazy `if(...)` ternary, and the color/math/list/map/string/
meta/selector built-ins. Remaining stubs are concentrated in selector AST
unification, the ExtendStore, the dedicated SelectorParser/MediaQueryParser/
AtRootQueryParser, PackageImporter/NodePackageImporter, and a handful of
edge-case helpers.

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
  `a-b`) are preserved. Comparison operators (`== != < <= > >=`) and logical
  operators (`and`, `or`, `not`) are also recognized in both space-split and
  tight-binding paths, with the standard Sass precedence (logical < comparison
  < arithmetic). String concatenation via `+` (`"a" + "b"`, `"v" + 1`) works
  through `Value.plus`'s string fallback. The `if($cond, $t, $f)` ternary is
  parsed as a `LegacyIfExpression` (the parser's `_tryParseFunctionCall`
  short-circuits the bare `if(...)` form), so the unchosen branch is never
  evaluated. The `MetaFunctions.global` `if` callable remains as a fallback
  for indirect calls (`call(get-function("if"), ...)`). A proper tokenizer
  for function calls, interpolation `#{...}`, and
  space-separated lists is still TODO.
- ✅ `@mixin` / `@function` / `@include` — parsed with positional parameters,
  default values, and a trailing rest parameter (`$args...`). `@include` call
  sites accept a trailing rest argument (`$list...`) that is splatted into
  positional parameters when the value is a `SassList`. **Keyword arguments**
  (`foo($name: value, $other: 10)`) are now supported at call sites for
  both `@include` and text-based function calls, including mixed
  positional + keyword. `_bindParameters` binds named args by parameter
  name after filling positional slots, and the `$args..., $kwargs...`
  parameter form is supported: leftover positional args become a
  `SassArgumentList` (carrying any unmatched named args as keywords),
  while `$kwargs...` is bound to a `SassMap` of leftover named args.
- ✅ `@content` block argument passing — `@content($a, $b, ...)` parses as
  a `ContentRule` with an `ArgumentList`, and `@include foo(args) using
  ($p1, $p2) { body }` parses the `using` clause into a `ContentBlock`
  whose `ParameterList` carries the declared content-block parameters
  (with default-value support). `visitContentRule` evaluates the argument
  expressions in the mixin's environment and binds them to the content
  block's parameters via `_bindParameters` before evaluating the body in
  a fresh scope. Bare `@content;` (no args) and bare `@include foo { ... }`
  (no `using`) continue to work unchanged.
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
- ✅ `@at-root` — parsed in `_atRule`. Supports the bare form
  `@at-root { ... }` and the selector form `@at-root .sel { ... }`.
  In the selector form the body is wrapped in a fresh `StyleRule`
  inside the `AtRootRule`, so the evaluator's existing
  `visitAtRootRule` (which reparents to the stylesheet root and clears
  `_styleRule`) emits the children at the top level regardless of how
  deeply the `@at-root` is nested. `@at-root (with/without: ...)`
  queries are still ignored.
- ✅ `@each` — parsed in `_atRule`. The variable list accepts
  one or more comma-separated `$name`s followed by `in <expression>`,
  producing an `EachRule` with `variables: List[String]`. The iterable
  expression is parsed via `_parseSimpleExpression`, which now also
  recognizes parenthesized comma lists and map literals
  (`(a, b, c)`, `(k: v, k2: v2)`, `(1 2, 3 4)`) as `ListExpression` /
  `MapExpression`. `visitEachRule` destructures each element against
  the declared variables: iterating a `SassMap` yields `(key, value)`
  pairs via `SassMap.asList`, and iterating a list of lists binds each
  sub-list's elements positionally (missing slots become `null`).
- ✅ `@debug expr;` / `@warn expr;` / `@error expr;` — parsed in `_atRule`
  as `DebugRule` / `WarnRule` / `ErrorRule`. The expression is evaluated
  via the normal expression visitor (variables, arithmetic, interpolation,
  function calls, etc.) and rendered as its CSS string form (unquoted for
  `SassString`). `@debug` appends `"DEBUG: <msg>"` to
  `EvaluateResult.warnings` (also forwarded through `_logger.debug`);
  `@warn` appends `"WARNING: <msg>"` (also `_logger.warn`); `@error`
  throws a `SassException` with the rendered message and the rule's
  span, aborting compilation. Surfaced through
  `CompileResult.warnings`.
- ✅ `@charset "UTF-8";` — handled by the generic at-rule path and
  round-tripped verbatim. (dart-sass strips `@charset` and emits a BOM
  in compressed mode for non-ascii output; we currently preserve it
  as-is.)
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

### `parse/SassParser.scala` ✅ MVP IMPLEMENTED
- ✅ `parse()` — overridden to translate the indented Sass source to its
  SCSS equivalent (`SassParser.indentedToScss`) and delegate to
  `ScssParser`. Handles variables, declarations, nested style rules,
  simple @-rules, and `//`/`/* */` comments. Indentation drives block
  open/close; statements terminate at newlines.
- ⚠️  Override hooks (`styleRuleSelector`, `expectStatementSeparator`,
  `atEndOfStatement`, `lookingAtChildren`, `scanElse`, `children`,
  `statements`) are stubs (`UnsupportedOperationException`) since the
  preprocessing approach skips StylesheetParser's statement loop.
- ❌ Multi-line selector continuations, `===`-style flags, and other
  edge cases of the indented syntax are not yet supported.
- ✅ `Compile.compileString` accepts `syntax: Syntax = Syntax.Scss`;
  `Syntax.Sass` selects `SassParser` instead of `ScssParser`.

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
- ⚠️  @forward — text-based MVP: load + merge into current env, with `show`/`hide` filtering, `as prefix-*` rename for variables/functions/mixins, and `with (...)` configuration that pre-sets variables in the loaded module's environment so `!default` declarations honor overrides. No module-level isolation; built-in callables are not re-forwarded.
- ⚠️  @extend — no-op (needs ExtensionStore integration)
- ⚠️  Function call dispatch: built-in functions not registered; unknown functions fall back to plain CSS
- ✅  First-class CSS calc/min/max/clamp — `visitFunctionExpression` intercepts
  the four names (when no namespace), walks the argument expressions translating
  `BinaryOperationExpression` Plus/Minus/Times/DividedBy nodes into
  `CalculationOperation`s and evaluating leaves through the normal expression
  visitor, then dispatches to `SassCalculation.calc/min/max/clamp` which
  simplifies. Compatible numeric operands collapse (`calc(10px + 5px)` → `15px`,
  `max(10px, 20px)` → `20px`); incompatible/variable operands round-trip as a
  `SassCalculation` value whose new `toCssString` override emits
  `name(arg1, arg2)` form with operator precedence-aware parenthesization.
  Falls back to the previous plain-CSS rendering on any conversion failure.
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
- ✅ `visitExtendRule` records target/extender pairs into a media-scoped
  extension store. The nearest enclosing `@media` rule (if any) is used
  as the store key, so extensions inside a `@media` block only apply to
  rules in the same block and never leak out to top-level rules with
  the same selector.
- ✅ `_applyExtends` walks the CSS tree after evaluation, switching the
  active media scope when it descends into a `ModifiableCssMediaRule`,
  and textually rewrites style rule selectors to add extender-replaced
  variants drawn from the matching store.
- ✅ `@extend .button` appends `.primary` to matching selectors.
- ✅ **`!optional` flag**: `visitExtendRule` records a `PendingExtend`
  check for every extend call site. After `_applyExtends`, any target
  that was never found in its own media scope raises
  `SassException("The target selector was not found. Use \"@extend ... !optional\" to avoid this error.")`
  unless the extend was marked `!optional`.
- ✅ **Compound/complex extend targets**: a target selector list whose
  complex components are not a single simple selector (e.g. `.a.b`,
  `.a .b`) raises
  `SassException("compound selectors may no longer be extended.")`
  at evaluation time, matching dart-sass.
- ✅ `CompileResult.warnings: List[String]` is wired through
  `EvaluateResult.warnings`. Currently always empty — with correct
  media scoping no cross-media-extend warning is emitted — but the
  channel is in place for future logger output.
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
- ✅ `PackageImporter` — rewrites `pkg:name/rest` URLs through a packages map and delegates to a wrapped importer
- ✅ `NodePackageImporter` (JVM-only, `src/main/scala-jvm/.../NodePackageImporter.scala`) — walks upward from the entry point to find `node_modules/<pkg>` (including scoped `@scope/name`), reads `package.json` for `sass`/`style`/`main` entry points via a tiny hand-written string-field scanner, and resolves the loaded path through `FilesystemImporter` so partials, extensions, and `_index.scss` all work. Returns `Nullable.empty` for unknown packages or non-`pkg:` URLs. Wires straight into `Compile.compileString(..., importer = NodePackageImporter(rootDir))` via the existing `Importer` trait.

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
- ✅ `ColorFunctions` — rgb/rgba/hsl/hsla, accessors (red/green/blue/hue/saturation/lightness/alpha), manipulations (lighten/darken/saturate/desaturate/mix/invert/grayscale/complement, opacify/fade-in, transparentize/fade-out, adjust-hue, change-color, adjust-color, scale-color, `rgba($color, $alpha)` overload). Module-namespaced via `@use "sass:color"` — `color.red`, `color.adjust`, etc. resolve through built-in module dispatch in `visitUseRule`. Serializer now emits non-opaque legacy RGB colors as `rgba(r, g, b, a)` instead of falling back to the default `Value.toCssString`.
- ✅ `MathFunctions` — abs/ceil/floor/round/max/min/percentage/div/unit/unitless/comparable/random/sqrt/pow/sin/cos/tan/asin/acos/atan/log/clamp/hypot
- ✅ `StringFunctions` — unquote/quote/str-length/to-upper-case/to-lower-case/str-insert/str-index/str-slice/unique-id, `string.split` (module-only)
- ✅ `ListFunctions` — length/nth (supports negative indices)/set-nth/join/append/zip/index/list-separator/is-bracketed, `list.slash` (module-only)
- ✅ `MapFunctions` — map-get/map-merge/map-remove/map-keys/map-values/map-has-key, `map.set`/`map.deep-merge`/`map.deep-remove` (module-only)
- ✅ `MetaFunctions` — type-of/inspect/feature-exists, plus the `-exists` family (`variable-exists`, `function-exists`, `mixin-exists`, `global-variable-exists`) which now consult the active `Environment` via the `CurrentEnvironment` holder set by `EvaluateVisitor`. `keywords($args)` surfaces a `SassArgumentList`'s keyword map (populated by `_bindParameters` for `$kwargs...`/captured-named bindings). `module-variables`/`module-functions` enumerate the active env's namespaces (and fall back to the static `sass:` module table); `module-functions` returns real `SassFunction` values keyed by name. `get-function($name, $css: false, $module: null)` and `get-mixin($name, $module: null)` look up the active env (or a `@use`d namespace, falling back to the built-in `sass:` module table) and return a `SassFunction`/`SassMixin` wrapping the resolved `Callable`; an unknown name throws `SassScriptException`. `call($function, $args...)` accepts either a `SassFunction` or a plain string function name (legacy form) and dispatches through a new `CurrentCallableInvoker` holder set by `EvaluateVisitor`, which routes to the visitor's `_invokeCallable` helper for both `BuiltInCallable` (with named-arg merging via `_mergeBuiltInNamedArgs`) and `UserDefinedCallable[FunctionRule]` (`_runUserDefinedFunction`); a single trailing `SassArgumentList` is splatted into positional+keyword args. `apply($mixin, $args...)` is a stub that throws `"meta.apply is not yet supported"` — invoking a mixin from a built-in needs a fresh statement-visitor entry point and is deferred. `content-exists` is still a placeholder pending mixin-call-stack tracking.
- ⚠️  `SelectorFunctions` — text-based MVP: `selector-append`, `selector-nest`, `selector-extend` (string replace), `selector-unify` (returns null stub). String args only; lists/non-strings return null. No selector AST.
- ✅ `Functions.scala` (barrel) — aggregates modules, `lookupGlobal(name)`
- ✅ `Environment.withBuiltins()` — pre-populates environment with global callables
- ✅ StylesheetParser recognizes `name(args)` as `FunctionExpression`

### `visitor/FindDependenciesVisitor.scala`
- ⚠️ `visitIncludeRule` — handles meta.load-css with literal strings (TODO)

---

## LOW — Compile Orchestration ✅ IMPLEMENTED

### `Compile.scala`
- ✅ `Compile.compileString(source[, OutputStyle])` — wires StylesheetParser → EvaluateVisitor → SerializeVisitor (cross-platform).
- ✅ `CompileFile.compile(path)` (scala-jvm) — file I/O + delegates to `compileString`.

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
