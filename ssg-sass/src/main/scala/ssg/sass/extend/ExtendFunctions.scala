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
 *   Idiom: Full port of paths / unifyCompound / unifyComplex / weave /
 *          weaveParents with LCS-based interleaving, rootish selector
 *          unification, and all helper functions (_groupSelectors,
 *          _firstIfRootish, _mergeLeadingCombinators, _mustUnify,
 *          _complexIsParentSuperselector, _chunks,
 *          _mergeTrailingCombinators).
 *          Second-law specificity trimming lives in ExtensionStore.
 *   Audited: 2026-04-07
 */
package ssg
package sass
package extend

import ssg.sass.Nullable
import ssg.sass.ast.css.CssValue
import ssg.sass.ast.selector.{ Combinator, ComplexSelector, ComplexSelectorComponent, CompoundSelector, IDSelector, PlaceholderSelector, PseudoSelector, SimpleSelector }
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

  /** Returns a [CompoundSelector] that matches only elements that are matched by both [compound1] and [compound2].
    *
    * The [compound1]'s `span` will be used for the new unified selector.
    *
    * This function ensures that the relative order of pseudo-classes (`:`) and pseudo-elements (`::`) within each input selector is preserved in the resulting combined selector.
    *
    * This function enforces a general rule that pseudo-classes (`:`) should come before pseudo-elements (`::`), but it won't change their order if they were originally interleaved within a single
    * input selector. This prevents unintended changes to the selector's meaning. For example, unifying `::foo:bar` and `:baz` results in `:baz::foo:bar`. `:baz` is a pseudo-class, so it is moved
    * before the pseudo-class `::foo`. Meanwhile, `:bar` is not moved before `::foo` because it appeared after `::foo` in the original selector.
    *
    * If no such selector can be produced, returns `Nullable.empty`.
    */
  def unifyCompound(
    compound1: CompoundSelector,
    compound2: CompoundSelector
  ): Nullable[CompoundSelector] =
    boundary[Nullable[CompoundSelector]] {
      var result: List[SimpleSelector] = compound1.components
      var pseudoResult                 = List.empty[SimpleSelector]
      var pseudoElementFound           = false

      for (simple <- compound2.components) {
        // All pseudo-classes are unified separately after a pseudo-element to
        // preserve their relative order with the pseudo-element.
        if (pseudoElementFound && simple.isInstanceOf[PseudoSelector]) {
          val unified = simple.unify(pseudoResult)
          if (unified.isEmpty) break(Nullable.Null)
          pseudoResult = unified.get
        } else {
          pseudoElementFound |= simple.isInstanceOf[PseudoSelector] && simple.asInstanceOf[PseudoSelector].isElement
          val unified = simple.unify(result)
          if (unified.isEmpty) break(Nullable.Null)
          result = unified.get
        }
      }

      Nullable(new CompoundSelector(result ++ pseudoResult, compound1.span))
    }

  /** Returns the contents of a [SelectorList] that matches only elements that are matched by every complex selector in [complexes].
    *
    * The [span] is used for the unified complex selectors.
    *
    * If no such list can be produced, returns `Nullable.empty`.
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
        if (complex.isUseless) break(Nullable.empty)

        // Single-component with a leading combinator: merge.
        if (complex.components.length == 1 && complex.leadingCombinators.length == 1) {
          val newLeading = complex.leadingCombinators.head
          if (leadingCombinator.isEmpty) leadingCombinator = Nullable(newLeading)
          else if (leadingCombinator.get != newLeading) break(Nullable.empty)
        }

        val base = complex.components.last
        if (base.combinators.length == 1) {
          val newTrailing = base.combinators.head
          if (trailingCombinator.isDefined && trailingCombinator.get != newTrailing)
            break(Nullable.empty)
          trailingCombinator = Nullable(newTrailing)
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

  /** Expands "parenthesized selectors" in [complexes].
    *
    * That is, if we have `.A .B {@extend .C}` and `.D .C {...}`, this conceptually expands into `.D .C, .D (.A .B)`, and this function translates `.D (.A .B)` into `.D .A .B, .A .D .B`. For
    * thoroughness, `.A.D .B` would also be required, but including merged selectors results in exponential output for very little gain.
    *
    * The selector `.D (.A .B)` is represented as the list `[.D, .A .B]`.
    *
    * The [span] will be used for any new combined selectors.
    *
    * If [forceLineBreak] is `true`, this will mark all returned complex selectors as having line breaks.
    */
  def weave(
    complexes:      List[ComplexSelector],
    span:           FileSpan,
    forceLineBreak: Boolean = false
  ): List[ComplexSelector] = complexes match {
    case Nil => Nil
    case complex :: Nil =>
      if (!forceLineBreak || complex.lineBreak) complexes
      else
        List(
          new ComplexSelector(
            complex.leadingCombinators,
            complex.components,
            complex.span,
            lineBreak = true
          )
        )
    case _ =>
      var prefixes: List[ComplexSelector] = List(complexes.head)
      for (complex <- complexes.tail)
        if (complex.components.length == 1) {
          prefixes = prefixes.map(_.concatenate(complex, span, forceLineBreak = forceLineBreak))
        } else {
          prefixes = {
            for {
              prefix       <- prefixes
              parentPrefix <- {
                val wp = weaveParents(prefix, complex, span)
                if (wp.isEmpty) Nil else wp.get
              }
            } yield parentPrefix.withAdditionalComponent(
              complex.components.last,
              span,
              forceLineBreak = forceLineBreak
            )
          }
        }
      prefixes
  }

  /** Returns all orderings of `prefix`'s components interleaved with `base`'s components _other than the last_, preserving the relative order of each.
    *
    * Uses the LCS-based algorithm from dart-sass `_weaveParents`. Returns `Nullable.empty` if the intersection is empty (incompatible combinators or rootish selectors).
    *
    * Semantically, for selectors `P` and `C`, this returns all selectors `PC_i` such that the union over all `i` of elements matched by `PC_i` is identical to the intersection of all elements
    * matched by `C` and all descendants of elements matched by `P`. Some `PC_i` are elided to reduce the size of the output.
    */
  private def weaveParents(
    prefix: ComplexSelector,
    base:   ComplexSelector,
    span:   FileSpan
  ): Nullable[List[ComplexSelector]] =
    boundary[Nullable[List[ComplexSelector]]] {
      val leadingCombinators = mergeLeadingCombinators(
        prefix.leadingCombinators,
        base.leadingCombinators
      )
      if (leadingCombinators.isEmpty) break(Nullable.empty)

      // Make queues of _only_ the parent selectors. The prefix only contains
      // parents, but the complex selector has a target that we don't want to weave
      // in.
      val queue1 = mutable.ListBuffer.from(prefix.components)
      val queue2 = mutable.ListBuffer.from(base.components.init)

      val result = mutable.ListBuffer.empty[List[List[ComplexSelectorComponent]]]
      val trailingCombinators = mergeTrailingCombinators(queue1, queue2, span, result)
      if (trailingCombinators.isEmpty) break(Nullable.empty)

      // Make sure all selectors that are required to be at the root are unified
      // with one another.
      val rootish1 = firstIfRootish(queue1)
      val rootish2 = firstIfRootish(queue2)
      if (rootish1.isDefined && rootish2.isDefined) {
        val rootish = unifyCompound(rootish1.get.selector, rootish2.get.selector)
        if (rootish.isEmpty) break(Nullable.empty)
        queue1.prepend(
          new ComplexSelectorComponent(rootish.get, rootish1.get.combinators, rootish1.get.span)
        )
        queue2.prepend(
          new ComplexSelectorComponent(rootish.get, rootish2.get.combinators, rootish1.get.span)
        )
      } else if (rootish1.isDefined) {
        // If there's only one rootish selector, it should only appear in the first
        // position of the resulting selector. We can ensure that happens by adding
        // it to the beginning of _both_ queues.
        queue1.prepend(rootish1.get)
        queue2.prepend(rootish1.get)
      } else if (rootish2.isDefined) {
        queue1.prepend(rootish2.get)
        queue2.prepend(rootish2.get)
      }

      val groups1 = groupSelectors(queue1)
      val groups2 = groupSelectors(queue2)
      val lcs = Utils.longestCommonSubsequence[List[ComplexSelectorComponent]](
        groups2.toList,
        groups1.toList,
        select = (group1, group2) => {
          if (group1 == group2) Nullable(group1)
          else if (complexIsParentSuperselector(group1, group2)) Nullable(group2)
          else if (complexIsParentSuperselector(group2, group1)) Nullable(group1)
          else if (!mustUnify(group1, group2)) Nullable.Null
          else {
            val unified = unifyComplex(
              List(
                new ComplexSelector(Nil, group1, span),
                new ComplexSelector(Nil, group2, span)
              ),
              span
            )
            if (unified.isEmpty) Nullable.Null
            else {
              val uList = unified.get
              if (uList.length == 1) Nullable(uList.head.components)
              else Nullable.Null
            }
          }
        }
      )

      val choices = mutable.ListBuffer.empty[List[Iterable[ComplexSelectorComponent]]]
      for (group <- lcs) {
        choices += chunks[List[ComplexSelectorComponent]](
          groups1,
          groups2,
          (sequence) => complexIsParentSuperselector(sequence(0), group)
        ).map(chunk => chunk.flatMap(identity))
        choices += List(group)
        groups1.remove(0)
        groups2.remove(0)
      }
      choices += chunks[List[ComplexSelectorComponent]](
        groups1,
        groups2,
        (sequence) => sequence.isEmpty
      ).map(chunk => chunk.flatMap(identity))
      choices ++= trailingCombinators.get.toList

      Nullable(
        paths(choices.filter(_.nonEmpty).toList).map { path =>
          new ComplexSelector(
            leadingCombinators.get,
            path.flatMap(identity),
            span,
            lineBreak = prefix.lineBreak || base.lineBreak
          )
        }
      )
    }

  /** Returns all orderings of initial subsequences of [queue1] and [queue2].
    *
    * The [done] callback determines the extent of the initial subsequences: it's called with each queue until it returns `true`. This destructively removes those initial subsequences from the two
    * buffers.
    *
    * Port of dart-sass's `_chunks`. Used by [weaveParents] in the LCS-based interleaving algorithm.
    */
  private def chunks[T](
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

  /** Returns a leading combinator list that's compatible with both [combinators1] and [combinators2].
    *
    * Returns `Nullable.empty` if the combinator lists can't be unified.
    */
  private def mergeLeadingCombinators(
    combinators1: List[CssValue[Combinator]],
    combinators2: List[CssValue[Combinator]]
  ): Nullable[List[CssValue[Combinator]]] = {
    if (combinators1.length > 1 || combinators2.length > 1) Nullable.empty
    else if (combinators1.isEmpty) Nullable(combinators2)
    else if (combinators2.isEmpty) Nullable(combinators1)
    else if (combinators1 == combinators2) Nullable(combinators1)
    else Nullable.empty
  }

  /** If the first element of [queue] has a selector like `:root` that can only appear in a complex selector's first component, removes and returns that element.
    */
  private def firstIfRootish(
    queue: mutable.ListBuffer[ComplexSelectorComponent]
  ): Nullable[ComplexSelectorComponent] =
    boundary[Nullable[ComplexSelectorComponent]] {
      if (queue.isEmpty) break(Nullable.empty)
      val first = queue(0)
      val found = first.selector.components.exists { simple =>
        simple.isInstanceOf[PseudoSelector] && {
          val pseudo = simple.asInstanceOf[PseudoSelector]
          pseudo.isClass && RootishPseudoClasses.contains(pseudo.normalizedName)
        }
      }
      if (found) {
        queue.remove(0)
        Nullable(first)
      } else {
        Nullable.empty
      }
    }

  /** Returns [complex], grouped into the longest possible sub-lists such that [ComplexSelectorComponent]s without combinators only appear at the end of sub-lists.
    *
    * For example, `(A B > C D + E ~ G)` is grouped into `[(A) (B > C) (D + E ~ G)]`.
    */
  private def groupSelectors(
    complex: Iterable[ComplexSelectorComponent]
  ): mutable.ListBuffer[List[ComplexSelectorComponent]] = {
    val groups = mutable.ListBuffer.empty[List[ComplexSelectorComponent]]
    var group  = mutable.ListBuffer.empty[ComplexSelectorComponent]
    for (component <- complex) {
      group += component
      if (component.combinators.isEmpty) {
        groups += group.toList
        group = mutable.ListBuffer.empty[ComplexSelectorComponent]
      }
    }

    if (group.nonEmpty) groups += group.toList
    groups
  }

  /** Returns whether [complex1] and [complex2] need to be unified to produce a valid combined selector.
    *
    * This is necessary when both selectors contain the same unique simple selector, such as an ID.
    */
  private def mustUnify(
    complex1: List[ComplexSelectorComponent],
    complex2: List[ComplexSelectorComponent]
  ): Boolean =
    boundary[Boolean] {
      val uniqueSelectors = {
        val buf = mutable.LinkedHashSet.empty[SimpleSelector]
        for {
          component <- complex1
          simple    <- component.selector.components
          if isUnique(simple)
        } buf += simple
        buf
      }
      if (uniqueSelectors.isEmpty) break(false)

      complex2.exists { component =>
        component.selector.components.exists { simple =>
          isUnique(simple) && uniqueSelectors.contains(simple)
        }
      }
    }

  /** Like [complexIsSuperselector], but compares [complex1] and [complex2] as though they shared an implicit base [SimpleSelector].
    *
    * For example, `B` is not normally a superselector of `B A`, since it doesn't match elements that match `A`. However, it *is* a parent superselector, since `B X` is a superselector of `B A X`.
    */
  private def complexIsParentSuperselector(
    complex1: List[ComplexSelectorComponent],
    complex2: List[ComplexSelectorComponent]
  ): Boolean =
    boundary[Boolean] {
      if (complex1.length > complex2.length) break(false)

      // TODO(nweiz): There's got to be a way to do this without a bunch of extra
      // allocations...
      val bogusSpan = FileSpan.synthetic("")
      val base = new ComplexSelectorComponent(
        new CompoundSelector(List(new PlaceholderSelector("<temp>", bogusSpan)), bogusSpan),
        Nil,
        bogusSpan
      )
      complexIsSuperselector(complex1 :+ base, complex2 :+ base)
    }

  // — Superselector functions —

  /** Returns whether a [CompoundSelector] may contain only one simple selector of
    * the same type as [simple].
    */
  private def isUnique(simple: SimpleSelector): Boolean =
    simple.isInstanceOf[IDSelector] || (simple.isInstanceOf[PseudoSelector] && simple.asInstanceOf[PseudoSelector].isElement)

  /** Returns whether [combinator1] is a supercombinator of [combinator2].
    *
    * That is, whether `X combinator1 Y` is a superselector of `X combinator2 Y`.
    */
  private def isSupercombinator(
    combinator1: Nullable[CssValue[Combinator]],
    combinator2: Nullable[CssValue[Combinator]]
  ): Boolean = {
    // combinator1 == combinator2 (both empty, or both same value)
    if (combinator1.isEmpty && combinator2.isEmpty) return true
    if (combinator1.isDefined && combinator2.isDefined && combinator1.get == combinator2.get) return true
    // null (descendant) is a supercombinator of child
    if (combinator1.isEmpty && combinator2.isDefined && combinator2.get.value == Combinator.Child) return true
    // followingSibling is a supercombinator of nextSibling
    if (combinator1.isDefined && combinator1.get.value == Combinator.FollowingSibling &&
        combinator2.isDefined && combinator2.get.value == Combinator.NextSibling) return true
    false
  }

  /** Returns whether [parents] are valid intersitial components between one
    * complex superselector and another, given that the earlier complex
    * superselector had the combinator [previous].
    */
  private def compatibleWithPreviousCombinator(
    previous: Nullable[CssValue[Combinator]],
    parents:  Iterable[ComplexSelectorComponent]
  ): Boolean = {
    if (parents.isEmpty) return true
    if (previous.isEmpty) return true

    // The child and next sibling combinators require that the *immediate*
    // following component be a superselector.
    if (previous.get.value != Combinator.FollowingSibling) return false

    // The following sibling combinator does allow intermediate components, but
    // only if they're all siblings.
    parents.forall { component =>
      val first = Nullable.fromOption(component.combinators.headOption)
      first.isDefined && (first.get.value == Combinator.FollowingSibling ||
        first.get.value == Combinator.NextSibling)
    }
  }

  /** Returns whether [list1] is a superselector of [list2].
    *
    * That is, whether [list1] matches every element that [list2] matches, as well
    * as possibly additional elements.
    */
  def listIsSuperselector(
    list1: List[ComplexSelector],
    list2: List[ComplexSelector]
  ): Boolean =
    list2.forall { complex1 =>
      list1.exists(complex2 => complex2.isSuperselector(complex1))
    }

  /** Returns whether [complex1] is a superselector of [complex2].
    *
    * That is, whether [complex1] matches every element that [complex2] matches, as well
    * as possibly additional elements.
    */
  def complexIsSuperselector(
    complex1: List[ComplexSelectorComponent],
    complex2: List[ComplexSelectorComponent]
  ): Boolean =
    boundary[Boolean] {
      // Selectors with trailing operators are neither superselectors nor
      // subselectors.
      if (complex1.last.combinators.nonEmpty) break(false)
      if (complex2.last.combinators.nonEmpty) break(false)

      var i1 = 0
      var i2 = 0
      var previousCombinator: Nullable[CssValue[Combinator]] = Nullable.empty
      while (true) {
        val remaining1 = complex1.length - i1
        val remaining2 = complex2.length - i2
        if (remaining1 == 0 || remaining2 == 0) break(false)

        // More complex selectors are never superselectors of less complex ones.
        if (remaining1 > remaining2) break(false)

        val component1 = complex1(i1)
        if (component1.combinators.length > 1) break(false)
        if (remaining1 == 1) {
          if (complex2.exists(parent => parent.combinators.length > 1)) {
            break(false)
          } else {
            val parents: Nullable[List[ComplexSelectorComponent]] =
              if (component1.selector.hasComplicatedSuperselectorSemantics)
                Nullable(complex2.slice(i2, complex2.length - 1))
              else Nullable.empty
            break(component1.selector.isSuperselector(
              complex2.last.selector,
              parents
            ))
          }
        }

        // Find the first index [endOfSubselector] in [complex2] such that
        // `complex2.sublist(i2, endOfSubselector + 1)` is a subselector of
        // [component1.selector].
        var endOfSubselector = i2
        var found = false
        while (!found) {
          val component2 = complex2(endOfSubselector)
          if (component2.combinators.length > 1) break(false)
          val parents: Nullable[List[ComplexSelectorComponent]] =
            if (component1.selector.hasComplicatedSuperselectorSemantics)
              Nullable(complex2.slice(i2, endOfSubselector))
            else Nullable.empty
          if (component1.selector.isSuperselector(
            component2.selector,
            parents
          )) {
            found = true
          } else {
            endOfSubselector += 1
            if (endOfSubselector == complex2.length - 1) {
              // Stop before the superselector would encompass all of [complex2]
              // because we know [complex1] has more than one element, and consuming
              // all of [complex2] wouldn't leave anything for the rest of [complex1]
              // to match.
              break(false)
            }
          }
        }

        if (!compatibleWithPreviousCombinator(
          previousCombinator,
          complex2.slice(i2, endOfSubselector)
        )) {
          break(false)
        }

        val component2 = complex2(endOfSubselector)
        val combinator1: Nullable[CssValue[Combinator]] = Nullable.fromOption(component1.combinators.headOption)
        val combinator2: Nullable[CssValue[Combinator]] = Nullable.fromOption(component2.combinators.headOption)
        if (!isSupercombinator(combinator1, combinator2)) {
          break(false)
        }

        i1 += 1
        i2 = endOfSubselector + 1
        previousCombinator = combinator1

        if (complex1.length - i1 == 1) {
          if (combinator1.isDefined && combinator1.get.value == Combinator.FollowingSibling) {
            // The selector `.foo ~ .bar` is only a superselector of selectors that
            // *exclusively* contain subcombinators of `~`.
            if (!complex2.slice(i2, complex2.length - 1).forall { component =>
              isSupercombinator(
                combinator1,
                Nullable.fromOption(component.combinators.headOption)
              )
            }) {
              break(false)
            }
          } else if (combinator1.isDefined) {
            // `.foo > .bar` and `.foo + bar` aren't superselectors of any selectors
            // with more than one combinator.
            if (complex2.length - i2 > 1) break(false)
          }
        }
      }
      // This point is unreachable because the while(true) loop always breaks,
      // but the compiler needs a return value for the boundary.
      false
    }
}
