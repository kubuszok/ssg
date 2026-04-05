/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color/space/utils.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: space/utils.dart → ColorSpaceUtils.scala
 *   Convention: Top-level functions → object methods
 *   Idiom: Dart math → scala.math; Dart double → Double
 *   Audited: 2026-04-06
 */
package ssg
package sass
package value
package color

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.NumberUtil.fuzzyEquals

import scala.language.implicitConversions

/** Shared utilities for color space conversions. */
object ColorSpaceUtils {

  /** A constant used to convert Lab to/from XYZ. 29^3/3^3 */
  val labKappa: Double = 24389.0 / 27.0

  /** A constant used to convert Lab to/from XYZ. 6^3/29^3 */
  val labEpsilon: Double = 216.0 / 24389.0

  /** The hue channel shared across all polar color spaces. */
  val hueChannel: ColorChannel = ColorChannel(
    "hue",
    isPolarAngle = true,
    associatedUnit = "deg"
  )

  /** The color channels shared across all RGB color spaces (except legacy RGB). */
  val rgbChannels: List[ColorChannel] = List(
    LinearChannel("red", 0, 1),
    LinearChannel("green", 0, 1),
    LinearChannel("blue", 0, 1)
  )

  /** The color channels shared across both XYZ color spaces. */
  val xyzChannels: List[ColorChannel] = List(
    LinearChannel("x", 0, 1),
    LinearChannel("y", 0, 1),
    LinearChannel("z", 0, 1)
  )

  /** Converts a legacy HSL/HWB hue to an RGB channel.
    *
    * Algorithm from http://www.w3.org/TR/css3-color/#hsl-color
    */
  def hueToRgb(m1: Double, m2: Double, hue0: Double): Double = {
    var hue = hue0
    if (hue < 0) hue += 1
    if (hue > 1) hue -= 1

    if (hue < 1.0 / 6) m1 + (m2 - m1) * hue * 6
    else if (hue < 1.0 / 2) m2
    else if (hue < 2.0 / 3) m1 + (m2 - m1) * (2.0 / 3 - hue) * 6
    else m1
  }

  /** The algorithm for converting a single srgb or display-p3 channel to linear-light form. */
  def srgbAndDisplayP3ToLinear(channel: Double): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val abs = math.abs(channel)
    if (abs <= 0.04045) channel / 12.92
    else math.signum(channel) * math.pow((abs + 0.055) / 1.055, 2.4)
  }

  /** The algorithm for converting a single srgb or display-p3 channel to gamma-corrected form. */
  def srgbAndDisplayP3FromLinear(channel: Double): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val abs = math.abs(channel)
    if (abs <= 0.0031308) channel * 12.92
    else math.signum(channel) * (1.055 * math.pow(abs, 1.0 / 2.4) - 0.055)
  }

  /** Converts a Lab or OKLab color to LCH or OKLCH, respectively. */
  def labToLch(
    dest: ColorSpace,
    lightness: Nullable[Double],
    a: Nullable[Double],
    b: Nullable[Double],
    alpha: Nullable[Double],
    missingChroma: Boolean = false,
    missingHue: Boolean = false
  ): SassColor = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val aVal = a.getOrElse(0.0)
    val bVal = b.getOrElse(0.0)
    val chroma = math.sqrt(math.pow(aVal, 2) + math.pow(bVal, 2))
    val hue: Nullable[Double] =
      if (missingHue || fuzzyEquals(chroma, 0)) Nullable.Null
      else {
        val h = math.atan2(bVal, aVal) * 180 / math.Pi
        if (h >= 0) Nullable(h) else Nullable(h + 360)
      }

    SassColor.forSpaceInternal(
      dest,
      lightness,
      if (missingChroma) Nullable.Null else Nullable(chroma),
      hue,
      alpha
    )
  }
}
