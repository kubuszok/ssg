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
 *   Idiom: Dart factory fromValue deferred (requires assertCommonListStyle);
 *          Dart HueInterpolationMethod? → Nullable[HueInterpolationMethod]
 *   Audited: 2026-04-06
 */
package ssg
package sass
package value
package color

import ssg.sass.{SassScriptException, Nullable}
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
  def fromName(name: String, argumentName: Option[String] = None): HueInterpolationMethod = {
    name.toLowerCase match {
      case "shorter" => HueInterpolationMethod.Shorter
      case "longer" => HueInterpolationMethod.Longer
      case "increasing" => HueInterpolationMethod.Increasing
      case "decreasing" => HueInterpolationMethod.Decreasing
      case _ =>
        throw SassScriptException(s"Unknown hue interpolation method $name.", argumentName)
    }
  }
}
