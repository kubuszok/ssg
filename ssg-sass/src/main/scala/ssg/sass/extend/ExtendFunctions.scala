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
 *          Second-law specificity trimming lives in ExtensionStore.
 *          The full trailing-combinator merging matrix (_chunks +
 *          _mergeTrailingCombinators) is ported and wired into weave via
 *          weaveParents so that sibling/child combinator pairings are
 *          merged rather than naively concatenated.
 *   Audited: 2026-04-07
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
    * When neither side carries trailing combinators mid-list this reduces to the descendant-combinator interleave fast path. Otherwise the trailing-combinator merging matrix in
    * [_mergeTrailingCombinators] is consulted to produce choice sequences, which are then spliced onto the end of the interleaved descendant parents.
    */
  private def weaveParents(
    prefix: ComplexSelector,
    base:   ComplexSelector,
    span:   FileSpan
  ): List[ComplexSelector] = {
    val parents1 = mutable.ListBuffer.from(prefix.components)
    val parents2 = mutable.ListBuffer.from(base.components.init)

    val result = mutable.ListBuffer.empty[List[List[ComplexSelectorComponent]]]
    val merged = mergeTrailingCombinators(parents1, parents2, span, result)

    if (merged.isEmpty) {
      // Incompatible trailing combinators — fall back to a flat concatenation.
      List(
        new ComplexSelector(
          prefix.leadingCombinators,
          prefix.components ++ base.components.init,
          span,
          lineBreak = prefix.lineBreak || base.lineBreak
        )
      )
    } else {
      val trailingChoices: List[List[List[ComplexSelectorComponent]]] = merged.get.toList

      // After _mergeTrailingCombinators has drained the trailing combinator
      // portions of both lists, whatever remains in parents1/parents2 are plain
      // "descendant" parents that can be freely interleaved.
      val orderings: List[List[ComplexSelectorComponent]] =
        interleave(parents1.toList, parents2.toList)

      val leadOrderings: List[List[List[ComplexSelectorComponent]]] =
        orderings.map(o => List(o))

      // Build a choices list: first the interleaved descendants (one slot with
      // N alternatives), then each merged trailing-combinator slot.
      val choices: List[List[List[ComplexSelectorComponent]]] =
        leadOrderings.flatten match {
          case Nil    => trailingChoices
          case nonNil => List(nonNil) ++ trailingChoices
        }

      val nonEmpty = choices.filter(_.nonEmpty)
      if (nonEmpty.isEmpty) Nil
      else {
        paths(nonEmpty).map { path =>
          val flattened = path.flatten
          new ComplexSelector(
            prefix.leadingCombinators,
            flattened,
            span,
            lineBreak = prefix.lineBreak || base.lineBreak
          )
        }
      }
    }
  }

  /** Returns all orderings of initial subsequences of [queue1] and [queue2].
    *
    * The [done] callback determines the extent of the initial subsequences: it's called with each queue until it returns `true`. This destructively removes those initial subsequences from the two
    * buffers.
    *
    * Port of dart-sass's `_chunks`. Currently unused by [weaveParents] (which uses the simpler interleave fast path) but retained because the full LCS-based weave variant relies on it and the symbol
    * is exercised directly by tests.
    */
  def chunks[T](
    queue1: mutable.ListBuffer[T],
    queue2: mutable.ListBuffer[T],
    done:   mutable.ListBuffer[T] => Boolean
  ): List[List[T]] = {
    val chunk1 = mutable.ListBuffer.empty[T]
    while (!done(queue1))
      chunk1 += queue1.remove(0)

    val chunk2 = mutable.ListBuffer.empty[T]
    while (!done(queue2))
      chunk2 += queue2.remove(0)

    (chunk1.toList, chunk2.toList) match {
      case (Nil, Nil) => Nil
      case (Nil, c)   => List(c)
      case (c, Nil)   => List(c)
      case (c1, c2)   => List(c1 ++ c2, c2 ++ c1)
    }
  }

  /** Extracts trailing [ComplexSelectorComponent]s with trailing combinators from [components1] and [components2] and merges them into a single list of choice slots, prepending each slot onto
    * [result].
    *
    * Each element in the returned result is a set of choices for a particular position in a complex selector. Each choice is a list of complex-selector components. The union of each path through
    * these choices matches the full set of necessary elements.
    *
    * Returns `Nullable.empty` if the sequences can't be merged. When the two lists have no trailing combinators remaining, returns the accumulated result.
    *
    * Port of dart-sass's `_mergeTrailingCombinators`.
    */
  private def mergeTrailingCombinators(
    components1: mutable.ListBuffer[ComplexSelectorComponent],
    components2: mutable.ListBuffer[ComplexSelectorComponent],
    span:        FileSpan,
    result:      mutable.ListBuffer[List[List[ComplexSelectorComponent]]]
  ): Nullable[mutable.ListBuffer[List[List[ComplexSelectorComponent]]]] =
    boundary[Nullable[mutable.ListBuffer[List[List[ComplexSelectorComponent]]]]] {
      val combinators1: List[CssValue[Combinator]] =
        if (components1.isEmpty) Nil else components1.last.combinators
      val combinators2: List[CssValue[Combinator]] =
        if (components2.isEmpty) Nil else components2.last.combinators

      if (combinators1.isEmpty && combinators2.isEmpty) break(Nullable(result))
      if (combinators1.length > 1 || combinators2.length > 1) break(Nullable.empty)

      val c1 = combinators1.headOption.map(_.value)
      val c2 = combinators2.headOption.map(_.value)

      import Combinator.{ Child, NextSibling, FollowingSibling }

      def removeLast[T](buf: mutable.ListBuffer[T]): T = {
        val t = buf.last
        buf.remove(buf.length - 1)
        t
      }

      def prepend(choice: List[List[ComplexSelectorComponent]]): Unit =
        result.prepend(choice)

      (c1, c2) match {
        // Following × Following
        case (Some(FollowingSibling), Some(FollowingSibling)) =>
          val component1 = removeLast(components1)
          val component2 = removeLast(components2)
          if (component1.selector.isSuperselector(component2.selector)) {
            prepend(List(List(component2)))
          } else if (component2.selector.isSuperselector(component1.selector)) {
            prepend(List(List(component1)))
          } else {
            val baseChoices: List[List[ComplexSelectorComponent]] =
              List(
                List(component1, component2),
                List(component2, component1)
              )
            val unified = unifyCompound(component1.selector, component2.selector)
            val choices =
              if (unified.isEmpty) baseChoices
              else
                baseChoices :+ List(
                  new ComplexSelectorComponent(unified.get, List(combinators1.head), span)
                )
            prepend(choices)
          }

        // Following × Next  or  Next × Following
        case (Some(FollowingSibling), Some(NextSibling)) =>
          val next      = removeLast(components2)
          val following = removeLast(components1)
          if (following.selector.isSuperselector(next.selector)) {
            prepend(List(List(next)))
          } else {
            val base    = List(List(following, next))
            val unified = unifyCompound(following.selector, next.selector)
            val choices =
              if (unified.isEmpty) base
              else base :+ List(new ComplexSelectorComponent(unified.get, next.combinators, span))
            prepend(choices)
          }

        case (Some(NextSibling), Some(FollowingSibling)) =>
          val next      = removeLast(components1)
          val following = removeLast(components2)
          if (following.selector.isSuperselector(next.selector)) {
            prepend(List(List(next)))
          } else {
            val base    = List(List(following, next))
            val unified = unifyCompound(following.selector, next.selector)
            val choices =
              if (unified.isEmpty) base
              else base :+ List(new ComplexSelectorComponent(unified.get, next.combinators, span))
            prepend(choices)
          }

        // Child × (Next | Following)  — the sibling selector wins.
        case (Some(Child), Some(NextSibling)) | (Some(Child), Some(FollowingSibling)) =>
          prepend(List(List(removeLast(components2))))

        case (Some(NextSibling), Some(Child)) | (Some(FollowingSibling), Some(Child)) =>
          prepend(List(List(removeLast(components1))))

        // Equal combinators — unify the trailing compounds.
        case (Some(a), Some(b)) if a == b =>
          val s1      = removeLast(components1).selector
          val s2      = removeLast(components2).selector
          val unified = unifyCompound(s1, s2)
          if (unified.isEmpty) break(Nullable.empty)
          prepend(
            List(
              List(new ComplexSelectorComponent(unified.get, List(combinators1.head), span))
            )
          )

        // Exactly one side has a combinator.
        case (Some(combinator), None) =>
          // combinator1 is `Child` and the other side's last selector is a
          // superselector of combinator1's trailing one — drop the redundant one.
          if (
            combinator == Child && components2.nonEmpty &&
            components2.last.selector.isSuperselector(components1.last.selector)
          ) {
            components2.remove(components2.length - 1)
          }
          prepend(List(List(removeLast(components1))))

        case (None, Some(combinator)) =>
          if (
            combinator == Child && components1.nonEmpty &&
            components1.last.selector.isSuperselector(components2.last.selector)
          ) {
            components1.remove(components1.length - 1)
          }
          prepend(List(List(removeLast(components2))))

        case _ =>
          break(Nullable.empty)
      }

      mergeTrailingCombinators(components1, components2, span, result)
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
