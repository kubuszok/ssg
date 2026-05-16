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
 *   Convention: Replaces khroma's Channel/Color types with immutable case classes
 *   Idiom: Companion-object parse() replaces khroma's Color.parse()
 *   Renames: khroma → ssg.mermaid.color
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package color

import lowlevel.Nullable

import scala.util.boundary
import scala.util.boundary.break

/** RGBA color with channels in the range [0, 255] for r/g/b and [0.0, 1.0] for alpha. */
final case class RgbaColor(r: Int, g: Int, b: Int, a: Double = 1.0) {

  /** Clamps all channels to valid ranges. */
  def clamped: RgbaColor =
    RgbaColor(
      math.max(0, math.min(255, r)),
      math.max(0, math.min(255, g)),
      math.max(0, math.min(255, b)),
      math.max(0.0, math.min(1.0, a))
    )

  /** Converts this RGBA color to HSLA. */
  def toHsla: HslaColor = {
    val rr = r / 255.0
    val gg = g / 255.0
    val bb = b / 255.0

    val max   = math.max(rr, math.max(gg, bb))
    val min   = math.min(rr, math.min(gg, bb))
    val delta = max - min

    val l = (max + min) / 2.0

    if (delta == 0.0) {
      HslaColor(0.0, 0.0, l * 100.0, a)
    } else {
      val s = if (l < 0.5) delta / (max + min) else delta / (2.0 - max - min)

      val h = if (max == rr) {
        val segment = (gg - bb) / delta
        val shift   = if (segment < 0) 360.0 / 60.0 else 0.0
        segment + shift
      } else if (max == gg) {
        (bb - rr) / delta + 2.0
      } else {
        (rr - gg) / delta + 4.0
      }

      HslaColor(
        ((h * 60.0) % 360.0 + 360.0) % 360.0,
        s * 100.0,
        l * 100.0,
        a
      )
    }
  }

  /** Renders as a CSS color string.
    *
    * Returns `#rrggbb` for fully opaque colors, `rgba(r,g,b,a)` for transparent.
    */
  def toCssString: String = {
    val c = clamped
    if (c.a >= 1.0) {
      f"#${c.r}%02x${c.g}%02x${c.b}%02x"
    } else {
      val aStr = formatAlpha(c.a)
      s"rgba(${c.r}, ${c.g}, ${c.b}, $aStr)"
    }
  }

  /** Renders as `rgba(r,g,b,a)` regardless of alpha value. */
  def toRgbaString: String = {
    val c    = clamped
    val aStr = formatAlpha(c.a)
    s"rgba(${c.r}, ${c.g}, ${c.b}, $aStr)"
  }

  override def toString: String = toCssString

  private def formatAlpha(alpha: Double): String =
    if (alpha == 1.0) "1"
    else if (alpha == 0.0) "0"
    else {
      // Format to at most 2 decimal places, trimming trailing zeros
      val formatted = f"$alpha%.2f"
      formatted.replaceAll("0+$", "").replaceAll("\\.$", "")
    }
}

object RgbaColor {

  /** Black. */
  val Black: RgbaColor = RgbaColor(0, 0, 0)

  /** White. */
  val White: RgbaColor = RgbaColor(255, 255, 255)

  /** Transparent black. */
  val Transparent: RgbaColor = RgbaColor(0, 0, 0, 0.0)
}

/** HSLA color with hue in [0, 360), saturation/lightness in [0, 100], alpha in [0.0, 1.0]. */
final case class HslaColor(h: Double, s: Double, l: Double, a: Double = 1.0) {

  /** Clamps all channels to valid ranges. */
  def clamped: HslaColor =
    HslaColor(
      ((h % 360.0) + 360.0) % 360.0,
      math.max(0.0, math.min(100.0, s)),
      math.max(0.0, math.min(100.0, l)),
      math.max(0.0, math.min(1.0, a))
    )

  /** Converts this HSLA color to RGBA. */
  def toRgba: RgbaColor = {
    val hh = ((h % 360.0) + 360.0) % 360.0
    val ss = math.max(0.0, math.min(100.0, s)) / 100.0
    val ll = math.max(0.0, math.min(100.0, l)) / 100.0

    if (ss == 0.0) {
      val v = math.round(ll * 255.0).toInt
      RgbaColor(v, v, v, a)
    } else {
      val q = if (ll < 0.5) ll * (1.0 + ss) else ll + ss - ll * ss
      val p = 2.0 * ll - q

      def hueToRgb(t0: Double): Int = {
        var t = t0
        if (t < 0.0) t += 1.0
        if (t > 1.0) t -= 1.0
        val v = if (t < 1.0 / 6.0) {
          p + (q - p) * 6.0 * t
        } else if (t < 1.0 / 2.0) {
          q
        } else if (t < 2.0 / 3.0) {
          p + (q - p) * (2.0 / 3.0 - t) * 6.0
        } else {
          p
        }
        math.round(v * 255.0).toInt
      }

      val hNorm = hh / 360.0
      RgbaColor(
        hueToRgb(hNorm + 1.0 / 3.0),
        hueToRgb(hNorm),
        hueToRgb(hNorm - 1.0 / 3.0),
        a
      )
    }
  }

  /** Renders as a CSS hsl/hsla string. */
  def toCssString: String = {
    val c    = clamped
    val hStr = formatNumber(c.h)
    val sStr = formatNumber(c.s)
    val lStr = formatNumber(c.l)
    if (c.a >= 1.0) {
      s"hsl($hStr, $sStr%, $lStr%)"
    } else {
      val aStr = formatAlpha(c.a)
      s"hsla($hStr, $sStr%, $lStr%, $aStr)"
    }
  }

  override def toString: String = toCssString

  private def formatNumber(v: Double): String =
    if (v == v.toLong.toDouble) v.toLong.toString
    else {
      val formatted = f"$v%.2f"
      formatted.replaceAll("0+$", "").replaceAll("\\.$", "")
    }

  private def formatAlpha(alpha: Double): String =
    if (alpha == 1.0) "1"
    else if (alpha == 0.0) "0"
    else {
      val formatted = f"$alpha%.2f"
      formatted.replaceAll("0+$", "").replaceAll("\\.$", "")
    }
}

/** Color parsing utilities.
  *
  * Parses CSS color strings into [[RgbaColor]] values. Supports:
  *   - Hex: `#rgb`, `#rrggbb`, `#rrggbbaa`
  *   - RGB: `rgb(r, g, b)`, `rgba(r, g, b, a)`
  *   - HSL: `hsl(h, s%, l%)`, `hsla(h, s%, l%, a)`
  *   - Named colors (via [[NamedColors]])
  *   - `transparent`
  */
object Color {

  /** Parses a CSS color string into an RgbaColor.
    *
    * @param input
    *   a CSS color string (hex, rgb, rgba, hsl, hsla, or named)
    * @return
    *   the parsed color, or empty if the string cannot be parsed
    */
  def parse(input: String): Nullable[RgbaColor] = {
    val trimmed = input.trim.toLowerCase
    if (trimmed.isEmpty) {
      Nullable.empty
    } else if (trimmed == "transparent") {
      Nullable(RgbaColor.Transparent)
    } else if (trimmed.startsWith("#")) {
      parseHex(trimmed)
    } else if (trimmed.startsWith("rgba(") || trimmed.startsWith("rgba (")) {
      parseRgba(trimmed)
    } else if (trimmed.startsWith("rgb(") || trimmed.startsWith("rgb (")) {
      parseRgb(trimmed)
    } else if (trimmed.startsWith("hsla(") || trimmed.startsWith("hsla (")) {
      parseHsla(trimmed)
    } else if (trimmed.startsWith("hsl(") || trimmed.startsWith("hsl (")) {
      parseHsl(trimmed)
    } else {
      // Try named color lookup
      NamedColors.get(trimmed)
    }
  }

  /** Parses a CSS color string, returning black on failure. */
  def parseOrBlack(input: String): RgbaColor =
    parse(input).getOrElse(RgbaColor.Black)

  /** Parses a hex color string: `#rgb`, `#rrggbb`, `#rgba`, `#rrggbbaa`. */
  private def parseHex(hex: String): Nullable[RgbaColor] = {
    val h = hex.substring(1) // strip leading #
    h.length match {
      case 3 =>
        // #rgb → #rrggbb
        val r = Integer.parseInt(h.substring(0, 1) * 2, 16)
        val g = Integer.parseInt(h.substring(1, 2) * 2, 16)
        val b = Integer.parseInt(h.substring(2, 3) * 2, 16)
        Nullable(RgbaColor(r, g, b))

      case 4 =>
        // #rgba → #rrggbbaa
        val r = Integer.parseInt(h.substring(0, 1) * 2, 16)
        val g = Integer.parseInt(h.substring(1, 2) * 2, 16)
        val b = Integer.parseInt(h.substring(2, 3) * 2, 16)
        val a = Integer.parseInt(h.substring(3, 4) * 2, 16) / 255.0
        Nullable(RgbaColor(r, g, b, a))

      case 6 =>
        // #rrggbb
        val r = Integer.parseInt(h.substring(0, 2), 16)
        val g = Integer.parseInt(h.substring(2, 4), 16)
        val b = Integer.parseInt(h.substring(4, 6), 16)
        Nullable(RgbaColor(r, g, b))

      case 8 =>
        // #rrggbbaa
        val r = Integer.parseInt(h.substring(0, 2), 16)
        val g = Integer.parseInt(h.substring(2, 4), 16)
        val b = Integer.parseInt(h.substring(4, 6), 16)
        val a = Integer.parseInt(h.substring(6, 8), 16) / 255.0
        Nullable(RgbaColor(r, g, b, a))

      case _ =>
        Nullable.empty
    }
  }

  /** Parses `rgb(r, g, b)`. */
  private def parseRgb(input: String): Nullable[RgbaColor] =
    boundary[Nullable[RgbaColor]] {
      val inner = extractParens(input)
      if (inner.isEmpty) break(Nullable.empty)
      val parts = inner.get.split("[,/\\s]+").map(_.trim).filter(_.nonEmpty)
      if (parts.length < 3) break(Nullable.empty)
      try {
        val r = parseChannelValue(parts(0), 255)
        val g = parseChannelValue(parts(1), 255)
        val b = parseChannelValue(parts(2), 255)
        Nullable(RgbaColor(r, g, b))
      } catch {
        case _: NumberFormatException => Nullable.empty
      }
    }

  /** Parses `rgba(r, g, b, a)`. */
  private def parseRgba(input: String): Nullable[RgbaColor] =
    boundary[Nullable[RgbaColor]] {
      val inner = extractParens(input)
      if (inner.isEmpty) break(Nullable.empty)
      val parts = inner.get.split("[,/\\s]+").map(_.trim).filter(_.nonEmpty)
      if (parts.length < 4) break(Nullable.empty)
      try {
        val r = parseChannelValue(parts(0), 255)
        val g = parseChannelValue(parts(1), 255)
        val b = parseChannelValue(parts(2), 255)
        val a = parseAlphaValue(parts(3))
        Nullable(RgbaColor(r, g, b, a))
      } catch {
        case _: NumberFormatException => Nullable.empty
      }
    }

  /** Parses `hsl(h, s%, l%)`. */
  private def parseHsl(input: String): Nullable[RgbaColor] =
    boundary[Nullable[RgbaColor]] {
      val inner = extractParens(input)
      if (inner.isEmpty) break(Nullable.empty)
      val parts = inner.get.split("[,/\\s]+").map(_.trim).filter(_.nonEmpty)
      if (parts.length < 3) break(Nullable.empty)
      try {
        val h = parts(0).replaceAll("deg$", "").toDouble
        val s = parts(1).replaceAll("%$", "").toDouble
        val l = parts(2).replaceAll("%$", "").toDouble
        Nullable(HslaColor(h, s, l).toRgba)
      } catch {
        case _: NumberFormatException => Nullable.empty
      }
    }

  /** Parses `hsla(h, s%, l%, a)`. */
  private def parseHsla(input: String): Nullable[RgbaColor] =
    boundary[Nullable[RgbaColor]] {
      val inner = extractParens(input)
      if (inner.isEmpty) break(Nullable.empty)
      val parts = inner.get.split("[,/\\s]+").map(_.trim).filter(_.nonEmpty)
      if (parts.length < 4) break(Nullable.empty)
      try {
        val h = parts(0).replaceAll("deg$", "").toDouble
        val s = parts(1).replaceAll("%$", "").toDouble
        val l = parts(2).replaceAll("%$", "").toDouble
        val a = parseAlphaValue(parts(3))
        Nullable(HslaColor(h, s, l, a).toRgba)
      } catch {
        case _: NumberFormatException => Nullable.empty
      }
    }

  /** Extracts the content between the first `(` and last `)` in the input. */
  private def extractParens(input: String): Nullable[String] = {
    val start = input.indexOf('(')
    val end   = input.lastIndexOf(')')
    if (start >= 0 && end > start) {
      Nullable(input.substring(start + 1, end))
    } else {
      Nullable.empty
    }
  }

  /** Parses a channel value which may be a percentage or an integer. */
  private def parseChannelValue(s: String, max: Int): Int =
    if (s.endsWith("%")) {
      val pct = s.dropRight(1).toDouble
      math.round(pct / 100.0 * max).toInt
    } else {
      math.round(s.toDouble).toInt
    }

  /** Parses an alpha value which may be a percentage or a decimal. */
  private def parseAlphaValue(s: String): Double =
    if (s.endsWith("%")) {
      s.dropRight(1).toDouble / 100.0
    } else {
      s.toDouble
    }
}
