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

  def fromAny(value: Any, hasTopRetain: Boolean): ToplevelConfig =
    value match {
      case true  => ToplevelConfig(funcs = true, vars = true)
      case false => ToplevelConfig(funcs = hasTopRetain, vars = hasTopRetain)
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
  sequencesLimit: Int = 200,
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
  warnings: Boolean = false
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

  /** All default-gated passes disabled — `compress: { defaults: false }` in Terser.
    *
    * Matches lib/compress/index.js:222-275: every option whose default is `!false_by_default` is set to `false` / disabled. Options that are already off by default (e.g. `arguments`, `hoist_funs`,
    * `unsafe_*`) or non-boolean (e.g. `ecma`, `passes`) keep their normal defaults.
    *
    * To enable a specific pass on top of this, use `.copy(evaluate = true)` etc.
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
    unused = false
  )

  /** Resolve `defaults = false` semantics, matching terser lib/compress/index.js:222.
    *
    * When `o.defaults == false`, each DEFAULT-GATED field (the fields overridden by
    * [[NoDefaults]]) that still equals its [[Defaults]] value is replaced with the
    * [[NoDefaults]] value (i.e. turned off). Fields the caller explicitly changed
    * (value differs from [[Defaults]]) are preserved.
    *
    * When `o.defaults != false`, returns `o` unchanged — this is a strict no-op.
    *
    * When the gated fields already predominantly match [[NoDefaults]] (i.e. the
    * options were likely built via `NoDefaults.copy(...)` rather than
    * `CompressorOptions(defaults = false, ...)`), the method returns `o` unchanged
    * to avoid clobbering explicitly re-enabled passes.
    *
    * Limitation: because a Scala case class cannot distinguish "the caller wrote
    * `evaluate = true`" from "the default was `true`", the value-comparison
    * heuristic for `CompressorOptions(defaults = false, evaluate = true)` treats
    * both as "unchanged" and turns evaluate off. To explicitly KEEP a normally-ON
    * pass under `defaults = false`, use `CompressorOptions.NoDefaults.copy(evaluate
    * = true)` instead — that is the documented API for this edge case (the
    * case-class-vs-presence tradeoff).
    *
    * terser lib/compress/index.js:220-275:
    * {{{
    *   if (options.defaults !== undefined && !options.defaults)
    *       false_by_default = true;
    *   // ... each gated option defaults to `!false_by_default`
    * }}}
    */
  def resolveDefaults(o: CompressorOptions): CompressorOptions = {
    if (o.defaults) {
      // defaults == true (or omitted) — no resolution needed
      o
    } else {
      val d = Defaults
      val n = NoDefaults

      // Count how many gated fields already match NoDefaults vs Defaults.
      // If the majority already match NoDefaults, the options were likely built
      // via `NoDefaults.copy(...)` and should be left as-is; resolving would
      // clobber any explicitly re-enabled passes (e.g. `NoDefaults.copy(evaluate
      // = true)` would lose its `evaluate = true` because it matches Defaults).
      var matchesNoDefaults = 0
      var matchesDefaults   = 0
      if (o.arrows == n.arrows) matchesNoDefaults += 1
      if (o.arrows == d.arrows) matchesDefaults += 1
      if (o.booleans == n.booleans) matchesNoDefaults += 1
      if (o.booleans == d.booleans) matchesDefaults += 1
      if (o.collapseVars == n.collapseVars) matchesNoDefaults += 1
      if (o.collapseVars == d.collapseVars) matchesDefaults += 1
      if (o.comparisons == n.comparisons) matchesNoDefaults += 1
      if (o.comparisons == d.comparisons) matchesDefaults += 1
      if (o.computedProps == n.computedProps) matchesNoDefaults += 1
      if (o.computedProps == d.computedProps) matchesDefaults += 1
      if (o.conditionals == n.conditionals) matchesNoDefaults += 1
      if (o.conditionals == d.conditionals) matchesDefaults += 1
      if (o.deadCode == n.deadCode) matchesNoDefaults += 1
      if (o.deadCode == d.deadCode) matchesDefaults += 1
      if (o.directives == n.directives) matchesNoDefaults += 1
      if (o.directives == d.directives) matchesDefaults += 1
      if (o.dropDebugger == n.dropDebugger) matchesNoDefaults += 1
      if (o.dropDebugger == d.dropDebugger) matchesDefaults += 1
      if (o.evaluate == n.evaluate) matchesNoDefaults += 1
      if (o.evaluate == d.evaluate) matchesDefaults += 1
      if (o.hoistProps == n.hoistProps) matchesNoDefaults += 1
      if (o.hoistProps == d.hoistProps) matchesDefaults += 1
      if (o.ifReturn == n.ifReturn) matchesNoDefaults += 1
      if (o.ifReturn == d.ifReturn) matchesDefaults += 1
      if (o.inline == n.inline) matchesNoDefaults += 1
      if (o.inline == d.inline) matchesDefaults += 1
      if (o.joinVars == n.joinVars) matchesNoDefaults += 1
      if (o.joinVars == d.joinVars) matchesDefaults += 1
      if (o.lhsConstants == n.lhsConstants) matchesNoDefaults += 1
      if (o.lhsConstants == d.lhsConstants) matchesDefaults += 1
      if (o.loops == n.loops) matchesNoDefaults += 1
      if (o.loops == d.loops) matchesDefaults += 1
      if (o.negateIife == n.negateIife) matchesNoDefaults += 1
      if (o.negateIife == d.negateIife) matchesDefaults += 1
      if (o.properties == n.properties) matchesNoDefaults += 1
      if (o.properties == d.properties) matchesDefaults += 1
      if (o.pureGetters == n.pureGetters) matchesNoDefaults += 1
      if (o.pureGetters == d.pureGetters) matchesDefaults += 1
      if (o.reduceFuncs == n.reduceFuncs) matchesNoDefaults += 1
      if (o.reduceFuncs == d.reduceFuncs) matchesDefaults += 1
      if (o.reduceVars == n.reduceVars) matchesNoDefaults += 1
      if (o.reduceVars == d.reduceVars) matchesDefaults += 1
      if (o.sequencesLimit == n.sequencesLimit) matchesNoDefaults += 1
      if (o.sequencesLimit == d.sequencesLimit) matchesDefaults += 1
      if (o.sideEffects == n.sideEffects) matchesNoDefaults += 1
      if (o.sideEffects == d.sideEffects) matchesDefaults += 1
      if (o.switches == n.switches) matchesNoDefaults += 1
      if (o.switches == d.switches) matchesDefaults += 1
      if (o.typeofs == n.typeofs) matchesNoDefaults += 1
      if (o.typeofs == d.typeofs) matchesDefaults += 1
      if (o.unused == n.unused) matchesNoDefaults += 1
      if (o.unused == d.unused) matchesDefaults += 1

      if (matchesNoDefaults > matchesDefaults) {
        // The gated fields predominantly match NoDefaults — this was likely
        // built via `NoDefaults.copy(...)`. Leave as-is to preserve any
        // explicitly re-enabled passes.
        o
      } else {
        // defaults == false and gated fields match Defaults: resolve each
        // gated field from its default-on value to its default-off value.
        //
        // This mirrors terser's `false_by_default = true` + `!false_by_default`
        // pattern (index.js:222-275): unset options resolve to their "off" value;
        // explicitly-set options are preserved by the `defaults()` helper because
        // the caller's value takes precedence over the default.
        o.copy(
          arrows = if (o.arrows == d.arrows) n.arrows else o.arrows,
          booleans = if (o.booleans == d.booleans) n.booleans else o.booleans,
          collapseVars = if (o.collapseVars == d.collapseVars) n.collapseVars else o.collapseVars,
          comparisons = if (o.comparisons == d.comparisons) n.comparisons else o.comparisons,
          computedProps = if (o.computedProps == d.computedProps) n.computedProps else o.computedProps,
          conditionals = if (o.conditionals == d.conditionals) n.conditionals else o.conditionals,
          deadCode = if (o.deadCode == d.deadCode) n.deadCode else o.deadCode,
          directives = if (o.directives == d.directives) n.directives else o.directives,
          dropDebugger = if (o.dropDebugger == d.dropDebugger) n.dropDebugger else o.dropDebugger,
          evaluate = if (o.evaluate == d.evaluate) n.evaluate else o.evaluate,
          hoistProps = if (o.hoistProps == d.hoistProps) n.hoistProps else o.hoistProps,
          ifReturn = if (o.ifReturn == d.ifReturn) n.ifReturn else o.ifReturn,
          inline = if (o.inline == d.inline) n.inline else o.inline,
          joinVars = if (o.joinVars == d.joinVars) n.joinVars else o.joinVars,
          lhsConstants = if (o.lhsConstants == d.lhsConstants) n.lhsConstants else o.lhsConstants,
          loops = if (o.loops == d.loops) n.loops else o.loops,
          negateIife = if (o.negateIife == d.negateIife) n.negateIife else o.negateIife,
          properties = if (o.properties == d.properties) n.properties else o.properties,
          pureGetters = if (o.pureGetters == d.pureGetters) n.pureGetters else o.pureGetters,
          reduceFuncs = if (o.reduceFuncs == d.reduceFuncs) n.reduceFuncs else o.reduceFuncs,
          reduceVars = if (o.reduceVars == d.reduceVars) n.reduceVars else o.reduceVars,
          sequencesLimit =
            if (o.sequencesLimit == d.sequencesLimit) n.sequencesLimit else o.sequencesLimit,
          sideEffects = if (o.sideEffects == d.sideEffects) n.sideEffects else o.sideEffects,
          switches = if (o.switches == d.switches) n.switches else o.switches,
          typeofs = if (o.typeofs == d.typeofs) n.typeofs else o.typeofs,
          unused = if (o.unused == d.unused) n.unused else o.unused
        )
      }
    }
  }
}
