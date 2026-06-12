/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file does conversion between units.  In particular, it provides
 * calculateSize to convert other units into ems.
 *
 * Original source: katex src/units.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

/** Minimal trait for the options parameter needed by calculateSize. The full Options class is not yet ported; this trait defines the subset of its API that calculateSize requires.
  */
trait OptionsLike {
  def fontMetrics():                       FontMetrics
  def sizeMultiplier:                      Double
  def maxSize:                             Double
  def style:                               ssg.katex.Style
  def havingStyle(style: ssg.katex.Style): OptionsLike
}

/** This file does conversion between units. In particular, it provides calculateSize to convert other units into ems.
  */
object Units {

  // This table gives the number of TeX pts in one of each *absolute* TeX unit.
  // Thus, multiplying a length by this number converts the length from units
  // into pts.  Dividing the result by ptPerEm gives the number of ems
  // *assuming* a font size of ptPerEm (normal size, normal style).
  private val ptPerUnit: Map[String, Double] = Map(
    // https://en.wikibooks.org/wiki/LaTeX/Lengths and
    // https://tex.stackexchange.com/a/8263
    "pt" -> 1.0, // TeX point
    "mm" -> (7227.0 / 2540.0), // millimeter
    "cm" -> (7227.0 / 254.0), // centimeter
    "in" -> 72.27, // inch
    "bp" -> (803.0 / 800.0), // big (PostScript) points
    "pc" -> 12.0, // pica
    "dd" -> (1238.0 / 1157.0), // didot
    "cc" -> (14856.0 / 1157.0), // cicero (12 didot)
    "nd" -> (685.0 / 642.0), // new didot
    "nc" -> (1370.0 / 107.0), // new cicero (12 new didot)
    "sp" -> (1.0 / 65536.0), // scaled point (TeX's internal smallest unit)
    // https://tex.stackexchange.com/a/41371
    "px" -> (803.0 / 800.0) // \pdfpxdimen defaults to 1 bp in pdfTeX and LuaTeX
  )

  // Dictionary of relative units, for fast validity testing.
  private val relativeUnit: Map[String, Boolean] = Map(
    "ex" -> true,
    "em" -> true,
    "mu" -> true
  )

  /** Determine whether the specified unit (either a string defining the unit or a "size" parse node containing a unit field) is valid.
    */
  def validUnit(unit: String): Boolean =
    ptPerUnit.contains(unit) || relativeUnit.contains(unit) || unit == "ex"

  /** Determine whether the specified unit (either a string defining the unit or a "size" parse node containing a unit field) is valid.
    */
  def validUnit(measurement: Measurement): Boolean =
    validUnit(measurement.unit)

  /*
   * Convert a "size" parse node (with numeric "number" and string "unit" fields,
   * as parsed by functions.js argType "size") into a CSS em value for the
   * current style/scale.  `options` gives the current options.
   */
  def calculateSize(sizeValue: Measurement, options: OptionsLike): Double = {
    val scale: Double =
      if (ptPerUnit.contains(sizeValue.unit)) {
        // Absolute units
        ptPerUnit(sizeValue.unit) / // Convert unit to pt
          options.fontMetrics().ptPerEm / // Convert pt to CSS em
          options.sizeMultiplier // Unscale to make absolute units
      } else if (sizeValue.unit == "mu") {
        // `mu` units scale with scriptstyle/scriptscriptstyle.
        options.fontMetrics().cssEmPerMu
      } else {
        // Other relative units always refer to the *textstyle* font
        // in the current size.
        val unitOptions: OptionsLike =
          if (options.style.isTight()) {
            // isTight() means current style is script/scriptscript.
            options.havingStyle(options.style.text())
          } else {
            options
          }
        // TODO: In TeX these units are relative to the quad of the current
        // *text* font, e.g. cmr10. KaTeX instead uses values from the
        // comparably-sized *Computer Modern symbol* font. At 10pt, these
        // match. At 7pt and 5pt, they differ: cmr7=1.138894, cmsy7=1.170641;
        // cmr5=1.361133, cmsy5=1.472241. Consider $\scriptsize a\kern1emb$.
        // TeX \showlists shows a kern of 1.13889 * fontsize;
        // KaTeX shows a kern of 1.171 * fontsize.
        val baseScale: Double =
          if (sizeValue.unit == "ex") {
            unitOptions.fontMetrics().xHeight
          } else if (sizeValue.unit == "em") {
            unitOptions.fontMetrics().quad
          } else {
            throw new ParseError("Invalid unit: '" + sizeValue.unit + "'")
          }
        if (unitOptions ne options) {
          baseScale * unitOptions.sizeMultiplier / options.sizeMultiplier
        } else {
          baseScale
        }
      }
    Math.min(sizeValue.number * scale, options.maxSize)
  }

  /** Round `n` to 4 decimal places, or to the nearest 1/10,000th em. See https://github.com/KaTeX/KaTeX/pull/2460.
    */
  def makeEm(n: Double): String = {
    // Upstream (units.ts:103): `+n.toFixed(4) + "em"`. toFixed(4) rounds to 4
    // decimal places and the unary `+` round-trip strips trailing zeros, always
    // emitting a "." decimal separator regardless of locale. We reproduce those
    // semantics with pure integer math so the output is locale-independent on
    // every platform (JVM/JS/Native) — never via String.format, which on the JVM
    // follows Locale.getDefault and would emit comma decimals on comma-locale
    // hosts (ISS-1153).
    // Round half-up (matching the original port's Math.round behaviour) to the
    // nearest 1/10,000th, then format the integral and fractional parts by hand
    // with a hardcoded '.', stripping trailing zeros to mirror the `+` round-trip.
    val scaled:    Long          = Math.round(n * 10000.0)
    val negative:  Boolean       = scaled < 0L
    val magnitude: Long          = Math.abs(scaled)
    val integral:  Long          = magnitude / 10000L
    val fraction:  Long          = magnitude % 10000L
    val sb:        StringBuilder = new StringBuilder()
    if (negative && (integral != 0L || fraction != 0L)) {
      sb.append('-')
    }
    sb.append(integral.toString)
    if (fraction != 0L) {
      sb.append('.')
      // Zero-pad the fractional part to 4 digits, then strip trailing zeros so
      // e.g. 0.0500 -> "0.05" and 0.8141 -> "0.8141".
      var frac: String = fraction.toString
      while (frac.length < 4)
        frac = "0" + frac
      var end: Int = frac.length
      while (end > 0 && frac.charAt(end - 1) == '0')
        end -= 1
      sb.append(frac.substring(0, end))
    }
    sb.append("em")
    sb.toString
  }
}
