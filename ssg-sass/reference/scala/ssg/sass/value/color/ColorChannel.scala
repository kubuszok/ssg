/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color/channel.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: channel.dart → ColorChannel.scala
 *   Convention: Dart sealed class → Scala class (open for LinearChannel subclass)
 *   Idiom: Dart String? → Nullable[String]; Dart bool? → default parameter
 *   Audited: 2026-04-06
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/color/channel.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package value
package color

import ssg.sass.Nullable
import ssg.sass.Nullable.*

/** Metadata about a single channel in a known color space. */
class ColorChannel(
  /** The channel's name. */
  val name: String,
  /** Whether this is a polar angle channel. */
  val isPolarAngle: Boolean,
  /** The unit associated with this channel (e.g., "%" or "deg"). */
  val associatedUnit: Nullable[String] = Nullable.Null
) {

  /** Returns whether this channel is analogous to [other].
    *
    * See: https://www.w3.org/TR/css-color-4/#interpolation-missing
    */
  def isAnalogous(other: ColorChannel): Boolean = (name, other.name) match {
    case ("red" | "x", "red" | "x")                         => true
    case ("green" | "y", "green" | "y")                     => true
    case ("blue" | "z", "blue" | "z")                       => true
    case ("chroma" | "saturation", "chroma" | "saturation") => true
    case ("lightness", "lightness")                         => true
    case ("hue", "hue")                                     => true
    case _                                                  => false
  }

  override def toString: String = name
}

object ColorChannel {

  /** The alpha channel shared across all colors. */
  val alpha: LinearChannel = LinearChannel("alpha", 0, 1)
}

/** Metadata about a color channel with a linear (as opposed to polar) value. */
final class LinearChannel(
  name: String,
  /** The channel's minimum value. */
  val min: Double,
  /** The channel's maximum value. */
  val max: Double,
  /** Whether this channel requires values to be specified with unit %. */
  val requiresPercent: Boolean = false,
  /** Whether the lower bound is clamped when created via global function syntax. */
  val lowerClamped: Boolean = false,
  /** Whether the upper bound is clamped when created via global function syntax. */
  val upperClamped:      Boolean = false,
  conventionallyPercent: Nullable[Boolean] = Nullable.Null
) extends ColorChannel(
      name,
      isPolarAngle = false,
      associatedUnit = {
        val isPercent = conventionallyPercent.getOrElse(min == 0 && max == 100)
        if (isPercent) Nullable("%") else Nullable.Null
      }
    )
