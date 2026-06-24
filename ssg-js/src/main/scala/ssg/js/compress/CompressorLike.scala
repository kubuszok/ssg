/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Core trait for compressor context used by optimization passes.
 *
 * This trait captures the interface that Evaluate, Inference, ReduceVars,
 * DropSideEffectFree, and DropUnused need from the full Compressor class.
 * The full Compressor will implement this trait.
 *
 * Original source: terser lib/compress/index.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: find_parent -> findParent, in_boolean_context -> inBooleanContext
 *   Convention: Trait with concrete defaults where possible
 *   Idiom: T | Null instead of undefined/null JS unions
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

import scala.collection.mutable

import ssg.js.ast.*

/** Core compressor interface used by optimization passes.
  *
  * Provides option queries, parent-chain access, directive checks, and other contextual information that the various compress modules need. The full `Compressor` will implement this trait.
  */
trait CompressorLike {

  /** Get an option value by name (returns Any to match Terser's dynamic options). */
  def option(name: String): Any

  /** Run the per-node optimizer once on `node`, equivalent to terser's `AST_Node.optimize(compressor)` (lib/compress/index.js `def_optimize`): honors the OPTIMIZED flag, dispatches the node's
    * optimizer a single time, and stamps the flag. Does NOT descend into children. Inlining paths in `Inline.inlineIntoCall` rely on this to re-optimize the sequence/call they synthesize, matching
    * the `.optimize(compressor)` calls in lib/compress/inline.js.
    */
  def optimizeNode(node: AstNode): AstNode

  /** Convenience: check if an option is truthy boolean. */
  def optionBool(name: String): Boolean =
    // Model JS truthiness of `compressor.option(name)`: upstream terser uses
    // option values directly in boolean position (e.g. `is_regular_func &&
    // compressor.option("inline")` in lib/compress/inline.js:353, where
    // `inline` is the numeric level 0..3). A non-zero number, a non-empty
    // string and `true` are truthy; 0, "", false and null are falsy.
    option(name) match {
      case b: Boolean => b
      case n: Int     => n != 0
      case n: Double  => n != 0.0 && !n.isNaN
      case s: String  => s.nonEmpty
      case null       => false
      case scala.None => false // upstream null/undefined is falsy in JS boolean position (e.g. unset top_retain)
      case tc: CompressorLike.ToplevelConfig => tc.funcs || tc.vars // upstream `toplevel: false` is falsy; truthy only when toplevel optimization is enabled
      case _ => true
    }

  /** Access parent nodes in the current tree walk. */
  def parent(n: Int = 0): AstNode | Null

  /** Find the nearest ancestor of a given type. */
  def findParent[T <: AstNode](using ct: scala.reflect.ClassTag[T]): T | Null

  /** Find the nearest enclosing scope from the *live* walker ancestry.
    *
    * Faithful port of terser `compressor.find_parent(AST_Scope)` used by drop-side-effect-free.js:241. In terser the Compressor IS the walker, so `find_parent` reads the live stack; this port's
    * Compressor stack is empty during a pass (the live ancestry is on `activeWalker`). Mirrors `TreeWalker.findScope()` but reads the active walker's stack.
    */
  def liveFindScope(): AstScope | Null

  /** Check if the current context expects a boolean result. */
  def inBooleanContext(): Boolean

  /** Check if the current context is a 32-bit integer context.
    *
    * When `otherOperandMustBeNumber` is true, the bitwise-binop branch narrows: it checks whether the OTHER operand (i.e. the sibling in the binary expression) `is_number`, so we only claim "32-bit
    * context" when the companion operand is known to be numeric. Terser index.js:387-393.
    */
  def in32BitContext(otherOperandMustBeNumber: Boolean = false): Boolean

  /** Check if this scope has a given directive (e.g. "use strict"). */
  def hasDirective(directive: String): AstNode | Null

  /** Check if a definition is exposed (accessible from outside). */
  def exposed(theDef: Any): Boolean

  /** Toplevel drop configuration. */
  var toplevel: CompressorLike.ToplevelConfig = CompressorLike.ToplevelConfig()

  /** Top-retain filter (returns true if a def should be retained). */
  var topRetain: Any => Boolean = _ => false

  /** Pure function filter (returns true if the call is NOT pure). */
  def pureFuncs(call: AstCall): Boolean

  /** Cache of evaluated regular expressions. */
  val evaluatedRegexps: mutable.Map[RegExpValue, Any] = mutable.Map.empty

  /** Maximum number of expressions to merge into a single sequence.
    *
    * Computed from the `sequences` option: if false → 0, if true → 800, if 1 → 800 (terser index.js:327 `sequences == 1 ? 800` — JS `true == 1` is true, so both map to 800), otherwise the numeric
    * value.
    */
  def sequencesLimit: Int = {
    val seq = option("sequences")
    seq match {
      case false => 0
      case true  => 800
      case n: Int if n == 1 => 800
      case n: Int           => n
      case _ => 0
    }
  }

  /** Check if we're in a computed property key context.
    *
    * This is true when inside `[...]` of a computed property access or computed property definition. Used by some optimizations that behave differently in computed key context.
    */
  def inComputedKey(): Boolean = {
    var i = 0
    var p: AstNode | Null = parent(i)
    while (p != null) {
      p.nn match {
        case _: AstObjectProperty | _: AstClassProperty =>
          // Check if we're in the key position (computed)
          val prevParent = if (i > 0) parent(i - 1) else null
          if (prevParent != null) {
            prevParent.nn match {
              case prop: AstObjectProperty if prop.computedKey() => return true // @nowarn
              case prop: AstClassProperty if prop.computedKey()  => return true // @nowarn
              case _ =>
            }
          }
        case _: AstScope =>
          return false // @nowarn — stop at scope boundary
        case _ =>
      }
      i += 1
      p = parent(i)
    }
    false
  }

  /** Get mangle options for scope analysis.
    *
    * Returns formatted mangle options including the nth_identifier generator and module flag. Used by scope analysis and other passes that need to know how names will be mangled.
    */
  def mangleOptions(): Map[String, Any] =
    // Default implementation returns basic module flag
    Map("module" -> optionBool("module"))
}

object CompressorLike {

  /** Configuration for what to drop at the top level. */
  final case class ToplevelConfig(
    funcs: Boolean = false,
    vars:  Boolean = false
  )
}
