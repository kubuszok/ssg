/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/extend/functions.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: functions.dart -> ExtendFunctions.scala
 *   Convention: Top-level Dart functions -> Scala object methods
 *   Idiom: Phase 7 skeleton — unifyComplex / unifyCompound / weave / paths
 *          deferred to Phase 10. Pseudo-class constants preserved.
 */
package ssg
package sass
package extend

import ssg.sass.Nullable
import ssg.sass.ast.selector.{ ComplexSelector, CompoundSelector }
import ssg.sass.util.FileSpan

/** Utility functions related to extending selectors.
  *
  * These functions aren't private methods on [ExtensionStore] because they also need to be accessible from elsewhere in the codebase. In addition, they aren't instance methods on other objects
  * because their APIs aren't a good fit — usually because they deal with raw component lists rather than selector classes, to reduce allocations.
  */
object ExtendFunctions {

  /** Pseudo-selectors that can only meaningfully appear in the first component of a complex selector.
    */
  val RootishPseudoClasses: Set[String] = Set("root", "scope", "host", "host-context")

  /** Returns the contents of a [SelectorList] that matches only elements that are matched by every complex selector in [complexes].
    *
    * If no such list can be produced, returns `Nullable.empty`.
    */
  def unifyComplex(
    complexes: List[ComplexSelector],
    span:      FileSpan
  ): Nullable[List[ComplexSelector]] = {
    // TODO: Phase 10 — port the full unification algorithm from
    //   functions.dart (leading/trailing combinators, rootish handling,
    //   weave integration).
    val _ = (complexes, span)
    Nullable.empty
  }

  /** Returns a [CompoundSelector] that matches only elements matched by both [compound1] and [compound2], or `Nullable.empty` if no such selector exists.
    */
  def unifyCompound(
    compound1: CompoundSelector,
    compound2: CompoundSelector
  ): Nullable[CompoundSelector] = {
    // TODO: Phase 10 — port unification of compound selectors.
    val _ = (compound1, compound2)
    Nullable.empty
  }

  /** Expands "parenthesized selectors" in [complexes] and returns a list of complex selectors, none of which contain parenthesized selectors.
    */
  def weave(
    complexes: List[List[ComplexSelector]],
    span:      FileSpan
  ): List[ComplexSelector] = {
    // TODO: Phase 10 — port the weave algorithm (second law of extend).
    val _ = (complexes, span)
    Nil
  }

  /** Returns all pairs of elements where the first element is taken from [list1] and the second from [list2].
    */
  def paths[T](choices: List[List[T]]): List[List[T]] = {
    // TODO: Phase 10 — port cross-product enumeration used by weave.
    val _ = choices
    Nil
  }
}
