# ssg-sass Tracker

Living status of the dart-sass → Scala 3 port. For per-file audit detail see
`ssg-dev db audit list --package <pkg>`; for migration status see
`ssg-dev db migration list --lib dart-sass`.

## Current state

- **Tests**: 497 JVM / 477 JS / 477 Native (last recorded)
- **Migration** (`dart-sass`): 279 ported, 4 done, 98 skipped — 381 total, 100% triaged
- **Audit** (all modules): 486 pass, 60 minor_issues, 0 major_issues — 546 files audited
- **Feature-complete for typical SCSS workloads.** The compiler drives the
  full Compile → Parse → Evaluate → Serialize pipeline with @use/@forward,
  @extend, control flow, built-in modules, calc(), custom properties,
  !important, source maps, and the filesystem/package importers.

## Implemented

### Parsing
- `Parser` tokenizer: whitespace, comments, identifiers, strings, escapes,
  declaration values, interpolation-aware scanners
- `ScssParser` + `SassParser` (indented-syntax via a preprocessor that
  translates to SCSS)
- `StylesheetParser`:
  - variables with `!default` / `!global`
  - `@mixin` / `@function` / `@include` with positional, named, defaults,
    rest (`$args...`), keyword rest (`$kwargs...`), `@content($...)` and
    `@include ... using ($...)`
  - `@if` / `@else if` / `@else` / `@for` / `@each` (with destructuring and
    map iteration) / `@while`
  - `@media`, `@supports` (including modern `selector(...)` form),
    `@at-root`, `@keyframes` (+ vendor prefixes)
  - `@debug`, `@warn`, `@error`, `@charset`
  - `@import` (dynamic and static), `@use`, `@forward` with
    `with (...)` / `as prefix-*` / `show` / `hide`
  - `@extend` with `!optional`, compound-target error, media scoping
  - CSS custom properties (`--foo: …;` verbatim values with `#{}` still evaluated)
  - `!important` on declarations
  - `#{expr}` interpolation in values, property names, selectors, strings
  - Arithmetic tokenizer for tight-binding operators (`10px+5px`, `$a*2`)
  - Comparison / logical operators with correct precedence
  - First-class `if($cond, $t, $f)` short-circuit via `LegacyIfExpression`
- `SelectorParser` — real recursive-descent parser producing a
  `SelectorList` AST (complex / compound / simple, pseudo-classes,
  attribute selectors, combinators)
- `MediaQueryParser`, `AtRootQueryParser`, `KeyframeSelectorParser`

### Evaluation
- Full statement and expression visitor tree
- `@use` module loading (default + explicit + `as *` flat merge + `with`)
- `@forward` with show/hide/as-prefix/with
- `@extend` with media-scoped `ExtensionStore`, `!optional`, cross-media
  isolation, compound-target errors
- Nested `@media` / `@supports` / style-rule bubbling
- Parent selector `&` expansion
- Full `Environment` with namespaces and built-ins pre-registered
- `CurrentEnvironment` / `CurrentCallableInvoker` holders so meta functions
  can introspect and dispatch
- First-class `calc()` / `min()` / `max()` / `clamp()` returning
  `SassCalculation`, collapsing compatible operands and round-tripping
  incompatible ones with precedence-aware parens
- `@return`, `@content`, `@debug` / `@warn` / `@error` (the last aborts
  compilation with a `SassException`); warnings surfaced through
  `CompileResult.warnings`

### Values
- `SassNumber` with the full absolute-length / time / angle / frequency /
  resolution conversion table, coercion, arithmetic, comparison
- `SassString`, `SassBoolean`, `SassNull`, `SassList` (with separators and
  brackets), `SassMap` (ordered), `SassArgumentList` with keyword tracking
- `SassFunction`, `SassMixin` as first-class values from meta
- `SassCalculation` with `CalculationOperation` / `CalculationOperator`

### Built-in functions (`sass:*` modules)
- `color` — rgb/rgba/hsl/hsla, accessors, lighten/darken/saturate/
  desaturate/mix/invert/grayscale/complement/opacify/transparentize/
  adjust-hue/change-color/adjust-color/scale-color
- `math` — abs/ceil/floor/round/max/min/percentage/div/unit/unitless/
  comparable/random/sqrt/pow/sin/cos/tan/asin/acos/atan/log/clamp/hypot
- `string` — unquote/quote/length/to-upper/lower/insert/index/slice/
  unique-id/split
- `list` — length/nth (negative indices)/set-nth/join/append/zip/index/
  separator/is-bracketed/slash
- `map` — get/merge/remove/keys/values/has-key, set/deep-merge/deep-remove
- `meta` — type-of/inspect/feature-exists, `*-exists` family,
  `keywords`, `module-variables` / `module-functions`, first-class
  `get-function` / `get-mixin`, `call($fn, $args...)`
- `selector` — AST-backed append/nest/extend/unify/parse/replace/
  is-superselector

### Serialization
- Expanded and compressed output styles, all nine visit methods
- Short hex (`#fff` / `#abc`), named-color collapse, 6-digit hex
- `SassNumber` trailing-zero stripping; compressed-mode leading `.5`
- `rgba(...)` for non-opaque legacy colors
- `!important` formatting per style
- Minimal v3 source maps (opt-in via `sourceMap = true`) — one mapping per
  style rule and declaration, base64 VLQ, no `sourcesContent` /
  `sourceRoot` / `file`

### Imports
- `FilesystemImporter` (JVM): partials, extensions, `_index.scss`
- `PackageImporter` rewriting `pkg:name/rest` through a package map
- `NodePackageImporter` (JVM): walks `node_modules`, scoped packages,
  `package.json` `sass`/`style`/`main`
- `ImportCache` with cycle detection, `StylesheetGraph.addCanonical`
- `MapImporter` (cross-platform) for in-memory import trees

## Still stubbed / partial

- **Color spaces beyond sRGB** — `value/color/*` now has `ColorSpace(s)`,
  `ColorChannel`, `GamutMapMethod`, `InterpolationMethod`, `Conversions`
  tables; round-tripping through the full lab/lch/oklab/oklch/xyz pipeline
  is audit status `pass` but not end-to-end exercised by the test suite.
  Non-RGB color functions may still hit gaps — verify before trusting.
- **`meta.apply($mixin, …)`** — throws
  `"meta.apply is not yet supported"`. Needs a fresh statement-visitor
  entry point to invoke a mixin from a built-in.
- **`content-exists`** — placeholder pending mixin-call-stack tracking.
- **`@extend` selector unification** — textual rewrite only.
  `ExtendFunctions.unifyComplex` / `unifyCompound` / `weave` / `paths`
  remain stubs. The "second law of extend" (specificity trimming) is
  not implemented. Basic cases and `!optional` / compound-target errors
  work.
- **`CssParser` strict mode** — skeleton only. Plain CSS is currently
  parsed through `ScssParser` / `StylesheetParser`.
- **Cross-media `@extend` warnings** —
  `EvaluateResult.warnings` / `CompileResult.warnings` channel exists but
  no message is emitted when an extend is isolated by media scoping.
- **Error-span synthetic placeholders** — some evaluator error paths build
  `FileSpan` values from synthesized sources rather than the original
  input; error messages point at the right token text but may carry a
  placeholder file URL.
- **`SassParser` override hooks** (`styleRuleSelector`,
  `expectStatementSeparator`, `atEndOfStatement`, `lookingAtChildren`,
  `scanElse`, `children`, `statements`) throw
  `UnsupportedOperationException` because the indented-to-SCSS
  preprocessor bypasses the statement loop. Multi-line selector
  continuations are unsupported.
- **StylesheetParser expression tokenizer** — still text-based. Tight
  binding works via a small arithmetic tokenizer, but space-separated
  lists and `fn(a, b)` call parsing live alongside raw-text collection
  rather than a proper lexer. Rare edge cases can mis-split.
- **`FindDependenciesVisitor`** — handles `meta.load-css` with literal
  strings; dynamic load-css is TODO.
- **Selector AST on style rules** — style rules still carry their
  selectors as plain `Interpolation`, not a parsed `SelectorList`.
  `SelectorParser` is used by selector functions and extend-targets but
  not yet for every rule's selector field.

## Next steps (priority order)

1. **End-to-end tests for non-sRGB color spaces** — verify `lab`/`lch`/
   `oklab`/`oklch`/`color(xyz ...)` round-trip through parse → evaluate →
   serialize. Fix fallout in color functions or `SerializeVisitor.formatColor`.
2. **Selector AST on style rules** — parse selectors at style-rule build
   time, drop textual `_expandSelector`, and unlock proper unification.
3. **`ExtensionStore` real unification** — port `unifyComplex` / `weave` /
   `paths` from dart-sass so `@extend` produces the dart-sass output for
   non-trivial compound cases.
4. **`meta.apply`** — add a statement-visitor entry point that can run a
   `UserDefinedCallable[MixinRule]` from a built-in.
5. **StylesheetParser proper expression lexer** — replace the text-based
   collector with a tokenizer covering space-separated lists, function
   calls, interpolation, and unary forms uniformly.
6. **Full v3 source maps** — per-token mappings, `sourcesContent`,
   `sourceRoot`, `file`, and propagation through `@import` boundaries.
7. **CssParser strict mode** — for the (rare) consumers who need to
   reject Sass-only syntax.
8. **Error-span fidelity** — propagate the original `FileSpan` through
   all synthesized expressions so error messages never point at a
   placeholder URL.
