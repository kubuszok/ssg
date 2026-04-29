/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/number.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: number.dart → SassNumber.scala
 *   Convention: Dart abstract class → Scala abstract class
 *   Idiom: Dart num → Double; Dart (SassNumber, SassNumber)? → Nullable[(SassNumber, SassNumber)];
 *          Dart List<String> → List[String]; Dart null → Nullable.Null;
 *          conversionFactor and constants in companion object
 *   Audited: 2026-04-05
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/number.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package value

import ssg.sass.{ Nullable, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.util.NumberUtil.*
import ssg.sass.value.number.{ ComplexSassNumber, SingleUnitSassNumber, UnitlessSassNumber }
import ssg.sass.visitor.ValueVisitor

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A SassScript number.
  *
  * Numbers can have units. Although there's no literal syntax for it, numbers support scientific-style numerator and denominator units (for example, `miles/hour`). These are expected to be resolved
  * before being emitted to CSS.
  */
abstract class SassNumber protected (
  private val _value: Double,
  val asSlash:        Nullable[(SassNumber, SassNumber)]
) extends Value {

  /** The value of this number. */
  def value: Double = _value

  /** The cached hash code for this number, if it's been computed. */
  protected var hashCache: Nullable[Int] = Nullable.Null

  /** This number's numerator units. */
  def numeratorUnits: List[String]

  /** This number's denominator units. */
  def denominatorUnits: List[String]

  /** Whether this number has any units. */
  def hasUnits: Boolean

  /** Whether this number has more than one numerator unit, or any denominator units. */
  def hasComplexUnits: Boolean

  /** Whether this is an integer, according to fuzzy equality. */
  def isInt: Boolean = fuzzyIsInt(value)

  /** If this is an integer according to isInt, returns value as an Int. Otherwise returns Nullable.Null. */
  def asInt: Nullable[Int] = fuzzyAsInt(value)

  /** Returns a human readable string representation of this number's units. */
  def unitString: String =
    if (hasUnits) SassNumber.unitString(numeratorUnits, denominatorUnits) else ""

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitNumber(this)

  /** Returns a number with the same units as this but with the given value. */
  protected[value] def withValue(value: Double): SassNumber

  /** Returns a copy of this without asSlash set. */
  override def withoutSlash: SassNumber = if (asSlash.isEmpty) this else withValue(value)

  /** Returns a copy of this with asSlash set to a pair containing numerator and denominator. */
  def withSlash(numerator: SassNumber, denominator: SassNumber): SassNumber

  override def assertNumber(name: Nullable[String]): SassNumber = this

  /** Returns value as an Int, if it's an integer value according to isInt. Throws a SassScriptException if value isn't an integer.
    */
  override def assertInt(name: Nullable[String]): Int = {
    val integer = fuzzyAsInt(value)
    if (integer.isDefined) integer.get
    else throw SassScriptException(s"$this is not an int.", name.toOption)
  }

  /** If value is between min and max, returns it. Otherwise throws a SassScriptException. */
  def valueInRange(min: Double, max: Double, name: Nullable[String] = Nullable.Null): Double = {
    val result = fuzzyCheckRange(value, min, max)
    if (result.isDefined) result.get
    else
      throw SassScriptException(
        s"Expected $this to be within $min$unitString and $max$unitString.",
        name.toOption
      )
  }

  /** Like valueInRange, but with an explicit unit for the expected upper and lower bounds. */
  def valueInRangeWithUnit(min: Double, max: Double, name: String, unit: String): Double = {
    val result = fuzzyCheckRange(value, min, max)
    if (result.isDefined) result.get
    else
      throw SassScriptException(
        s"Expected $this to be within $min$unit and $max$unit.",
        Some(name)
      )
  }

  /** Returns whether this has unit as its only unit (and as a numerator). */
  def hasUnit(unit: String): Boolean

  /** Returns whether this has units that are compatible with other. */
  def hasCompatibleUnits(other: SassNumber): Boolean =
    if (numeratorUnits.length != other.numeratorUnits.length) false
    else if (denominatorUnits.length != other.denominatorUnits.length) false
    else isComparableTo(other)

  /** Returns whether this has units that are possibly-compatible with other, as defined by the Sass spec. */
  def hasPossiblyCompatibleUnits(other: SassNumber): Boolean

  /** Returns whether this can be coerced to the given unit. */
  def compatibleWithUnit(unit: String): Boolean

  /** Throws a SassScriptException unless this has unit as its only unit (and as a numerator). */
  def assertUnit(unit: String, name: Nullable[String] = Nullable.Null): Unit =
    if (!hasUnit(unit)) {
      throw SassScriptException(s"""Expected $this to have unit "$unit".""", name.toOption)
    }

  /** Throws a SassScriptException unless this has no units. */
  def assertNoUnits(name: Nullable[String] = Nullable.Null): Unit =
    if (hasUnits) {
      throw SassScriptException(s"Expected $this to have no units.", name.toOption)
    }

  /** Returns a copy of this number, converted to the units represented by newNumerators and newDenominators. Throws if units aren't compatible, or if either number is unitless but the other is not.
    */
  def convert(
    newNumerators:   List[String],
    newDenominators: List[String],
    name:            Nullable[String] = Nullable.Null
  ): SassNumber =
    SassNumber.withUnits(
      convertValue(newNumerators, newDenominators, name),
      numeratorUnits = newNumerators,
      denominatorUnits = newDenominators
    )

  /** Returns value, converted to the units represented by newNumerators and newDenominators. Throws if units aren't compatible or this number is unitless.
    */
  def convertValue(
    newNumerators:   List[String],
    newDenominators: List[String],
    name:            Nullable[String] = Nullable.Null
  ): Double =
    coerceOrConvertValue(
      newNumerators,
      newDenominators,
      coerceUnitless = false,
      name = name
    )

  /** A shorthand for convertValue with only one numerator unit. */
  def convertValueToUnit(unit: String, name: Nullable[String] = Nullable.Null): Double =
    convertValue(List(unit), Nil, name)

  /** Returns a copy of this number, converted to the same units as other. Throws if units aren't compatible, or if either number is unitless but the other is not.
    */
  def convertToMatch(
    other:     SassNumber,
    name:      Nullable[String] = Nullable.Null,
    otherName: Nullable[String] = Nullable.Null
  ): SassNumber =
    SassNumber.withUnits(
      convertValueToMatch(other, name, otherName),
      numeratorUnits = other.numeratorUnits,
      denominatorUnits = other.denominatorUnits
    )

  /** Returns value, converted to the same units as other. Throws if units aren't compatible, or if either number is unitless but the other is not.
    */
  def convertValueToMatch(
    other:     SassNumber,
    name:      Nullable[String] = Nullable.Null,
    otherName: Nullable[String] = Nullable.Null
  ): Double =
    coerceOrConvertValue(
      other.numeratorUnits,
      other.denominatorUnits,
      coerceUnitless = false,
      name = name,
      other = other,
      otherName = otherName
    )

  /** Returns a copy of this number, coerced to the units represented by newNumerators and newDenominators. Unlike convert, treats unitless numbers as convertible to/from all units without changing
    * value.
    */
  def coerce(
    newNumerators:   List[String],
    newDenominators: List[String],
    name:            Nullable[String] = Nullable.Null
  ): SassNumber =
    SassNumber.withUnits(
      coerceValue(newNumerators, newDenominators, name),
      numeratorUnits = newNumerators,
      denominatorUnits = newDenominators
    )

  /** Returns value, coerced to the units represented by newNumerators and newDenominators. Unlike convertValue, treats unitless numbers as convertible to/from all units.
    */
  def coerceValue(
    newNumerators:   List[String],
    newDenominators: List[String],
    name:            Nullable[String] = Nullable.Null
  ): Double =
    coerceOrConvertValue(
      newNumerators,
      newDenominators,
      coerceUnitless = true,
      name = name
    )

  /** A shorthand for coerceValue with only one numerator unit. */
  def coerceValueToUnit(unit: String, name: Nullable[String] = Nullable.Null): Double =
    coerceValue(List(unit), Nil, name)

  /** Returns a copy of this number, coerced to the same units as other. Unlike convertToMatch, treats unitless numbers as convertible to/from all units.
    */
  def coerceToMatch(
    other:     SassNumber,
    name:      Nullable[String] = Nullable.Null,
    otherName: Nullable[String] = Nullable.Null
  ): SassNumber =
    SassNumber.withUnits(
      coerceValueToMatch(other, name, otherName),
      numeratorUnits = other.numeratorUnits,
      denominatorUnits = other.denominatorUnits
    )

  /** Returns value, coerced to the same units as other. Unlike convertValueToMatch, treats unitless numbers as convertible to/from all units.
    */
  def coerceValueToMatch(
    other:     SassNumber,
    name:      Nullable[String] = Nullable.Null,
    otherName: Nullable[String] = Nullable.Null
  ): Double =
    coerceOrConvertValue(
      other.numeratorUnits,
      other.denominatorUnits,
      coerceUnitless = true,
      name = name,
      other = other,
      otherName = otherName
    )

  /** Converts value to newNumerators and newDenominators.
    *
    * If coerceUnitless is true, considers unitless numbers convertible to and from any unit. Otherwise, throws a SassScriptException for such a conversion.
    */
  protected def coerceOrConvertValue(
    newNumerators:   List[String],
    newDenominators: List[String],
    coerceUnitless:  Boolean,
    name:            Nullable[String] = Nullable.Null,
    other:           Nullable[SassNumber] = Nullable.Null,
    otherName:       Nullable[String] = Nullable.Null
  ): Double = boundary[Double] {
    if (
      Utils.listEquals(Nullable(numeratorUnits), Nullable(newNumerators)) &&
      Utils.listEquals(Nullable(denominatorUnits), Nullable(newDenominators))
    ) {
      break(this.value)
    }

    val otherHasUnits = newNumerators.nonEmpty || newDenominators.nonEmpty
    if (coerceUnitless && (!hasUnits || !otherHasUnits)) break(this.value)

    def compatibilityException(): SassScriptException =
      if (other.isDefined) {
        val message = new StringBuilder()
        message.append(s"$this and")
        if (otherName.isDefined) message.append(s" $$${otherName.get}:")
        message.append(s" ${other.get} have incompatible units")
        if (!hasUnits || !otherHasUnits) {
          message.append(" (one has units and the other doesn't)")
        }
        SassScriptException(s"$message.", name.toOption)
      } else if (!otherHasUnits) {
        SassScriptException(s"Expected $this to have no units.", name.toOption)
      } else {
        if (newNumerators.length == 1 && newDenominators.isEmpty) {
          val unitType = SassNumber.typesByUnit.get(newNumerators.head)
          if (unitType.isDefined) {
            // If we're converting to a unit of a named type, use that type name
            // and make it clear exactly which units are convertible.
            break(
              throw SassScriptException(
                s"Expected $this to have ${Utils.a(unitType.get)} unit " +
                  s"(${SassNumber.unitsByType(unitType.get).mkString(", ")}).",
                name.toOption
              )
            )
          }
        }

        val unitWord = Utils.pluralize(
          "unit",
          newNumerators.length + newDenominators.length
        )
        SassScriptException(
          s"Expected $this to have $unitWord " +
            s"${SassNumber.unitString(newNumerators, newDenominators)}.",
          name.toOption
        )
      }

    var result        = this.value
    val oldNumerators = ArrayBuffer.from(numeratorUnits)
    for (newNumerator <- newNumerators)
      Utils.removeFirstWhere[String](
        oldNumerators,
        { oldNumerator =>
          val factor = SassNumber.conversionFactor(newNumerator, oldNumerator)
          if (factor.isEmpty) false
          else {
            result *= factor.get
            true
          }
        },
        orElse = () => throw compatibilityException()
      )

    val oldDenominators = ArrayBuffer.from(denominatorUnits)
    for (newDenominator <- newDenominators)
      Utils.removeFirstWhere[String](
        oldDenominators,
        { oldDenominator =>
          val factor = SassNumber.conversionFactor(newDenominator, oldDenominator)
          if (factor.isEmpty) false
          else {
            result /= factor.get
            true
          }
        },
        orElse = () => throw compatibilityException()
      )

    if (oldNumerators.nonEmpty || oldDenominators.nonEmpty) {
      throw compatibilityException()
    }

    result
  }

  /** Returns whether this number can be compared to other. Two numbers can be compared if they have compatible units, or if either number has no units.
    */
  def isComparableTo(other: SassNumber): Boolean =
    if (!hasUnits || !other.hasUnits) true
    else {
      try {
        greaterThan(other)
        true
      } catch {
        case _: SassScriptException => false
      }
    }

  override def greaterThan(other: Value): SassBoolean = other match {
    case n: SassNumber =>
      SassBoolean(coerceUnits(n, fuzzyGreaterThan))
    case _ =>
      throw SassScriptException(s"""Undefined operation "$this > $other".""")
  }

  override def greaterThanOrEquals(other: Value): SassBoolean = other match {
    case n: SassNumber =>
      SassBoolean(coerceUnits(n, fuzzyGreaterThanOrEquals))
    case _ =>
      throw SassScriptException(s"""Undefined operation "$this >= $other".""")
  }

  override def lessThan(other: Value): SassBoolean = other match {
    case n: SassNumber =>
      SassBoolean(coerceUnits(n, fuzzyLessThan))
    case _ =>
      throw SassScriptException(s"""Undefined operation "$this < $other".""")
  }

  override def lessThanOrEquals(other: Value): SassBoolean = other match {
    case n: SassNumber =>
      SassBoolean(coerceUnits(n, fuzzyLessThanOrEquals))
    case _ =>
      throw SassScriptException(s"""Undefined operation "$this <= $other".""")
  }

  override def modulo(other: Value): Value = other match {
    case n: SassNumber =>
      withValue(coerceUnits(n, moduloLikeSass))
    case _ =>
      throw SassScriptException(s"""Undefined operation "$this % $other".""")
  }

  override def plus(other: Value): Value = other match {
    case n: SassNumber =>
      withValue(coerceUnits(n, (num1, num2) => num1 + num2))
    case _: SassColor =>
      throw SassScriptException(s"""Undefined operation "$this + $other".""")
    case _ =>
      super.plus(other)
  }

  override def minus(other: Value): Value = other match {
    case n: SassNumber =>
      withValue(coerceUnits(n, (num1, num2) => num1 - num2))
    case _: SassColor =>
      throw SassScriptException(s"""Undefined operation "$this - $other".""")
    case _ =>
      super.minus(other)
  }

  override def times(other: Value): Value = other match {
    case n: SassNumber =>
      if (!n.hasUnits) withValue(value * n.value)
      else multiplyUnits(value * n.value, n.numeratorUnits, n.denominatorUnits)
    case _ =>
      throw SassScriptException(s"""Undefined operation "$this * $other".""")
  }

  override def dividedBy(other: Value): Value = other match {
    case n: SassNumber =>
      if (!n.hasUnits) withValue(value / n.value)
      else multiplyUnits(value / n.value, n.denominatorUnits, n.numeratorUnits)
    case _ =>
      super.dividedBy(other)
  }

  override def unaryPlus(): Value = this

  /** Converts other's value to be compatible with this number's, and calls operation with the resulting numbers. */
  protected def coerceUnits[T](other: SassNumber, operation: (Double, Double) => T): T =
    try
      operation(value, other.coerceValueToMatch(this))
    catch {
      case e: SassScriptException =>
        // If the conversion fails, re-run it in the other direction. This will
        // generate an error message that prints `this` before other, which is
        // more readable.
        coerceValueToMatch(other)
        throw e // This should be unreachable.
    }

  /** Returns a new number that's equivalent to `value this.numeratorUnits/this.denominatorUnits * 1 otherNumerators/otherDenominators`. */
  protected def multiplyUnits(
    value:             Double,
    otherNumerators:   List[String],
    otherDenominators: List[String]
  ): SassNumber = boundary[SassNumber] {
    // Short-circuit without allocating any new unit lists if possible.
    (numeratorUnits, denominatorUnits, otherNumerators, otherDenominators) match {
      case (nums, denoms, Nil, Nil) =>
        break(SassNumber.withUnits(value, numeratorUnits = nums, denominatorUnits = denoms))
      case (Nil, Nil, nums, denoms) =>
        break(SassNumber.withUnits(value, numeratorUnits = nums, denominatorUnits = denoms))
      case (Nil, denoms, nums, Nil) if !SassNumber.areAnyConvertible(nums, denoms) =>
        break(SassNumber.withUnits(value, numeratorUnits = nums, denominatorUnits = denoms))
      case (nums, Nil, Nil, denoms) if !SassNumber.areAnyConvertible(nums, denoms) =>
        break(SassNumber.withUnits(value, numeratorUnits = nums, denominatorUnits = denoms))
      case _ => // fall through
    }

    var mutableValue             = value
    val newNumerators            = ArrayBuffer.empty[String]
    val mutableOtherDenominators = ArrayBuffer.from(otherDenominators)
    for (numerator <- numeratorUnits)
      Utils.removeFirstWhere[String](
        mutableOtherDenominators,
        { denominator =>
          val factor = SassNumber.conversionFactor(numerator, denominator)
          if (factor.isEmpty) false
          else {
            mutableValue /= factor.get
            true
          }
        },
        orElse = () => newNumerators += numerator
      )

    val mutableDenominatorUnits = ArrayBuffer.from(denominatorUnits)
    for (numerator <- otherNumerators)
      Utils.removeFirstWhere[String](
        mutableDenominatorUnits,
        { denominator =>
          val factor = SassNumber.conversionFactor(numerator, denominator)
          if (factor.isEmpty) false
          else {
            mutableValue /= factor.get
            true
          }
        },
        orElse = () => newNumerators += numerator
      )

    mutableDenominatorUnits ++= mutableOtherDenominators

    SassNumber.withUnits(
      mutableValue,
      numeratorUnits = newNumerators.toList,
      denominatorUnits = mutableDenominatorUnits.toList
    )
  }

  override def equals(other: Any): Boolean = other match {
    case that: SassNumber =>
      if (
        numeratorUnits.length != that.numeratorUnits.length ||
        denominatorUnits.length != that.denominatorUnits.length
      ) {
        false
      } else if (!hasUnits) {
        fuzzyEquals(value, that.value)
      } else {
        Utils.listEquals(
          Nullable(SassNumber.canonicalizeUnitList(numeratorUnits)),
          Nullable(SassNumber.canonicalizeUnitList(that.numeratorUnits))
        ) &&
        Utils.listEquals(
          Nullable(SassNumber.canonicalizeUnitList(denominatorUnits)),
          Nullable(SassNumber.canonicalizeUnitList(that.denominatorUnits))
        ) &&
        fuzzyEquals(
          value *
            SassNumber.canonicalMultiplier(numeratorUnits) /
            SassNumber.canonicalMultiplier(denominatorUnits),
          that.value *
            SassNumber.canonicalMultiplier(that.numeratorUnits) /
            SassNumber.canonicalMultiplier(that.denominatorUnits)
        )
      }
    case _ => false
  }

  override def hashCode(): Int = {
    if (hashCache.isEmpty) {
      hashCache = fuzzyHashCode(
        value *
          SassNumber.canonicalMultiplier(numeratorUnits) /
          SassNumber.canonicalMultiplier(denominatorUnits)
      )
    }
    hashCache.get
  }

  /** Returns a suggested Sass snippet for converting a variable named name (without %) containing this number into a number with the same value and the given unit.
    */
  def unitSuggestion(name: String, unit: Nullable[String] = Nullable.Null): String = {
    val result =
      s"$$$name" +
        denominatorUnits.map(u => s" * 1$u").mkString +
        numeratorUnits.map(u => s" / 1$u").mkString +
        (if (unit.isEmpty) "" else s" * 1${unit.get}")
    if (numeratorUnits.isEmpty) result else s"calc($result)"
  }

  // dart-sass: toCssString delegates to the full serializer which handles
  // asSlash, non-finite values, and complex units.  Value.toCssString calls
  // serializeValue(this) which dispatches to formatSassNumber, faithfully
  // porting dart-sass visitNumber (serialize.dart:1108-1112).
  // No override needed — the base Value.toCssString correctly delegates.

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append(SassNumber.formatNumber(value))
    sb.append(unitString)
    sb.toString()
  }
}

object SassNumber {

  /** The number of distinct digits that are emitted when converting a number to CSS. */
  val precision: Int = 10

  /** Formats a Double for CSS output: integers as `10`, non-integers rounded to `precision` significant digits with trailing zeros stripped.
    *
    * Locale-independent (Scala.js can't link `String.format` with `Locale`). Uses integer arithmetic: round(value * 10^precision) / 10^precision.
    */
  def formatNumber(value: Double): String =
    if (value.isNaN) "NaN"
    else if (value.isPosInfinity) "Infinity"
    else if (value.isNegInfinity) "-Infinity"
    else if (value == value.toLong.toDouble && math.abs(value) < 1e15) {
      // Integer-valued: emit without decimal point
      value.toLong.toString
    } else {
      val scale     = math.pow(10.0, precision.toDouble)
      val scaled    = math.round(value * scale)
      val isNeg     = scaled < 0
      val absScaled = math.abs(scaled)
      // Split into integer and fractional parts
      val intPart  = absScaled / scale.toLong
      val fracPart = absScaled - intPart * scale.toLong
      val sb       = new StringBuilder()
      if (isNeg) sb.append('-')
      sb.append(intPart.toString)
      if (fracPart != 0) {
        sb.append('.')
        // Zero-pad the fractional part to `precision` digits
        val fracStr = fracPart.toString
        var padding = precision - fracStr.length
        while (padding > 0) { sb.append('0'); padding -= 1 }
        sb.append(fracStr)
        // Strip trailing zeros
        var end = sb.length
        while (end > 0 && sb.charAt(end - 1) == '0') end -= 1
        if (end > 0 && sb.charAt(end - 1) == '.') end -= 1
        sb.setLength(end)
      }
      val result = sb.toString()
      if (result == "-0") "0" else result
    }

  /** Creates a number, optionally with a single numerator unit. */
  def apply(value: Double): UnitlessSassNumber = UnitlessSassNumber(value)

  /** Creates a number with a single numerator unit. */
  def apply(value: Double, unit: String): SingleUnitSassNumber = SingleUnitSassNumber(value, unit)

  /** Creates a number with full numeratorUnits and denominatorUnits. */
  def withUnits(
    value:            Double,
    numeratorUnits:   List[String] = Nil,
    denominatorUnits: List[String] = Nil
  ): SassNumber =
    (numeratorUnits, denominatorUnits) match {
      case (Nil, Nil) =>
        UnitlessSassNumber(value)
      case (unit :: Nil, Nil) =>
        SingleUnitSassNumber(value, unit)
      case (nums, Nil) if nums.length > 1 =>
        ComplexSassNumber(value, nums, Nil)
      case (Nil, denoms) if denoms.nonEmpty =>
        ComplexSassNumber(value, Nil, denoms)
      case _ =>
        // Simplify compatible numerator/denominator pairs
        val mutableNumerators        = ArrayBuffer.from(numeratorUnits)
        val unsimplifiedDenominators = ArrayBuffer.from(denominatorUnits)
        var mutableValue             = value

        val finalDenominators = ArrayBuffer.empty[String]
        for (denominator <- unsimplifiedDenominators) {
          var simplifiedAway = false
          boundary[Unit] {
            var i = 0
            while (i < mutableNumerators.length) {
              val factor = conversionFactor(denominator, mutableNumerators(i))
              if (factor.isDefined) {
                mutableValue *= factor.get
                mutableNumerators.remove(i)
                simplifiedAway = true
                break(())
              }
              i += 1
            }
          }
          if (!simplifiedAway) finalDenominators += denominator
        }

        (mutableNumerators.toList, finalDenominators.toList) match {
          case (Nil, Nil)         => UnitlessSassNumber(mutableValue)
          case (unit :: Nil, Nil) => SingleUnitSassNumber(mutableValue, unit)
          case (nums, denoms)     => ComplexSassNumber(mutableValue, nums, denoms)
        }
    }

  // --- Unit conversion tables ---

  /** A nested map containing unit conversion rates. `1unit1 * conversions(unit2)(unit1) = 1unit2`.
    */
  private[value] val conversions: Map[String, Map[String, Double]] = Map(
    // Length
    "in" -> Map(
      "in" -> 1.0,
      "cm" -> 1.0 / 2.54,
      "pc" -> 1.0 / 6.0,
      "mm" -> 1.0 / 25.4,
      "q" -> 1.0 / 101.6,
      "pt" -> 1.0 / 72.0,
      "px" -> 1.0 / 96.0
    ),
    "cm" -> Map(
      "in" -> 2.54,
      "cm" -> 1.0,
      "pc" -> 2.54 / 6.0,
      "mm" -> 1.0 / 10.0,
      "q" -> 1.0 / 40.0,
      "pt" -> 2.54 / 72.0,
      "px" -> 2.54 / 96.0
    ),
    "pc" -> Map(
      "in" -> 6.0,
      "cm" -> 6.0 / 2.54,
      "pc" -> 1.0,
      "mm" -> 6.0 / 25.4,
      "q" -> 6.0 / 101.6,
      "pt" -> 1.0 / 12.0,
      "px" -> 1.0 / 16.0
    ),
    "mm" -> Map(
      "in" -> 25.4,
      "cm" -> 10.0,
      "pc" -> 25.4 / 6.0,
      "mm" -> 1.0,
      "q" -> 1.0 / 4.0,
      "pt" -> 25.4 / 72.0,
      "px" -> 25.4 / 96.0
    ),
    "q" -> Map(
      "in" -> 101.6,
      "cm" -> 40.0,
      "pc" -> 101.6 / 6.0,
      "mm" -> 4.0,
      "q" -> 1.0,
      "pt" -> 101.6 / 72.0,
      "px" -> 101.6 / 96.0
    ),
    "pt" -> Map(
      "in" -> 72.0,
      "cm" -> 72.0 / 2.54,
      "pc" -> 12.0,
      "mm" -> 72.0 / 25.4,
      "q" -> 72.0 / 101.6,
      "pt" -> 1.0,
      "px" -> 3.0 / 4.0
    ),
    "px" -> Map(
      "in" -> 96.0,
      "cm" -> 96.0 / 2.54,
      "pc" -> 16.0,
      "mm" -> 96.0 / 25.4,
      "q" -> 96.0 / 101.6,
      "pt" -> 4.0 / 3.0,
      "px" -> 1.0
    ),
    // Rotation
    "deg" -> Map("deg" -> 1.0, "grad" -> 9.0 / 10.0, "rad" -> 180.0 / math.Pi, "turn" -> 360.0),
    "grad" -> Map("deg" -> 10.0 / 9.0, "grad" -> 1.0, "rad" -> 200.0 / math.Pi, "turn" -> 400.0),
    "rad" -> Map("deg" -> math.Pi / 180.0, "grad" -> math.Pi / 200.0, "rad" -> 1.0, "turn" -> 2.0 * math.Pi),
    "turn" -> Map("deg" -> 1.0 / 360.0, "grad" -> 1.0 / 400.0, "rad" -> 1.0 / (2.0 * math.Pi), "turn" -> 1.0),
    // Time
    "s" -> Map("s" -> 1.0, "ms" -> 1.0 / 1000.0),
    "ms" -> Map("s" -> 1000.0, "ms" -> 1.0),
    // Frequency
    "Hz" -> Map("Hz" -> 1.0, "kHz" -> 1000.0),
    "kHz" -> Map("Hz" -> 1.0 / 1000.0, "kHz" -> 1.0),
    // Pixel density
    "dpi" -> Map("dpi" -> 1.0, "dpcm" -> 2.54, "dppx" -> 96.0),
    "dpcm" -> Map("dpi" -> 1.0 / 2.54, "dpcm" -> 1.0, "dppx" -> 96.0 / 2.54),
    "dppx" -> Map("dpi" -> 1.0 / 96.0, "dpcm" -> 2.54 / 96.0, "dppx" -> 1.0)
  )

  /** A map from human-readable names of unit types to the convertible units in those types. */
  private[value] val unitsByType: Map[String, List[String]] = Map(
    "length" -> List("in", "cm", "pc", "mm", "q", "pt", "px"),
    "angle" -> List("deg", "grad", "rad", "turn"),
    "time" -> List("s", "ms"),
    "frequency" -> List("Hz", "kHz"),
    "pixel density" -> List("dpi", "dpcm", "dppx")
  )

  /** A map from units to the human-readable names of those unit types. */
  private[value] val typesByUnit: Map[String, String] =
    unitsByType.flatMap { case (typeName, units) =>
      units.map(unit => unit -> typeName)
    }

  /** Returns the number of unit1s per unit2. Equivalently, `1unit2 * conversionFactor(unit1, unit2) = 1unit1`.
    */
  private[value] def conversionFactor(unit1: String, unit2: String): Nullable[Double] =
    if (unit1 == unit2) Nullable(1.0)
    else {
      conversions.get(unit1) match {
        case Some(innerMap) =>
          innerMap.get(unit2) match {
            case Some(factor) => Nullable(factor)
            case scala.None   => Nullable.Null
          }
        case scala.None => Nullable.Null
      }
    }

  /** Returns whether there exists a unit in units1 that can be converted to a unit in units2. */
  private[value] def areAnyConvertible(units1: List[String], units2: List[String]): Boolean =
    units1.exists { unit1 =>
      conversions.get(unit1) match {
        case Some(innerMap) => units2.exists(innerMap.contains)
        case scala.None     => units2.contains(unit1)
      }
    }

  /** Returns a human-readable string representation of numerators and denominators. */
  private[value] def unitString(numerators: List[String], denominators: List[String]): String =
    (numerators, denominators) match {
      case (Nil, Nil)      => "no units"
      case (Nil, d :: Nil) => s"$d^-1"
      case (Nil, _)        => s"(${denominators.mkString("*")})^-1"
      case (_, Nil)        => numerators.mkString("*")
      case (_, d :: Nil)   => s"${numerators.mkString("*")}/$d"
      case _               => s"${numerators.mkString("*")}/(${denominators.mkString("*")})"
    }

  /** Converts a unit list into an equivalent list in canonical form, to make it easier to check whether two numbers have compatible units.
    */
  private[value] def canonicalizeUnitList(units: List[String]): List[String] =
    if (units.isEmpty) units
    else if (units.length == 1) {
      typesByUnit.get(units.head) match {
        case Some(typeName) => List(unitsByType(typeName).head)
        case scala.None     => units
      }
    } else {
      units.map { unit =>
        typesByUnit.get(unit) match {
          case Some(typeName) => unitsByType(typeName).head
          case scala.None     => unit
        }
      }.sorted
    }

  /** Returns a multiplier that encapsulates unit equivalence in units. */
  private[value] def canonicalMultiplier(units: List[String]): Double =
    units.foldLeft(1.0) { (multiplier, unit) =>
      multiplier * canonicalMultiplierForUnit(unit)
    }

  /** Returns a multiplier that encapsulates unit equivalence with unit. */
  private[value] def canonicalMultiplierForUnit(unit: String): Double =
    conversions.get(unit) match {
      case Some(innerMap) => 1.0 / innerMap.values.head
      case scala.None     => 1.0
    }

  /** Sets of units that are known to be compatible with one another in the browser. */
  private[value] val knownCompatibilities: List[Set[String]] = List(
    Set(
      "em",
      "rem",
      "ex",
      "rex",
      "cap",
      "rcap",
      "ch",
      "rch",
      "ic",
      "ric",
      "lh",
      "rlh",
      "vw",
      "lvw",
      "svw",
      "dvw",
      "vh",
      "lvh",
      "svh",
      "dvh",
      "vi",
      "lvi",
      "svi",
      "dvi",
      "vb",
      "lvb",
      "svb",
      "dvb",
      "vmin",
      "lvmin",
      "svmin",
      "dvmin",
      "vmax",
      "lvmax",
      "svmax",
      "dvmax",
      "cqw",
      "cqh",
      "cqi",
      "cqb",
      "cqmin",
      "cqmax",
      "cm",
      "mm",
      "q",
      "in",
      "pt",
      "pc",
      "px"
    ),
    Set("deg", "grad", "rad", "turn"),
    Set("s", "ms"),
    Set("hz", "khz"),
    Set("dpi", "dpcm", "dppx")
  )

  /** A map from units to the other units they're known to be compatible with. */
  private[value] val knownCompatibilitiesByUnit: Map[String, Set[String]] =
    knownCompatibilities.flatMap { set =>
      set.map(unit => unit -> set)
    }.toMap
}
