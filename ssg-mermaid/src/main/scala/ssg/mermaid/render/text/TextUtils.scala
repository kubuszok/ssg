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

  /** Converts a string/boolean-like value into a boolean.
    *
    * Faithful port of `evaluate` from `common.ts` (:175-176): `false`, `"false"`, `"null"`, `"0"` (case-insensitive, trimmed) are falsey; everything else is truthy. Used to resolve
    * `flowchart.htmlLabels` (shapes/util.js:9, :11).
    *
    * @param value
    *   the value to coerce (a String form of the config flag)
    * @return
    *   the boolean result
    */
  def evaluate(value: String): Boolean = {
    val v = value.trim.toLowerCase
    !(v == "false" || v == "null" || v == "0")
  }

  /** Boolean overload of [[evaluate]] — `false` stays falsey, everything else truthy. */
  def evaluate(value: Boolean): Boolean = value

  /** Removes script-like content from text (DOMPurify stand-in).
    *
    * Mirrors `removeScript` from `common.ts` (:58-64), which delegates to `DOMPurify.sanitize`. Since there is no browser DOM server-side, this strips
    * `<script>`/`<style>`/`<iframe>`/`<object>`/`<embed>` tags and `on*` event-handler attributes — the dangerous subset DOMPurify removes by default.
    *
    * @param txt
    *   the text to sanitize
    * @return
    *   the safer text
    */
  def removeScript(txt: String): String = sanitizeText(txt)

  /** Applies the security-level sanitization gate for HTML labels.
    *
    * Faithful port of `sanitizeMore` from `common.ts` (:66-79). This gate is DISTINCT from the URL gate (`Utils.formatUrl`): it only runs when `htmlLabels !== false`, and branches on `securityLevel`:
    *   - `antiscript` / `strict` → strip script-like content (`removeScript`)
    *   - any level other than `loose` → escape `<`→`&lt;`, `>`→`&gt;`, `=`→`&equals;` with the `<br>`→`#br#`→`<br/>` round-trip (so line breaks survive)
    *   - `loose` → raw passthrough
    *
    * @param text
    *   the raw label text
    * @param securityLevel
    *   the resolved `MermaidConfig.securityLevel`
    * @param htmlLabels
    *   the resolved `flowchart.htmlLabels` flag (gate is skipped when false)
    * @return
    *   the sanitized text
    */
  def sanitizeMore(text: String, securityLevel: String, htmlLabels: Boolean): String =
    // common.ts:67 — `if (config.flowchart?.htmlLabels !== false)`
    if (!htmlLabels) {
      text
    } else {
      val level = securityLevel
      if (level == "antiscript" || level == "strict") {
        // common.ts:69-70
        removeScript(text)
      } else if (level != "loose") {
        // common.ts:71-76 — `<br>`→`#br#`→`<br/>` round-trip then escape < > =
        var t = breakToPlaceholder(text)
        t = t.replace("<", "&lt;").replace(">", "&gt;")
        t = t.replace("=", "&equals;")
        placeholderToBreak(t)
      } else {
        text
      }
    }

  /** Sanitizes label text for HTML rendering, applying the security gate.
    *
    * Faithful port of `sanitizeText` from `common.ts` (:81-94). Runs [[sanitizeMore]] then ALWAYS forbids `style` tags (the `FORBID_TAGS: ['style']` branch at common.ts:89-91). Empty text is returned
    * unchanged (common.ts:82-84).
    *
    * @param text
    *   the raw label text
    * @param securityLevel
    *   the resolved `MermaidConfig.securityLevel`
    * @param htmlLabels
    *   the resolved `flowchart.htmlLabels` flag
    * @return
    *   sanitized text safe for HTML-label rendering
    */
  def sanitizeTextHtml(text: String, securityLevel: String, htmlLabels: Boolean): String =
    if (text.isEmpty) {
      text
    } else {
      // common.ts:89-91 — DOMPurify.sanitize(..., { FORBID_TAGS: ['style'] })
      forbidStyleTags(sanitizeMore(text, securityLevel, htmlLabels))
    }

  /** Strips `<style>...</style>` blocks (the always-on `FORBID_TAGS: ['style']`). */
  private def forbidStyleTags(text: String): String =
    text.replaceAll("(?i)<style[^>]*>[\\s\\S]*?</style>", "").replaceAll("(?i)<style[^>]*/?>", "")

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
      result = stripItalicUnderscores(result)
      // Code: `text`
      result = result.replaceAll("`(.+?)`", "$1")
      // Strikethrough: ~~text~~
      result = result.replaceAll("~~(.+?)~~", "$1")
      result
    }

  /** Strips italic underscore markers (`_text_` to `text`) where the opening `_` is preceded by whitespace or start-of-string, and the closing `_` is followed by whitespace or end-of-string.
    *
    * Mirrors the original regex `(?<=\s|^)_(.+?)_(?=\s|$)` without lookaround (unsupported on Scala Native re2, ISS-1344). Boundary characters are preserved (not consumed), so adjacent italics like
    * `_a_ _b_` both match — the shared space remains as a boundary for both.
    */
  private def stripItalicUnderscores(s: String): String = {
    val len = s.length
    if (len < 3) {
      // Need at least _X_ (3 chars) for any match
      s
    } else {
      val sb = new java.lang.StringBuilder(len)
      var i  = 0
      while (i < len)
        if (s.charAt(i) == '_') {
          // Check start boundary: preceded by whitespace or at position 0
          val startBoundary = i == 0 || Character.isWhitespace(s.charAt(i - 1))
          if (startBoundary && i + 2 < len) {
            // Search for the closest closing '_' (reluctant: shortest match) that
            // is followed by whitespace or end-of-string
            var j     = i + 2 // content must be at least 1 char (.+?)
            var found = false
            while (j < len && !found) {
              if (s.charAt(j) == '_') {
                val endBoundary = j + 1 >= len || Character.isWhitespace(s.charAt(j + 1))
                if (endBoundary) {
                  // Match: append the content between the underscores (without them)
                  sb.append(s, i + 1, j)
                  i = j + 1
                  found = true
                }
              }
              if (!found) j += 1
            }
            if (!found) {
              // No closing underscore with end-boundary found — emit the opening '_' literally
              sb.append('_')
              i += 1
            }
          } else {
            sb.append('_')
            i += 1
          }
        } else {
          sb.append(s.charAt(i))
          i += 1
        }
      sb.toString
    }
  }
}
