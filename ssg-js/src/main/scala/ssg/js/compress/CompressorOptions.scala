/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Full compressor options for the Terser-compatible JS minifier.
 *
 * Each option controls a specific optimization pass or behavior.
 * Defaults match Terser's `defaults: true` configuration.
 *
 * Ported from: terser lib/compress/index.js (Compressor constructor)
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: snake_case JS options -> camelCase Scala fields with snake_case
 *     accessors for compatibility
 *   Convention: case class with default values instead of JS defaults() helper
 *   Idiom: sealed trait InlineLevel instead of boolean|int union
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
  /** Optimize `if/return` and `if/continue`. */
  ifReturn: Boolean = true,
  /** Inline function calls. */
  inline: InlineLevel = InlineLevel.InlineFull,
  /** Join consecutive `var` statements. */
  joinVars: Boolean = true,
  /** Prevent class name mangling. `true` keeps all, regex/set filters. */
  keepClassnames: Boolean = false,
  /** Prevent unused function args from being dropped. */
  keepFargs: Boolean = true,
  /** Prevent function name mangling. `true` keeps all. */
  keepFnames: Boolean = false,
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
  /** Assume property access has no side effects. `"strict"` = only on known-safe objects. */
  pureGetters: String = "strict",
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
  unused: Boolean = true
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
      case "reduce_funcs"     => reduceFuncs
      case "reduce_vars"      => reduceVars
      case "sequences"        => sequencesLimit > 0
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
      case _                  => false
    }
}
