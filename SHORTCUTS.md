# SSG Shortcuts

Scratchpad for cross-agent coordination on the `sass-port` branch.

## Recent work

### Plain-CSS function preservation + sass:meta gap fills (ISS-093, ISS-014)

- `ssg-sass/src/main/scala/ssg/sass/visitor/EvaluateVisitor.scala` —
  `visitFunctionExpression` already rendered unknown callables as plain
  CSS `name(args)`; verified `var()`, `linear-gradient()`, `polygon()`
  now pass through via new regression tests.
- `ssg-sass/src/main/scala/ssg/sass/functions/MetaFunctions.scala` —
  - `content-exists()` now reads
    `CurrentEnvironment.get.flatMap(_.content).isDefined` instead of
    always returning false. The existing environment mechanic (set in
    `_invokeMixinCallable`) already stacks the current content block on
    mixin entry and restores it on exit.
  - New `calc-name($calc)` / `calc-args($calc)` module-only functions:
    `calc-name` returns the `SassCalculation.name` as an unquoted
    `SassString`; `calc-args` returns a comma-separated `SassList` of
    operands — `SassNumber` / nested `SassCalculation` pass through as
    values, other arg types (`CalculationOperation`, `SassString`) are
    rendered via `SassCalculation.argumentToCss` and wrapped in an
    unquoted `SassString`.
  - New `accepts-content($mixin)`: unwraps the `SassMixin.callable`,
    reads `BuiltInCallable.acceptsContent` for built-ins or
    `MixinRule.hasContent` for user-defined mixins.
  - `MetaFunctions.module` is now `global ::: moduleOnly`, so the three
    new entries live only under `sass:meta` (not as globals), matching
    dart-sass.
- `ssg-sass/src/test/scala/ssg/sass/CompileSuite.scala` — 7 new cases:
  `var(--foo)`, `linear-gradient(red, blue)`, `polygon(0 0, 100% 0, 50%
  100%)`, `meta.calc-name(calc(100% + 2px))`, `meta.calc-name(min(100%,
  2px))`, `meta.calc-args(min(100%, 2px))`, `meta.accepts-content` for
  a mixin with / without `@content`.
- Resolved ISS-093 and ISS-014.

All 3 platforms: 610/610 (+1 ignored) green on JVM, JS, Native (+7 per
platform).

### SerializeVisitor: skip invisible parent nodes (sass-spec parity)

- `ssg-sass/src/main/scala/ssg/sass/visitor/SerializeVisitor.scala` — added a
  local `isNodeInvisible` helper mirroring dart-sass's `_IsInvisibleVisitor`
  semantics for the ssg-sass AST subset. A `CssParentNode` is invisible when
  `!isChildless && children.forall(isNodeInvisible)`. Declarations and imports
  are always visible; comments are invisible in compressed mode unless marked
  `isPreserved`. `writeChildren` and `visitCssStylesheet` now filter children
  through this check before emission.
- Previously the serializer eagerly emitted every `CssStyleRule` / `CssAtRule`
  / `CssMediaRule` / `CssSupportsRule` even when the rule had no effective
  output, producing `a {\n\n}` and residual `@if`/`@while`/`@media` wrappers
  that never match dart-sass's output.
- `ssg-sass/src/test/scala/ssg/sass/visitor/SerializeVisitorSuite.scala` — 8
  new cases: empty style rule skipped, rule whose children are all empty
  rules skipped, empty `@media` skipped, empty sibling dropped while real
  sibling emits, childless `@charset` still emitted, rule containing only a
  loud comment still emitted (expanded), compressed empty-rule skip,
  compressed comment-only rule skip.

All 3 platforms: JVM 616, JS 602 (+1 ignored), Native 602 (+1 ignored), green
(+8 per platform). sass-spec self-contained compliance moved from 27.8% to
29.3% (+172 passing, wrong-output 6003 -> 5791).

### Deprecation framework (ISS-057, ISS-058)

- `ssg-sass/src/main/scala/ssg/sass/EvaluationContext.scala` — trait now
  exposes `warnForDeprecation(d: Deprecation, message: String)` with a
  default that tags messages with `[id]`. Companion object has a
  matching static `warnForDeprecation` that forwards to `current`.
- `ssg-sass/src/main/scala/ssg/sass/visitor/EvaluateVisitor.scala` —
  visitor now extends `EvaluationContext`, pushes itself via
  `EvaluationContext.withContext` at the top of `run()`, and forwards
  parse-time warnings from `stylesheet.parseTimeWarnings` into
  `_warnings` using the same `DEPRECATION WARNING [id]: message`
  format as runtime emissions. `warn` / `warnForDeprecation` overrides
  append to `_warnings` and also fan out to the configured `_logger`.
- `ssg-sass/src/main/scala/ssg/sass/parse/StylesheetParser.scala` — new
  `warnDeprecation(d, message, span)` helper that pushes a
  `ParseTimeWarning`; `_atRule` now emits `elseif` and `moz-document`
  deprecations when it encounters `@elseif` or `@-moz-document` in the
  generic at-rule fallback branch.
- `ssg-sass/src/main/scala/ssg/sass/logger/DeprecationProcessing.scala`
  — thin factory / default shim around the existing
  `DeprecationProcessingLogger` (silence / fatal / future /
  repetition-limited) already defined in `Logger.scala`.
- **Deprecations emitted at runtime**:
  - `slash-div` — `EvaluateVisitor.visitBinaryOperationExpression`
    on `number / number`.
  - `elseif` — `StylesheetParser._atRule` on `@elseif` (parse-time).
  - `moz-document` — `StylesheetParser._atRule` on `@-moz-document`
    (parse-time).
  - `color-functions` — `ColorFunctions` on `lighten`, `darken`,
    `saturate`, `desaturate`, `opacify`, `transparentize`.
  - `color-module-compat` — `ColorFunctions` on `rgb($color, $alpha)`
    two-arg form.
  - `null-alpha` — `ColorFunctions` on `rgb/rgba(r, g, b, null)`.
  - `abs-percent` — `EvaluateVisitor._evaluateCalculation` on
    `abs(<percent>)`.
  - `feature-exists` — `MetaFunctions` on every call.
- `ssg-sass/src/test/scala/ssg/sass/DeprecationSuite.scala` — 14 cases
  covering each emitted deprecation plus `DeprecationProcessingLogger`
  forwarding and silencing.

All 3 platforms: JVM / JS / Native 594/594 green (+14 per platform).

### Strict `CssParser` for plain CSS (ISS-019)

- `ssg-sass/src/main/scala/ssg/sass/parse/CssParser.scala` — previously a
  no-op skeleton (`plainCss = true` only). Now overrides `parse()` to
  delegate to `ScssParser.parse()` and then walk the AST, rejecting any
  Sass-only node with a `SassFormatException`. Blocked: variable
  declarations, `@mixin`, `@include`, `@function`, `@return`,
  `@if` / `@else`, `@each`, `@for`, `@while`, `@debug`, `@warn`, `@error`,
  `@extend`, `@at-root`, `@use`, `@forward`, silent (`//`) comments,
  `#{...}` interpolation anywhere (selectors, declaration names, media
  queries, import URLs, at-rule names), nested style rules, and the
  parent selector `&`. Dedicated AST nodes are blocked via pattern
  match; Sass keywords that land in a generic `AtRule` (e.g. `@if`,
  `@while`) are blocked via an `_forbiddenAtRuleNames` blocklist.
  Custom properties (`--var: value`), `@media`, `@supports`, and
  standard vendor at-rules pass through unchanged.
- `ssg-sass/src/main/scala/ssg/sass/Compile.scala` — `compileString(...,
  syntax = Syntax.Css)` now instantiates `CssParser` instead of falling
  back to `ScssParser`.
- `ssg-sass/src/main/scala/ssg/sass/ImportCache.scala` —
  `importCanonical` picks the parser by effective syntax: it honors
  `ImporterResult.syntax` when set, otherwise falls back to
  `Syntax.forPath(canonicalUrl)`. A `.css` import routes through
  `CssParser`, a `.scss` import through `ScssParser`.
- `ssg-sass/src/main/scala/ssg/sass/importer/Importer.scala` —
  `MapImporter.load` now returns
  `ImporterResult(src, Syntax.forPath(url))` so file extension drives
  parser selection for the in-memory importer.
- `ssg-sass/src/test/scala/ssg/sass/CssParserSuite.scala` — new
  cross-platform suite, 12 cases covering the allow/reject matrix plus
  `@use "foo.css"` routing through `CssParser` via `ImportCache` and
  `@use "foo.scss"` still routing through `ScssParser`.

All 3 platforms: JVM 580, JS 580 (+1 ignored), Native 580 (+1 ignored),
green (+12 per platform). Resolves ISS-019.

### First-class CSS Math 3 calculation functions (ISS-005 / ISS-006)

- `ssg-sass/src/main/scala/ssg/sass/visitor/EvaluateVisitor.scala` —
  `_evaluateCalculation` now intercepts `round`, `mod`, `rem`, `abs`, `sign`,
  `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `sqrt`, `exp`, `pow`,
  `log`, `hypot` (plus the existing `calc` / `min` / `max` / `clamp`) before
  built-in callable dispatch. Arithmetic operators inside the arguments are
  turned into `CalculationOperation`s and the call is routed to the matching
  `SassCalculation` factory. Each factory already does eager collapse to a
  `SassNumber` when all arguments are known numbers and otherwise round-trips
  to a CSS-level `name(...)` call (preserving deferred `var()`, variables, or
  incompatible units). `clamp` now also accepts 1- or 2-arg forms.
- `toArg` additionally recognises the CSS calc keywords `pi`, `e`, `infinity`,
  `-infinity`, `NaN` as their `SassNumber` equivalents when they surface as
  unquoted strings from the inner expression evaluation.
- The `SassCalculation` factories themselves already existed in full
  (round-with-strategy, nested simplification, unit coercion, etc.) — this
  task only wired them through the evaluator so global calls like
  `sqrt(16)` / `pow(2, 10)` / `mod(7px, 3px)` / `round(up, 12.3px, 5px)`
  short-circuit through the calc machinery instead of the legacy math-module
  built-ins.
- `ssg-sass/src/test/scala/ssg/sass/CalcSuite.scala` — new cross-platform
  suite, 14 cases: unitless round, round with explicit step, round with
  strategy (up / to-zero), mod, rem, abs, sign, sqrt, pow, hypot,
  round-through-variable, cos(0), sin(0deg).
- Resolved ISS-005 and ISS-006 in `scripts/data/issues.tsv`.

All 3 platforms: JVM 568, JS 568, Native 568 (+14 per platform), green.
sass-spec self-contained compliance moved from ~20.7% to 27.8% over the
parallel work in this session.

### PseudoSelector per-name specificity specialization (ISS-040)

- `ssg-sass/src/main/scala/ssg/sass/ast/selector/PseudoSelector.scala` —
  added three companion sets that document the per-pseudo-name
  specialization already encoded in `PseudoSelector.specificity`:
  `selectorPseudoClasses` (not, is, matches, current, any, has, host,
  host-context), `selectorPseudoElements` (slotted), and
  `rootishPseudoClasses` (host, host-context). The specificity logic
  itself already matched dart-sass (`:where` -> 0, `:is/:not/:has/:matches`
  -> max of components, `:nth-child/:nth-last-child` -> base + max of
  components, default -> class).
- `ssg-sass/src/test/scala/ssg/sass/SelectorSpecificitySuite.scala` —
  new cross-platform suite, 11 cases covering `:where(.a, .b)` = (0,0,0),
  `:is(.a, #b)` = (1,0,0), `:not(.a, .b, .c)` = (0,1,0), `.a:where(.b.c)`
  = (0,1,0), `:has(:not(.x))` = (0,1,0), plain `:hover` = (0,1,0),
  `::before` / `:before` = (0,0,1), `#a.b.c` = (1,2,0), plus a sanity
  check on the new companion sets. Specificity is decoded from dart-sass's
  base-1000 int representation (ID = 1_000_000, class = 1_000, type = 1),
  which is order-isomorphic to the (id, class, type) triple.

11/11 green on JVM, JS, Native.

### CSS Color Module 4 introspection API (sass:color)

- `ssg-sass/src/main/scala/ssg/sass/functions/ColorFunctions.scala` —
  added nine module-only entries exposed under `sass:color`: `channel`,
  `space`, `is-legacy`, `is-in-gamut`, `is-powerless`, `is-missing`,
  `to-space`, `to-gamut`, `same`. All take a `$space: null` kwarg where
  applicable (channel/is-in-gamut/is-powerless/to-gamut). `to-gamut`
  accepts `$method: null` and defaults to `local-minde` (delegates to
  `GamutMapMethod.localMinde`/`clip` which were already ported).
  `same` compares after normalizing both colors to `xyz-d65` via
  `fuzzyEquals`. Registered only under the module list
  (`ColorFunctions.module = global ::: moduleOnly`), not as globals,
  matching dart-sass.
- `SassColor.scala` already had `space`, `isLegacy`, `isInGamut`,
  `isChannelMissing`, `isChannelPowerless`, `toSpace`, `toGamut(method)`,
  and `channel(name)` — no changes needed in the value layer.
- `ssg-sass/src/test/scala/ssg/sass/ColorModule4Suite.scala` — new
  cross-platform suite, 14 cases covering every required example plus
  `is-missing(red, red)` and `same(red, blue)` as a negative case.
- Resolves ISS-008 (to-gamut API wiring), ISS-009 (powerless/missing
  exposure), ISS-011 (sass:color Module 4 API).
- JVM: ColorModule4Suite 14/14 green. JS/Native currently blocked from
  running by unrelated pre-existing compile errors in
  `parse/CssParser.scala` (`_checkInterpolation` arity mismatch) and
  an unused-import warning in `Compile.scala`, both in the parallel
  "do not touch" zone.

### SassNumber compound unit algebra test coverage (ISS-002 resolved)

- `ssg-sass/src/main/scala/ssg/sass/value/SassNumber.scala` already
  contains the full dart-sass compound unit algebra: three subclasses
  (`UnitlessSassNumber`, `SingleUnitSassNumber`, `ComplexSassNumber`
  under `value/number/`), `numeratorUnits` / `denominatorUnits` lists,
  `multiplyUnits`, `coerceOrConvertValue`, `coerceValueToUnit`,
  `convertToMatch`, automatic cancellation in `SassNumber.withUnits`,
  and the full conversion table (length in/cm/pc/mm/q/pt/px, angle
  deg/grad/rad/turn, time s/ms, frequency Hz/kHz, resolution
  dpi/dpcm/dppx). No source changes were required for ISS-002.
- `ssg-sass/src/test/scala/ssg/sass/SassNumberSuite.scala` — new
  cross-platform suite, 18 cases covering: subclass dispatch from
  `withUnits`, compound `*` unioning numerators, compound `/` splitting
  numerator/denominator, mid-expression cancellation (`10px*s / 1px`),
  full cancellation to unitless (`10px * 1/1px`), compound cross-type
  multiplication (`1px*s * 1pt/1ms`), `+`/`-` with cross-unit coercion
  (`10px + 1pt`, `1in - 1cm`), incompatible-unit `SassScriptException`
  on `10px + 1s`, `coerceValueToUnit` across length/angle/time/
  frequency/resolution, round-trips, modulo with coercion, and the
  numerator+denominator cross-type simplification done by
  `withUnits` (`px*s / pt*ms` collapses to unitless).
- ISS-002 resolved via `ssg-dev db issues resolve ISS-002`.

All 3 platforms: 529/529 (+1 ignored) green on JVM, JS, Native
(+18 per platform).

### Modern CSS color space support (lab / lch / oklab / oklch / hwb / color())

- `ssg-sass/src/main/scala/ssg/sass/functions/ColorFunctions.scala` —
  registered six new global color constructors: `lab`, `lch`, `oklab`,
  `oklch`, `hwb`, and `color($space, ...)`. All accept comma-separated
  arguments (the modern space-separated / slash syntax isn't parsed by
  `StylesheetParser` yet, so SCSS sources must use `lab(50, 20, -30)`
  for now). `color($space, c1, c2, c3, $alpha: 1)` accepts any of the
  names exposed by `ColorSpace.fromName` (srgb, srgb-linear, display-p3,
  display-p3-linear, a98-rgb, prophoto-rgb, rec2020, xyz, xyz-d50,
  xyz-d65, lab, lch, oklab, oklch, hwb, hsl, rgb).
- `mixFn` now takes an optional `$space` kwarg: when supplied it routes
  through `SassColor.interpolate` with an `InterpolationMethod` in the
  given space (Shorter hue interpolation for polar spaces). When omitted
  it falls back to the legacy rgb-alpha-weighted mix for backwards
  compatibility with existing `CompileSuite` tests.
- `ssg-sass/src/main/scala/ssg/sass/value/SassColor.scala` — overrode
  `toCssString` to render modern CSS syntax for every non-legacy-rgb
  space: `hsl(...)` / `hwb(...)` / `lab(...)` / `lch(...)` /
  `oklab(...)` / `oklch(...)` / `color(<space> ...)` with a `/ alpha`
  suffix when alpha ≠ 1 and `none` for missing channels. Legacy `rgb`
  colors still go through `SerializeVisitor.formatColor` (hex / name /
  rgba) so existing compile tests are untouched.
- The underlying color space conversions (sRGB ↔ XYZ D65/D50, XYZ D50 ↔
  Lab, Lab ↔ Lch, LMS ↔ Oklab via `oklabToLms`/`lmsToOklab`, and all
  predefined-space matrices) were already ported into
  `ssg-sass/src/main/scala/ssg/sass/value/color/Conversions.scala` and
  wired through `ColorSpaces.scala`; this task only needed to expose
  them via constructors/serializer.
- `ssg-sass/src/test/scala/ssg/sass/ColorSpacesSuite.scala` — new
  cross-platform suite, 12 cases covering: lab/lch/oklch/oklab/hwb
  parsing, `color(display-p3 1 0.5 0)`, modern serialization, rgb→lab→rgb
  and rgb→oklch→rgb round-trips within 1e-3, legacy rgb `mix`, `mix`
  with `$space: oklch`, and direct `SassColor.interpolate` in oklch.

All 3 platforms: JVM 510, JS 489 (+2 ignored), Native 489 (+2 ignored),
green (+12 per platform).

### Cross-platform MapImporter + ImportMapSuite

- `ssg-sass/src/main/scala/ssg/sass/importer/Importer.scala` — new
  `MapImporter(sources: Map[String, String])` — a cross-platform
  in-memory importer. Mirrors `FilesystemImporter`'s candidate order
  (exact, `_partial.scss`, `name/_index.scss`, `name/index.scss`) but
  using plain string keys, no `java.nio.file`. Keys are the canonical
  form (e.g. `_colors.scss`, `vars.scss`); `load` returns
  `ImporterResult(src, Syntax.Scss)`.
- `ssg-sass/src/test/scala/ssg/sass/ImportMapSuite.scala` — new
  cross-platform suite, 10 cases ported from the JVM-only `ImportSuite`
  covering basic `@import`, explicit `.scss`, missing imports, `@use`
  with default / explicit namespace, `@use as *`, `@forward` re-export,
  `@use with (...)` config override, `@use` without config falling back
  on `!default`, and `loadedUrls` tracking.
- JVM-only `ImportSuite` is untouched (still exercises real
  `java.nio.file` I/O) — this is additional coverage.

All 3 platforms: JVM 449, JS 428 (+2 ignored), Native 428 (+2 ignored), green
(+10 per platform).

### Module / EvaluationContext / Callable / FindDependencies polish

- `ssg-sass/src/main/scala/ssg/sass/Module.scala` —
  - `BuiltInModule.css` now returns `CssStylesheet.empty(url)` instead of
    throwing (built-in modules genuinely have no CSS to emit).
  - `ForwardedView` now implements show/hide/prefix filtering. New
    constructor params: `prefix`, `shownVariables`,
    `shownMixinsAndFunctions`, `hiddenVariables`,
    `hiddenMixinsAndFunctions`. `variables`/`functions`/`mixins` filter
    the underlying maps and prepend the prefix; `setVariable` strips the
    prefix and rejects names that aren't visible.
    `ForwardedView.apply(inner, rule: ForwardRule)` builds a view from a
    parsed `@forward` rule.
  - `ShadowedView` now takes `shadowedVars` / `shadowedMixins` /
    `shadowedFunctions` blocklists; `setVariable` rejects shadowed names.
- `ssg-sass/src/main/scala/ssg/sass/EvaluationContext.scala` —
  `EvaluationContext.current` now reads from a real stack;
  `withContext(ctx) { body }` pushes, runs, pops in `try/finally`.
  Single shared stack (Sass evaluation is single-threaded; ssg-js /
  ssg-native lack working ThreadLocals across all platforms).
- `ssg-sass/src/main/scala/ssg/sass/Callable.scala` —
  `BuiltInCallable.overloadedFunction` now does real arity dispatch:
  exact match wins, then non-rest with more declared params (defaulted
  tail), then rest-parameter overload (`$args...`); throws
  `IllegalArgumentException` if nothing matches.
- `ssg-sass/src/main/scala/ssg/sass/visitor/FindDependenciesVisitor.scala`
  — `visitIncludeRule` now recognises `meta.load-css("literal")` (single
  positional `StringExpression` with a plain interpolation) and records
  the URL in `_metaLoadCss`; non-literal arguments are ignored.
- Tests: `ssg-sass/src/test/scala/ssg/sass/ModuleInfraSuite.scala`
  (10 cases) covers the empty BuiltIn css, all three ForwardedView
  filtering modes (show/hide/prefix), ShadowedView blocklist,
  EvaluationContext push/pop and exception unwinding,
  `overloadedFunction` arity dispatch, and the new FindDependencies
  meta.load-css literal/dynamic branches.

All 3 platforms: 418/418 (+2 ignored) green on JVM, JS, Native.

### Media / Keyframe / AtRoot query parsers (Phase 11)

- `ssg-sass/src/main/scala/ssg/sass/parse/MediaQueryParser.scala` — full
  recursive-descent parser. Handles bare types (`screen`, `print`, `all`),
  feature queries (`(max-width: 600px)`), `type and (cond) and (cond)`,
  comma-separated query lists, and `not`/`only` modifiers. Returns
  `List[CssMediaQuery]`. Static helpers `parseList` / `tryParseList`.
- `ssg-sass/src/main/scala/ssg/sass/parse/KeyframeSelectorParser.scala` —
  parses `0%`, `100%`, `12.5%`, `from`, `to`, and comma-separated lists.
  Normalizes `from`->`0%`, `to`->`100%`, validates percentages in [0,100].
- `ssg-sass/src/main/scala/ssg/sass/parse/AtRootQueryParser.scala` —
  parses `(with: media supports)` / `(without: rule)` / `(without: all)`
  into `AtRootQuery`. Static helpers `parseQuery` / `tryParseQuery`.
- `EvaluateVisitor.visitMediaRule` now calls `MediaQueryParser.tryParseList`
  on the interpolated text and falls back to wrapping the raw text as a
  condition-only query (preserves all existing tests including the
  `@media supports #{...}` interpolation case).
- `EvaluateVisitor.visitAtRootRule` now uses `AtRootQueryParser` (or the
  default query if absent) and walks the parent chain to pick the
  topmost non-excluded ancestor as the new attachment point. Style-rule
  context is cleared when `excludesStyleRules` is true.
- Tests: `MediaQueryParserSuite`, `KeyframeSelectorParserSuite`,
  `AtRootQueryParserSuite` under
  `ssg-sass/src/test/scala/ssg/sass/parse/`, plus a new CompileSuite case
  `@at-root (with: media) inside @media keeps the media wrapper`.

All 3 platforms: JVM 425, JS 408 (+2 ignored), Native 408 (+2 ignored), green.

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
