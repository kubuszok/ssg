/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/number/single_unit.dart
 * Original: Copyright (c) 2020 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: single_unit.dart → SingleUnitSassNumber.scala
 *   Convention: Dart sealed class → Scala final class
 *   Idiom: Dart _unit private field → Scala private val unit;
 *          Dart ?. andThen pattern → Nullable map/getOrElse;
 *          _knownCompatibilities → SassNumber.knownCompatibilities
 *   Audited: 2026-04-05
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/number/single_unit.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package value
package number

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.Utils
import ssg.sass.util.NumberUtil.*
import ssg.sass.value.{ SassNumber, Value }

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

/** A specialized subclass of SassNumber for numbers that have exactly one numerator unit. */
final class SingleUnitSassNumber private (
  value:             Double,
  private val _unit: String,
  asSlash:           Nullable[(SassNumber, SassNumber)] = Nullable.Null
) extends SassNumber(value, asSlash) {

  def numeratorUnits: List[String] = List(_unit)

  def denominatorUnits: List[String] = Nil

  def hasUnits:        Boolean = true
  def hasComplexUnits: Boolean = false

  /** Returns the single unit of this number. Exposed for subclass access. */
  private[value] def unit: String = _unit

  protected[value] def withValue(value: Double): SassNumber = SingleUnitSassNumber(value, _unit)

  def withSlash(numerator: SassNumber, denominator: SassNumber): SassNumber =
    new SingleUnitSassNumber(value, _unit, Nullable((numerator, denominator)))

  def hasUnit(unit: String): Boolean = unit == _unit

  override def hasCompatibleUnits(other: SassNumber): Boolean = other match {
    case s: SingleUnitSassNumber => compatibleWithUnit(s._unit)
    case _ => false
  }

  def hasPossiblyCompatibleUnits(other: SassNumber): Boolean = other match {
    case s: SingleUnitSassNumber =>
      val knownCompats = SassNumber.knownCompatibilitiesByUnit.get(_unit.toLowerCase)
      knownCompats match {
        case Some(compatSet) =>
          val otherUnit = s._unit.toLowerCase
          compatSet.contains(otherUnit) ||
          !SassNumber.knownCompatibilitiesByUnit.contains(otherUnit)
        case scala.None => true
      }
    case _ => false
  }

  def compatibleWithUnit(unit: String): Boolean =
    SassNumber.conversionFactor(_unit, unit).isDefined

  override def coerceToMatch(
    other:     SassNumber,
    name:      Nullable[String],
    otherName: Nullable[String]
  ): SassNumber = {
    val result = other match {
      case s: SingleUnitSassNumber => coerceToUnit(s._unit)
      case _ => Nullable.Null
    }
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.coerceToMatch(other, name, otherName)
    }
  }

  override def coerceValueToMatch(
    other:     SassNumber,
    name:      Nullable[String],
    otherName: Nullable[String]
  ): Double = {
    val result = other match {
      case s: SingleUnitSassNumber => coerceValueToUnitInternal(s._unit)
      case _ => Nullable.Null
    }
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.coerceValueToMatch(other, name, otherName)
    }
  }

  override def convertValueToUnit(unit: String, name: Nullable[String]): Double = {
    val result = coerceValueToUnitInternal(unit)
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.convertValueToUnit(unit, name)
    }
  }

  override def convertToMatch(
    other:     SassNumber,
    name:      Nullable[String],
    otherName: Nullable[String]
  ): SassNumber = {
    val result = other match {
      case s: SingleUnitSassNumber => coerceToUnit(s._unit)
      case _ => Nullable.Null
    }
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.convertToMatch(other, name, otherName)
    }
  }

  override def convertValueToMatch(
    other:     SassNumber,
    name:      Nullable[String],
    otherName: Nullable[String]
  ): Double = {
    val result = other match {
      case s: SingleUnitSassNumber => coerceValueToUnitInternal(s._unit)
      case _ => Nullable.Null
    }
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.convertValueToMatch(other, name, otherName)
    }
  }

  override def coerce(
    newNumerators:   List[String],
    newDenominators: List[String],
    name:            Nullable[String]
  ): SassNumber = {
    val result =
      if (newNumerators.length == 1 && newDenominators.isEmpty) coerceToUnit(newNumerators.head)
      else Nullable.Null
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.coerce(newNumerators, newDenominators, name)
    }
  }

  override def coerceValue(
    newNumerators:   List[String],
    newDenominators: List[String],
    name:            Nullable[String]
  ): Double = {
    val result =
      if (newNumerators.length == 1 && newDenominators.isEmpty) coerceValueToUnitInternal(newNumerators.head)
      else Nullable.Null
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.coerceValue(newNumerators, newDenominators, name)
    }
  }

  override def coerceValueToUnit(unit: String, name: Nullable[String]): Double = {
    val result = coerceValueToUnitInternal(unit)
    if (result.isDefined) result.get
    else {
      // Call super to generate a consistent error message.
      super.coerceValueToUnit(unit, name)
    }
  }

  /** A shorthand for coerce with only one numerator unit, except that it returns Nullable.Null if coercion fails.
    */
  private def coerceToUnit(unit: String): Nullable[SassNumber] =
    if (_unit == unit) Nullable(this)
    else {
      val factor = SassNumber.conversionFactor(unit, _unit)
      factor.map(f => SingleUnitSassNumber(value * f, unit))
    }

  /** Like coerceValueToUnit, except that it returns Nullable.Null if coercion fails. */
  private def coerceValueToUnitInternal(unit: String): Nullable[Double] =
    SassNumber.conversionFactor(unit, _unit).map(factor => value * factor)

  override protected def multiplyUnits(
    value:             Double,
    otherNumerators:   List[String],
    otherDenominators: List[String]
  ): SassNumber = {
    var mutableValue             = value
    val newNumerators            = ArrayBuffer.from(otherNumerators)
    val mutableOtherDenominators = ArrayBuffer.from(otherDenominators)
    Utils.removeFirstWhere[String](
      mutableOtherDenominators,
      { denominator =>
        val factor = SassNumber.conversionFactor(denominator, _unit)
        if (factor.isEmpty) false
        else {
          mutableValue *= factor.get
          true
        }
      },
      orElse = () => newNumerators.prepend(_unit)
    )

    SassNumber.withUnits(
      mutableValue,
      numeratorUnits = newNumerators.toList,
      denominatorUnits = mutableOtherDenominators.toList
    )
  }

  override def unaryMinus(): Value = SingleUnitSassNumber(-value, _unit)

  override def equals(other: Any): Boolean = other match {
    case that: SingleUnitSassNumber =>
      val factor = SassNumber.conversionFactor(that._unit, _unit)
      factor.isDefined && fuzzyEquals(value * factor.get, that.value)
    case _ => false
  }

  override def hashCode(): Int = {
    if (hashCache.isEmpty) {
      hashCache = fuzzyHashCode(value * SassNumber.canonicalMultiplierForUnit(_unit))
    }
    hashCache.get
  }
}

object SingleUnitSassNumber {
  def apply(value: Double, unit: String): SingleUnitSassNumber =
    new SingleUnitSassNumber(value, unit)

  def apply(
    value:   Double,
    unit:    String,
    asSlash: Nullable[(SassNumber, SassNumber)]
  ): SingleUnitSassNumber =
    new SingleUnitSassNumber(value, unit, asSlash)
}
