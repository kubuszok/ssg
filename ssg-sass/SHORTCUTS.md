# ssg-sass Tracker

Living status of the dart-sass → Scala 3 port. For per-file audit detail see
`ssg-dev db audit list --package <pkg>`; for migration status see
`ssg-dev db migration list --lib dart-sass`.

## Current state

- **Tests**: 532 JVM / 511 JS / 511 Native (last recorded)
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
  - **Hex color literals** (`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`) and
    CSS **named color keywords** (`red`, `blue`, `transparent`, …) resolve
    to a `ColorExpression` / `SassColor` at parse time, so
    `color.mix(#ff0000, #0000ff, $space: oklch)` and `lighten(red, 10%)`
    work as expected. `SassColor.toCssString` and `SerializeVisitor` both
    collapse legacy-rgb opaque colors to the shortest of name / short hex
    / full hex, matching dart-sass.
- `SelectorParser` — real recursive-descent parser producing a
  `SelectorList` AST (complex / compound / simple, pseudo-classes,
  attribute selectors, combinators)
- `MediaQueryParser`, `AtRootQueryParser`, `KeyframeSelectorParser`

### Evaluation
- Full statement and expression visitor tree
- `@use` module loading (default + explicit + `as *` flat merge + `with`)
- `@forward` with show/hide/as-prefix/with
- `@extend` with media-scoped `ExtensionStore`, `!optional`, cross-media
  isolation, compound-target errors, AST-level `paths` / `unifyCompound`
  / `unifyComplex` / `weave` with descendant-combinator interleaving
  and incompatible-compound skipping (e.g. two IDs → no-op)
- Style rules carry a real `SelectorList` AST built via
  `SelectorList.nestWithin` for `&` expansion, with textual fallback
  when either parent or child selector fails to parse
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

### Color spaces (CSS Color 4)
- Full `lab` / `lch` / `oklab` / `oklch` / `hwb` constructors + legacy
  `rgb` / `hsl` / `hsla`
- `color(<space> c1 c2 c3 / alpha)` for all predefined spaces
  (srgb, srgb-linear, display-p3, a98-rgb, prophoto-rgb, rec2020,
  xyz, xyz-d50, xyz-d65)
- Modern CSS function-call syntax end-to-end: `lab(50% 20 -30 / 0.5)`
  and friends parse as space-separated channels with optional `/ alpha`;
  legacy comma form still works. `none` is accepted as a channel keyword
  and round-trips through the serializer
- `color.mix($a, $b, $space: oklch)` (and any non-legacy space) performs
  the interpolation in the requested space via `InterpolationMethod`
- Round-tripping through the full lab ↔ xyz ↔ rgb ↔ oklch pipeline is
  exercised by `ColorSpacesSuite` end-to-end compile tests

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

- **`meta.apply($mixin, …)`** — throws
  `"meta.apply is not yet supported"`. Needs a fresh statement-visitor
  entry point to invoke a mixin from a built-in.
- **`content-exists`** — placeholder pending mixin-call-stack tracking.
- **`@extend` second-law edge cases** — the "second law of extend"
  trailing-sibling-combinator merging matrix (the
  `_mergeTrailingCombinators` matrix in dart-sass `functions.dart`) is
  skipped. Basic specificity trimming is already enforced by
  `extendComplex`. Extended weave interleaving currently covers the
  descendant-combinator path only; mid-complex sibling combinator
  interleavings fall back to plain concatenation.
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

## Next steps (priority order)

1. **`meta.apply`** — add a statement-visitor entry point that can run a
   `UserDefinedCallable[MixinRule]` from a built-in.
2. **StylesheetParser proper expression lexer** — replace the text-based
   collector with a tokenizer covering space-separated lists, function
   calls, interpolation, and unary forms uniformly.
3. **Full v3 source maps** — per-token mappings, `sourcesContent`,
   `sourceRoot`, `file`, and propagation through `@import` boundaries.
4. **CssParser strict mode** — for the (rare) consumers who need to
   reject Sass-only syntax.
5. **Error-span fidelity** — propagate the original `FileSpan` through
   all synthesized expressions so error messages never point at a
   placeholder URL.
