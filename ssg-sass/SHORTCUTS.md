# ssg-sass Tracker

Living status of the dart-sass → Scala 3 port. For per-file audit detail see
`ssg-dev db audit list --package <pkg>`; for migration status see
`ssg-dev db migration list --lib dart-sass`; for the full gap catalog see
`ssg-dev db issues list --category parser|evaluator|serializer|...`.

## Scope delta vs dart-sass

ssg-sass is **not** a spec-parity port of dart-sass. It is a pragmatic Scala 3
reimplementation that runs the common SCSS/SASS surface on JVM, JS, and Native.

| Metric | dart-sass (lib/src) | ssg-sass (main) | Ratio |
|--------|--------------------:|----------------:|------:|
| Effective Dart/Scala LoC (excluding embedded/JS/CLI) | ~100,000 | ~22,000 | 4.5× |
| Source files ported | 381 triaged | 279 ported, 98 skipped | — |

A 4–5× size delta is only partially explained by Scala being more terse. The
rest is real: missing deprecations, missing CSS Math functions, simplified
unit algebra, partial extend weave, skeleton CSS parser, text-based
expression lexer, single-token source maps, synthetic error spans, and no
logger/deprecation framework. See "Still stubbed / partial" below for the
subsystem breakdown and `ssg-dev db issues list` for the detailed catalog
(~100 tracked gaps as of 2026-04).

## Production readiness

**ssg-sass is NOT production-ready as a drop-in dart-sass replacement.**

It handles the SCSS subset commonly found in Jekyll-style sites, design
systems, and CSS-framework sources. It has **not** been run against the
`sass-spec` conformance suite; when it is, expect a non-trivial failure rate
in parser edge cases, color-4 round-trips, extend second-law matrices, and
any code path that relies on deprecation warnings. Closing those gaps is
tracked in `scripts/data/issues.tsv` and requires sustained sass-spec
compliance work — it is not a weekend task.

Use ssg-sass when:
- The input SCSS is authored by you or is from a reasonably modern design
  system that does not depend on sass-spec edge cases
- Cross-platform (JVM/JS/Native) execution is required and shelling out to
  dart-sass is not acceptable
- You can tolerate minor divergences in compressed-mode byte output and
  color serialization rounding

Do **not** use ssg-sass when:
- You need the deprecation warnings to migrate legacy code
- You consume a third-party SCSS library (Bootstrap, Foundation, Bourbon,
  Compass, etc.) without a compatibility pass
- You need byte-exact parity with dart-sass output (e.g. CI diff-based
  regression tests against reference dart-sass output)
- You rely on advanced source maps for IDE debugging

## Current state

- **Tests**: 532 JVM / 511 JS / 511 Native (last recorded)
- **Migration** (`dart-sass`): 279 ported, 4 done, 98 skipped — 381 total, 100% triaged
- **Audit** (all modules): 486 pass, 60 minor_issues, 0 major_issues — 546 files audited
- **sass-spec**: **not run**. Conformance is currently unknown.
- **Gap catalog**: ~100 tracked issues in `scripts/data/issues.tsv`.
- The compiler drives the Compile → Parse → Evaluate → Serialize pipeline
  with @use/@forward, @extend, control flow, built-in modules, calc(),
  custom properties, !important, minimal source maps, and the
  filesystem/package importers. This covers "typical SCSS workloads"; it
  does not cover "everything dart-sass does".

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

This is the honest, subsystem-level gap list. Severities: **C** critical
(breaks common SCSS), **H** high (breaks unusual SCSS), **M** medium (edge
case), **L** low (cosmetic). Detailed entries live in
`scripts/data/issues.tsv` (ISS-002 through ISS-102).

### Parser (`ssg-sass/parse/`)
- **H** Expression lexer is text-based; a proper tokenizer has not been
  ported from `dart-sass lib/src/parse/stylesheet.dart _expression`. Rare
  shapes can mis-split. (ISS-018)
- **H** `CssParser` strict mode is a skeleton. Plain `.css` inputs go
  through `ScssParser` and therefore silently accept Sass-only syntax.
  dart-sass `parse/css.dart` is a distinct rejecting subclass. (ISS-019)
- **M** `SassParser` (indented) override hooks throw
  `UnsupportedOperationException`; the indented-to-SCSS preprocessor
  bypasses the statement loop. Multi-line selector continuations
  unsupported. (ISS-020)
- **M** Media Queries Level 4 (range syntax `400px <= width < 1000px`)
  not verified. (ISS-021)
- **M** `@supports font-format()` / `font-tech()` untracked. (ISS-022)
- **M** Namespace-qualified variable writes and map-literal `!default`
  combinations need verification. (ISS-023)
- **M** Interpolation in at-rule names (`@#{foo}-rule`). (ISS-089)
- **M** Placeholder selectors in `@forward show/hide`. (ISS-090)
- **M** Declaration-with-nested-children sub-property syntax
  (`font: { family: sans; size: 1em; }`). (ISS-091)
- **L** Loud comments between `@if` and `@else`. (ISS-024)
- **L** `@charset` + UTF-8 BOM on compressed output. (ISS-025)

### Evaluator (`ssg-sass/visitor/EvaluateVisitor.scala`)
- **H** `mixed-decls` ordering: declarations after nested rules are not
  diagnosed. (ISS-026)
- **H** `@at-root (with:/without:)` query coverage needs verification
  including the `rule` / `all` pseudo-at-rules. (ISS-027)
- **H** `@forward` cross-namespace isolation (no function↔mixin
  cross-leak) needs verification. (ISS-066)
- **H** `with-private` (configuring `$_foo` in @use/@forward) not
  enforced. (ISS-033)
- **H** Plain-CSS function preservation for `env()`, `var()`, unknown
  functions (must be emitted verbatim, not evaluated). (ISS-093)
- **H** Nested `@use` / deep `@forward` chains with `as *` re-export
  semantics need verification. (ISS-034)
- **M** `var(--foo, default)` fallback must not be evaluated as a Sass
  expression. (ISS-094)
- **M** Interpolated calc contents (`calc(#{$x} + 1px)`) fed back into
  the simplifier. (ISS-092)
- **M** `&` inside `@content` blocks (ambient-selector capture). (ISS-035)
- **M** `FindDependenciesVisitor` only handles literal
  `meta.load-css`. (ISS-072)
- **M** `async_evaluate` (and therefore the async-importer JS API) is
  not ported; ssg-sass is sync-only. (ISS-102)
- **M** Auxiliary visitors (`statement_search`, `source_interpolation`,
  `ast_search`, `selector_search`) used by IDE integrations are not
  ported. (ISS-095, ISS-073)
- **L** `content-exists` is a placeholder pending mixin-call-stack
  tracking. (ISS-014)

### Serializer (`ssg-sass/visitor/SerializeVisitor.scala`)
- **H** Source maps emit one mapping per style rule / declaration.
  dart-sass emits per-token mappings plus `sourcesContent`, `sourceRoot`,
  `file`, and `@import`-boundary propagation. (ISS-044)
- **M** Compressed-mode byte-for-byte parity is unverified. (ISS-045)
- **M** Quoted-string escape rules (quote flipping, hex-escape
  termination, control-char escapes). (ISS-046)
- **M** `url()` payload pass-through (must not double-escape). (ISS-047)
- **M** Multi-line custom-property value preservation (newlines, inner
  comments, only `#{}` evaluated). (ISS-048)
- **M** Per-space color serialization (`lab()`, `oklch()`,
  `color(display-p3 …)`) with correct channel clamping and `none`
  round-trip. (ISS-100)
- **L** Loud-comment placement inside nested rules. (ISS-049)
- **L** Unicode-range form and collapsing. (ISS-050)

### Values (`ssg-sass/value/`)
- **H** `SassNumber` is a single flat class; dart-sass has three
  specialized subclasses (`Unitless`, `SingleUnit`, `Complex`) with
  compound numerator/denominator algebra, cross-type coercion, and
  conversion caching. (ISS-002)
- **M** `fuzzyEquals` / `fuzzyRound` / `fuzzyAsInt` from
  `util/number.dart` are not uniformly applied; direct double compares
  cause false inequality near 1e-11. (ISS-003)
- **M** Number printing (precision, trailing zeros, -0, `Infinity`)
  needs verification. (ISS-004)
- **M** `SassArgumentList` unused-keyword tracking parity. (ISS-068)
- **M** `SassMixin` / `meta.apply` wiring verification. (ISS-071)
- **L** `SassMap` empty-equals-empty-list Sass compat. (ISS-069)
- **L** Quoted vs unquoted `SassString` equality as map key. (ISS-070)

### Calculations (`ssg-sass/value/SassCalculation.scala`)
- **H** `SassCalculation` covers only `calc`/`min`/`max`/`clamp`.
  dart-sass treats `round/mod/rem/abs/sign/sin/cos/tan/asin/acos/atan/
  atan2/sqrt/exp/pow/log/hypot` as first-class calc nodes that can
  round-trip CSS-math-3 expressions. (ISS-005)
- **H** Nested `CalculationOperation` simplification is shallow; deep
  folds are not attempted. (ISS-006)

### Colors (`ssg-sass/value/SassColor.scala`)
- **C** Non-sRGB predefined color-space matrices (lab ↔ xyz ↔ lms ↔
  oklab ↔ prophoto ↔ a98-rgb ↔ display-p3 ↔ rec2020) need per-constant
  verification against `dart-sass conversions.dart`. (ISS-007)
- **H** Gamut mapping (`color.to-gamut`, MINDE-based LCH chroma
  reduction + clip fallback) not implemented. (ISS-008)
- **H** Powerless-channel / missing-channel (`none`) propagation through
  `mix()` and `toSpace()` is partial. (ISS-009)
- **M** Hue-interpolation hints (`shorter`, `longer`, `increasing`,
  `decreasing`, `specified`) not handled. (ISS-010)
- **M** Legacy accessors (`red($color)`) on non-legacy colors must
  throw, not silently convert. (ISS-098)
- **M** Color equality across space boundaries. (ISS-099)

### Selectors (`ssg-sass/selector/`)
- **H** `PseudoSelector` specialization for `:is`, `:where`, `:not`,
  `:has`, `:host`, `:host-context`, `:slotted` (per-pseudo unify /
  extend / specificity). (ISS-040)
- **M** Specificity `(a,b,c)` computation including `:is`/`:where`/`:not`
  rules. (ISS-041)
- **M** Complex-component prefix combinator fidelity through nesting
  (`& + .b`). (ISS-042)
- **L** Namespaced attribute selectors (`ns|attr`) and `i`/`s`
  modifiers. (ISS-043)

### Extend (`ssg-sass/extend/ExtensionStore.scala`)
- **H** Second-law trailing-combinator merge matrix
  (`_mergeTrailingCombinators` in dart-sass `extend/functions.dart`)
  skipped — compound extends with shared `+`/`~`/`>` trailing
  combinators fall back to plain concatenation. (ISS-036)
- **H** Weave sibling-combinator interleaving is descendant-only;
  mid-complex `+`/`~`/`>` interleavings fall back to concatenation.
  (ISS-037)
- **M** `MergedExtension` lattice is not tracked (hurts error
  provenance). (ISS-038)
- **M** `bogus-combinators` deprecation during extend resolution.
  (ISS-039)

### Modules (`ssg-sass/Module.scala`, `Configuration.scala`)
- **H** Lazy member views vs eager merge — may mask re-export edge
  cases. (ISS-063)
- **M** "Unused configuration" errors for `@use ... with (...)` keys
  the target module does not accept. (ISS-064)
- **M** `@import` vs `@use` scoping semantics parity. (ISS-065)
- **L** Watcher/REPL/CLI (`executable/*`) explicitly out of
  scope. (ISS-096, ISS-067)

### Imports (`ssg-sass/importer/`)
- **M** `CanonicalizeContext` (containing URL chain) not exposed to
  custom importers. (ISS-051)
- **M** FilesystemImporter load-path precedence and ambiguity error
  parity. (ISS-052)
- **M** NodePackageImporter conditional `exports` + subpath patterns.
  (ISS-054)
- **M** Import cycle error chains. (ISS-055)
- **L** `fs-importer-cwd` / `relative-canonical` deprecations. (ISS-053,
  ISS-056)

### Functions (`ssg-sass/fn/`)
- **H** Color-4 API: `color.channel`, `color.space`, `color.is-in-gamut`,
  `color.is-legacy`, `color.is-powerless`, `color.is-missing`,
  `color.to-space`, `color.to-gamut`, `color.same`. (ISS-011)
- **H** `meta.load-css` with dynamic URL + `$with` configuration;
  `meta.content-exists` requires call-stack. (ISS-014)
- **M** `math.atan2`, `math.cbrt`, `math.log` base, fractional
  `math.pow`, per-operation unit validation. (ISS-013)
- **M** `selector.*` edge cases against sass-spec (`is-superselector`,
  `extend`). (ISS-015)
- **L** `sass:string` code-point / surrogate handling on JS/Native.
  (ISS-016)
- **L** `sass:list` slash separator propagation. (ISS-017)

### Errors (`ssg-sass/SassException.scala`)
- **H** Selector / media / at-root sub-parsers still build
  `FileSpan.synthetic` placeholders with synthesized file URLs.
  (ISS-060)
- **M** No `MultiSpanSassException` (primary + secondary spans).
  (ISS-061)
- **M** Include/use stack-trace formatting in error messages needs
  verification. (ISS-062)

### Deprecations (`ssg-sass/Deprecation.scala`)
- **H** **No deprecation framework at all.** dart-sass defines ~30
  deprecations with `deprecatedIn`/`obsoleteIn` version metadata and a
  `DeprecationProcessingLogger` honoring
  silence/fatal/future flags. **None** of the following warnings are
  emitted: `call-string`, `elseif`, `moz-document`,
  `relative-canonical`, `new-global`, `color-module-compat`,
  `slash-div`, `bogus-combinators`, `strict-unary`, `function-units`,
  `duplicate-var-flags`, `null-alpha`, `abs-percent`, `fs-importer-cwd`,
  `css-function-mixin`, `mixed-decls`, `feature-exists`, `color-4-api`,
  `color-functions`, `legacy-js-api`, `import`, `global-builtin`,
  `type-function`, `compile-string-relative-url`, `misplaced-rest`,
  `with-private`, `if-function`, `function-name`. (ISS-057 plus
  ISS-028..ISS-032, ISS-074..ISS-088)
- **M** Pluggable Logger chain (`default`/`stderr`/`tracking`/
  `deprecation_processing`) — ssg-sass only has
  `CompileResult.warnings`. (ISS-058)
- **M** Future-deprecation opt-in. (ISS-059)

### Units (`ssg-sass/value/SassNumber.scala` unit algebra)
- See Values section — compound numerator/denominator, fuzzy
  comparison, printing (ISS-002, ISS-003, ISS-004).

## Next steps (priority order)

1. **Run sass-spec** against ssg-sass. Without this, every "verify
   matches dart-sass" item above is aspirational.
2. **Deprecation framework** — the whole subsystem is missing and many
   other gaps overlap with it.
3. **SassNumber compound unit algebra** — foundation for many
   calculation / math function fixes.
4. **StylesheetParser proper expression lexer** — replaces the
   text-based collector.
5. **CssParser strict mode** — reject Sass-only syntax in plain `.css`.
6. **Extend weave sibling-combinator matrix + second-law merging.**
7. **Color-4 API: to-space / to-gamut + powerless channel
   propagation.**
8. **Full v3 source maps** — per-token mappings, `sourcesContent`,
   `@import`-boundary propagation.
9. **Error-span fidelity (selector / media / at-root sub-parsers).**
10. **PseudoSelector per-name specializations** (`:is`/`:where`/`:not`
    specificity).
