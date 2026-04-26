/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color/space.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: space.dart → ColorSpace.scala
 *   Convention: Dart sealed abstract base class → Scala abstract class
 *   Idiom: Dart Float64List → Array[Double]; extension methods → direct methods;
 *          all 16 space instances in companion object; Dart double? → Nullable[Double]
 *   Audited: 2026-04-06
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/color/space.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package value
package color

import ssg.sass.{ Nullable, SassScriptException }
import ssg.sass.Nullable.*

/** A color space whose channel names and semantics Sass knows. */
abstract class ColorSpace(
  /** The CSS name of the color space. */
  val name:              String,
  private val _channels: List[ColorChannel]
) {

  /** This color space's channels. */
  def channels: List[ColorChannel] = _channels

  /** Whether this color space has a bounded gamut. */
  def isBounded: Boolean

  /** Whether this is a legacy color space. */
  def isLegacy: Boolean = false

  /** Whether this color space uses a polar coordinate system. */
  def isPolar: Boolean = false

  /** Converts a color with the given channels from this color space to [dest].
    *
    * By default, this uses this color space's toLinear and transformationMatrix as well as dest's fromLinear.
    */
  def convert(
    dest:     ColorSpace,
    channel0: Nullable[Double],
    channel1: Nullable[Double],
    channel2: Nullable[Double],
    alpha:    Nullable[Double]
  ): SassColor =
    convertLinear(dest, channel0, channel1, channel2, alpha)

  /** The default implementation of convert, which always starts with a linear transformation from RGB or XYZ channels to a linear destination space.
    */
  final protected def convertLinear(
    dest:             ColorSpace,
    red:              Nullable[Double],
    green:            Nullable[Double],
    blue:             Nullable[Double],
    alpha:            Nullable[Double],
    missingLightness: Boolean = false,
    missingChroma:    Boolean = false,
    missingHue:       Boolean = false,
    missingA:         Boolean = false,
    missingB:         Boolean = false
  ): SassColor = {
    val linearDest: ColorSpace = dest match {
      case ColorSpace.hsl | ColorSpace.hwb     => ColorSpace.srgb
      case ColorSpace.lab | ColorSpace.lch     => ColorSpace.xyzD50
      case ColorSpace.oklab | ColorSpace.oklch => ColorSpace.lms
      case other                               => other
    }

    var transformedRed:   Nullable[Double] = Nullable.Null
    var transformedGreen: Nullable[Double] = Nullable.Null
    var transformedBlue:  Nullable[Double] = Nullable.Null

    if (linearDest eq this) {
      transformedRed = red
      transformedGreen = green
      transformedBlue = blue
    } else {
      val linearRed   = toLinear(red.getOrElse(0.0))
      val linearGreen = toLinear(green.getOrElse(0.0))
      val linearBlue  = toLinear(blue.getOrElse(0.0))
      val matrix      = transformationMatrix(linearDest)

      transformedRed = Nullable(
        linearDest.fromLinear(
          matrix(0) * linearRed + matrix(1) * linearGreen + matrix(2) * linearBlue
        )
      )
      transformedGreen = Nullable(
        linearDest.fromLinear(
          matrix(3) * linearRed + matrix(4) * linearGreen + matrix(5) * linearBlue
        )
      )
      transformedBlue = Nullable(
        linearDest.fromLinear(
          matrix(6) * linearRed + matrix(7) * linearGreen + matrix(8) * linearBlue
        )
      )
    }

    dest match {
      case ColorSpace.hsl | ColorSpace.hwb =>
        ColorSpace.srgb
          .asInstanceOf[SrgbColorSpace]
          .convertWithMissing(
            dest,
            transformedRed,
            transformedGreen,
            transformedBlue,
            alpha,
            missingLightness = missingLightness,
            missingChroma = missingChroma,
            missingHue = missingHue
          )
      case ColorSpace.lab | ColorSpace.lch =>
        ColorSpace.xyzD50
          .asInstanceOf[XyzD50ColorSpace]
          .convertWithMissing(
            dest,
            transformedRed,
            transformedGreen,
            transformedBlue,
            alpha,
            missingLightness = missingLightness,
            missingChroma = missingChroma,
            missingHue = missingHue,
            missingA = missingA,
            missingB = missingB
          )
      case ColorSpace.oklab | ColorSpace.oklch =>
        ColorSpace.lms
          .asInstanceOf[LmsColorSpace]
          .convertWithMissing(
            dest,
            transformedRed,
            transformedGreen,
            transformedBlue,
            alpha,
            missingLightness = missingLightness,
            missingChroma = missingChroma,
            missingHue = missingHue,
            missingA = missingA,
            missingB = missingB
          )
      case _ =>
        SassColor.forSpaceInternal(
          dest,
          if (red.isEmpty) Nullable.Null else transformedRed,
          if (green.isEmpty) Nullable.Null else transformedGreen,
          if (blue.isEmpty) Nullable.Null else transformedBlue,
          alpha
        )
    }
  }

  /** Converts a channel in this color space into an element of a linear vector. */
  protected def toLinear(channel: Double): Double =
    throw new UnsupportedOperationException(
      s"[BUG] Color space $this doesn't support linear conversions."
    )

  /** Converts a linear vector element back into a channel in this color space. */
  protected def fromLinear(channel: Double): Double =
    throw new UnsupportedOperationException(
      s"[BUG] Color space $this doesn't support linear conversions."
    )

  /** Returns the matrix for performing a linear transformation from this space to [dest]. */
  protected def transformationMatrix(dest: ColorSpace): Array[Double] =
    throw new UnsupportedOperationException(
      s"[BUG] Color space conversion from $this to $dest not implemented."
    )

  override def toString: String = name
}

object ColorSpace {

  /** The legacy RGB color space. */
  val rgb: ColorSpace = RgbColorSpace()

  /** The legacy HSL color space. */
  val hsl: ColorSpace = HslColorSpace()

  /** The legacy HWB color space. */
  val hwb: ColorSpace = HwbColorSpace()

  /** The sRGB color space. */
  val srgb: ColorSpace = SrgbColorSpace()

  /** The linear-light sRGB color space. */
  val srgbLinear: ColorSpace = SrgbLinearColorSpace()

  /** The display-p3 color space. */
  val displayP3: ColorSpace = DisplayP3ColorSpace()

  /** The display-p3-linear color space. */
  val displayP3Linear: ColorSpace = DisplayP3LinearColorSpace()

  /** The a98-rgb color space. */
  val a98Rgb: ColorSpace = A98RgbColorSpace()

  /** The prophoto-rgb color space. */
  val prophotoRgb: ColorSpace = ProphotoRgbColorSpace()

  /** The rec2020 color space. */
  val rec2020: ColorSpace = Rec2020ColorSpace()

  /** The xyz-d65 color space. */
  val xyzD65: ColorSpace = XyzD65ColorSpace()

  /** The xyz-d50 color space. */
  val xyzD50: ColorSpace = XyzD50ColorSpace()

  /** The CIE Lab color space. */
  val lab: ColorSpace = LabColorSpace()

  /** The CIE LCH color space. */
  val lch: ColorSpace = LchColorSpace()

  /** The internal LMS color space (only for OKLab/OKLCH conversions). */
  val lms: ColorSpace = LmsColorSpace()

  /** The Oklab color space. */
  val oklab: ColorSpace = OklabColorSpace()

  /** The Oklch color space. */
  val oklch: ColorSpace = OklchColorSpace()

  /** Given a color space name, returns the known color space with that name or throws a SassScriptException if there is none.
    */
  def fromName(name: String, argumentName: Option[String] = None): ColorSpace =
    name.toLowerCase match {
      case "rgb"               => rgb
      case "hwb"               => hwb
      case "hsl"               => hsl
      case "srgb"              => srgb
      case "srgb-linear"       => srgbLinear
      case "display-p3"        => displayP3
      case "display-p3-linear" => displayP3Linear
      case "a98-rgb"           => a98Rgb
      case "prophoto-rgb"      => prophotoRgb
      case "rec2020"           => rec2020
      case "xyz" | "xyz-d65"   => xyzD65
      case "xyz-d50"           => xyzD50
      case "lab"               => lab
      case "lch"               => lch
      case "oklab"             => oklab
      case "oklch"             => oklch
      case _                   =>
        throw SassScriptException(s"Unknown color space \"$name\".", argumentName)
    }
}
