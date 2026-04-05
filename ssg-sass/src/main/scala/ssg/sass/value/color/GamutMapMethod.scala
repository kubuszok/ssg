/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/color/gamut_map_method.dart,
 *              lib/src/value/color/gamut_map_method/clip.dart,
 *              lib/src/value/color/gamut_map_method/local_minde.dart
 * Original: Copyright (c) 2024 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: gamut_map_method.dart + clip.dart + local_minde.dart → GamutMapMethod.scala
 *   Convention: Dart sealed abstract base class + subclasses → Scala abstract class + objects
 *   Idiom: Dart math → scala.math; Dart double? → Nullable[Double]
 *   Audited: 2026-04-06
 */
package ssg
package sass
package value
package color

import ssg.sass.{SassScriptException, Nullable}
import ssg.sass.Nullable.*
import ssg.sass.util.NumberUtil.*

import scala.util.boundary
import scala.util.boundary.break

/** Different algorithms that can be used to map an out-of-gamut Sass color
  * into the gamut for its color space.
  */
abstract class GamutMapMethod(
  /** The Sass name of the gamut-mapping algorithm. */
  val name: String
) {

  /** Maps color to its gamut using this method's algorithm.
    * Callers should use SassColor.toGamut instead.
    */
  def map(color: SassColor): SassColor

  override def toString: String = name
}

object GamutMapMethod {
  /** Clamp each color channel to the minimum or maximum value. */
  val clip: GamutMapMethod = ClipGamutMap

  /** The CSS Color 4 local-MINDE algorithm. */
  val localMinde: GamutMapMethod = LocalMindeGamutMap

  /** Parses a GamutMapMethod from its Sass name. */
  def fromName(name: String, argumentName: Option[String] = None): GamutMapMethod = {
    name match {
      case "clip" => clip
      case "local-minde" => localMinde
      case _ =>
        throw SassScriptException(s"Unknown gamut map method \"$name\".", argumentName)
    }
  }
}

// ---- Clip ----

/** Gamut mapping by clipping individual channels. */
private object ClipGamutMap extends GamutMapMethod("clip") {

  override def map(color: SassColor): SassColor = {
    SassColor.forSpaceInternal(
      color.space,
      _clampChannel(color.channel0OrNull, color.space.channels(0)),
      _clampChannel(color.channel1OrNull, color.space.channels(1)),
      _clampChannel(color.channel2OrNull, color.space.channels(2)),
      color.alphaOrNull
    )
  }

  /** Clamps the channel value within the bounds given by channel. */
  private def _clampChannel(value: Nullable[Double], channel: ColorChannel): Nullable[Double] = {
    if (value.isEmpty) Nullable.Null
    else {
      channel match {
        case lc: LinearChannel => Nullable(clampLikeCss(value.get, lc.min, lc.max))
        case _ => value
      }
    }
  }
}

// ---- Local MINDE ----

/** Gamut mapping using the deltaEOK difference formula and the local-MINDE improvement. */
private object LocalMindeGamutMap extends GamutMapMethod("local-minde") {

  /** A constant from the gamut-mapping algorithm. */
  private val _jnd = 0.02

  /** A constant from the gamut-mapping algorithm. */
  private val _epsilon = 0.0001

  override def map(color: SassColor): SassColor = boundary {
    // Algorithm from https://www.w3.org/TR/2022/CRD-css-color-4-20221101/#css-gamut-mapping-algorithm
    val originOklch = color.toSpace(ColorSpace.oklch)

    val lightness = originOklch.channel0OrNull
    val hue = originOklch.channel2OrNull
    val alpha = originOklch.alphaOrNull

    if (fuzzyGreaterThanOrEquals(lightness.getOrElse(0.0), 1)) {
      if (color.isLegacy) {
        break(SassColor.rgb(Nullable(255.0), Nullable(255.0), Nullable(255.0), color.alphaOrNull)
          .toSpace(color.space))
      } else {
        break(SassColor.forSpaceInternal(color.space, Nullable(1.0), Nullable(1.0), Nullable(1.0), color.alphaOrNull))
      }
    }
    if (fuzzyLessThanOrEquals(lightness.getOrElse(0.0), 0)) {
      break(SassColor.rgb(Nullable(0.0), Nullable(0.0), Nullable(0.0), color.alphaOrNull).toSpace(color.space))
    }

    var clipped = color.toGamut(GamutMapMethod.clip)
    if (_deltaEOK(clipped, color) < _jnd) {
      break(clipped)
    }

    var min = 0.0
    var max = originOklch.channel1
    var minInGamut = true
    while (max - min > _epsilon) {
      val chroma = (min + max) / 2

      val current = ColorSpace.oklch.convert(
        color.space,
        lightness,
        Nullable(chroma),
        hue,
        alpha
      )

      if (minInGamut && current.isInGamut) {
        min = chroma
      } else {
        clipped = current.toGamut(GamutMapMethod.clip)
        val e = _deltaEOK(clipped, current)
        if (e < _jnd) {
          if (_jnd - e < _epsilon) {
            break(clipped)
          }
          minInGamut = false
          min = chroma
        } else {
          max = chroma
        }
      }
    }
    clipped
  }

  /** Returns the deltaEOK measure between color1 and color2. */
  private def _deltaEOK(color1: SassColor, color2: SassColor): Double = {
    // Algorithm from https://www.w3.org/TR/css-color-4/#color-difference-OK
    val lab1 = color1.toSpace(ColorSpace.oklab)
    val lab2 = color2.toSpace(ColorSpace.oklab)

    math.sqrt(
      math.pow(lab1.channel0 - lab2.channel0, 2) +
        math.pow(lab1.channel1 - lab2.channel1, 2) +
        math.pow(lab1.channel2 - lab2.channel2, 2)
    )
  }
}
