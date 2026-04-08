/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector/complex.dart,
 *              lib/src/ast/selector/complex_component.dart
 * Original: Copyright (c) 2016, 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: complex.dart, complex_component.dart -> ComplexSelector.scala (merged)
 *   Convention: Dart final class -> Scala final class
 *   Idiom: CssValue[Combinator] for combinators with spans;
 *          List.unmodifiable -> immutable List;
 *          isSuperselector delegates to extend functions (stub for now)
 */
package ssg
package sass
package ast
package selector

import ssg.sass.Nullable
import ssg.sass.Utils
import ssg.sass.ast.css.CssValue
import ssg.sass.util.FileSpan

import scala.language.implicitConversions

/** A complex selector.
  *
  * A complex selector is composed of [[CompoundSelector]]s separated by [[Combinator]]s. It selects elements based on their parent selectors.
  */
final class ComplexSelector(
  val leadingCombinators: List[CssValue[Combinator]],
  val components:         List[ComplexSelectorComponent],
  span:                   FileSpan,
  val lineBreak:          Boolean = false
) extends Selector(span) {

  require(
    leadingCombinators.nonEmpty || components.nonEmpty,
    "leadingCombinators and components may not both be empty."
  )

  /** This selector's specificity.
    *
    * Specificity is represented in base 1000. The spec says this should be "sufficiently high"; it's extremely unlikely that any single selector sequence will contain 1000 simple selectors.
    */
  lazy val specificity: Int =
    components.foldLeft(0)((sum, component) => sum + component.selector.specificity)

  /** If this complex selector is composed of a single compound selector with no combinators, returns it. Otherwise, returns `Nullable.Null`.
    */
  def singleCompound: Nullable[CompoundSelector] =
    if (leadingCombinators.nonEmpty) Nullable.Null
    else
      components match {
        case List(component) if component.combinators.isEmpty =>
          Nullable(component.selector)
        case _ => Nullable.Null
      }

  override def accept[T](visitor: SelectorVisitor[T]): T =
    visitor.visitComplexSelector(this)

  /** Whether this is a superselector of `other`.
    *
    * That is, whether this matches every element that `other` matches, as well as possibly matching more.
    *
    * Note: The full implementation delegates to `complexIsSuperselector` in the extend functions module, which will be ported separately.
    */
  def isSuperselector(other: ComplexSelector): Boolean =
    leadingCombinators.isEmpty &&
      other.leadingCombinators.isEmpty &&
      complexIsSuperselector(components, other.components)

  /** Stub for complex superselector logic.
    *
    * The real implementation will be in the extend functions module. This provides a basic approximation.
    */
  private def complexIsSuperselector(
    superComponents: List[ComplexSelectorComponent],
    subComponents:   List[ComplexSelectorComponent]
  ): Boolean =
    // Basic stub: delegates to compound-level comparison
    if (superComponents.isEmpty) true
    else if (subComponents.isEmpty) false
    else {
      superComponents.forall { superComp =>
        subComponents.exists { subComp =>
          superComp.selector.isSuperselector(subComp.selector)
        }
      }
    }

  /** Returns a copy of `this` with `combinators` added to the end of the final component in [[components]].
    *
    * If `forceLineBreak` is `true`, this will mark the new complex selector as having a line break.
    */
  def withAdditionalCombinators(
    combinators:    List[CssValue[Combinator]],
    forceLineBreak: Boolean = false
  ): ComplexSelector =
    if (combinators.isEmpty) this
    else
      (components: @unchecked) match {
        case init :+ last =>
          ComplexSelector(
            leadingCombinators,
            init :+ last.withAdditionalCombinators(combinators),
            span,
            lineBreak = lineBreak || forceLineBreak
          )
        case Nil =>
          ComplexSelector(
            leadingCombinators ++ combinators,
            Nil,
            span,
            lineBreak = lineBreak || forceLineBreak
          )
      }

  /** Returns a copy of `this` with an additional `component` added to the end.
    *
    * If `forceLineBreak` is `true`, this will mark the new complex selector as having a line break.
    *
    * The `newSpan` is used for the new selector.
    */
  def withAdditionalComponent(
    component:      ComplexSelectorComponent,
    newSpan:        FileSpan,
    forceLineBreak: Boolean = false
  ): ComplexSelector =
    ComplexSelector(
      leadingCombinators,
      components :+ component,
      newSpan,
      lineBreak = lineBreak || forceLineBreak
    )

  /** Returns a copy of `this` with `child`'s combinators added to the end.
    *
    * If `child` has [[leadingCombinators]], they're appended to `this`'s last combinator. This does _not_ resolve parent selectors.
    *
    * The `newSpan` is used for the new selector.
    *
    * If `forceLineBreak` is `true`, this will mark the new complex selector as having a line break.
    */
  def concatenate(
    child:          ComplexSelector,
    newSpan:        FileSpan,
    forceLineBreak: Boolean = false
  ): ComplexSelector =
    if (child.leadingCombinators.isEmpty) {
      ComplexSelector(
        leadingCombinators,
        components ++ child.components,
        newSpan,
        lineBreak = lineBreak || child.lineBreak || forceLineBreak
      )
    } else {
      (components: @unchecked) match {
        case init :+ last =>
          ComplexSelector(
            leadingCombinators,
            init ++ (last.withAdditionalCombinators(child.leadingCombinators) :: child.components),
            newSpan,
            lineBreak = lineBreak || child.lineBreak || forceLineBreak
          )
        case Nil =>
          ComplexSelector(
            leadingCombinators ++ child.leadingCombinators,
            child.components,
            newSpan,
            lineBreak = lineBreak || child.lineBreak || forceLineBreak
          )
      }
    }

  override def hashCode(): Int =
    Utils.iterableHash(leadingCombinators) ^ Utils.iterableHash(components)

  override def equals(other: Any): Boolean = other match {
    case that: ComplexSelector =>
      Utils.iterableEquals(leadingCombinators, that.leadingCombinators) &&
      Utils.iterableEquals(components, that.components)
    case _ => false
  }
}

/** A component of a [[ComplexSelector]].
  *
  * This is a [[CompoundSelector]] with zero or more trailing [[Combinator]]s.
  */
final class ComplexSelectorComponent(
  val selector:    CompoundSelector,
  val combinators: List[CssValue[Combinator]],
  val span:        FileSpan
) {

  /** Returns a copy of `this` with `additionalCombinators` added to the end of [[combinators]].
    */
  def withAdditionalCombinators(
    additionalCombinators: List[CssValue[Combinator]]
  ): ComplexSelectorComponent =
    if (additionalCombinators.isEmpty) this
    else ComplexSelectorComponent(selector, combinators ++ additionalCombinators, span)

  override def hashCode(): Int =
    selector.hashCode() ^ Utils.iterableHash(combinators)

  override def equals(other: Any): Boolean = other match {
    case that: ComplexSelectorComponent =>
      selector == that.selector &&
      Utils.iterableEquals(combinators, that.combinators)
    case _ => false
  }

  override def toString: String =
    selector.toString + combinators.map(c => s" ${c.value}").mkString
}
