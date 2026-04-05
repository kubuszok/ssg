/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/number.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: number.dart → NumberUtil.scala
 *   Convention: Top-level functions → object methods
 *   Idiom: Dart num → Double, SassNumber functions deferred to Phase 9
 */
package ssg
package sass
package util

import ssg.sass.Nullable
import ssg.sass.Nullable.*

/** Sass number precision: 10 decimal digits. */
object NumberUtil {

  /** The precision to which Sass numbers are compared. */
  val precision: Int = 10

  private val epsilon: Double = math.pow(10, -(precision + 1))
  private val inverseEpsilon: Double = math.pow(10, precision + 1)

  /** Returns whether number1 and number2 are equal up to the 11th decimal digit. */
  def fuzzyEquals(number1: Double, number2: Double): Boolean = {
    if (number1 == number2) true
    else {
      (number1 - number2).abs <= epsilon &&
        math.round(number1 * inverseEpsilon) == math.round(number2 * inverseEpsilon)
    }
  }

  /** Like fuzzyEquals, but allows Nullable values. Null values are only equal to each other. */
  def fuzzyEqualsNullable(number1: Nullable[Double], number2: Nullable[Double]): Boolean = {
    if (number1.isEmpty && number2.isEmpty) true
    else if (number1.isEmpty || number2.isEmpty) false
    else fuzzyEquals(number1.get, number2.get)
  }

  /** Returns a hash code for number that matches fuzzyEquals. */
  def fuzzyHashCode(number: Double): Int = {
    if (!number.isFinite) number.hashCode
    else math.round(number * inverseEpsilon).hashCode
  }

  /** Returns whether number1 < number2, and not fuzzyEquals. */
  def fuzzyLessThan(number1: Double, number2: Double): Boolean =
    number1 < number2 && !fuzzyEquals(number1, number2)

  /** Returns whether number1 <= number2, or fuzzyEquals. */
  def fuzzyLessThanOrEquals(number1: Double, number2: Double): Boolean =
    number1 < number2 || fuzzyEquals(number1, number2)

  /** Returns whether number1 > number2, and not fuzzyEquals. */
  def fuzzyGreaterThan(number1: Double, number2: Double): Boolean =
    number1 > number2 && !fuzzyEquals(number1, number2)

  /** Returns whether number1 >= number2, or fuzzyEquals. */
  def fuzzyGreaterThanOrEquals(number1: Double, number2: Double): Boolean =
    number1 > number2 || fuzzyEquals(number1, number2)

  /** Returns whether number is fuzzyEquals to an integer. */
  def fuzzyIsInt(number: Double): Boolean = {
    if (number.isInfinite || number.isNaN) false
    else fuzzyEquals(number, math.round(number).toDouble)
  }

  /** If number is an integer according to fuzzyIsInt, returns it as an Int. Otherwise, returns Nullable.Null. */
  def fuzzyAsInt(number: Double): Nullable[Int] = {
    if (number.isInfinite || number.isNaN) Nullable.Null
    else {
      val rounded = math.round(number)
      if (fuzzyEquals(number, rounded.toDouble)) Nullable(rounded.toInt)
      else Nullable.Null
    }
  }

  /** Rounds number to the nearest integer. Rounds up numbers that are fuzzyEquals to X.5. */
  def fuzzyRound(number: Double): Int = {
    if (number > 0) {
      if (fuzzyLessThan(number % 1, 0.5)) math.floor(number).toInt
      else math.ceil(number).toInt
    } else {
      if (fuzzyLessThanOrEquals(number % 1, 0.5)) math.floor(number).toInt
      else math.ceil(number).toInt
    }
  }

  /** Returns whether number is within min and max inclusive, using fuzzy equality. */
  def fuzzyInRange(number: Double, min: Double, max: Double): Boolean =
    fuzzyGreaterThanOrEquals(number, min) && fuzzyLessThanOrEquals(number, max)

  /** Returns number if it's within min and max, or Nullable.Null if it's not. Clamps to boundary if fuzzyEquals. */
  def fuzzyCheckRange(number: Double, min: Double, max: Double): Nullable[Double] = {
    if (fuzzyEquals(number, min)) Nullable(min)
    else if (fuzzyEquals(number, max)) Nullable(max)
    else if (number > min && number < max) Nullable(number)
    else Nullable.Null
  }

  /** Throws a RangeError if number isn't within min and max. Clamps to boundary if fuzzyEquals. */
  def fuzzyAssertRange(number: Double, min: Int, max: Int, name: Nullable[String] = Nullable.Null): Double = {
    val result = fuzzyCheckRange(number, min.toDouble, max.toDouble)
    if (result.isDefined) result.get
    else {
      val label = name.getOrElse("value")
      throw new IllegalArgumentException(s"$label: $number must be between $min and $max")
    }
  }

  /** Floored-division modulo (Ruby/Sass semantics, differs from Scala's %).
    *
    * In Dart, `%` always returns non-negative when divisor is positive.
    * Scala's `%` uses truncated division, so we must adjust for negative results.
    */
  def moduloLikeSass(num1: Double, num2: Double): Double = {
    if (num1.isInfinite) Double.NaN
    else if (num2.isInfinite) {
      if (signIncludingZero(num1) == math.signum(num2)) num1
      else Double.NaN
    } else if (num2 == 0) Double.NaN
    else {
      val result = num1 % num2
      if (result == 0) 0.0
      else if ((result > 0) != (num2 > 0)) result + num2
      else result
    }
  }

  /** Clamp preferring lower bound on NaN (unlike Scala's clamp which prefers upper). */
  def clampLikeCss(number: Double, lowerBound: Double, upperBound: Double): Double = {
    if (number.isNaN) lowerBound
    else math.max(lowerBound, math.min(number, upperBound))
  }

  /** Returns the sign of a double, treating -0.0 as negative. */
  def signIncludingZero(value: Double): Double = {
    if (value == 0.0) {
      if ((1.0 / value).isNegInfinity) -1.0 else 1.0
    } else {
      math.signum(value)
    }
  }
}
