/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/common/common.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces DOMPurify-based sanitization with server-safe string operations
 *   Idiom: Pure functions for text processing; no DOM dependency
 *   Renames: common.sanitizeText → TextUtils.sanitizeText
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package text

import scala.collection.mutable.ArrayBuffer

/** Text processing utilities for Mermaid diagram rendering.
  *
  * Provides sanitization, wrapping, and HTML entity decoding without requiring a browser DOM. These are server-safe equivalents of the browser-side text utilities in `common.ts`.
  */
object TextUtils {

  /** Regex for HTML `<br>` tags in various forms: `<br>`, `<br/>`, `<br />`. */
  private val LineBreakRegex = """<br\s*/?>""".r

  /** Placeholder for `<br>` tags during processing. */
  private val BreakPlaceholder = "#br#"

  /** Common HTML entities mapped to their character equivalents. */
  private val HtmlEntities: Map[String, String] = Map(
    "&amp;" -> "&",
    "&lt;" -> "<",
    "&gt;" -> ">",
    "&quot;" -> "\"",
    "&apos;" -> "'",
    "&#39;" -> "'",
    "&#x27;" -> "'",
    "&nbsp;" -> " ",
    "&ndash;" -> "–",
    "&mdash;" -> "—",
    "&laquo;" -> "«",
    "&raquo;" -> "»",
    "&copy;" -> "©",
    "&reg;" -> "®",
    "&trade;" -> "™",
    "&deg;" -> "°",
    "&plusmn;" -> "±",
    "&times;" -> "×",
    "&divide;" -> "÷",
    "&equals;" -> "=",
    "&lsquo;" -> "‘",
    "&rsquo;" -> "’",
    "&ldquo;" -> "“",
    "&rdquo;" -> "”",
    "&bull;" -> "•",
    "&hellip;" -> "…",
    "&larr;" -> "←",
    "&rarr;" -> "→",
    "&uarr;" -> "↑",
    "&darr;" -> "↓"
  )

  /** Regex for numeric HTML entities: `&#123;` or `&#x1F;`. */
  private val NumericEntityRegex = """&#(x?)([0-9a-fA-F]+);""".r

  /** Sanitizes text for safe display in SVG.
    *
    * Strips HTML script-like content and dangerous tags. This is a server-side approximation of DOMPurify's sanitization.
    *
    * @param text
    *   the raw text to sanitize
    * @return
    *   sanitized text safe for SVG rendering
    */
  def sanitizeText(text: String): String =
    if (text.isEmpty) {
      text
    } else {
      var result = text
      // Remove script tags and their content
      result = result.replaceAll("(?i)<script[^>]*>[\\s\\S]*?</script>", "")
      // Remove on* event attributes
      result = result.replaceAll("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\"", "")
      result = result.replaceAll("(?i)\\son\\w+\\s*=\\s*'[^']*'", "")
      // Remove style tags
      result = result.replaceAll("(?i)<style[^>]*>[\\s\\S]*?</style>", "")
      // Remove iframe/object/embed tags
      result = result.replaceAll("(?i)<(iframe|object|embed)[^>]*>[\\s\\S]*?</\\1>", "")
      result = result.replaceAll("(?i)<(iframe|object|embed)[^>]*/>", "")
      result
    }

  /** Gets the rows of lines in a string, converting `<br>` tags and `\n` to line breaks.
    *
    * Mirrors `getRows` from `common.ts`.
    *
    * @param s
    *   the input string
    * @return
    *   array of lines
    */
  def getRows(s: String): Array[String] =
    if (s.isEmpty) {
      Array("")
    } else {
      val str = breakToPlaceholder(s).replace("\\n", BreakPlaceholder)
      str.split(BreakPlaceholder, -1)
    }

  /** Replaces `<br>` tags with a placeholder string. */
  def breakToPlaceholder(text: String): String =
    LineBreakRegex.replaceAllIn(text, BreakPlaceholder)

  /** Replaces placeholder strings back with `<br/>` tags. */
  def placeholderToBreak(text: String): String =
    text.replace(BreakPlaceholder, "<br/>")

  /** Wraps text to fit within a maximum width.
    *
    * Uses [[TextMetrics]] to estimate character widths and breaks text at word boundaries.
    *
    * @param text
    *   the text to wrap
    * @param maxWidth
    *   maximum width in pixels
    * @param fontSize
    *   font size in pixels for measurement
    * @return
    *   array of wrapped lines
    */
  def wrapText(text: String, maxWidth: Double, fontSize: Double): Array[String] =
    if (text.isEmpty || maxWidth <= 0) {
      Array(text)
    } else {
      val result = ArrayBuffer.empty[String]
      val lines  = text.split("\n", -1)

      var lineIdx = 0
      while (lineIdx < lines.length) {
        val line = lines(lineIdx)
        if (line.isEmpty) {
          result += ""
        } else {
          val lineWidth = TextMetrics.estimateWidth(line, fontSize)
          if (lineWidth <= maxWidth) {
            result += line
          } else {
            // Need to wrap
            val words        = line.split("\\s+")
            val currentLine  = new StringBuilder()
            var currentWidth = 0.0

            var wordIdx = 0
            while (wordIdx < words.length) {
              val word       = words(wordIdx)
              val wordWidth  = TextMetrics.estimateWidth(word, fontSize)
              val spaceWidth = if (currentLine.isEmpty) 0.0 else TextMetrics.estimateWidth(" ", fontSize)

              if (currentWidth + spaceWidth + wordWidth <= maxWidth || currentLine.isEmpty) {
                // Add word to current line
                if (currentLine.nonEmpty) {
                  currentLine.append(' ')
                  currentWidth += spaceWidth
                }
                currentLine.append(word)
                currentWidth += wordWidth
              } else {
                // Start new line
                result += currentLine.toString
                currentLine.clear()
                currentLine.append(word)
                currentWidth = wordWidth
              }
              wordIdx += 1
            }
            if (currentLine.nonEmpty) {
              result += currentLine.toString
            }
          }
        }
        lineIdx += 1
      }
      result.toArray
    }

  /** Decodes HTML entities in text.
    *
    * Handles named entities (`&amp;`, `&lt;`, etc.) and numeric entities (`&#123;`, `&#x1F;`).
    *
    * @param text
    *   text containing HTML entities
    * @return
    *   text with entities decoded to their character equivalents
    */
  def entityDecode(text: String): String =
    if (text.isEmpty || !text.contains("&")) {
      text
    } else {
      var result = text

      // Decode named entities
      HtmlEntities.foreach { case (entity, replacement) =>
        result = result.replace(entity, replacement)
      }

      // Decode numeric entities: &#123; and &#x1F;
      result = NumericEntityRegex.replaceAllIn(
        result,
        m => {
          val isHex   = m.group(1) == "x"
          val codeStr = m.group(2)
          try {
            val codePoint = if (isHex) Integer.parseInt(codeStr, 16) else Integer.parseInt(codeStr)
            if (codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT) {
              new String(Character.toChars(codePoint))
            } else {
              m.matched
            }
          } catch {
            case _: NumberFormatException => m.matched
          }
        }
      )

      result
    }

  /** Removes common leading whitespace from all lines (dedent/unindent).
    *
    * Useful for cleaning up multi-line strings embedded in code.
    *
    * @param text
    *   indented text
    * @return
    *   text with common leading whitespace removed
    */
  def dedent(text: String): String =
    if (text.isEmpty) {
      text
    } else {
      val lines = text.split("\n", -1)

      // Find the minimum indentation among non-empty lines
      var minIndent = Int.MaxValue
      var i         = 0
      while (i < lines.length) {
        val line = lines(i)
        if (line.trim.nonEmpty) {
          val indent = line.length - line.stripLeading().length
          if (indent < minIndent) {
            minIndent = indent
          }
        }
        i += 1
      }

      if (minIndent == 0 || minIndent == Int.MaxValue) {
        text
      } else {
        lines
          .map { line =>
            if (line.length >= minIndent) line.substring(minIndent)
            else line
          }
          .mkString("\n")
      }
    }

  /** Removes markdown-style formatting markers from text.
    *
    * Strips `**bold**`, `*italic*`, `__bold__`, `_italic_`, `` `code` ``, and `~~strikethrough~~` markers.
    *
    * @param text
    *   text with markdown markers
    * @return
    *   plain text with markers removed
    */
  def stripMarkdown(text: String): String =
    if (text.isEmpty) {
      text
    } else {
      var result = text
      // Bold: **text** and __text__
      result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
      result = result.replaceAll("__(.+?)__", "$1")
      // Italic: *text* and _text_
      result = result.replaceAll("\\*(.+?)\\*", "$1")
      result = result.replaceAll("(?<=\\s|^)_(.+?)_(?=\\s|$)", "$1")
      // Code: `text`
      result = result.replaceAll("`(.+?)`", "$1")
      // Strikethrough: ~~text~~
      result = result.replaceAll("~~(.+?)~~", "$1")
      result
    }
}
