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

  /** Convenience: check if an option is truthy boolean. */
  def optionBool(name: String): Boolean =
    option(name) match {
      case b: Boolean => b
      case _ => false
    }

  /** Access parent nodes in the current tree walk. */
  def parent(n: Int = 0): AstNode | Null

  /** Find the nearest ancestor of a given type. */
  def findParent[T <: AstNode](using ct: scala.reflect.ClassTag[T]): T | Null

  /** Check if the current context expects a boolean result. */
  def inBooleanContext(): Boolean

  /** Check if the current context is a 32-bit integer context. */
  def in32BitContext(): Boolean

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
    * Computed from the `sequences` option: if false → 0, if true → 200,
    * if 1 → 800 (special case for maximum merging), otherwise the numeric value.
    */
  def sequencesLimit: Int = {
    val seq = option("sequences")
    seq match {
      case false     => 0
      case true      => 200
      case n: Int if n == 1 => 800
      case n: Int    => n
      case _         => 0
    }
  }

  /** Check if we're in a computed property key context.
    *
    * This is true when inside `[...]` of a computed property access or
    * computed property definition. Used by some optimizations that behave
    * differently in computed key context.
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
              case prop: AstClassProperty if prop.computedKey() => return true // @nowarn
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
    * Returns formatted mangle options including the nth_identifier generator
    * and module flag. Used by scope analysis and other passes that need to
    * know how names will be mangled.
    */
  def mangleOptions(): Map[String, Any] = {
    // Default implementation returns basic module flag
    Map("module" -> optionBool("module"))
  }
}

object CompressorLike {

  /** Configuration for what to drop at the top level. */
  final case class ToplevelConfig(
    funcs: Boolean = false,
    vars:  Boolean = false
  )
}
