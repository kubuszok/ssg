/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Minimal trait for compressor context used by optimization passes.
 *
 * This trait captures the interface that Evaluate, Inference, ReduceVars,
 * DropSideEffectFree, and DropUnused need from the full Compressor class.
 * The full Compressor (Phase 7) will implement this trait.
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

/** Minimal compressor interface used by optimization passes.
  *
  * Provides option queries, parent-chain access, directive checks, and other contextual information that the various compress modules need. The full `Compressor` class (Phase 7) will implement this
  * trait.
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
}

object CompressorLike {

  /** Configuration for what to drop at the top level. */
  final case class ToplevelConfig(
    funcs: Boolean = false,
    vars:  Boolean = false
  )
}
