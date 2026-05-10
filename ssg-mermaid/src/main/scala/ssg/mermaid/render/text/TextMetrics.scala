/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid (server-side text measurement approximation)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces browser DOM Canvas.measureText() with pre-computed character width tables
 *   Idiom: Pure functions with no side effects; immutable BBox return values
 *   Renames: N/A — new server-side implementation
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package text

import ssg.mermaid.svg.BBox

import scala.util.boundary
import scala.util.boundary.break

/** Server-side text dimension estimation.
  *
  * Browser-based Mermaid uses `Canvas.measureText()` or DOM layout to compute precise text dimensions. For server-side rendering, we estimate text dimensions using pre-computed average character
  * widths for common font families.
  *
  * This approximation is typically within 5-10% of actual browser measurements for Latin text. CJK and other wide character sets will have larger variance.
  */
object TextMetrics {

  /** Average character width as a fraction of font size, keyed by font family keyword.
    *
    * These values are empirically measured averages for common ASCII characters (a-z, A-Z, 0-9). Proportional fonts vary more per character; these are midpoint estimates.
    */
  private val CharWidthFactors: Map[String, Double] = Map(
    "arial" -> 0.55,
    "helvetica" -> 0.55,
    "sans-serif" -> 0.55,
    "verdana" -> 0.58,
    "trebuchet" -> 0.52,
    "trebuchet ms" -> 0.52,
    "tahoma" -> 0.55,
    "georgia" -> 0.56,
    "times" -> 0.51,
    "times new roman" -> 0.51,
    "serif" -> 0.51,
    "courier" -> 0.60,
    "courier new" -> 0.60,
    "monospace" -> 0.60,
    "lucida console" -> 0.60,
    "consolas" -> 0.55
  )

  /** Default character width factor when the font family is not recognized. */
  private val DefaultCharWidthFactor: Double = 0.55

  /** Line height as a multiple of font size. */
  private val LineHeightFactor: Double = 1.2

  /** Font weight multiplier for bold text. Bold text is typically ~5-10% wider. */
  private val BoldWidthMultiplier: Double = 1.07

  /** Measures text dimensions, producing a bounding box.
    *
    * Handles multi-line text (split on `\n`). The returned BBox has x=0, y=0, and width/height representing the total text extent.
    *
    * @param text
    *   the text to measure (may contain newlines)
    * @param fontSize
    *   font size in pixels
    * @param fontFamily
    *   CSS font-family value
    * @param fontWeight
    *   CSS font-weight value (e.g. "normal", "bold", "700")
    * @return
    *   estimated bounding box with x=0, y=0
    */
  def measureText(text: String, fontSize: Double, fontFamily: String, fontWeight: String = "normal"): BBox =
    if (text.isEmpty) {
      BBox.Empty
    } else {
      val lines        = text.split("\n", -1)
      val widthFactor  = resolveWidthFactor(fontFamily)
      val weightFactor = resolveWeightFactor(fontWeight)
      val charWidth    = fontSize * widthFactor * weightFactor
      val lineHeight   = fontSize * LineHeightFactor

      var maxWidth = 0.0
      var i        = 0
      while (i < lines.length) {
        val lineWidth = measureLine(lines(i), charWidth)
        if (lineWidth > maxWidth) {
          maxWidth = lineWidth
        }
        i += 1
      }

      val totalHeight = lines.length * lineHeight
      BBox(0.0, 0.0, maxWidth, totalHeight)
    }

  /** Measures a single line of text using the given average character width.
    *
    * Accounts for character-specific width variations: narrow characters (i, l, 1) are ~60% of average width; wide characters (m, w, M, W) are ~130%.
    */
  private def measureLine(line: String, avgCharWidth: Double): Double = {
    var width = 0.0
    var i     = 0
    while (i < line.length) {
      val c = line.charAt(i)
      width += charWidthMultiplier(c) * avgCharWidth
      i += 1
    }
    width
  }

  /** Returns a width multiplier for a specific character relative to the average character width.
    *
    * Narrow characters get a smaller multiplier, wide characters get a larger one.
    */
  private def charWidthMultiplier(c: Char): Double =
    c match {
      // Narrow characters
      case 'i' | 'j' | 'l' | '!' | '|' | '.' | ',' | ';' | ':' | '\'' | '`' => 0.50
      case 'I'                                                              => 0.55
      case 'f' | 'r' | 't'                                                  => 0.65
      case '1'                                                              => 0.65
      case ' '                                                              => 0.50

      // Wide characters
      case 'm' | 'w'             => 1.30
      case 'M' | 'W'             => 1.35
      case '@'                   => 1.40
      case 'A' | 'G' | 'O' | 'Q' => 1.15
      case 'D' | 'H' | 'N' | 'U' => 1.10

      // Tab
      case '\t' => 4.0

      // CJK characters (U+2E80 to U+9FFF) — approximately double width
      case c if c >= '⺀' && c <= '鿿' => 2.0

      // Default: average width
      case _ => 1.0
    }

  /** Resolves the character width factor for a font family string.
    *
    * Parses the CSS font-family value (which may be a comma-separated list) and returns the width factor for the first recognized font.
    */
  private def resolveWidthFactor(fontFamily: String): Double =
    boundary[Double] {
      val normalized = fontFamily.toLowerCase.replaceAll("\"", "").replaceAll("'", "")
      val families   = normalized.split(",").map(_.trim)

      var i = 0
      while (i < families.length) {
        CharWidthFactors.get(families(i)).foreach { factor =>
          break(factor)
        }
        i += 1
      }
      DefaultCharWidthFactor
    }

  /** Resolves a weight multiplier from a CSS font-weight value.
    *
    * Bold (700+) and bolder weights get a slight width increase since bold glyphs are wider.
    */
  private def resolveWeightFactor(fontWeight: String): Double = {
    val w = fontWeight.trim.toLowerCase
    if (w == "bold" || w == "bolder") {
      BoldWidthMultiplier
    } else {
      try {
        val numeric = w.toInt
        if (numeric >= 700) BoldWidthMultiplier else 1.0
      } catch {
        case _: NumberFormatException => 1.0
      }
    }
  }

  /** Estimates the width of a single line of text with default font settings. */
  def estimateWidth(text: String, fontSize: Double = 16.0): Double =
    measureText(text, fontSize, "sans-serif").width

  /** Estimates the height of text (handling multi-line) with default font settings. */
  def estimateHeight(text: String, fontSize: Double = 16.0): Double =
    measureText(text, fontSize, "sans-serif").height
}
