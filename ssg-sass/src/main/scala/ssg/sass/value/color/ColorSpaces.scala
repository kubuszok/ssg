/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color/space/{all 16 space files}.dart
 * Original: Copyright (c) 2022-2025 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: space/rgb.dart, hsl.dart, hwb.dart, srgb.dart, srgb_linear.dart,
 *            display_p3.dart, display_p3_linear.dart, a98_rgb.dart, prophoto_rgb.dart,
 *            rec2020.dart, xyz_d65.dart, xyz_d50.dart, lab.dart, lch.dart,
 *            oklab.dart, oklch.dart, lms.dart -> ColorSpaces.scala
 *   Convention: 16 small Dart files merged into one Scala file
 *   Idiom: Dart Float64List -> Array[Double]; Dart double? -> Nullable[Double];
 *          Dart null?.andThen -> Nullable map; Dart const -> val
 *   Audited: 2026-04-06
 */
package ssg
package sass
package value
package color

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.NumberUtil.{fuzzyEquals, fuzzyGreaterThanOrEquals}

// ---- Legacy RGB ----

/** The legacy RGB color space. */
final class RgbColorSpace extends ColorSpace(
  "rgb",
  List(
    LinearChannel("red", 0, 255, lowerClamped = true, upperClamped = true),
    LinearChannel("green", 0, 255, lowerClamped = true, upperClamped = true),
    LinearChannel("blue", 0, 255, lowerClamped = true, upperClamped = true)
  )
) {
  override def isBounded: Boolean = true
  override def isLegacy: Boolean = true

  override def convert(
    dest: ColorSpace,
    red: Nullable[Double],
    green: Nullable[Double],
    blue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    ColorSpace.srgb.convert(
      dest,
      red.map(_ / 255),
      green.map(_ / 255),
      blue.map(_ / 255),
      alpha
    )
  }

  override protected def toLinear(channel: Double): Double =
    ColorSpaceUtils.srgbAndDisplayP3ToLinear(channel / 255)

  override protected def fromLinear(channel: Double): Double =
    ColorSpaceUtils.srgbAndDisplayP3FromLinear(channel) * 255
}

// ---- Legacy HSL ----

/** The legacy HSL color space. */
final class HslColorSpace extends ColorSpace(
  "hsl",
  List(
    ColorSpaceUtils.hueChannel,
    LinearChannel("saturation", 0, 100, requiresPercent = true, lowerClamped = true),
    LinearChannel("lightness", 0, 100, requiresPercent = true)
  )
) {
  override def isBounded: Boolean = true
  override def isLegacy: Boolean = true
  override def isPolar: Boolean = true

  override def convert(
    dest: ColorSpace,
    hue: Nullable[Double],
    saturation: Nullable[Double],
    lightness: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    // Algorithm from https://www.w3.org/TR/css3-color/#hsl-color
    val scaledHue = ((hue.getOrElse(0.0)) / 360) % 1
    val scaledSaturation = (saturation.getOrElse(0.0)) / 100
    val scaledLightness = (lightness.getOrElse(0.0)) / 100

    val m2 =
      if (scaledLightness <= 0.5) scaledLightness * (scaledSaturation + 1)
      else scaledLightness + scaledSaturation - scaledLightness * scaledSaturation
    val m1 = scaledLightness * 2 - m2

    ColorSpace.srgb.asInstanceOf[SrgbColorSpace].convertWithMissing(
      dest,
      Nullable(ColorSpaceUtils.hueToRgb(m1, m2, scaledHue + 1.0 / 3)),
      Nullable(ColorSpaceUtils.hueToRgb(m1, m2, scaledHue)),
      Nullable(ColorSpaceUtils.hueToRgb(m1, m2, scaledHue - 1.0 / 3)),
      alpha,
      missingLightness = lightness.isEmpty,
      missingChroma = saturation.isEmpty,
      missingHue = hue.isEmpty
    )
  }
}

// ---- Legacy HWB ----

/** The legacy HWB color space. */
final class HwbColorSpace extends ColorSpace(
  "hwb",
  List(
    ColorSpaceUtils.hueChannel,
    LinearChannel("whiteness", 0, 100, requiresPercent = true),
    LinearChannel("blackness", 0, 100, requiresPercent = true)
  )
) {
  override def isBounded: Boolean = true
  override def isLegacy: Boolean = true
  override def isPolar: Boolean = true

  override def convert(
    dest: ColorSpace,
    hue: Nullable[Double],
    whiteness: Nullable[Double],
    blackness: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    // From https://www.w3.org/TR/css-color-4/#hwb-to-rgb
    val scaledHue = (hue.getOrElse(0.0)) % 360 / 360
    var scaledWhiteness = (whiteness.getOrElse(0.0)) / 100
    var scaledBlackness = (blackness.getOrElse(0.0)) / 100

    val sum = scaledWhiteness + scaledBlackness
    if (sum > 1) {
      scaledWhiteness /= sum
      scaledBlackness /= sum
    }

    val factor = 1 - scaledWhiteness - scaledBlackness
    def toRgb(h: Double): Double = ColorSpaceUtils.hueToRgb(0, 1, h) * factor + scaledWhiteness

    ColorSpace.srgb.asInstanceOf[SrgbColorSpace].convertWithMissing(
      dest,
      Nullable(toRgb(scaledHue + 1.0 / 3)),
      Nullable(toRgb(scaledHue)),
      Nullable(toRgb(scaledHue - 1.0 / 3)),
      alpha,
      missingHue = hue.isEmpty
    )
  }
}

// ---- sRGB ----

/** The sRGB color space.
  *
  * https://www.w3.org/TR/css-color-4/#predefined-sRGB
  */
final class SrgbColorSpace extends ColorSpace("srgb", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  /** Convert with missing-channel flags (used by HSL/HWB → sRGB → dest). */
  def convertWithMissing(
    dest: ColorSpace,
    red: Nullable[Double],
    green: Nullable[Double],
    blue: Nullable[Double],
    alpha: Nullable[Double],
    missingLightness: Boolean = false,
    missingChroma: Boolean = false,
    missingHue: Boolean = false
  ): SassColor = {
    dest match {
      case ColorSpace.hsl | ColorSpace.hwb =>
        val r = red.getOrElse(0.0)
        val g = green.getOrElse(0.0)
        val b = blue.getOrElse(0.0)

        // Algorithm from https://drafts.csswg.org/css-color-4/#rgb-to-hsl
        val max = math.max(math.max(r, g), b)
        val min = math.min(math.min(r, g), b)
        val delta = max - min

        var hue: Double =
          if (max == min) 0
          else if (max == r) 60 * (g - b) / delta + 360
          else if (max == g) 60 * (b - r) / delta + 120
          else 60 * (r - g) / delta + 240 // max == blue

        if (dest eq ColorSpace.hsl) {
          val lightness = (min + max) / 2

          var saturation =
            if (lightness == 0 || lightness == 1) 0.0
            else 100 * (max - lightness) / math.min(lightness, 1 - lightness)
          if (saturation < 0) {
            hue += 180
            saturation = math.abs(saturation)
          }

          SassColor.forSpaceInternal(
            dest,
            if (missingHue || fuzzyEquals(saturation, 0)) Nullable.Null else Nullable(hue % 360),
            if (missingChroma) Nullable.Null else Nullable(saturation),
            if (missingLightness) Nullable.Null else Nullable(lightness * 100),
            alpha
          )
        } else {
          val whiteness = min * 100
          val blackness = 100 - max * 100
          SassColor.forSpaceInternal(
            dest,
            if (missingHue || fuzzyGreaterThanOrEquals(whiteness + blackness, 100)) Nullable.Null
            else Nullable(hue % 360),
            Nullable(whiteness),
            Nullable(blackness),
            alpha
          )
        }

      case ColorSpace.rgb =>
        SassColor.rgb(
          red.map(_ * 255),
          green.map(_ * 255),
          blue.map(_ * 255),
          alpha
        )

      case ColorSpace.srgbLinear =>
        SassColor.forSpaceInternal(
          dest,
          red.map(toLinear),
          green.map(toLinear),
          blue.map(toLinear),
          alpha
        )

      case _ =>
        convertLinear(dest, red, green, blue, alpha,
          missingLightness = missingLightness,
          missingChroma = missingChroma,
          missingHue = missingHue
        )
    }
  }

  override def convert(
    dest: ColorSpace,
    red: Nullable[Double],
    green: Nullable[Double],
    blue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = convertWithMissing(dest, red, green, blue, alpha)

  override protected def toLinear(channel: Double): Double =
    ColorSpaceUtils.srgbAndDisplayP3ToLinear(channel)

  override protected def fromLinear(channel: Double): Double =
    ColorSpaceUtils.srgbAndDisplayP3FromLinear(channel)

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.linearSrgbToLinearDisplayP3
    case ColorSpace.a98Rgb => Conversions.linearSrgbToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.linearSrgbToLinearProphotoRgb
    case ColorSpace.rec2020 => Conversions.linearSrgbToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.linearSrgbToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearSrgbToXyzD50
    case ColorSpace.lms => Conversions.linearSrgbToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- sRGB Linear ----

/** The linear-light sRGB color space. */
final class SrgbLinearColorSpace extends ColorSpace("srgb-linear", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  override def convert(
    dest: ColorSpace,
    red: Nullable[Double],
    green: Nullable[Double],
    blue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = dest match {
    case ColorSpace.rgb | ColorSpace.hsl | ColorSpace.hwb | ColorSpace.srgb =>
      ColorSpace.srgb.convert(
        dest,
        red.map(ColorSpaceUtils.srgbAndDisplayP3FromLinear),
        green.map(ColorSpaceUtils.srgbAndDisplayP3FromLinear),
        blue.map(ColorSpaceUtils.srgbAndDisplayP3FromLinear),
        alpha
      )
    case _ => super.convert(dest, red, green, blue, alpha)
  }

  override protected def toLinear(channel: Double): Double = channel
  override protected def fromLinear(channel: Double): Double = channel

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.linearSrgbToLinearDisplayP3
    case ColorSpace.a98Rgb => Conversions.linearSrgbToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.linearSrgbToLinearProphotoRgb
    case ColorSpace.rec2020 => Conversions.linearSrgbToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.linearSrgbToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearSrgbToXyzD50
    case ColorSpace.lms => Conversions.linearSrgbToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- Display P3 ----

/** The display-p3 color space. */
final class DisplayP3ColorSpace extends ColorSpace("display-p3", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  override def convert(
    dest: ColorSpace,
    red: Nullable[Double],
    green: Nullable[Double],
    blue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    if (dest eq ColorSpace.displayP3Linear) {
      SassColor.forSpaceInternal(
        dest,
        red.map(toLinear),
        green.map(toLinear),
        blue.map(toLinear),
        alpha
      )
    } else {
      convertLinear(dest, red, green, blue, alpha)
    }
  }

  override protected def toLinear(channel: Double): Double =
    ColorSpaceUtils.srgbAndDisplayP3ToLinear(channel)

  override protected def fromLinear(channel: Double): Double =
    ColorSpaceUtils.srgbAndDisplayP3FromLinear(channel)

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.linearDisplayP3ToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.linearDisplayP3ToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.linearDisplayP3ToLinearProphotoRgb
    case ColorSpace.rec2020 => Conversions.linearDisplayP3ToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.linearDisplayP3ToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearDisplayP3ToXyzD50
    case ColorSpace.lms => Conversions.linearDisplayP3ToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- Display P3 Linear ----

/** The display-p3-linear color space. */
final class DisplayP3LinearColorSpace extends ColorSpace("display-p3-linear", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  override def convert(
    dest: ColorSpace,
    red: Nullable[Double],
    green: Nullable[Double],
    blue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    if (dest eq ColorSpace.displayP3) {
      SassColor.forSpaceInternal(
        dest,
        red.map(ColorSpaceUtils.srgbAndDisplayP3FromLinear),
        green.map(ColorSpaceUtils.srgbAndDisplayP3FromLinear),
        blue.map(ColorSpaceUtils.srgbAndDisplayP3FromLinear),
        alpha
      )
    } else {
      super.convert(dest, red, green, blue, alpha)
    }
  }

  override protected def toLinear(channel: Double): Double = channel
  override protected def fromLinear(channel: Double): Double = channel

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.linearDisplayP3ToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.linearDisplayP3ToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.linearDisplayP3ToLinearProphotoRgb
    case ColorSpace.rec2020 => Conversions.linearDisplayP3ToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.linearDisplayP3ToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearDisplayP3ToXyzD50
    case ColorSpace.lms => Conversions.linearDisplayP3ToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- A98 RGB ----

/** The a98-rgb color space. */
final class A98RgbColorSpace extends ColorSpace("a98-rgb", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  override protected def toLinear(channel: Double): Double =
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    math.signum(channel) * math.pow(math.abs(channel), 563.0 / 256)

  override protected def fromLinear(channel: Double): Double =
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    math.signum(channel) * math.pow(math.abs(channel), 256.0 / 563)

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.linearA98RgbToLinearSrgb
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.linearA98RgbToLinearDisplayP3
    case ColorSpace.prophotoRgb => Conversions.linearA98RgbToLinearProphotoRgb
    case ColorSpace.rec2020 => Conversions.linearA98RgbToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.linearA98RgbToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearA98RgbToXyzD50
    case ColorSpace.lms => Conversions.linearA98RgbToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- ProPhoto RGB ----

/** The prophoto-rgb color space. */
final class ProphotoRgbColorSpace extends ColorSpace("prophoto-rgb", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  override protected def toLinear(channel: Double): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val abs = math.abs(channel)
    if (abs <= 16.0 / 512) channel / 16
    else math.signum(channel) * math.pow(abs, 1.8)
  }

  override protected def fromLinear(channel: Double): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val abs = math.abs(channel)
    if (abs >= 1.0 / 512) math.signum(channel) * math.pow(abs, 1.0 / 1.8)
    else 16 * channel
  }

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.linearProphotoRgbToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.linearProphotoRgbToLinearA98Rgb
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.linearProphotoRgbToLinearDisplayP3
    case ColorSpace.rec2020 => Conversions.linearProphotoRgbToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.linearProphotoRgbToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearProphotoRgbToXyzD50
    case ColorSpace.lms => Conversions.linearProphotoRgbToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- Rec2020 ----

/** The rec2020 color space. */
final class Rec2020ColorSpace extends ColorSpace("rec2020", ColorSpaceUtils.rgbChannels) {
  override def isBounded: Boolean = true

  /** A constant used in the rec2020 gamma encoding/decoding functions. */
  private val _alpha = 1.09929682680944
  /** A constant used in the rec2020 gamma encoding/decoding functions. */
  private val _beta = 0.018053968510807

  override protected def toLinear(channel: Double): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val abs = math.abs(channel)
    if (abs < _beta * 4.5) channel / 4.5
    else math.signum(channel) * math.pow((abs + _alpha - 1) / _alpha, 1.0 / 0.45)
  }

  override protected def fromLinear(channel: Double): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
    val abs = math.abs(channel)
    if (abs > _beta) math.signum(channel) * (_alpha * math.pow(abs, 0.45) - (_alpha - 1))
    else 4.5 * channel
  }

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.linearRec2020ToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.linearRec2020ToLinearA98Rgb
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.linearRec2020ToLinearDisplayP3
    case ColorSpace.prophotoRgb => Conversions.linearRec2020ToLinearProphotoRgb
    case ColorSpace.xyzD65 => Conversions.linearRec2020ToXyzD65
    case ColorSpace.xyzD50 => Conversions.linearRec2020ToXyzD50
    case ColorSpace.lms => Conversions.linearRec2020ToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- XYZ D65 ----

/** The xyz-d65 color space. */
final class XyzD65ColorSpace extends ColorSpace("xyz", ColorSpaceUtils.xyzChannels) {
  override def isBounded: Boolean = false

  override protected def toLinear(channel: Double): Double = channel
  override protected def fromLinear(channel: Double): Double = channel

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.xyzD65ToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.xyzD65ToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.xyzD65ToLinearProphotoRgb
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.xyzD65ToLinearDisplayP3
    case ColorSpace.rec2020 => Conversions.xyzD65ToLinearRec2020
    case ColorSpace.xyzD50 => Conversions.xyzD65ToXyzD50
    case ColorSpace.lms => Conversions.xyzD65ToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- XYZ D50 ----

/** The xyz-d50 color space. */
final class XyzD50ColorSpace extends ColorSpace("xyz-d50", ColorSpaceUtils.xyzChannels) {
  override def isBounded: Boolean = false

  /** Convert with missing-channel flags (used by Lab → XYZ-D50 → dest). */
  def convertWithMissing(
    dest: ColorSpace,
    x: Nullable[Double],
    y: Nullable[Double],
    z: Nullable[Double],
    alpha: Nullable[Double],
    missingLightness: Boolean = false,
    missingChroma: Boolean = false,
    missingHue: Boolean = false,
    missingA: Boolean = false,
    missingB: Boolean = false
  ): SassColor = {
    dest match {
      case ColorSpace.lab | ColorSpace.lch =>
        // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
        val f0 = _convertComponentToLabF((x.getOrElse(0.0)) / Conversions.d50(0))
        val f1 = _convertComponentToLabF((y.getOrElse(0.0)) / Conversions.d50(1))
        val f2 = _convertComponentToLabF((z.getOrElse(0.0)) / Conversions.d50(2))
        val lightness: Nullable[Double] = if (missingLightness) Nullable.Null else Nullable((116 * f1) - 16)
        val a = 500 * (f0 - f1)
        val b = 200 * (f1 - f2)

        if (dest eq ColorSpace.lab) {
          SassColor.lab(
            lightness,
            if (missingA) Nullable.Null else Nullable(a),
            if (missingB) Nullable.Null else Nullable(b),
            alpha
          )
        } else {
          ColorSpaceUtils.labToLch(
            ColorSpace.lch,
            lightness,
            Nullable(a),
            Nullable(b),
            alpha,
            missingChroma = missingChroma,
            missingHue = missingHue
          )
        }

      case _ =>
        convertLinear(dest, x, y, z, alpha,
          missingLightness = missingLightness,
          missingChroma = missingChroma,
          missingHue = missingHue,
          missingA = missingA,
          missingB = missingB
        )
    }
  }

  override def convert(
    dest: ColorSpace,
    x: Nullable[Double],
    y: Nullable[Double],
    z: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = convertWithMissing(dest, x, y, z, alpha)

  /** Does a partial conversion of a single XYZ component to Lab. */
  private def _convertComponentToLabF(component: Double): Double = {
    if (component > ColorSpaceUtils.labEpsilon) math.pow(component, 1.0 / 3) + 0.0
    else (ColorSpaceUtils.labKappa * component + 16) / 116
  }

  override protected def toLinear(channel: Double): Double = channel
  override protected def fromLinear(channel: Double): Double = channel

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.xyzD50ToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.xyzD50ToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.xyzD50ToLinearProphotoRgb
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.xyzD50ToLinearDisplayP3
    case ColorSpace.rec2020 => Conversions.xyzD50ToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.xyzD50ToXyzD65
    case ColorSpace.lms => Conversions.xyzD50ToLms
    case _ => super.transformationMatrix(dest)
  }
}

// ---- Lab ----

/** The Lab color space. */
final class LabColorSpace extends ColorSpace(
  "lab",
  List(
    LinearChannel("lightness", 0, 100, lowerClamped = true, upperClamped = true),
    LinearChannel("a", -125, 125),
    LinearChannel("b", -125, 125)
  )
) {
  override def isBounded: Boolean = false

  override def convert(
    dest: ColorSpace,
    lightness: Nullable[Double],
    a: Nullable[Double],
    b: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = convertWithMissing(dest, lightness, a, b, alpha)

  /** Convert with missing-channel flags. */
  def convertWithMissing(
    dest: ColorSpace,
    lightness: Nullable[Double],
    a: Nullable[Double],
    b: Nullable[Double],
    alpha: Nullable[Double],
    missingChroma: Boolean = false,
    missingHue: Boolean = false
  ): SassColor = {
    dest match {
      case ColorSpace.lab =>
        val powerlessAB = lightness.isEmpty || fuzzyEquals(lightness.get, 0)
        SassColor.lab(
          lightness,
          if (a.isEmpty || powerlessAB) Nullable.Null else a,
          if (b.isEmpty || powerlessAB) Nullable.Null else b,
          alpha
        )

      case ColorSpace.lch =>
        ColorSpaceUtils.labToLch(dest, lightness, a, b, alpha)

      case _ =>
        val missingLightness = lightness.isEmpty
        val lVal = lightness.getOrElse(0.0)
        // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
        val f1 = (lVal + 16) / 116

        ColorSpace.xyzD50.asInstanceOf[XyzD50ColorSpace].convertWithMissing(
          dest,
          Nullable(_convertFToXorZ((a.getOrElse(0.0)) / 500 + f1) * Conversions.d50(0)),
          Nullable(
            (if (lVal > ColorSpaceUtils.labKappa * ColorSpaceUtils.labEpsilon)
               math.pow((lVal + 16) / 116, 3) * 1.0
             else lVal / ColorSpaceUtils.labKappa) * Conversions.d50(1)
          ),
          Nullable(_convertFToXorZ(f1 - (b.getOrElse(0.0)) / 200) * Conversions.d50(2)),
          alpha,
          missingLightness = missingLightness,
          missingChroma = missingChroma,
          missingHue = missingHue,
          missingA = a.isEmpty,
          missingB = b.isEmpty
        )
    }
  }

  /** Converts an f-format component to the X or Z channel of an XYZ color. */
  private def _convertFToXorZ(component: Double): Double = {
    val cubed = math.pow(component, 3) + 0.0
    if (cubed > ColorSpaceUtils.labEpsilon) cubed
    else (116 * component - 16) / ColorSpaceUtils.labKappa
  }
}

// ---- LCH ----

/** The LCH color space. */
final class LchColorSpace extends ColorSpace(
  "lch",
  List(
    LinearChannel("lightness", 0, 100, lowerClamped = true, upperClamped = true),
    LinearChannel("chroma", 0, 150, lowerClamped = true),
    ColorSpaceUtils.hueChannel
  )
) {
  override def isBounded: Boolean = false
  override def isPolar: Boolean = true

  override def convert(
    dest: ColorSpace,
    lightness: Nullable[Double],
    chroma: Nullable[Double],
    hue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    val hueRadians = (hue.getOrElse(0.0)) * math.Pi / 180
    ColorSpace.lab.asInstanceOf[LabColorSpace].convertWithMissing(
      dest,
      lightness,
      Nullable((chroma.getOrElse(0.0)) * math.cos(hueRadians)),
      Nullable((chroma.getOrElse(0.0)) * math.sin(hueRadians)),
      alpha,
      missingChroma = chroma.isEmpty,
      missingHue = hue.isEmpty
    )
  }
}

// ---- OKLab ----

/** The OKLab color space. */
final class OklabColorSpace extends ColorSpace(
  "oklab",
  List(
    LinearChannel("lightness", 0, 1, conventionallyPercent = Nullable(true), lowerClamped = true, upperClamped = true),
    LinearChannel("a", -0.4, 0.4),
    LinearChannel("b", -0.4, 0.4)
  )
) {
  override def isBounded: Boolean = false

  override def convert(
    dest: ColorSpace,
    lightness: Nullable[Double],
    a: Nullable[Double],
    b: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = convertWithMissing(dest, lightness, a, b, alpha)

  /** Convert with missing-channel flags. */
  def convertWithMissing(
    dest: ColorSpace,
    lightness: Nullable[Double],
    a: Nullable[Double],
    b: Nullable[Double],
    alpha: Nullable[Double],
    missingChroma: Boolean = false,
    missingHue: Boolean = false
  ): SassColor = {
    if (dest eq ColorSpace.oklch) {
      ColorSpaceUtils.labToLch(dest, lightness, a, b, alpha,
        missingChroma = missingChroma, missingHue = missingHue)
    } else {
      val missingLightness = lightness.isEmpty
      val missingA = a.isEmpty
      val missingB = b.isEmpty
      val l = lightness.getOrElse(0.0)
      val aVal = a.getOrElse(0.0)
      val bVal = b.getOrElse(0.0)
      val m = Conversions.oklabToLms
      // Algorithm from https://www.w3.org/TR/css-color-4/#color-conversion-code
      ColorSpace.lms.asInstanceOf[LmsColorSpace].convertWithMissing(
        dest,
        Nullable(math.pow(m(0) * l + m(1) * aVal + m(2) * bVal, 3) + 0.0),
        Nullable(math.pow(m(3) * l + m(4) * aVal + m(5) * bVal, 3) + 0.0),
        Nullable(math.pow(m(6) * l + m(7) * aVal + m(8) * bVal, 3) + 0.0),
        alpha,
        missingLightness = missingLightness,
        missingChroma = missingChroma,
        missingHue = missingHue,
        missingA = missingA,
        missingB = missingB
      )
    }
  }
}

// ---- OKLCH ----

/** The OKLCH color space. */
final class OklchColorSpace extends ColorSpace(
  "oklch",
  List(
    LinearChannel("lightness", 0, 1, conventionallyPercent = Nullable(true), lowerClamped = true, upperClamped = true),
    LinearChannel("chroma", 0, 0.4, lowerClamped = true),
    ColorSpaceUtils.hueChannel
  )
) {
  override def isBounded: Boolean = false
  override def isPolar: Boolean = true

  override def convert(
    dest: ColorSpace,
    lightness: Nullable[Double],
    chroma: Nullable[Double],
    hue: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = {
    val hueRadians = (hue.getOrElse(0.0)) * math.Pi / 180
    ColorSpace.oklab.asInstanceOf[OklabColorSpace].convertWithMissing(
      dest,
      lightness,
      Nullable((chroma.getOrElse(0.0)) * math.cos(hueRadians)),
      Nullable((chroma.getOrElse(0.0)) * math.sin(hueRadians)),
      alpha,
      missingChroma = chroma.isEmpty,
      missingHue = hue.isEmpty
    )
  }
}

// ---- LMS (internal) ----

/** The LMS color space (internal, for OKLab/OKLCH conversions). */
final class LmsColorSpace extends ColorSpace(
  "lms",
  List(
    LinearChannel("long", 0, 1),
    LinearChannel("medium", 0, 1),
    LinearChannel("short", 0, 1)
  )
) {
  override def isBounded: Boolean = false

  /** Convert with missing-channel flags. */
  def convertWithMissing(
    dest: ColorSpace,
    long: Nullable[Double],
    medium: Nullable[Double],
    short: Nullable[Double],
    alpha: Nullable[Double],
    missingLightness: Boolean = false,
    missingChroma: Boolean = false,
    missingHue: Boolean = false,
    missingA: Boolean = false,
    missingB: Boolean = false
  ): SassColor = {
    val m = Conversions.lmsToOklab
    dest match {
      case ColorSpace.oklab =>
        // Algorithm from https://drafts.csswg.org/css-color-4/#color-conversion-code
        val longScaled = _cubeRootPreservingSign(long.getOrElse(0.0))
        val mediumScaled = _cubeRootPreservingSign(medium.getOrElse(0.0))
        val shortScaled = _cubeRootPreservingSign(short.getOrElse(0.0))

        SassColor.oklab(
          if (missingLightness) Nullable.Null
          else Nullable(m(0) * longScaled + m(1) * mediumScaled + m(2) * shortScaled),
          if (missingA) Nullable.Null
          else Nullable(m(3) * longScaled + m(4) * mediumScaled + m(5) * shortScaled),
          if (missingB) Nullable.Null
          else Nullable(m(6) * longScaled + m(7) * mediumScaled + m(8) * shortScaled),
          alpha
        )

      case ColorSpace.oklch =>
        // Inline OKLab → OKLCH for common path
        val longScaled = _cubeRootPreservingSign(long.getOrElse(0.0))
        val mediumScaled = _cubeRootPreservingSign(medium.getOrElse(0.0))
        val shortScaled = _cubeRootPreservingSign(short.getOrElse(0.0))
        ColorSpaceUtils.labToLch(
          dest,
          if (missingLightness) Nullable.Null
          else Nullable(m(0) * longScaled + m(1) * mediumScaled + m(2) * shortScaled),
          Nullable(m(3) * longScaled + m(4) * mediumScaled + m(5) * shortScaled),
          Nullable(m(6) * longScaled + m(7) * mediumScaled + m(8) * shortScaled),
          alpha,
          missingChroma = missingChroma,
          missingHue = missingHue
        )

      case _ =>
        convertLinear(dest, long, medium, short, alpha,
          missingLightness = missingLightness,
          missingChroma = missingChroma,
          missingHue = missingHue,
          missingA = missingA,
          missingB = missingB
        )
    }
  }

  override def convert(
    dest: ColorSpace,
    long: Nullable[Double],
    medium: Nullable[Double],
    short: Nullable[Double],
    alpha: Nullable[Double]
  ): SassColor = convertWithMissing(dest, long, medium, short, alpha)

  /** Returns the cube root of the absolute value of number with the same sign. */
  private def _cubeRootPreservingSign(number: Double): Double =
    math.pow(math.abs(number), 1.0 / 3) * math.signum(number)

  override protected def toLinear(channel: Double): Double = channel
  override protected def fromLinear(channel: Double): Double = channel

  override protected def transformationMatrix(dest: ColorSpace): Array[Double] = dest match {
    case ColorSpace.srgbLinear | ColorSpace.srgb | ColorSpace.rgb => Conversions.lmsToLinearSrgb
    case ColorSpace.a98Rgb => Conversions.lmsToLinearA98Rgb
    case ColorSpace.prophotoRgb => Conversions.lmsToLinearProphotoRgb
    case ColorSpace.displayP3 | ColorSpace.displayP3Linear => Conversions.lmsToLinearDisplayP3
    case ColorSpace.rec2020 => Conversions.lmsToLinearRec2020
    case ColorSpace.xyzD65 => Conversions.lmsToXyzD65
    case ColorSpace.xyzD50 => Conversions.lmsToXyzD50
    case _ => super.transformationMatrix(dest)
  }
}
