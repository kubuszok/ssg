/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/number/complex.dart
 * Original: Copyright (c) 2020 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: complex.dart → ComplexSassNumber.scala
 *   Convention: Dart sealed class → Scala final class
 *   Idiom: Dart assert → Scala assert; constructor delegates to private this()
 *   Audited: 2026-04-05
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/number/complex.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package value
package number

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.value.SassNumber

import scala.language.implicitConversions

/** A specialized subclass of SassNumber for numbers that are not UnitlessSassNumbers or SingleUnitSassNumbers.
  */
final class ComplexSassNumber private (
  value:                         Double,
  private val _numeratorUnits:   List[String],
  private val _denominatorUnits: List[String],
  asSlash:                       Nullable[(SassNumber, SassNumber)] = Nullable.Null
) extends SassNumber(value, asSlash) {

  assert(
    _numeratorUnits.length > 1 || _denominatorUnits.nonEmpty,
    "ComplexSassNumber requires more than one numerator unit or at least one denominator unit"
  )

  def numeratorUnits: List[String] = _numeratorUnits

  def denominatorUnits: List[String] = _denominatorUnits

  def hasUnits:        Boolean = true
  def hasComplexUnits: Boolean = true

  def hasUnit(unit: String): Boolean = false

  def compatibleWithUnit(unit: String): Boolean = false

  def hasPossiblyCompatibleUnits(other: SassNumber): Boolean =
    // This logic is well-defined, and we could implement it in principle.
    // However, it would be fairly complex and there's no clear need for it yet.
    throw new UnsupportedOperationException(
      "ComplexSassNumber.hasPossiblyCompatibleUnits is not implemented."
    )

  protected[value] def withValue(value: Double): SassNumber =
    new ComplexSassNumber(value, _numeratorUnits, _denominatorUnits)

  def withSlash(numerator: SassNumber, denominator: SassNumber): SassNumber =
    new ComplexSassNumber(
      value,
      _numeratorUnits,
      _denominatorUnits,
      Nullable((numerator, denominator))
    )
}

object ComplexSassNumber {
  def apply(
    value:            Double,
    numeratorUnits:   List[String],
    denominatorUnits: List[String]
  ): ComplexSassNumber =
    new ComplexSassNumber(value, numeratorUnits, denominatorUnits)

  def apply(
    value:            Double,
    numeratorUnits:   List[String],
    denominatorUnits: List[String],
    asSlash:          Nullable[(SassNumber, SassNumber)]
  ): ComplexSassNumber =
    new ComplexSassNumber(value, numeratorUnits, denominatorUnits, asSlash)
}
