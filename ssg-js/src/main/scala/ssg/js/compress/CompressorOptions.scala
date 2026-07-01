/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Full compressor options for the Terser-compatible JS minifier.
 *
 * Each option controls a specific optimization pass or behavior.
 * Defaults match Terser's `defaults: true` configuration.
 *
 * Ported from: terser lib/compress/index.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: snake_case JS options -> camelCase Scala fields with snake_case
 *     accessors for compatibility
 *   Convention: case class with default values instead of JS defaults() helper
 *   Idiom: sealed trait InlineLevel instead of boolean|int union
 *   Idiom (ISS-1193): terser's HOP-based defaults() (lib/utils/index.js:66-92)
 *     distinguishes a caller-set key from an unset one; a Scala case class with
 *     plain Boolean fields cannot. We add a presence record `gatedPresence`
 *     (GatedPresence: Unspecified | Provided(names)) on CompressorOptions so
 *     resolveDefaults can replicate HOP exactly (present -> keep, absent ->
 *     !false_by_default). This design (Option A: presence on the input, resolved
 *     to a fully-concrete CompressorOptions) localizes the change to
 *     resolveDefaults + NoDefaults and leaves every downstream read (get(),
 *     options.<field>) untouched. It replaces the earlier fragile majority-match
 *     vote (matchesDefaults vs matchesNoDefaults counting) that CLOBBERED
 *     explicitly-enabled passes once >=13 gated passes were enabled via
 *     NoDefaults.copy(...) through a public entry (the count tied/flipped).
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/compress/index.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 6080510127d6c871ad58ce27c5c6b3045d948baa
 */
package ssg
package js
package compress

/** Inline level controls how aggressively functions are inlined.
  *
  *   - `InlineDisabled` (0): no inlining
  *   - `InlineSimple` (1): inline simple functions
  *   - `InlineWithArgs` (2): inline functions with arguments
  *   - `InlineFull` (3): inline functions with arguments and variables
  */
enum InlineLevel(val level: Int) extends java.lang.Enum[InlineLevel] {
  case InlineDisabled extends InlineLevel(0)
  case InlineSimple extends InlineLevel(1)
  case InlineWithArgs extends InlineLevel(2)
  case InlineFull extends InlineLevel(3)
}

object InlineLevel {

  def fromAny(value: Any): InlineLevel =
    value match {
      case false => InlineDisabled
      case true  => InlineFull
      case n: Int =>
        n match {
          case 0 => InlineDisabled
          case 1 => InlineSimple
          case 2 => InlineWithArgs
          case _ => InlineFull
        }
      case _ => InlineFull
    }
}

/** Configuration for what to drop/retain at the top level. */
final case class ToplevelConfig(
  funcs: Boolean = false,
  vars:  Boolean = false
)

object ToplevelConfig {

  def fromAny(value: Any): ToplevelConfig =
    value match {
      case true  => ToplevelConfig(funcs = true, vars = true)
      case false => ToplevelConfig()
      case s: String =>
        ToplevelConfig(
          funcs = s.contains("funcs"),
          vars = s.contains("vars")
        )
      case _ => ToplevelConfig()
    }
}

/** Configuration for console method dropping. */
enum DropConsoleConfig {

  /** Don't drop any console calls. */
  case Disabled

  /** Drop all console calls. */
  case All

  /** Drop only specific console methods. */
  case Methods(names: Set[String])
}

object DropConsoleConfig {

  def fromAny(value: Any): DropConsoleConfig =
    value match {
      case false => Disabled
      case true  => All
      case list: List[?] => Methods(list.map(_.toString).toSet)
      case _ => Disabled
    }
}

/** Presence tracking for the DEFAULT-GATED options, mirroring terser's HOP (`Object.prototype.hasOwnProperty`) semantics in `defaults()` (lib/utils/index.js:66-92).
  *
  * A Scala `case class` with plain `Boolean` fields cannot distinguish "the caller wrote `evaluate = false`" from "the field defaulted to its value", so we carry an explicit record of which gated
  * options the caller specified.
  *
  *   - [[GatedPresence.Unspecified]]: no presence information (a plain `CompressorOptions(...)` construction). Under `defaults = false` this falls back to a per-field value comparison against
  *     [[CompressorOptions.Defaults]] (a gated field still equal to its on-default is treated as unset and turned off; a differing value is treated as caller-set and kept). This is deterministic and
  *     per-field — it is NOT the deleted majority-match vote.
  *   - [[GatedPresence.Provided]]: the exact set of gated field names the caller specified. This is the faithful terser HOP path: a present name keeps its value, an absent name resolves to terser's
  *     `!false_by_default` default. [[CompressorOptions.NoDefaults]] uses `Provided(gatedFieldNames)` so that every `NoDefaults.copy(pass = true)` override survives resolution intact, regardless of
  *     how many passes are enabled (ISS-1193).
  */
enum GatedPresence {

  /** No presence info — plain construction; resolved via value comparison under `defaults = false`. */
  case Unspecified

  /** Explicit set of caller-specified gated field names (Scala field names). */
  case Provided(names: Set[String])
}

/** Full compressor options.
  *
  * Controls all optimization passes in the Terser-compatible compressor. Option names match Terser's naming for compatibility; the Scala field names use camelCase while `option("snake_case")` lookups
  * are supported via the `get` method.
  */
final case class CompressorOptions(
  /** Optimize `arguments` object references. */
  arguments: Boolean = false,
  /** Convert `()=>{ return x }` to `()=>x`. */
  arrows: Boolean = true,
  /** Various boolean-context optimizations. */
  booleans: Boolean = true,
  /** Turn booleans into 0 and 1 (smaller but less readable). */
  booleansAsIntegers: Boolean = false,
  /** Collapse single-use non-constant vars into references. */
  collapseVars: Boolean = true,
  /** Apply comparison optimizations (e.g. `===` to `==`). */
  comparisons: Boolean = true,
  /** Optimize computed property keys (e.g. `["p"]:1` to `p:1`). */
  computedProps: Boolean = true,
  /** Various optimizations for `if-s` and conditional expressions. */
  conditionals: Boolean = true,
  /** Remove unreachable code. */
  deadCode: Boolean = true,
  /** Whether default values are true (when `false`, all options default to off). */
  defaults: Boolean = true,
  /** Remove redundant or non-standard directives. */
  directives: Boolean = true,
  /** Remove `console.*` calls. */
  dropConsole: DropConsoleConfig = DropConsoleConfig.Disabled,
  /** Remove `debugger;` statements. */
  dropDebugger: Boolean = true,
  /** Target ECMAScript version (5, 2015, 2020, etc.). */
  ecma: Int = 5,
  /** Attempt to evaluate constant expressions. */
  evaluate: Boolean = true,
  /** Parse the toplevel as an expression (for bookmarklets). */
  expression: Boolean = false,
  /** Global constant definitions for substitution. */
  globalDefs: Map[String, Any] = Map.empty,
  /** Hoist `function` declarations to top of scope. */
  hoistFuns: Boolean = false,
  /** Hoist properties from constant object/array literals. */
  hoistProps: Boolean = true,
  /** Hoist `var` declarations to top of scope. */
  hoistVars: Boolean = false,
  /** Support IE8 quirks (set/get without reserved words, etc.). */
  ie8: Boolean = false,
  /** Optimize `if/return` and `if/continue`. */
  ifReturn: Boolean = true,
  /** Inline function calls. */
  inline: InlineLevel = InlineLevel.InlineFull,
  /** Join consecutive `var` statements. */
  joinVars: Boolean = true,
  /** Prevent class name mangling. `true` keeps all; can be `Boolean` or a `scala.util.matching.Regex` (terser keep_classnames: Boolean | RegExp — keep only names matching the regex).
    */
  keepClassnames: Any = false,
  /** Prevent unused function args from being dropped. */
  keepFargs: Boolean = true,
  /** Prevent function name mangling. `true` keeps all; can be `Boolean` or a `scala.util.matching.Regex` (terser keep_fnames: Boolean | RegExp — keep only names matching the regex).
    */
  keepFnames: Any = false,
  /** Pass `Infinity` through as identifier (not `1/0`). */
  keepInfinity: Boolean = false,
  /** Swap constant to LHS of commutative operators. */
  lhsConstants: Boolean = true,
  /** Optimize loops. */
  loops: Boolean = true,
  /** Enable ES module scope. */
  module: Boolean = false,
  /** Negate IIFEs (smaller output). */
  negateIife: Boolean = true,
  /** Number of compression passes. */
  passes: Int = 1,
  /** Rewrite property access using the dot notation. */
  properties: Boolean = true,
  /** Names of functions known to have no side effects. */
  pureFuncs: List[String] = Nil,
  /** Assume property access has no side effects. `"strict"` = only on known-safe objects; `true` assumes ALL property access is pure (terser pure_getters: Boolean | "strict" — index.js:258
    * `!false_by_default && "strict"`). Widened to `Any` (mirroring [[keepFnames]]) so `option("pure_getters")` can return the boolean `true` and the boolean-gated paths
    * (Compressor.optimizeDestructuring, DropUnused) become reachable (ISS-1177; cf. ISS-1040 keep_fnames widening).
    */
  pureGetters: Any = "strict",
  /** Assume `new X()` always returns a new object (enable for side-effect-free `new`). */
  pureNew: Boolean = false,
  /** Allow inlining of function bodies. */
  reduceFuncs: Boolean = true,
  /** Data-flow analysis: track variable assignments. */
  reduceVars: Boolean = true,
  /** Join consecutive simple statements into sequences. Max count or boolean. */
  sequencesLimit: Int = 800,
  /** Drop pure expressions (no side effects). */
  sideEffects: Boolean = true,
  /** Optimize switch statements. */
  switches: Boolean = true,
  /** Drop/inline top-level variables and functions. */
  toplevel: ToplevelConfig = ToplevelConfig(),
  /** Regex or list of names to retain at top level. */
  topRetain: Option[String => Boolean] = None,
  /** Optimize `typeof` comparisons. */
  typeofs: Boolean = true,
  /** Apply "unsafe" transformations (detailed below). */
  unsafe: Boolean = false,
  /** Allow unsafe arrow function conversions. */
  unsafeArrows: Boolean = false,
  /** Allow unsafe comparison optimizations. */
  unsafeComps: Boolean = false,
  /** Allow unsafe `Function()` optimizations. */
  unsafeFunction: Boolean = false,
  /** Allow unsafe math optimizations. */
  unsafeMath: Boolean = false,
  /** Allow unsafe method conversions. */
  unsafeMethods: Boolean = false,
  /** Allow unsafe `__proto__` optimizations. */
  unsafeProto: Boolean = false,
  /** Allow unsafe regexp optimizations. */
  unsafeRegexp: Boolean = false,
  /** Allow unsafe Symbol optimizations. */
  unsafeSymbols: Boolean = false,
  /** Allow referencing `undefined` as a variable. */
  unsafeUndefined: Boolean = false,
  /** Drop unreferenced variables and functions. */
  unused: Boolean = true,
  /** Legacy option: emit warnings (no-op in Terser 5+). */
  warnings: Boolean = false,
  /** Presence record for the default-gated fields, used by [[CompressorOptions.resolveDefaults]] to replicate terser's HOP-based `defaults()` semantics (lib/utils/index.js:66-92). Not a compressor
    * pass; defaults to [[GatedPresence.Unspecified]] for plain constructions and is never surfaced via [[get]]. See [[GatedPresence]] and [[CompressorOptions.resolveDefaults]] (ISS-1193).
    */
  gatedPresence: GatedPresence = GatedPresence.Unspecified
) {

  /** Look up an option by its Terser-compatible snake_case name.
    *
    * Returns the option value as `Any` to match the dynamic lookup pattern used throughout the compressor.
    */
  def get(name: String): Any =
    name match {
      case "arguments"            => arguments
      case "arrows"               => arrows
      case "booleans"             => booleans
      case "booleans_as_integers" => booleansAsIntegers
      case "collapse_vars"        => collapseVars
      case "comparisons"          => comparisons
      case "computed_props"       => computedProps
      case "conditionals"         => conditionals
      case "dead_code"            => deadCode
      case "defaults"             => defaults
      case "directives"           => directives
      case "drop_console"         =>
        dropConsole match {
          case DropConsoleConfig.Disabled       => false
          case DropConsoleConfig.All            => true
          case DropConsoleConfig.Methods(names) => names
        }
      case "drop_debugger"    => dropDebugger
      case "ecma"             => ecma
      case "evaluate"         => evaluate
      case "expression"       => expression
      case "global_defs"      => globalDefs
      case "hoist_funs"       => hoistFuns
      case "hoist_props"      => hoistProps
      case "hoist_vars"       => hoistVars
      case "ie8"              => ie8
      case "if_return"        => ifReturn
      case "inline"           => inline.level
      case "join_vars"        => joinVars
      case "keep_classnames"  => keepClassnames
      case "keep_fargs"       => keepFargs
      case "keep_fnames"      => keepFnames
      case "keep_infinity"    => keepInfinity
      case "lhs_constants"    => lhsConstants
      case "loops"            => loops
      case "module"           => module
      case "negate_iife"      => negateIife
      case "passes"           => passes
      case "properties"       => properties
      case "pure_funcs"       => pureFuncs
      case "pure_getters"     => pureGetters
      case "pure_new"         => pureNew
      case "reduce_funcs"     => reduceFuncs
      case "reduce_vars"      => reduceVars
      case "sequences"        => sequencesLimit
      case "side_effects"     => sideEffects
      case "switches"         => switches
      case "toplevel"         => toplevel
      case "top_retain"       => topRetain
      case "typeofs"          => typeofs
      case "unsafe"           => unsafe
      case "unsafe_arrows"    => unsafeArrows
      case "unsafe_comps"     => unsafeComps
      case "unsafe_Function"  => unsafeFunction
      case "unsafe_math"      => unsafeMath
      case "unsafe_methods"   => unsafeMethods
      case "unsafe_proto"     => unsafeProto
      case "unsafe_regexp"    => unsafeRegexp
      case "unsafe_symbols"   => unsafeSymbols
      case "unsafe_undefined" => unsafeUndefined
      case "unused"           => unused
      case "warnings"         => warnings
      case _                  => false
    }
}

object CompressorOptions {

  /** All-defaults-enabled configuration — `compress: {}` / `compress: true` in Terser. */
  val Defaults: CompressorOptions = CompressorOptions()

  /** The Scala field names of the DEFAULT-GATED options — the options terser defaults to `!false_by_default` (lib/compress/index.js:223-278). These are exactly the fields [[NoDefaults]] overrides,
    * excluding the `defaults` control flag itself. Used by [[resolveDefaults]] for the terser HOP analog and by [[NoDefaults]]'s presence record.
    */
  val gatedFieldNames: Set[String] = Set(
    "arrows",
    "booleans",
    "collapseVars",
    "comparisons",
    "computedProps",
    "conditionals",
    "deadCode",
    "directives",
    "dropDebugger",
    "evaluate",
    "hoistProps",
    "ifReturn",
    "inline",
    "joinVars",
    "lhsConstants",
    "loops",
    "negateIife",
    "properties",
    "pureGetters",
    "reduceFuncs",
    "reduceVars",
    "sequencesLimit",
    "sideEffects",
    "switches",
    "typeofs",
    "unused"
  )

  /** All default-gated passes disabled — `compress: { defaults: false }` in Terser.
    *
    * Matches lib/compress/index.js:222-275: every option whose default is `!false_by_default` is set to `false` / disabled. Options that are already off by default (e.g. `arguments`, `hoist_funs`,
    * `unsafe_*`) or non-boolean (e.g. `ecma`, `passes`) keep their normal defaults.
    *
    * To enable a specific pass on top of this, use `.copy(evaluate = true)` etc. — the [[gatedPresence]] marker below records every gated field as `Provided`, so [[resolveDefaults]] preserves any
    * such `.copy` override verbatim (this is the terser HOP analog: the resolved options equal what terser produces for `{ defaults: false, evaluate: true, ... }`). See ISS-1193.
    */
  val NoDefaults: CompressorOptions = CompressorOptions(
    arrows = false,
    booleans = false,
    collapseVars = false,
    comparisons = false,
    computedProps = false,
    conditionals = false,
    deadCode = false,
    defaults = false,
    directives = false,
    dropDebugger = false,
    evaluate = false,
    hoistProps = false,
    ifReturn = false,
    inline = InlineLevel.InlineDisabled,
    joinVars = false,
    lhsConstants = false,
    loops = false,
    negateIife = false,
    properties = false,
    // terser defaults:false -> pure_getters = !true && "strict" = false (falsy boolean);
    // optionBool (CompressorLike.scala:60) maps String via s.nonEmpty, so "" is the
    // falsy String value matching terser's boolean false (index.js:258)
    pureGetters = "",
    reduceFuncs = false,
    reduceVars = false,
    sequencesLimit = 0,
    sideEffects = false,
    switches = false,
    typeofs = false,
    unused = false,
    // Every gated field is recorded as Provided so resolveDefaults treats this as a
    // fully-specified off-state: NoDefaults resolves to all-off, and any
    // NoDefaults.copy(pass = true) override is kept (present -> keep value), no matter
    // how many passes are enabled (ISS-1193 — the old majority-match vote clobbered
    // >=13 enabled passes).
    gatedPresence = GatedPresence.Provided(gatedFieldNames)
  )

  /** Resolve terser's `defaults()` semantics, matching lib/compress/index.js:220-278 and the HOP-based `defaults()` helper (lib/utils/index.js:66-92).
    *
    * terser computes `false_by_default = (options.defaults === false)` and then, for every DEFAULT-GATED option, applies `defaults(options, { arrows: !false_by_default, ... })` where `defaults()`
    * keeps a key the caller EXPLICITLY set (HOP true) and fills an UNSET key with `!false_by_default` (its default). This method is a faithful analog:
    *
    *   - `false_by_default = !o.defaults` (i.e. `o.defaults == false`).
    *   - For each gated field: PRESENT (caller-specified) -> keep the caller's value; ABSENT -> terser's default (`!false_by_default` for the boolean passes; the analogous per-field default for the
    *     non-boolean gated fields: `inline` -> `InlineFull`/`InlineDisabled`, `sequencesLimit` -> `800`/`0`, `pureGetters` -> `"strict"`/`""`).
    *
    * Presence is carried by [[gatedPresence]] (see [[GatedPresence]]):
    *
    *   - [[GatedPresence.Provided]] gives the exact caller-specified set — the true HOP path. [[NoDefaults]] uses `Provided(gatedFieldNames)`, so `NoDefaults.copy(pass = true)` keeps every override
    *     regardless of how many passes are enabled (ISS-1193; the old majority-match vote flipped and clobbered >=13 enabled passes).
    *   - [[GatedPresence.Unspecified]] (a plain `CompressorOptions(...)`) has no presence record. When `defaults == true` it is a no-op (the case-class defaults ARE terser's `!false_by_default`
    *     on-defaults). When `defaults == false` presence is approximated per-field by value comparison against [[Defaults]]: a gated field still at its on-default is treated as unset and turned off;
    *     a differing value is treated as caller-set and kept. This is deterministic and per-field — NOT the deleted majority-match counting vote. To express an explicitly-ENABLED normally-on pass
    *     under `defaults = false` (which value comparison cannot detect, e.g. `CompressorOptions(defaults = false, evaluate = true)`), pass a presence record —
    *     `CompressorOptions.NoDefaults.copy(evaluate = true)` or `.copy(evaluate = true, gatedPresence = GatedPresence.Provided(Set("evaluate")))`.
    *
    * The `toplevel <- !!(top_retain)` cross-default (index.js:267) is applied first, regardless of the `defaults` flag.
    *
    * terser lib/compress/index.js:220-278:
    * {{{
    *   if (options.defaults !== undefined && !options.defaults)
    *       false_by_default = true;
    *   this.options = defaults(options, { arrows: !false_by_default, ... }, true);
    *   // defaults() (utils/index.js): HOP(args, key) ? args[key] : defs[key]
    * }}}
    */
  def resolveDefaults(o: CompressorOptions): CompressorOptions = {
    // terser lib/compress/index.js:267 — toplevel defaults to !!(options["top_retain"]):
    // when toplevel is at its unset default and top_retain is provided, toplevel
    // is enabled (both funcs and vars). This cross-default applies regardless of
    // the `defaults` flag.
    val resolved =
      if (o.toplevel == ToplevelConfig() && o.topRetain.isDefined)
        o.copy(toplevel = ToplevelConfig(funcs = true, vars = true))
      else o

    // terser: false_by_default = (options.defaults === false).
    val falseByDefault = !resolved.defaults

    resolved.gatedPresence match {
      case GatedPresence.Provided(names) =>
        // Faithful terser HOP: present name -> keep caller value; absent name ->
        // terser default (`!false_by_default`, or the per-field analog).
        def boolDefault: Boolean = !falseByDefault
        resolved.copy(
          arrows = if (names("arrows")) resolved.arrows else boolDefault,
          booleans = if (names("booleans")) resolved.booleans else boolDefault,
          collapseVars = if (names("collapseVars")) resolved.collapseVars else boolDefault,
          comparisons = if (names("comparisons")) resolved.comparisons else boolDefault,
          computedProps = if (names("computedProps")) resolved.computedProps else boolDefault,
          conditionals = if (names("conditionals")) resolved.conditionals else boolDefault,
          deadCode = if (names("deadCode")) resolved.deadCode else boolDefault,
          directives = if (names("directives")) resolved.directives else boolDefault,
          dropDebugger = if (names("dropDebugger")) resolved.dropDebugger else boolDefault,
          evaluate = if (names("evaluate")) resolved.evaluate else boolDefault,
          hoistProps = if (names("hoistProps")) resolved.hoistProps else boolDefault,
          ifReturn = if (names("ifReturn")) resolved.ifReturn else boolDefault,
          // terser `inline: !false_by_default` (index.js:246) — later `true` -> level 3.
          inline =
            if (names("inline")) resolved.inline
            else if (falseByDefault) InlineLevel.InlineDisabled
            else InlineLevel.InlineFull,
          joinVars = if (names("joinVars")) resolved.joinVars else boolDefault,
          lhsConstants = if (names("lhsConstants")) resolved.lhsConstants else boolDefault,
          loops = if (names("loops")) resolved.loops else boolDefault,
          negateIife = if (names("negateIife")) resolved.negateIife else boolDefault,
          properties = if (names("properties")) resolved.properties else boolDefault,
          // terser `pure_getters: !false_by_default && "strict"` (index.js:258) — "strict"
          // under defaults, falsy "" under defaults:false (optionBool maps "" -> false).
          pureGetters =
            if (names("pureGetters")) resolved.pureGetters
            else if (falseByDefault) ""
            else "strict",
          reduceFuncs = if (names("reduceFuncs")) resolved.reduceFuncs else boolDefault,
          reduceVars = if (names("reduceVars")) resolved.reduceVars else boolDefault,
          // terser `sequences: !false_by_default` (index.js:261) — SSG models the limit as
          // Int: 800 (on-default) / 0 (off).
          sequencesLimit =
            if (names("sequencesLimit")) resolved.sequencesLimit
            else if (falseByDefault) 0
            else 800,
          sideEffects = if (names("sideEffects")) resolved.sideEffects else boolDefault,
          switches = if (names("switches")) resolved.switches else boolDefault,
          typeofs = if (names("typeofs")) resolved.typeofs else boolDefault,
          unused = if (names("unused")) resolved.unused else boolDefault
        )

      case GatedPresence.Unspecified =>
        if (resolved.defaults) {
          // defaults == true (or omitted): the case-class defaults ARE terser's
          // `!false_by_default` on-defaults, and any explicit override is already the
          // field value — no resolution needed.
          resolved
        } else {
          // defaults == false with no presence record: approximate presence per-field by
          // value comparison against Defaults (a gated field still at its on-default is
          // treated as unset -> off; a differing value is caller-set -> kept). This is a
          // deterministic per-field resolution, not the deleted majority-match vote.
          val d = Defaults
          val n = NoDefaults
          resolved.copy(
            arrows = if (resolved.arrows == d.arrows) n.arrows else resolved.arrows,
            booleans = if (resolved.booleans == d.booleans) n.booleans else resolved.booleans,
            collapseVars =
              if (resolved.collapseVars == d.collapseVars) n.collapseVars else resolved.collapseVars,
            comparisons =
              if (resolved.comparisons == d.comparisons) n.comparisons else resolved.comparisons,
            computedProps =
              if (resolved.computedProps == d.computedProps) n.computedProps else resolved.computedProps,
            conditionals =
              if (resolved.conditionals == d.conditionals) n.conditionals else resolved.conditionals,
            deadCode = if (resolved.deadCode == d.deadCode) n.deadCode else resolved.deadCode,
            directives = if (resolved.directives == d.directives) n.directives else resolved.directives,
            dropDebugger =
              if (resolved.dropDebugger == d.dropDebugger) n.dropDebugger else resolved.dropDebugger,
            evaluate = if (resolved.evaluate == d.evaluate) n.evaluate else resolved.evaluate,
            hoistProps = if (resolved.hoistProps == d.hoistProps) n.hoistProps else resolved.hoistProps,
            ifReturn = if (resolved.ifReturn == d.ifReturn) n.ifReturn else resolved.ifReturn,
            inline = if (resolved.inline == d.inline) n.inline else resolved.inline,
            joinVars = if (resolved.joinVars == d.joinVars) n.joinVars else resolved.joinVars,
            lhsConstants =
              if (resolved.lhsConstants == d.lhsConstants) n.lhsConstants else resolved.lhsConstants,
            loops = if (resolved.loops == d.loops) n.loops else resolved.loops,
            negateIife = if (resolved.negateIife == d.negateIife) n.negateIife else resolved.negateIife,
            properties = if (resolved.properties == d.properties) n.properties else resolved.properties,
            pureGetters =
              if (resolved.pureGetters == d.pureGetters) n.pureGetters else resolved.pureGetters,
            reduceFuncs =
              if (resolved.reduceFuncs == d.reduceFuncs) n.reduceFuncs else resolved.reduceFuncs,
            reduceVars = if (resolved.reduceVars == d.reduceVars) n.reduceVars else resolved.reduceVars,
            sequencesLimit =
              if (resolved.sequencesLimit == d.sequencesLimit) n.sequencesLimit
              else resolved.sequencesLimit,
            sideEffects =
              if (resolved.sideEffects == d.sideEffects) n.sideEffects else resolved.sideEffects,
            switches = if (resolved.switches == d.switches) n.switches else resolved.switches,
            typeofs = if (resolved.typeofs == d.typeofs) n.typeofs else resolved.typeofs,
            unused = if (resolved.unused == d.unused) n.unused else resolved.unused
          )
        }
    }
  }
}
