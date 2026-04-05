/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/number/unitless.dart
 * Original: Copyright (c) 2020 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: unitless.dart → UnitlessSassNumber.scala
 *   Convention: Dart sealed class → Scala final class
 *   Idiom: Dart super.protected() → Scala extends SassNumber(value, asSlash);
 *          overrides arithmetic for efficiency (no unit coercion needed)
 *   Audited: 2026-04-05
 */
package ssg
package sass
package value
package number

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.NumberUtil.*
import ssg.sass.value.{Value, SassBoolean, SassNumber}

import scala.language.implicitConversions

/** A specialized subclass of SassNumber for numbers that have no units. */
final class UnitlessSassNumber(
  value: Double,
  asSlash: Nullable[(SassNumber, SassNumber)] = Nullable.Null
) extends SassNumber(value, asSlash) {

  def numeratorUnits: List[String] = Nil

  def denominatorUnits: List[String] = Nil

  def hasUnits: Boolean = false
  def hasComplexUnits: Boolean = false

  protected[value] def withValue(value: Double): SassNumber = UnitlessSassNumber(value)

  def withSlash(numerator: SassNumber, denominator: SassNumber): SassNumber =
    UnitlessSassNumber(value, Nullable((numerator, denominator)))

  def hasUnit(unit: String): Boolean = false

  override def hasCompatibleUnits(other: SassNumber): Boolean = other.isInstanceOf[UnitlessSassNumber]

  def hasPossiblyCompatibleUnits(other: SassNumber): Boolean = other.isInstanceOf[UnitlessSassNumber]

  def compatibleWithUnit(unit: String): Boolean = true

  override def coerceToMatch(
    other: SassNumber,
    name: Nullable[String],
    otherName: Nullable[String]
  ): SassNumber =
    other.withValue(value)

  override def coerceValueToMatch(
    other: SassNumber,
    name: Nullable[String],
    otherName: Nullable[String]
  ): Double =
    value

  override def convertToMatch(
    other: SassNumber,
    name: Nullable[String],
    otherName: Nullable[String]
  ): SassNumber = {
    if (other.hasUnits) {
      // Call super to generate a consistent error message.
      super.convertToMatch(other, name, otherName)
    } else {
      this
    }
  }

  override def convertValueToMatch(
    other: SassNumber,
    name: Nullable[String],
    otherName: Nullable[String]
  ): Double = {
    if (other.hasUnits) {
      // Call super to generate a consistent error message.
      super.convertValueToMatch(other, name, otherName)
    } else {
      value
    }
  }

  override def coerce(
    newNumerators: List[String],
    newDenominators: List[String],
    name: Nullable[String]
  ): SassNumber = {
    SassNumber.withUnits(
      value,
      numeratorUnits = newNumerators,
      denominatorUnits = newDenominators
    )
  }

  override def coerceValue(
    newNumerators: List[String],
    newDenominators: List[String],
    name: Nullable[String]
  ): Double =
    value

  override def coerceValueToUnit(unit: String, name: Nullable[String]): Double = value

  override def greaterThan(other: Value): SassBoolean = other match {
    case n: SassNumber => SassBoolean(fuzzyGreaterThan(value, n.value))
    case _ => super.greaterThan(other)
  }

  override def greaterThanOrEquals(other: Value): SassBoolean = other match {
    case n: SassNumber => SassBoolean(fuzzyGreaterThanOrEquals(value, n.value))
    case _ => super.greaterThanOrEquals(other)
  }

  override def lessThan(other: Value): SassBoolean = other match {
    case n: SassNumber => SassBoolean(fuzzyLessThan(value, n.value))
    case _ => super.lessThan(other)
  }

  override def lessThanOrEquals(other: Value): SassBoolean = other match {
    case n: SassNumber => SassBoolean(fuzzyLessThanOrEquals(value, n.value))
    case _ => super.lessThanOrEquals(other)
  }

  override def modulo(other: Value): Value = other match {
    case n: SassNumber => n.withValue(moduloLikeSass(value, n.value))
    case _ => super.modulo(other)
  }

  override def plus(other: Value): Value = other match {
    case n: SassNumber => n.withValue(value + n.value)
    case _ => super.plus(other)
  }

  override def minus(other: Value): Value = other match {
    case n: SassNumber => n.withValue(value - n.value)
    case _ => super.minus(other)
  }

  override def times(other: Value): Value = other match {
    case n: SassNumber => n.withValue(value * n.value)
    case _ => super.times(other)
  }

  override def dividedBy(other: Value): Value = other match {
    case n: SassNumber =>
      if (n.hasUnits) {
        SassNumber.withUnits(
          value / n.value,
          numeratorUnits = n.denominatorUnits,
          denominatorUnits = n.numeratorUnits
        )
      } else {
        UnitlessSassNumber(value / n.value)
      }
    case _ => super.dividedBy(other)
  }

  override def unaryMinus(): Value = UnitlessSassNumber(-value)

  override def equals(other: Any): Boolean = other match {
    case that: UnitlessSassNumber => fuzzyEquals(value, that.value)
    case _ => false
  }

  override def hashCode(): Int = {
    if (hashCache.isEmpty) {
      hashCache = fuzzyHashCode(value)
    }
    hashCache.get
  }
}

object UnitlessSassNumber {
  def apply(value: Double): UnitlessSassNumber = new UnitlessSassNumber(value)

  def apply(value: Double, asSlash: Nullable[(SassNumber, SassNumber)]): UnitlessSassNumber =
    new UnitlessSassNumber(value, asSlash)
}
