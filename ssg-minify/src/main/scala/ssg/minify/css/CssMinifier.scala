/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * CSS minification — removes comments, collapses whitespace, shortens values.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (cssminify2 gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: cssminify2 gem → ssg.minify.css.CssMinifier
 *   Convention: Pure Scala 3, regex-pipeline based, cross-platform
 *   Idiom: Stateless pure functions, no external dependencies
 *   Gap: No rgb()/rgba()/named-color → hex folding, no margin/padding/font
 *     shorthand collapsing, no vendor-prefix culling, no source maps (ISS-041).
 *     Core whitespace/comment/zero/hex passes are complete.
 *     See docs/architecture/jekyll-minifier-port.md.
 *   Audited: 2026-04-07 (minor_issues)
 */
package ssg
package minify
package css

import scala.util.boundary
import scala.util.boundary.break

object CssMinifier {

  /** Minify CSS content by removing comments, collapsing whitespace, and optimizing values. */
  def minify(input: String, options: CssMinifyOptions = CssMinifyOptions.Defaults): String =
    if (input.isEmpty) {
      input
    } else {
      var result = input
      if (options.removeComments) result = removeComments(result)
      if (options.collapseWhitespace) result = collapseWhitespace(result)
      if (options.removeTrailingSemicolons) result = removeTrailingSemicolons(result)
      if (options.removeEmptyRules) result = removeEmptyRules(result)
      if (options.shortenColors) {
        result = shortenColors(result)
        result = foldRgbToHex(result)
        result = foldNamedColors(result)
      }
      if (options.collapseZeros) result = collapseZeros(result)
      result.trim
    }

  // -- Comment removal --

  /** Remove CSS block comments, preserving content inside strings and important comments (`/*! ... */`). */
  private def removeComments(css: String): String = {
    val len = css.length
    val sb  = new StringBuilder(len)
    var i   = 0

    while (i < len) {
      val c = css.charAt(i)
      if (c == '/' && i + 1 < len && css.charAt(i + 1) == '*') {
        val isBangComment = i + 2 < len && css.charAt(i + 2) == '!'
        if (isBangComment) {
          // Preserve /*! ... */ important/license comments
          sb.append('/')
          sb.append('*')
          i += 2
          while (i + 1 < len && !(css.charAt(i) == '*' && css.charAt(i + 1) == '/')) {
            sb.append(css.charAt(i))
            i += 1
          }
          if (i + 1 < len) {
            sb.append('*')
            sb.append('/')
            i += 2
          }
        } else {
          // Regular block comment — skip to */
          i += 2
          while (i + 1 < len && !(css.charAt(i) == '*' && css.charAt(i + 1) == '/'))
            i += 1
          if (i + 1 < len) i += 2
        }
      } else if (c == '\'' || c == '"') {
        i = copyStringLiteral(css, i, len, sb)
      } else {
        sb.append(c)
        i += 1
      }
    }

    sb.toString()
  }

  // -- Whitespace collapsing --

  private val WhitespaceRun         = "\\s+".r
  private val SpaceAroundBrace      = "\\s*([{}])\\s*".r
  private val SpaceAroundColon      = "\\s*:\\s*".r
  private val SpaceAroundSemicolon  = "\\s*;\\s*".r
  private val SpaceAroundComma      = "\\s*,\\s*".r
  private val SpaceAfterOpenParen   = "\\(\\s+".r
  private val SpaceBeforeCloseParen = "\\s+\\)".r

  private def collapseWhitespace(css: String): String = {
    val (cleaned, preserved) = extractPreserved(css)

    var result = cleaned
    result = WhitespaceRun.replaceAllIn(result, " ")
    result = SpaceAroundBrace.replaceAllIn(result, "$1")
    result = SpaceAroundColon.replaceAllIn(result, ":")
    result = SpaceAroundSemicolon.replaceAllIn(result, ";")
    result = SpaceAroundComma.replaceAllIn(result, ",")
    result = SpaceAfterOpenParen.replaceAllIn(result, "(")
    result = SpaceBeforeCloseParen.replaceAllIn(result, ")")

    restorePreserved(result, preserved)
  }

  // -- Trailing semicolons --

  private val TrailingSemicolon = ";\\}".r

  private def removeTrailingSemicolons(css: String): String =
    TrailingSemicolon.replaceAllIn(css, "}")

  // -- Empty rules --

  private val EmptyRule = "[^{}]+\\{\\}".r

  private def removeEmptyRules(css: String): String = {
    var result = css
    var prev   = ""
    while (result != prev) {
      prev = result
      result = EmptyRule.replaceAllIn(result, "")
    }
    result
  }

  // -- Color shortening --
  // Backreferences (\1) are not supported on Scala Native's re2 engine,
  // so we match all 6-digit hex colors and check for shortening programmatically.

  private val SixDigitHex = "#([0-9a-fA-F]{6})".r

  private def shortenColors(css: String): String =
    SixDigitHex.replaceAllIn(
      css,
      m => {
        val hex = m.group(1)
        if (hex.charAt(0) == hex.charAt(1) && hex.charAt(2) == hex.charAt(3) && hex.charAt(4) == hex.charAt(5)) {
          s"#${hex.charAt(0)}${hex.charAt(2)}${hex.charAt(4)}"
        } else {
          m.matched
        }
      }
    )

  // -- rgb()/rgba() to hex --

  private val RgbPattern  = "rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)".r
  private val RgbaPattern = "rgba\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*1(?:\\.0*)?\\s*\\)".r

  private def foldRgbToHex(css: String): String = {
    var result = RgbaPattern.replaceAllIn(css, m => rgbToHex(m.group(1).toInt, m.group(2).toInt, m.group(3).toInt))
    result = RgbPattern.replaceAllIn(result, m => rgbToHex(m.group(1).toInt, m.group(2).toInt, m.group(3).toInt))
    result
  }

  private def rgbToHex(r: Int, g: Int, b: Int): String = {
    if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
      s"rgb($r,$g,$b)"
    } else {
      val hex = f"#$r%02x$g%02x$b%02x"
      // Shorten if possible: #aabbcc → #abc
      if (hex.charAt(1) == hex.charAt(2) && hex.charAt(3) == hex.charAt(4) && hex.charAt(5) == hex.charAt(6)) {
        s"#${hex.charAt(1)}${hex.charAt(3)}${hex.charAt(5)}"
      } else {
        hex
      }
    }
  }

  // -- Named color → hex (shorter alternatives only) --

  private val NamedColorMap: Map[String, String] = Map(
    "white"                -> "#fff",
    "black"                -> "#000",
    "red"                  -> "red", // already 3 chars, #f00 is same length
    "fuchsia"              -> "#f0f",
    "magenta"              -> "#f0f",
    "yellow"               -> "#ff0",
    "cyan"                 -> "#0ff",
    "aqua"                 -> "#0ff",
    "darkblue"             -> "#00008b",
    "darkgreen"            -> "#006400",
    "darkred"              -> "#8b0000",
    "darkcyan"             -> "#008b8b",
    "darkmagenta"          -> "#8b008b",
    "cornsilk"             -> "#fff8dc",
    "bisque"               -> "#ffe4c4",
    "azure"                -> "#f0ffff",
    "beige"                -> "#f5f5dc",
    "coral"                -> "#ff7f50",
    "ivory"                -> "#fffff0",
    "khaki"                -> "#f0e68c",
    "linen"                -> "#faf0e6",
    "orchid"               -> "#da70d6",
    "plum"                 -> "#dda0dd",
    "salmon"               -> "#fa8072",
    "sienna"               -> "#a0522d",
    "silver"               -> "#c0c0c0",
    "tomato"               -> "#ff6347",
    "violet"               -> "#ee82ee",
    "wheat"                -> "#f5deb3"
  )

  private def foldNamedColors(css: String): String = {
    // Only fold named colors in value positions (after : and before ; or })
    // Use a simple word-boundary approach
    var result = css
    for ((name, hex) <- NamedColorMap) {
      // Only replace if hex is shorter than name
      if (hex.length < name.length) {
        // Case-insensitive replacement in value context
        val pattern = java.util.regex.Pattern.compile(
          "(?<=[:\\s,])" + java.util.regex.Pattern.quote(name) + "(?=[;\\s},!])",
          java.util.regex.Pattern.CASE_INSENSITIVE
        )
        result = pattern.matcher(result).replaceAll(hex)
      }
    }
    result
  }

  // -- Zero collapsing --

  private val UnitList = Array("vmin", "vmax", "rem", "px", "em", "pt", "pc", "in", "cm", "mm", "ex", "ch", "vw", "vh", "%")

  private def collapseZeros(css: String): String = {
    val len = css.length
    val sb  = new StringBuilder(len)
    var i   = 0

    while (i < len) {
      val c = css.charAt(i)
      if (c == '0' && !precededByDigitOrDot(css, i)) {
        val unitLen = matchUnit(css, i + 1, len)
        if (unitLen > 0) {
          sb.append('0')
          i += 1 + unitLen
        } else {
          sb.append(c)
          i += 1
        }
      } else {
        sb.append(c)
        i += 1
      }
    }

    sb.toString()
  }

  private def precededByDigitOrDot(css: String, pos: Int): Boolean =
    if (pos <= 0) {
      false
    } else {
      val prev = css.charAt(pos - 1)
      prev.isDigit || prev == '.'
    }

  private def matchUnit(css: String, start: Int, len: Int): Int =
    boundary[Int] {
      var j = 0
      while (j < UnitList.length) {
        val unit = UnitList(j)
        if (start + unit.length <= len && css.regionMatches(start, unit, 0, unit.length)) {
          val afterUnit = start + unit.length
          if (afterUnit >= len || !css.charAt(afterUnit).isLetterOrDigit) {
            break(unit.length)
          }
        }
        j += 1
      }
      0
    }

  // -- String/url preservation utilities --

  private val PlaceholderPrefix = "\u0000SSG_CSS_"

  /** Extract quoted strings and url() values, replacing them with placeholders. */
  private def extractPreserved(css: String): (String, Array[String]) = {
    val preserved = scala.collection.mutable.ArrayBuffer[String]()
    val len       = css.length
    val sb        = new StringBuilder(len)
    var i         = 0

    while (i < len) {
      val c = css.charAt(i)
      if (c == '\'' || c == '"') {
        val start = i
        i = skipStringLiteral(css, i, len)
        val literal = css.substring(start, i)
        val idx     = preserved.size
        preserved += literal
        sb.append(PlaceholderPrefix)
        sb.append(idx)
        sb.append('\u0000')
      } else if (c == 'u' && i + 3 < len && css.charAt(i + 1) == 'r' && css.charAt(i + 2) == 'l' && css.charAt(i + 3) == '(') {
        val start = i
        i += 4
        var depth = 1
        while (i < len && depth > 0) {
          val uc = css.charAt(i)
          if (uc == '(') depth += 1
          else if (uc == ')') depth -= 1
          if (depth > 0) i += 1
        }
        if (i < len) i += 1
        val literal = css.substring(start, i)
        val idx     = preserved.size
        preserved += literal
        sb.append(PlaceholderPrefix)
        sb.append(idx)
        sb.append('\u0000')
      } else {
        sb.append(c)
        i += 1
      }
    }

    (sb.toString(), preserved.toArray)
  }

  /** Restore placeholders with their original content. */
  private def restorePreserved(css: String, preserved: Array[String]): String =
    if (preserved.isEmpty) {
      css
    } else {
      val sb  = new StringBuilder(css.length)
      var i   = 0
      val len = css.length
      while (i < len)
        if (css.charAt(i) == '\u0000' && css.regionMatches(i, PlaceholderPrefix, 0, PlaceholderPrefix.length)) {
          i += PlaceholderPrefix.length
          val numStart = i
          while (i < len && css.charAt(i).isDigit)
            i += 1
          val idx = css.substring(numStart, i).toInt
          if (i < len && css.charAt(i) == '\u0000') i += 1
          sb.append(preserved(idx))
        } else {
          sb.append(css.charAt(i))
          i += 1
        }
      sb.toString()
    }

  /** Copy a string literal verbatim to sb. Returns index after closing quote. */
  private def copyStringLiteral(css: String, start: Int, len: Int, sb: StringBuilder): Int = {
    val quote = css.charAt(start)
    sb.append(quote)
    var i = start + 1
    boundary {
      while (i < len) {
        val c = css.charAt(i)
        sb.append(c)
        if (c == '\\' && i + 1 < len) {
          i += 1
          sb.append(css.charAt(i))
        } else if (c == quote) {
          i += 1
          break()
        }
        i += 1
      }
    }
    i
  }

  /** Skip past a string literal. Returns index after closing quote. */
  private def skipStringLiteral(css: String, start: Int, len: Int): Int = {
    val quote = css.charAt(start)
    var i     = start + 1
    boundary {
      while (i < len) {
        val c = css.charAt(i)
        if (c == '\\' && i + 1 < len) {
          i += 2
        } else if (c == quote) {
          i += 1
          break()
        } else {
          i += 1
        }
      }
    }
    i
  }
}
