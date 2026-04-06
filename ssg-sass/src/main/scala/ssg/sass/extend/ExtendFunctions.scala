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
 *   Idiom: Pragmatic port of paths / unifyCompound / unifyComplex / weave.
 *          Skips "second law" specificity trimming edge cases and the
 *          trailing-sibling-combinator merging matrix in favour of the
 *          descendant / child cases that cover typical extend output.
 */
package ssg
package sass
package extend

import ssg.sass.Nullable
import ssg.sass.ast.css.CssValue
import ssg.sass.ast.selector.{ Combinator, ComplexSelector, ComplexSelectorComponent, CompoundSelector, SelectorList }
import ssg.sass.util.FileSpan

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Utility functions related to extending selectors.
  *
  * These functions aren't private methods on [ExtensionStore] because they also need to be accessible from elsewhere in the codebase. In addition, they aren't instance methods on other objects
  * because their APIs aren't a good fit — usually because they deal with raw component lists rather than selector classes, to reduce allocations.
  */
object ExtendFunctions {

  /** Pseudo-selectors that can only meaningfully appear in the first component of a complex selector.
    */
  val RootishPseudoClasses: Set[String] = Set("root", "scope", "host", "host-context")

  /** Returns a [CompoundSelector] that matches only elements matched by both [compound1] and [compound2], or `Nullable.empty` if no such selector exists.
    *
    * Note: unlike the Dart original this does not maintain strict pseudo-class / pseudo-element ordering; it defers to [SelectorList.unifyCompounds] which folds `compound2`'s simples into `compound1`
    * one at a time via [SimpleSelector.unify].
    */
  def unifyCompound(
    compound1: CompoundSelector,
    compound2: CompoundSelector
  ): Nullable[CompoundSelector] =
    SelectorList.unifyCompounds(compound1, compound2)

  /** Returns the contents of a [SelectorList] that matches only elements that are matched by every complex selector in [complexes].
    *
    * This is a pragmatic port of dart-sass's `unifyComplex`. It handles the common cases required by `@extend` wiring — leading-combinator checks, unification of the trailing base compound, and
    * delegation to [weave] for the prefixes — while skipping the trailing-combinator merging matrix and second-law specificity trimming that drive only rare edge cases.
    *
    * Returns `Nullable.empty` if no such list can be produced.
    */
  def unifyComplex(
    complexes: List[ComplexSelector],
    span:      FileSpan
  ): Nullable[List[ComplexSelector]] =
    boundary[Nullable[List[ComplexSelector]]] {
      if (complexes.length == 1) break(Nullable(complexes))

      var unifiedBase:        Nullable[CompoundSelector]     = Nullable.empty
      var leadingCombinator:  Nullable[CssValue[Combinator]] = Nullable.empty
      var trailingCombinator: Nullable[CssValue[Combinator]] = Nullable.empty

      for (complex <- complexes) {
        if (complex.components.isEmpty) break(Nullable.empty)

        // Single-component with a leading combinator: merge.
        if (complex.components.length == 1 && complex.leadingCombinators.length == 1) {
          val newLeading = complex.leadingCombinators.head
          if (leadingCombinator.isEmpty) leadingCombinator = Nullable(newLeading)
          else if (leadingCombinator.get != newLeading) break(Nullable.empty)
        } else if (complex.leadingCombinators.nonEmpty) {
          // Any other leading combinator combination is unsupported here.
          break(Nullable.empty)
        }

        val base = complex.components.last
        if (base.combinators.length == 1) {
          val newTrailing = base.combinators.head
          if (trailingCombinator.isDefined && trailingCombinator.get != newTrailing)
            break(Nullable.empty)
          trailingCombinator = Nullable(newTrailing)
        } else if (base.combinators.length > 1) {
          break(Nullable.empty)
        }

        if (unifiedBase.isEmpty) unifiedBase = Nullable(base.selector)
        else {
          val merged = unifyCompound(unifiedBase.get, base.selector)
          if (merged.isEmpty) break(Nullable.empty)
          unifiedBase = merged
        }
      }

      val withoutBases: List[ComplexSelector] =
        complexes.collect {
          case c if c.components.length > 1 =>
            new ComplexSelector(
              c.leadingCombinators,
              c.components.init,
              c.span,
              lineBreak = c.lineBreak
            )
        }

      val baseComponent =
        new ComplexSelectorComponent(
          unifiedBase.get,
          if (trailingCombinator.isEmpty) Nil else List(trailingCombinator.get),
          span
        )
      val base = new ComplexSelector(
        if (leadingCombinator.isEmpty) Nil else List(leadingCombinator.get),
        List(baseComponent),
        span,
        lineBreak = complexes.exists(_.lineBreak)
      )

      val woven =
        if (withoutBases.isEmpty) weave(List(base), span)
        else {
          val init    = withoutBases.init
          val last    = withoutBases.last
          val newLast = last.concatenate(base, span)
          weave(init :+ newLast, span)
        }
      Nullable(woven)
    }

  /** Interweaves a sequence of complex-selector prefixes into every possible ordering that respects each input's internal order.
    *
    * This is a simplified port of dart-sass's `weave`. It handles the descendant-combinator / child-combinator cases needed by the common `@extend` rewrites. The trailing-sibling-combinator merge
    * matrix is skipped — prefixes whose final component carries a trailing combinator are passed through unchanged via [ComplexSelector.concatenate].
    */
  def weave(
    complexes: List[ComplexSelector],
    span:      FileSpan
  ): List[ComplexSelector] = complexes match {
    case Nil            => Nil
    case complex :: Nil => List(complex)
    case _              =>
      var prefixes: List[ComplexSelector] = List(complexes.head)
      for (complex <- complexes.tail)
        if (complex.components.length == 1 && complex.leadingCombinators.isEmpty) {
          prefixes = prefixes.map(_.concatenate(complex, span))
        } else {
          val next = mutable.ListBuffer.empty[ComplexSelector]
          for (prefix <- prefixes) {
            val parentOrderings = weaveParents(prefix, complex, span)
            for (parentPrefix <- parentOrderings)
              next += parentPrefix.withAdditionalComponent(
                complex.components.last,
                span
              )
          }
          prefixes = next.toList
        }
      prefixes
  }

  /** Returns all orderings of `prefix`'s components interleaved with `base`'s components _other than the last_, preserving the relative order of each.
    *
    * This is the descendant-combinator fast path used by [weave]. Child / sibling combinators mid-prefix are not handled explicitly — such cases fall back to the plain concatenation ordering.
    */
  private def weaveParents(
    prefix: ComplexSelector,
    base:   ComplexSelector,
    span:   FileSpan
  ): List[ComplexSelector] = {
    val parents1  = prefix.components
    val parents2  = base.components.init
    val orderings = interleave(parents1, parents2)
    orderings.map { components =>
      new ComplexSelector(
        prefix.leadingCombinators,
        components,
        span,
        lineBreak = prefix.lineBreak || base.lineBreak
      )
    }
  }

  /** Returns every ordered interleaving of `xs` and `ys` that preserves the relative order of each input.
    */
  private def interleave[T](
    xs: List[T],
    ys: List[T]
  ): List[List[T]] = (xs, ys) match {
    case (Nil, _)           => List(ys)
    case (_, Nil)           => List(xs)
    case (x :: xr, y :: yr) =>
      interleave(xr, ys).map(x :: _) ++ interleave(xs, yr).map(y :: _)
  }

  /** Returns all paths through a list of choices.
    *
    * For example, given `[[1, 2], [3, 4], [5]]`, this returns `[[1, 3, 5], [2, 3, 5], [1, 4, 5], [2, 4, 5]]`.
    */
  def paths[T](choices: List[List[T]]): List[List[T]] =
    choices.foldLeft(List(List.empty[T])) { (acc, options) =>
      for {
        prefix <- acc
        option <- options
      } yield prefix :+ option
    }
}
