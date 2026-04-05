/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/supports_condition.dart,
 *              lib/src/ast/sass/supports_condition/anything.dart,
 *              lib/src/ast/sass/supports_condition/declaration.dart,
 *              lib/src/ast/sass/supports_condition/function.dart,
 *              lib/src/ast/sass/supports_condition/interpolation.dart,
 *              lib/src/ast/sass/supports_condition/negation.dart,
 *              lib/src/ast/sass/supports_condition/operation.dart
 * Original: Copyright (c) 2016, 2020 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: supports_condition.dart + 6 subtypes -> SupportsCondition.scala
 *   Convention: Dart abstract interface class -> Scala trait;
 *               Dart final class -> Scala final case class
 *   Idiom: toInterpolation and withSpan deferred (need InterpolationBuffer)
 */
package ssg
package sass
package ast
package sass

import ssg.sass.util.FileSpan

/** An abstract class for defining the condition a `@supports` rule selects. */
trait SupportsCondition extends SassNode {

  /** Converts this condition into an interpolation that produces the same value.
    * Deferred to Phase 5 when InterpolationBuffer is available.
    */
  def toInterpolation(): Interpolation

  /** Returns a copy of this condition with [span] as its span. */
  def withSpan(span: FileSpan): SupportsCondition
}

// ---------------------------------------------------------------------------
// SupportsAnything — forwards-compatible `<general-enclosed>` production
// ---------------------------------------------------------------------------

/** A supports condition that represents the forwards-compatible
  * `<general-enclosed>` production.
  *
  * @param contents the contents of the condition
  * @param span     the source span
  */
final case class SupportsAnything(
  contents: Interpolation,
  span: FileSpan
) extends SupportsCondition {

  def toInterpolation(): Interpolation = {
    // Simplified: full version uses InterpolationBuffer
    Interpolation.plain(toString, span)
  }

  def withSpan(newSpan: FileSpan): SupportsAnything =
    SupportsAnything(contents, newSpan)

  override def toString: String = s"($contents)"
}

// ---------------------------------------------------------------------------
// SupportsDeclaration — selects for supported declarations
// ---------------------------------------------------------------------------

/** A condition that selects for browsers where a given declaration is supported.
  *
  * @param name  the name of the declaration being tested
  * @param value the value of the declaration being tested
  * @param span  the source span
  */
final case class SupportsDeclaration(
  name: Expression,
  value: Expression,
  span: FileSpan
) extends SupportsCondition {

  /** Returns whether this is a CSS Custom Property declaration. */
  def isCustomProperty: Boolean = name match {
    case se: StringExpression if !se.hasQuotes =>
      se.text.initialPlain.startsWith("--")
    case _ => false
  }

  def toInterpolation(): Interpolation = {
    // Simplified: full version uses InterpolationBuffer
    Interpolation.plain(toString, span)
  }

  def withSpan(newSpan: FileSpan): SupportsDeclaration =
    SupportsDeclaration(name, value, newSpan)

  override def toString: String = s"($name: $value)"
}

// ---------------------------------------------------------------------------
// SupportsFunction — function-syntax condition
// ---------------------------------------------------------------------------

/** A function-syntax condition.
  *
  * @param name      the name of the function
  * @param arguments the arguments to the function
  * @param span      the source span
  */
final case class SupportsFunction(
  name: Interpolation,
  arguments: Interpolation,
  span: FileSpan
) extends SupportsCondition {

  def toInterpolation(): Interpolation = {
    // Simplified: full version uses InterpolationBuffer
    Interpolation.plain(toString, span)
  }

  def withSpan(newSpan: FileSpan): SupportsFunction =
    SupportsFunction(name, arguments, newSpan)

  override def toString: String = s"$name($arguments)"
}

// ---------------------------------------------------------------------------
// SupportsInterpolation — interpolated condition
// ---------------------------------------------------------------------------

/** An interpolated condition.
  *
  * @param expression the expression in the interpolation
  * @param span       the source span
  */
final case class SupportsInterpolation(
  expression: Expression,
  span: FileSpan
) extends SupportsCondition {

  def toInterpolation(): Interpolation =
    new Interpolation(List(expression), List(ssg.sass.Nullable(span)), span)

  def withSpan(newSpan: FileSpan): SupportsInterpolation =
    SupportsInterpolation(expression, newSpan)

  override def toString: String = s"#{$expression}"
}

// ---------------------------------------------------------------------------
// SupportsNegation — negated condition
// ---------------------------------------------------------------------------

/** A negated condition.
  *
  * @param condition the condition that's been negated
  * @param span      the source span
  */
final case class SupportsNegation(
  condition: SupportsCondition,
  span: FileSpan
) extends SupportsCondition {

  def toInterpolation(): Interpolation = {
    // Simplified: full version uses InterpolationBuffer
    Interpolation.plain(toString, span)
  }

  def withSpan(newSpan: FileSpan): SupportsNegation =
    SupportsNegation(condition, newSpan)

  override def toString: String = condition match {
    case _: SupportsNegation  => s"not ($condition)"
    case _: SupportsOperation => s"not ($condition)"
    case _                    => s"not $condition"
  }
}

// ---------------------------------------------------------------------------
// SupportsOperation — relationship between two conditions
// ---------------------------------------------------------------------------

/** An operation defining the relationship between two conditions.
  *
  * @param left     the left-hand operand
  * @param right    the right-hand operand
  * @param operator the operator
  * @param span     the source span
  */
final case class SupportsOperation(
  left: SupportsCondition,
  right: SupportsCondition,
  operator: BooleanOperator,
  span: FileSpan
) extends SupportsCondition {

  def toInterpolation(): Interpolation = {
    // Simplified: full version uses InterpolationBuffer
    Interpolation.plain(toString, span)
  }

  def withSpan(newSpan: FileSpan): SupportsOperation =
    SupportsOperation(left, right, operator, newSpan)

  override def toString: String =
    s"${_parenthesize(left)} $operator ${_parenthesize(right)}"

  private def _parenthesize(cond: SupportsCondition): String = cond match {
    case _: SupportsNegation => s"($cond)"
    case op: SupportsOperation if op.operator == operator => s"($cond)"
    case _ => cond.toString
  }
}
