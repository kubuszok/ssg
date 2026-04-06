/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: color.dart → SassColor.scala
 *   Convention: Dart sealed class → Scala final class; Dart double? → Nullable[Double]
 *   Idiom: Dart null-aware → Nullable map/getOrElse; Dart num → Double;
 *          no return (uses boundary/break in gamut map); ColorFormat as sealed trait
 *   Audited: 2026-04-06
 */
package ssg
package sass
package value

import ssg.sass.{ Nullable, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.util.NumberUtil.*
import ssg.sass.value.color.*
import ssg.sass.visitor.ValueVisitor

import scala.language.implicitConversions

/** A SassScript color. */
final class SassColor private (
  private val _space: ColorSpace,
  /** This color's first channel (or Nullable.Null for missing). */
  val channel0OrNull: Nullable[Double],
  /** This color's second channel (or Nullable.Null for missing). */
  val channel1OrNull: Nullable[Double],
  /** This color's third channel (or Nullable.Null for missing). */
  val channel2OrNull: Nullable[Double],
  /** This color's alpha channel (or Nullable.Null for missing). */
  val alphaOrNull: Nullable[Double],
  /** The format in which this color was originally written (RGB space only). */
  val format: Nullable[ColorFormat]
) extends Value {

  assert(format.isEmpty || (_space eq ColorSpace.rgb))
  assert(!(_space eq ColorSpace.lms))

  // --- Space ---

  /** This color's space. */
  def space: ColorSpace = _space

  // --- Channels ---

  /** The values of this color's channels (excluding alpha), 0 for missing. */
  def channels: List[Double] = List(channel0, channel1, channel2)

  /** The values of this color's channels (excluding alpha), Nullable.Null for missing. */
  def channelsOrNull: List[Nullable[Double]] = List(channel0OrNull, channel1OrNull, channel2OrNull)

  /** This color's first channel. Returns 0 for missing. */
  def channel0: Double = channel0OrNull.getOrElse(0.0)

  /** This color's second channel. Returns 0 for missing. */
  def channel1: Double = channel1OrNull.getOrElse(0.0)

  /** This color's third channel. Returns 0 for missing. */
  def channel2: Double = channel2OrNull.getOrElse(0.0)

  /** Whether this color's first channel is missing. */
  def isChannel0Missing: Boolean = channel0OrNull.isEmpty

  /** Whether this color's second channel is missing. */
  def isChannel1Missing: Boolean = channel1OrNull.isEmpty

  /** Whether this color's third channel is missing. */
  def isChannel2Missing: Boolean = channel2OrNull.isEmpty

  /** Whether this color's first channel is powerless. */
  def isChannel0Powerless: Boolean = space match {
    case ColorSpace.hsl => fuzzyEquals(channel1, 0)
    case ColorSpace.hwb => fuzzyGreaterThanOrEquals(channel1 + channel2, 100)
    case _              => false
  }

  /** Whether this color's second channel is powerless. */
  def isChannel1Powerless: Boolean = false

  /** Whether this color's third channel is powerless. */
  def isChannel2Powerless: Boolean = space match {
    case ColorSpace.lch | ColorSpace.oklch => fuzzyEquals(channel1, 0)
    case _                                 => false
  }

  // --- Alpha ---

  /** This color's alpha channel, between 0 and 1. Returns 0 for missing. */
  def alpha: Double = alphaOrNull.getOrElse(0.0)

  /** Whether this color's alpha channel is missing. */
  def isAlphaMissing: Boolean = alphaOrNull.isEmpty

  // --- Properties ---

  /** Whether this is a legacy color (pre-color-spaces syntax). */
  def isLegacy: Boolean = space.isLegacy

  /** Whether this color is in-gamut for its color space. */
  def isInGamut: Boolean =
    if (!space.isBounded) true
    else {
      _isChannelInGamut(channel0, space.channels(0)) &&
      _isChannelInGamut(channel1, space.channels(1)) &&
      _isChannelInGamut(channel2, space.channels(2))
    }

  /** Returns whether value is in-gamut for the given channel. */
  private def _isChannelInGamut(value: Double, channel: ColorChannel): Boolean =
    channel match {
      case lc: LinearChannel =>
        fuzzyLessThanOrEquals(value, lc.max) && fuzzyGreaterThanOrEquals(value, lc.min)
      case _ => true
    }

  /** Whether this color has any missing channels. */
  def hasMissingChannel: Boolean =
    isChannel0Missing || isChannel1Missing || isChannel2Missing || isAlphaMissing

  // --- Legacy accessors (deprecated) ---

  /** This color's red channel, between 0 and 255 (rounded). */
  @deprecated("Use channel() instead.", "always")
  def red: Int = _legacyChannel(ColorSpace.rgb, "red").round.toInt

  /** This color's green channel, between 0 and 255 (rounded). */
  @deprecated("Use channel() instead.", "always")
  def green: Int = _legacyChannel(ColorSpace.rgb, "green").round.toInt

  /** This color's blue channel, between 0 and 255 (rounded). */
  @deprecated("Use channel() instead.", "always")
  def blue: Int = _legacyChannel(ColorSpace.rgb, "blue").round.toInt

  /** This color's hue, between 0 and 360. */
  @deprecated("Use channel() instead.", "always")
  def hue: Double = _legacyChannel(ColorSpace.hsl, "hue")

  /** This color's saturation, a percentage between 0 and 100. */
  @deprecated("Use channel() instead.", "always")
  def saturation: Double = _legacyChannel(ColorSpace.hsl, "saturation")

  /** This color's lightness, a percentage between 0 and 100. */
  @deprecated("Use channel() instead.", "always")
  def lightness: Double = _legacyChannel(ColorSpace.hsl, "lightness")

  /** This color's whiteness, a percentage between 0 and 100. */
  @deprecated("Use channel() instead.", "always")
  def whiteness: Double = _legacyChannel(ColorSpace.hwb, "whiteness")

  /** This color's blackness, a percentage between 0 and 100. */
  @deprecated("Use channel() instead.", "always")
  def blackness: Double = _legacyChannel(ColorSpace.hwb, "blackness")

  // --- Channel lookup ---

  /** Returns the value of the given channel in this color. */
  def channel(channelName: String, colorName: Nullable[String] = Nullable.Null, channelArgName: Nullable[String] = Nullable.Null): Double = {
    val chs = space.channels
    if (channelName == chs(0).name) channel0
    else if (channelName == chs(1).name) channel1
    else if (channelName == chs(2).name) channel2
    else if (channelName == "alpha") alpha
    else
      throw SassScriptException(
        s"Color $this doesn't have a channel named \"$channelName\".",
        channelArgName.toOption
      )
  }

  /** Returns whether the given channel in this color is missing. */
  def isChannelMissing(channelName: String, colorName: Nullable[String] = Nullable.Null, channelArgName: Nullable[String] = Nullable.Null): Boolean = {
    val chs = space.channels
    if (channelName == chs(0).name) isChannel0Missing
    else if (channelName == chs(1).name) isChannel1Missing
    else if (channelName == chs(2).name) isChannel2Missing
    else if (channelName == "alpha") isAlphaMissing
    else
      throw SassScriptException(
        s"Color $this doesn't have a channel named \"$channelName\".",
        channelArgName.toOption
      )
  }

  /** Returns whether the given channel in this color is powerless. */
  def isChannelPowerless(channelName: String, colorName: Nullable[String] = Nullable.Null, channelArgName: Nullable[String] = Nullable.Null): Boolean = {
    val chs = space.channels
    if (channelName == chs(0).name) isChannel0Powerless
    else if (channelName == chs(1).name) isChannel1Powerless
    else if (channelName == chs(2).name) isChannel2Powerless
    else if (channelName == "alpha") false
    else
      throw SassScriptException(
        s"Color $this doesn't have a channel named \"$channelName\".",
        channelArgName.toOption
      )
  }

  /** If this is a legacy color, converts it to the given space and returns the given channel. */
  private def _legacyChannel(destSpace: ColorSpace, channelName: String): Double = {
    if (!isLegacy) {
      throw SassScriptException(
        s"color.$channelName() is only supported for legacy colors. Please use " +
          "color.channel() instead with an explicit $$space argument."
      )
    }
    toSpace(destSpace).channel(channelName)
  }

  // --- Conversions ---

  /** Converts this color to the given space. */
  def toSpace(destSpace: ColorSpace, legacyMissing: Boolean = true): SassColor =
    if (this.space eq destSpace) this
    else {
      val converted = this.space.convert(
        destSpace,
        channel0OrNull,
        channel1OrNull,
        channel2OrNull,
        Nullable(alpha)
      )
      if (
        !legacyMissing && converted.isLegacy &&
        (converted.isChannel0Missing || converted.isChannel1Missing ||
          converted.isChannel2Missing || converted.isAlphaMissing)
      ) {
        SassColor.forSpaceInternal(
          converted.space,
          Nullable(converted.channel0),
          Nullable(converted.channel1),
          Nullable(converted.channel2),
          Nullable(converted.alpha)
        )
      } else {
        converted
      }
    }

  /** Returns a copy of this color that's in-gamut in the current color space. */
  def toGamut(method: GamutMapMethod): SassColor =
    if (isInGamut) this else method.map(this)

  // --- Visitor ---

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitColor(this)

  override def assertColor(name: Nullable[String] = Nullable.Null): SassColor = this

  /** Throws a SassScriptException if this isn't in a legacy color space. */
  def assertLegacy(name: Nullable[String] = Nullable.Null): Unit =
    if (!isLegacy) {
      throw SassScriptException(
        s"Expected $this to be in the legacy RGB, HSL, or HWB color space.",
        name.toOption
      )
    }

  // --- Change channels ---

  /** Returns a new copy with the alpha channel set to alpha. */
  def changeAlpha(newAlpha: Double): SassColor =
    SassColor.forSpaceInternal(
      space,
      Nullable(channel0),
      Nullable(channel1),
      Nullable(channel2),
      Nullable(newAlpha)
    )

  /** Changes one or more of this color's channels and returns the result. */
  def changeChannels(
    newValues: Map[String, Double],
    destSpace: Nullable[ColorSpace] = Nullable.Null,
    colorName: Nullable[String] = Nullable.Null
  ): SassColor =
    if (newValues.isEmpty) this
    else if (destSpace.isDefined && !(destSpace.get eq this.space)) {
      toSpace(destSpace.get).changeChannels(newValues, destSpace = Nullable.Null, colorName = colorName).toSpace(this.space)
    } else {
      var new0:     Nullable[Double] = Nullable.Null
      var new1:     Nullable[Double] = Nullable.Null
      var new2:     Nullable[Double] = Nullable.Null
      var newAlpha: Nullable[Double] = Nullable.Null
      val chs = this.space.channels

      for ((ch, v) <- newValues)
        if (ch == chs(0).name) {
          if (new0.isDefined) throw SassScriptException(s"Multiple values supplied for \"${chs(0)}\": ${new0.get} and $v.", colorName.toOption)
          new0 = Nullable(v)
        } else if (ch == chs(1).name) {
          if (new1.isDefined) throw SassScriptException(s"Multiple values supplied for \"${chs(1)}\": ${new1.get} and $v.", colorName.toOption)
          new1 = Nullable(v)
        } else if (ch == chs(2).name) {
          if (new2.isDefined) throw SassScriptException(s"Multiple values supplied for \"${chs(2)}\": ${new2.get} and $v.", colorName.toOption)
          new2 = Nullable(v)
        } else if (ch == "alpha") {
          if (newAlpha.isDefined) throw SassScriptException(s"Multiple values supplied for \"alpha\": ${newAlpha.get} and $v.", colorName.toOption)
          newAlpha = Nullable(v)
        } else {
          throw SassScriptException(s"Color $this doesn't have a channel named \"$ch\".", colorName.toOption)
        }

      SassColor.forSpaceInternal(
        this.space,
        new0.orElse(channel0OrNull),
        new1.orElse(channel1OrNull),
        new2.orElse(channel2OrNull),
        newAlpha.orElse(alphaOrNull)
      )
    }

  // --- Interpolation ---

  /** Returns a color partway between this and other according to method.
    *
    * The weight is a number between 0 and 1 that indicates how much of this should be in the resulting color. It defaults to 0.5.
    */
  def interpolate(
    other:         SassColor,
    method:        InterpolationMethod,
    weight:        Double = 0.5,
    legacyMissing: Boolean = true
  ): SassColor =
    if (fuzzyEquals(weight, 0)) other
    else if (fuzzyEquals(weight, 1)) this
    else {
      val color1 = toSpace(method.space)
      val color2 = other.toSpace(method.space)

      if (weight < 0 || weight > 1) {
        throw new IllegalArgumentException(s"weight: Expected $weight to be within 0 and 1.")
      }

      // If either color is missing a channel _and_ that channel is analogous with
      // one in the output space, then the output channel should take on the other
      // color's value.
      val missing1_0 = _isAnalogousChannelMissing(this, color1, 0)
      val missing1_1 = _isAnalogousChannelMissing(this, color1, 1)
      val missing1_2 = _isAnalogousChannelMissing(this, color1, 2)
      val missing2_0 = _isAnalogousChannelMissing(other, color2, 0)
      val missing2_1 = _isAnalogousChannelMissing(other, color2, 1)
      val missing2_2 = _isAnalogousChannelMissing(other, color2, 2)

      val channel1_0 = (if (missing1_0) color2 else color1).channel0
      val channel1_1 = (if (missing1_1) color2 else color1).channel1
      val channel1_2 = (if (missing1_2) color2 else color1).channel2
      val channel2_0 = (if (missing2_0) color1 else color2).channel0
      val channel2_1 = (if (missing2_1) color1 else color2).channel1
      val channel2_2 = (if (missing2_2) color1 else color2).channel2
      val alpha1     = alphaOrNull.getOrElse(other.alpha)
      val alpha2     = other.alphaOrNull.getOrElse(alpha)

      val thisMultiplier  = alphaOrNull.getOrElse(1.0) * weight
      val otherMultiplier = other.alphaOrNull.getOrElse(1.0) * (1 - weight)
      val mixedAlpha: Nullable[Double] =
        if (isAlphaMissing && other.isAlphaMissing) Nullable.Null
        else Nullable(alpha1 * weight + alpha2 * (1 - weight))
      val mixedAlphaVal = mixedAlpha.getOrElse(1.0)

      val mixed0: Nullable[Double] =
        if (missing1_0 && missing2_0) Nullable.Null
        else Nullable((channel1_0 * thisMultiplier + channel2_0 * otherMultiplier) / mixedAlphaVal)
      val mixed1: Nullable[Double] =
        if (missing1_1 && missing2_1) Nullable.Null
        else Nullable((channel1_1 * thisMultiplier + channel2_1 * otherMultiplier) / mixedAlphaVal)
      val mixed2: Nullable[Double] =
        if (missing1_2 && missing2_2) Nullable.Null
        else Nullable((channel1_2 * thisMultiplier + channel2_2 * otherMultiplier) / mixedAlphaVal)

      val result = method.space match {
        case ColorSpace.hsl | ColorSpace.hwb =>
          SassColor.forSpaceInternal(
            method.space,
            if (missing1_0 && missing2_0) Nullable.Null
            else Nullable(_interpolateHues(channel1_0, channel2_0, method.hue.get, weight)),
            mixed1,
            mixed2,
            mixedAlpha
          )
        case ColorSpace.lch | ColorSpace.oklch =>
          SassColor.forSpaceInternal(
            method.space,
            mixed0,
            mixed1,
            if (missing1_2 && missing2_2) Nullable.Null
            else Nullable(_interpolateHues(channel1_2, channel2_2, method.hue.get, weight)),
            mixedAlpha
          )
        case _ =>
          SassColor.forSpaceInternal(
            method.space,
            mixed0,
            mixed1,
            mixed2,
            mixedAlpha
          )
      }

      result.toSpace(space, legacyMissing = legacyMissing)
    }

  /** Returns whether output (converted from original) should have a missing channel at outputChannelIndex.
    */
  private def _isAnalogousChannelMissing(
    original:           SassColor,
    output:             SassColor,
    outputChannelIndex: Int
  ): Boolean =
    if (output.channelsOrNull(outputChannelIndex).isEmpty) true
    else if (original eq output) false
    else {
      val outputChannel   = output.space.channels(outputChannelIndex)
      val originalChannel = original.space.channels.find(outputChannel.isAnalogous)
      originalChannel match {
        case None     => false
        case Some(ch) => original.isChannelMissing(ch.name)
      }
    }

  /** Returns a hue partway between hue1 and hue2 according to method. */
  private def _interpolateHues(
    hue1:   Double,
    hue2:   Double,
    method: HueInterpolationMethod,
    weight: Double
  ): Double = {
    var h1 = hue1
    var h2 = hue2
    // Algorithms from https://www.w3.org/TR/css-color-4/#hue-interpolation
    method match {
      case HueInterpolationMethod.Shorter =>
        val diff = h2 - h1
        if (diff > 180) h1 += 360
        else if (diff < -180) h2 += 360

      case HueInterpolationMethod.Longer =>
        val diff = h2 - h1
        if (diff > 0 && diff < 180) h2 += 360
        else if (diff > -180 && diff <= 0) h1 += 360

      case HueInterpolationMethod.Increasing =>
        if (h2 < h1) h2 += 360

      case HueInterpolationMethod.Decreasing =>
        if (h1 < h2) h1 += 360
    }

    h1 * weight + h2 * (1 - weight)
  }

  // --- Arithmetic ---

  override def plus(other: Value): Value = other match {
    case _: SassNumber | _: SassColor =>
      throw SassScriptException(s"Undefined operation \"$this + $other\".")
    case _ => super.plus(other)
  }

  override def minus(other: Value): Value = other match {
    case _: SassNumber | _: SassColor =>
      throw SassScriptException(s"Undefined operation \"$this - $other\".")
    case _ => super.minus(other)
  }

  override def dividedBy(other: Value): Value = other match {
    case _: SassNumber | _: SassColor =>
      throw SassScriptException(s"Undefined operation \"$this / $other\".")
    case _ => super.dividedBy(other)
  }

  // --- Equality ---

  override def equals(other: Any): Boolean = other match {
    case that: SassColor =>
      if (isLegacy) {
        if (!that.isLegacy) false
        else if (!fuzzyEqualsNullable(alphaOrNull, that.alphaOrNull)) false
        else if (space eq that.space) {
          fuzzyEqualsNullable(channel0OrNull, that.channel0OrNull) &&
          fuzzyEqualsNullable(channel1OrNull, that.channel1OrNull) &&
          fuzzyEqualsNullable(channel2OrNull, that.channel2OrNull)
        } else {
          toSpace(ColorSpace.rgb) == that.toSpace(ColorSpace.rgb)
        }
      } else {
        (space eq that.space) &&
        fuzzyEqualsNullable(channel0OrNull, that.channel0OrNull) &&
        fuzzyEqualsNullable(channel1OrNull, that.channel1OrNull) &&
        fuzzyEqualsNullable(channel2OrNull, that.channel2OrNull) &&
        fuzzyEqualsNullable(alphaOrNull, that.alphaOrNull)
      }
    case _ => false
  }

  override def hashCode: Int =
    if (isLegacy) {
      val rgb = toSpace(ColorSpace.rgb)
      fuzzyHashCode(rgb.channel0) ^
        fuzzyHashCode(rgb.channel1) ^
        fuzzyHashCode(rgb.channel2) ^
        fuzzyHashCode(alpha)
    } else {
      space.hashCode ^
        fuzzyHashCode(channel0) ^
        fuzzyHashCode(channel1) ^
        fuzzyHashCode(channel2) ^
        fuzzyHashCode(alpha)
    }
}

object SassColor {

  // --- Factory methods ---

  /** Creates a color in ColorSpace.rgb. */
  def rgb(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    rgbInternal(red, green, blue, alpha)

  /** Like rgb, but also takes a format parameter. */
  def rgbInternal(
    red:    Nullable[Double],
    green:  Nullable[Double],
    blue:   Nullable[Double],
    alpha:  Nullable[Double] = Nullable(1.0),
    format: Nullable[ColorFormat] = Nullable.Null
  ): SassColor =
    _forSpace(ColorSpace.rgb, red, green, blue, alpha, format)

  /** Creates a color in ColorSpace.hsl. */
  def hsl(hue: Nullable[Double], saturation: Nullable[Double], lightness: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    forSpaceInternal(ColorSpace.hsl, hue, saturation, lightness, alpha)

  /** Creates a color in ColorSpace.hwb. */
  def hwb(hue: Nullable[Double], whiteness: Nullable[Double], blackness: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    forSpaceInternal(ColorSpace.hwb, hue, whiteness, blackness, alpha)

  /** Creates a color in ColorSpace.srgb. */
  def srgb(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.srgb, red, green, blue, alpha)

  /** Creates a color in ColorSpace.srgbLinear. */
  def srgbLinear(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.srgbLinear, red, green, blue, alpha)

  /** Creates a color in ColorSpace.displayP3. */
  def displayP3(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.displayP3, red, green, blue, alpha)

  /** Creates a color in ColorSpace.displayP3Linear. */
  def displayP3Linear(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.displayP3Linear, red, green, blue, alpha)

  /** Creates a color in ColorSpace.a98Rgb. */
  def a98Rgb(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.a98Rgb, red, green, blue, alpha)

  /** Creates a color in ColorSpace.prophotoRgb. */
  def prophotoRgb(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.prophotoRgb, red, green, blue, alpha)

  /** Creates a color in ColorSpace.rec2020. */
  def rec2020(red: Nullable[Double], green: Nullable[Double], blue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.rec2020, red, green, blue, alpha)

  /** Creates a color in ColorSpace.xyzD50. */
  def xyzD50(x: Nullable[Double], y: Nullable[Double], z: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.xyzD50, x, y, z, alpha)

  /** Creates a color in ColorSpace.xyzD65. */
  def xyzD65(x: Nullable[Double], y: Nullable[Double], z: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.xyzD65, x, y, z, alpha)

  /** Creates a color in ColorSpace.lab. */
  def lab(lightness: Nullable[Double], a: Nullable[Double], b: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.lab, lightness, a, b, alpha)

  /** Creates a color in ColorSpace.lch. */
  def lch(lightness: Nullable[Double], chroma: Nullable[Double], hue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    forSpaceInternal(ColorSpace.lch, lightness, chroma, hue, alpha)

  /** Creates a color in ColorSpace.oklab. */
  def oklab(lightness: Nullable[Double], a: Nullable[Double], b: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    _forSpace(ColorSpace.oklab, lightness, a, b, alpha)

  /** Creates a color in ColorSpace.oklch. */
  def oklch(lightness: Nullable[Double], chroma: Nullable[Double], hue: Nullable[Double], alpha: Nullable[Double] = Nullable(1.0)): SassColor =
    forSpaceInternal(ColorSpace.oklch, lightness, chroma, hue, alpha)

  /** Creates a color in the given color space from a list of channels. */
  def forSpace(space: ColorSpace, channels: List[Nullable[Double]], alpha: Nullable[Double] = Nullable(1.0)): SassColor = {
    if (channels.length != space.channels.length) {
      throw new IllegalArgumentException(
        s"channels.length: must be exactly ${space.channels.length} for color space \"$space\""
      )
    }
    forSpaceInternal(space, channels(0), channels(1), channels(2), alpha)
  }

  /** Like forSpace, but takes three channels explicitly. Normalizes polar/chroma channels. */
  def forSpaceInternal(
    space:    ColorSpace,
    channel0: Nullable[Double],
    channel1: Nullable[Double],
    channel2: Nullable[Double],
    alpha:    Nullable[Double] = Nullable(1.0)
  ): SassColor =
    space match {
      case ColorSpace.hsl =>
        _forSpace(
          space,
          _normalizeHue(channel0, invert = channel1.isDefined && fuzzyLessThan(channel1.get, 0)),
          channel1.map(v => math.abs(v)),
          channel2,
          alpha
        )
      case ColorSpace.hwb =>
        _forSpace(
          space,
          _normalizeHue(channel0, invert = false),
          channel1,
          channel2,
          alpha
        )
      case ColorSpace.lch | ColorSpace.oklch =>
        _forSpace(
          space,
          channel0,
          channel1.map(v => math.abs(v)),
          _normalizeHue(channel2, invert = channel1.isDefined && fuzzyLessThan(channel1.get, 0)),
          alpha
        )
      case _ =>
        _forSpace(space, channel0, channel1, channel2, alpha)
    }

  /** Creates a SassColor without any pre-processing of channels. */
  private def _forSpace(
    space:    ColorSpace,
    channel0: Nullable[Double],
    channel1: Nullable[Double],
    channel2: Nullable[Double],
    alpha:    Nullable[Double],
    format:   Nullable[ColorFormat] = Nullable.Null
  ): SassColor = {
    val clampedAlpha = alpha.map(a => fuzzyAssertRange(a, 0, 1, Nullable("alpha")))
    new SassColor(space, channel0, channel1, channel2, clampedAlpha, format)
  }

  /** If hue isn't null, normalizes it to the range [0, 360). If invert is true, returns the hue 180deg offset from the original value.
    */
  private def _normalizeHue(hue: Nullable[Double], invert: Boolean): Nullable[Double] =
    if (hue.isEmpty) hue
    else {
      val h = hue.get
      Nullable((h % 360 + 360 + (if (invert) 180 else 0)) % 360)
    }
}

/** A union interface of possible formats in which a Sass color could be defined.
  *
  * When a color is serialized in expanded mode, it should preserve its original format.
  */
sealed trait ColorFormat

object ColorFormat {

  /** A color defined using the rgb() or rgba() functions. */
  case object RgbFunction extends ColorFormat {
    override def toString: String = "rgbFunction"
  }
}

/** A ColorFormat where the color is serialized as the exact same text that was used to specify it originally.
  */
final class SpanColorFormat(private val _original: String) extends ColorFormat {

  /** The original string that was used to define this color in the Sass source. */
  def original: String = _original
}
