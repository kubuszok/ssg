/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color/interpolation_method.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interpolation_method.dart → InterpolationMethod.scala
 *   Convention: Dart class → Scala final case class; Dart enum → Scala enum
 *   Idiom: Dart factory fromValue ported;
 *          Dart HueInterpolationMethod? → Nullable[HueInterpolationMethod]
 *   Audited: 2026-04-06
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/color/interpolation_method.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package value
package color

import scala.util.boundary
import scala.util.boundary.break

import ssg.sass.{ Nullable, SassScriptException }
import ssg.sass.Nullable.*

/** The method by which two colors are interpolated to find a color in the middle.
  *
  * Used by SassColor.interpolate.
  */
final case class InterpolationMethod(
  /** The color space in which to perform the interpolation. */
  space: ColorSpace,
  /** How to interpolate the hues between two colors. */
  hue: Nullable[HueInterpolationMethod] = Nullable.Null
) {

  // Validate and default the hue field
  locally {
    if (space.isPolar && hue.isEmpty) {
      // Default to shorter for polar spaces — but case class vals are immutable,
      // so we handle this via the companion apply
    }
    if (!space.isPolar && hue.isDefined) {
      throw new IllegalArgumentException(
        s"Hue interpolation method may not be set for rectangular color space $space."
      )
    }
  }

  override def toString: String =
    space.toString + hue.fold("")(h => s" $h hue")
}

object InterpolationMethod {

  /** Creates an InterpolationMethod, defaulting hue to Shorter for polar spaces. */
  def apply(space: ColorSpace, hue: Nullable[HueInterpolationMethod]): InterpolationMethod = {
    val resolvedHue: Nullable[HueInterpolationMethod] =
      if (space.isPolar) hue.orElse(Nullable(HueInterpolationMethod.Shorter))
      else Nullable.Null

    if (!space.isPolar && hue.isDefined) {
      throw new IllegalArgumentException(
        s"Hue interpolation method may not be set for rectangular color space $space."
      )
    }

    new InterpolationMethod(space, resolvedHue)
  }

  def apply(space: ColorSpace): InterpolationMethod = apply(space, Nullable.Null)

  /** Parses a SassScript value representing an interpolation method, not beginning with "in".
    *
    * Throws a SassScriptException if value isn't a valid interpolation method. If value came from a function argument, name is the argument name (without the `$`). This is used for error reporting.
    */
  def fromValue(value: Value, name: Nullable[String] = Nullable.Null): InterpolationMethod = boundary {
    val list = value.assertCommonListStyle(name, allowSlash = false)
    if (list.isEmpty) {
      throw SassScriptException(
        "Expected a color interpolation method, got an empty list.",
        name.toOption
      )
    }

    val firstStr = list.head.assertString(name)
    firstStr.assertUnquoted(name)
    val space = ColorSpace.fromName(firstStr.text, name.toOption)
    if (list.length == 1) break(InterpolationMethod(space))

    val hueMethod = HueInterpolationMethod.fromValue(list(1), name)
    if (list.length == 2) {
      throw SassScriptException(
        s"Expected unquoted string \"hue\" after $value.",
        name.toOption
      )
    } else {
      val thirdStr = list(2).assertString(name)
      thirdStr.assertUnquoted(name)
      if (thirdStr.text.toLowerCase != "hue") {
        throw SassScriptException(
          s"Expected unquoted string \"hue\" at the end of $value, was ${list(2)}.",
          name.toOption
        )
      } else if (list.length > 3) {
        throw SassScriptException(
          s"Expected nothing after \"hue\" in $value.",
          name.toOption
        )
      } else if (!space.isPolar) {
        throw SassScriptException(
          s"Hue interpolation method \"$hueMethod hue\" may not be set for rectangular color space $space.",
          name.toOption
        )
      }
    }

    InterpolationMethod(space, Nullable(hueMethod))
  }
}

/** The method by which two hues are adjusted when interpolating between colors. */
enum HueInterpolationMethod extends java.lang.Enum[HueInterpolationMethod] {

  /** Angles are adjusted so that theta2 - theta1 is in [-180, 180]. */
  case Shorter

  /** Angles are adjusted so that theta2 - theta1 is in {0, [180, 360)}. */
  case Longer

  /** Angles are adjusted so that theta2 - theta1 is in [0, 360). */
  case Increasing

  /** Angles are adjusted so that theta2 - theta1 is in (-360, 0]. */
  case Decreasing
}

object HueInterpolationMethod {

  /** Parses a HueInterpolationMethod from its CSS name. */
  def fromName(name: String, argumentName: Option[String] = None): HueInterpolationMethod =
    name.toLowerCase match {
      case "shorter"    => HueInterpolationMethod.Shorter
      case "longer"     => HueInterpolationMethod.Longer
      case "increasing" => HueInterpolationMethod.Increasing
      case "decreasing" => HueInterpolationMethod.Decreasing
      case _            =>
        throw SassScriptException(s"Unknown hue interpolation method $name.", argumentName)
    }

  /** Parses a SassScript value representing a hue interpolation method, not ending with "hue".
    *
    * Throws a SassScriptException if value isn't a valid hue interpolation method. If value came from a function argument, name is the argument name (without the `$`). This is used for error
    * reporting.
    */
  private[color] def fromValue(value: Value, name: Nullable[String] = Nullable.Null): HueInterpolationMethod = {
    val str = value.assertString(name)
    str.assertUnquoted(name)
    fromName(str.text, name.toOption)
  }
}
