/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: khroma (npm package used by mermaid for color manipulation)
 * Original: Copyright (c) Fabio Spampinato
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces khroma's top-level functions with object methods
 *   Idiom: Pure functions operating on immutable case classes
 *   Renames: khroma → ssg.mermaid.color
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package color

/** Options for the [[ColorOps.adjust]] function.
  *
  * Each field is an optional delta to apply to the corresponding HSLA channel. Fields left at 0.0 are not modified.
  *
  * @param h
  *   hue adjustment in degrees
  * @param s
  *   saturation adjustment in percentage points
  * @param l
  *   lightness adjustment in percentage points
  * @param a
  *   alpha adjustment (added to existing alpha)
  */
final case class AdjustOptions(
  h: Double = 0.0,
  s: Double = 0.0,
  l: Double = 0.0,
  a: Double = 0.0
)

/** Color manipulation operations matching the khroma npm package API.
  *
  * All functions accept [[RgbaColor]] and return [[RgbaColor]]. Internally, operations that modify hue, saturation, or lightness convert to HSLA, apply the change, and convert back.
  */
object ColorOps {

  /** Increases the lightness of a color by the given amount (percentage points, 0-100). */
  def lighten(color: RgbaColor, amount: Double): RgbaColor = {
    val hsl = color.toHsla
    HslaColor(hsl.h, hsl.s, math.min(100.0, hsl.l + amount), hsl.a).toRgba
  }

  /** Decreases the lightness of a color by the given amount (percentage points, 0-100). */
  def darken(color: RgbaColor, amount: Double): RgbaColor = {
    val hsl = color.toHsla
    HslaColor(hsl.h, hsl.s, math.max(0.0, hsl.l - amount), hsl.a).toRgba
  }

  /** Increases the saturation of a color by the given amount (percentage points, 0-100). */
  def saturate(color: RgbaColor, amount: Double): RgbaColor = {
    val hsl = color.toHsla
    HslaColor(hsl.h, math.min(100.0, hsl.s + amount), hsl.l, hsl.a).toRgba
  }

  /** Decreases the saturation of a color by the given amount (percentage points, 0-100). */
  def desaturate(color: RgbaColor, amount: Double): RgbaColor = {
    val hsl = color.toHsla
    HslaColor(hsl.h, math.max(0.0, hsl.s - amount), hsl.l, hsl.a).toRgba
  }

  /** Adjusts a color's HSLA channels by the deltas specified in `options`.
    *
    * This mirrors khroma's `adjust(color, {h, s, l})` used extensively by mermaid themes.
    */
  def adjust(color: RgbaColor, options: AdjustOptions): RgbaColor = {
    val hsl = color.toHsla
    HslaColor(
      hsl.h + options.h,
      math.max(0.0, math.min(100.0, hsl.s + options.s)),
      math.max(0.0, math.min(100.0, hsl.l + options.l)),
      math.max(0.0, math.min(1.0, hsl.a + options.a))
    ).toRgba
  }

  /** Mixes two colors together.
    *
    * @param color1
    *   the first color
    * @param color2
    *   the second color
    * @param weight
    *   the weight of the first color (0.0 = all color2, 1.0 = all color1). Default is 0.5.
    * @return
    *   the mixed color
    */
  def mix(color1: RgbaColor, color2: RgbaColor, weight: Double = 0.5): RgbaColor = {
    // Sass-style mixing algorithm (same as khroma)
    val w  = weight * 2.0 - 1.0
    val da = color1.a - color2.a

    val w1 = {
      val combined = w * da
      val w1Raw    = if (combined == -1.0) w else (w + da) / (1.0 + combined)
      (w1Raw + 1.0) / 2.0
    }
    val w2 = 1.0 - w1

    RgbaColor(
      math.round(color1.r * w1 + color2.r * w2).toInt,
      math.round(color1.g * w1 + color2.g * w2).toInt,
      math.round(color1.b * w1 + color2.b * w2).toInt,
      color1.a * weight + color2.a * (1.0 - weight)
    )
  }

  /** Returns the inverse (complement) of a color. */
  def invert(color: RgbaColor): RgbaColor =
    RgbaColor(255 - color.r, 255 - color.g, 255 - color.b, color.a)

  /** Computes the relative luminance of a color per WCAG 2.0.
    *
    * @return
    *   relative luminance in the range [0.0, 1.0] where 0 is black and 1 is white
    */
  def luminance(color: RgbaColor): Double = {
    // sRGB to linear conversion
    def linearize(channel: Int): Double = {
      val c = channel / 255.0
      if (c <= 0.03928) c / 12.92
      else math.pow((c + 0.055) / 1.055, 2.4)
    }

    val rLinear = linearize(color.r)
    val gLinear = linearize(color.g)
    val bLinear = linearize(color.b)

    0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
  }

  /** Computes the WCAG 2.0 contrast ratio between two colors.
    *
    * @return
    *   contrast ratio in the range [1.0, 21.0]
    */
  def contrast(color1: RgbaColor, color2: RgbaColor): Double = {
    val l1      = luminance(color1)
    val l2      = luminance(color2)
    val lighter = math.max(l1, l2)
    val darker  = math.min(l1, l2)
    (lighter + 0.05) / (darker + 0.05)
  }

  /** Returns true if the color is considered "dark" (luminance < 0.5). */
  def isDark(color: RgbaColor): Boolean =
    luminance(color) < 0.5

  /** Returns true if the color is considered "light" (luminance >= 0.5). */
  def isLight(color: RgbaColor): Boolean =
    luminance(color) >= 0.5

  /** Returns a new color with the specified alpha value. */
  def opacity(color: RgbaColor, alpha: Double): RgbaColor =
    color.copy(a = math.max(0.0, math.min(1.0, alpha)))

  /** Converts a color to grayscale by fully desaturating it. */
  def grayscale(color: RgbaColor): RgbaColor =
    desaturate(color, 100.0)

  /** Returns the complement of a color (hue rotated by 180 degrees). */
  def complement(color: RgbaColor): RgbaColor =
    adjust(color, AdjustOptions(h = 180.0))

  /** Convenience for string-based color operations: parse, apply operation, return string. */
  def lighten(cssColor: String, amount: Double): String =
    lighten(Color.parseOrBlack(cssColor), amount).toCssString

  /** Convenience for string-based darken. */
  def darken(cssColor: String, amount: Double): String =
    darken(Color.parseOrBlack(cssColor), amount).toCssString

  /** Convenience for string-based adjust. */
  def adjust(cssColor: String, options: AdjustOptions): String =
    adjust(Color.parseOrBlack(cssColor), options).toCssString

  /** Convenience for string-based invert. */
  def invert(cssColor: String): String =
    invert(Color.parseOrBlack(cssColor)).toCssString

  /** Convenience for string-based isDark. */
  def isDark(cssColor: String): Boolean =
    isDark(Color.parseOrBlack(cssColor))

  /** Convenience for string-based mix. */
  def mix(cssColor1: String, cssColor2: String, weight: Double): String =
    mix(Color.parseOrBlack(cssColor1), Color.parseOrBlack(cssColor2), weight).toCssString
}
