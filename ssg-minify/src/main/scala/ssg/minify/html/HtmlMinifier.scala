/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * HTML minification — removes comments, collapses whitespace, optimizes attributes.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (htmlcompressor gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: htmlcompressor gem → ssg.minify.html.HtmlMinifier
 *   Convention: Pure Scala 3, regex-pipeline based, cross-platform
 *   Idiom: Stateless pure functions, preserved-block extraction pattern
 *   Gap: preserveLineBreaks option declared but never read by doMinify
 *     (ISS-038). Quote-stripping over-conservative, only [A-Za-z0-9_-]+ values
 *     (ISS-039). No built-in SSI/JSP/PHP/CDATA preserve presets (ISS-040).
 *     See docs/architecture/jekyll-minifier-port.md.
 *   Audited: 2026-04-07 (minor_issues)
 */
package ssg
package minify
package html

import scala.util.boundary
import scala.util.boundary.break
import ssg.minify.css.CssMinifier
import ssg.minify.js.JsMinifier as BasicJsMinifier

object HtmlMinifier {

  /** Minify HTML content.
    *
    * @param input
    *   HTML string to minify
    * @param options
    *   minification options
    * @param jsCompressor
    *   pluggable JS compressor (defaults to basic JsMinifier)
    * @return
    *   minified HTML, or original on failure
    */
  def minify(
    input:        String,
    options:      HtmlMinifyOptions = HtmlMinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): String =
    if (input.isEmpty) {
      input
    } else {
      try
        doMinify(input, options, jsCompressor)
      catch {
        case _: Exception => input // graceful degradation
      }
    }

  private def doMinify(input: String, options: HtmlMinifyOptions, jsCompressor: JsCompressor): String = {
    // 1. Extract preserved blocks (pre, textarea, script, style, user patterns)
    val (html, preserved) = PreservedBlock.extract(input, options.preservePatterns)

    // 2. Apply whitespace/comment pipeline (operates on text outside preserved blocks)
    var result = html
    if (options.removeComments) result = removeComments(result)
    if (options.simpleDoctype) result = simplifyDoctype(result)
    if (options.removeMultiSpaces) result = collapseMultiSpaces(result)
    if (options.removeIntertagSpaces) result = removeIntertagSpaces(result)
    if (options.removeSpacesInsideTags) result = removeSpacesInsideTags(result)

    // 3. Restore preserved blocks (so tag-level optimizations can see script/style tags)
    result = PreservedBlock.restore(result, preserved)

    // 4. Apply tag-level optimizations (need to see full tags including script/style)
    if (options.removeQuotes) result = removeUnnecessaryQuotes(result)
    if (options.simpleBooleanAttributes) result = simplifyBooleanAttributes(result)
    if (options.removeScriptAttributes) result = removeDefaultScriptType(result)
    if (options.removeStyleAttributes) result = removeDefaultStyleType(result)
    if (options.removeLinkAttributes) result = removeDefaultLinkType(result)
    if (options.removeFormAttributes) result = removeDefaultFormMethod(result)
    if (options.removeInputAttributes) result = removeDefaultInputType(result)
    if (options.removeJavascriptProtocol) result = removeJavascriptProtocol(result)
    if (options.removeHttpProtocol) result = removeHttpProtocol(result)
    if (options.removeHttpsProtocol) result = removeHttpsProtocol(result)

    // 5. Compress inline CSS and JS
    if (options.compressCssInHtml) result = compressInlineCss(result)
    if (options.compressJsInHtml) result = compressInlineJs(result, jsCompressor)

    result
  }

  // -- Comment removal --
  // State-machine based to avoid negative lookahead (not supported on Scala Native re2).
  // Removes <!-- ... --> but preserves conditional comments <!--[if ... -->

  private def removeComments(html: String): String = {
    val marker    = "<!--"
    val endMarker = "-->"
    val sb        = new StringBuilder(html.length)
    var i         = 0
    val len       = html.length

    while (i < len)
      if (i + marker.length <= len && html.regionMatches(i, marker, 0, marker.length)) {
        // Check if this is a conditional comment: <!--[if
        if (i + 7 <= len && html.regionMatches(i, "<!--[if", 0, 7)) {
          // Conditional comment — copy verbatim until -->
          val endIdx = html.indexOf(endMarker, i + 4)
          if (endIdx >= 0) {
            sb.append(html, i, endIdx + endMarker.length)
            i = endIdx + endMarker.length
          } else {
            sb.append(html.charAt(i))
            i += 1
          }
        } else {
          // Regular comment — skip until -->
          val endIdx = html.indexOf(endMarker, i + 4)
          if (endIdx >= 0) {
            i = endIdx + endMarker.length
          } else {
            // Unclosed comment — skip to end
            i = len
          }
        }
      } else {
        sb.append(html.charAt(i))
        i += 1
      }

    sb.toString()
  }

  // -- Doctype simplification --

  private val DoctypePattern = "(?i)<!DOCTYPE[^>]*>".r

  private def simplifyDoctype(html: String): String =
    DoctypePattern.replaceFirstIn(html, "<!DOCTYPE html>")

  // -- Whitespace --

  private val MultiSpace = " {2,}".r

  private def collapseMultiSpaces(html: String): String =
    // Collapse runs of spaces (not newlines)
    MultiSpace.replaceAllIn(html, " ")

  private val IntertagSpace = ">\\s+<".r

  private def removeIntertagSpaces(html: String): String =
    IntertagSpace.replaceAllIn(html, "><")

  // Remove unnecessary whitespace inside tags: <tag  attr = "val" > → <tag attr="val">
  private val MultiSpaceInTag = "\\s{2,}".r

  private def removeSpacesInsideTags(html: String): String = {
    // This is tricky — we only want to operate inside tags, not in text content.
    // Process tag by tag.
    val sb  = new StringBuilder(html.length)
    var i   = 0
    val len = html.length

    while (i < len)
      if (html.charAt(i) == '<') {
        // Find the end of this tag
        val tagEnd = html.indexOf('>', i)
        if (tagEnd >= 0) {
          val tag = html.substring(i, tagEnd + 1)
          // Collapse multiple spaces within the tag
          val cleaned = MultiSpaceInTag.replaceAllIn(tag, " ")
          // Remove space before >
          val trimmed = if (cleaned.endsWith(" >")) {
            cleaned.substring(0, cleaned.length - 2) + ">"
          } else {
            cleaned
          }
          sb.append(trimmed)
          i = tagEnd + 1
        } else {
          sb.append(html.charAt(i))
          i += 1
        }
      } else {
        sb.append(html.charAt(i))
        i += 1
      }

    sb.toString()
  }

  // -- Attribute optimization --

  // Remove quotes on simple attribute values (no spaces, no special chars)
  private val QuotedSimpleAttr = """(\w+)="([a-zA-Z0-9_\-]+)"""".r

  private def removeUnnecessaryQuotes(html: String): String =
    QuotedSimpleAttr.replaceAllIn(html, "$1=$2")

  // Boolean attributes that can be simplified
  private val BooleanAttrs = Set(
    "checked",
    "disabled",
    "selected",
    "readonly",
    "multiple",
    "autofocus",
    "autoplay",
    "controls",
    "loop",
    "muted",
    "default",
    "formnovalidate",
    "novalidate",
    "open",
    "required",
    "reversed",
    "hidden",
    "async",
    "defer"
  )

  private def simplifyBooleanAttributes(html: String): String = {
    var result = html
    for (attr <- BooleanAttrs) {
      // Match attr="attr" or attr='attr' and simplify to just attr
      val pattern = s"""(?i)$attr\\s*=\\s*["']$attr["']""".r
      result = pattern.replaceAllIn(result, attr)
    }
    result
  }

  // -- Default attribute removal --

  // <script type="text/javascript"> → <script>
  private val ScriptType = """(<script[^>]*)\s+type\s*=\s*["']text/javascript["']""".r

  private def removeDefaultScriptType(html: String): String =
    ScriptType.replaceAllIn(html, "$1")

  // <style type="text/css"> → <style>
  private val StyleType = """(<style[^>]*)\s+type\s*=\s*["']text/css["']""".r

  private def removeDefaultStyleType(html: String): String =
    StyleType.replaceAllIn(html, "$1")

  // <link type="text/css"> → <link>
  private val LinkType = """(<link[^>]*)\s+type\s*=\s*["']text/css["']""".r

  private def removeDefaultLinkType(html: String): String =
    LinkType.replaceAllIn(html, "$1")

  // <form method="get"> → <form>
  private val FormMethod = """(<form[^>]*)\s+method\s*=\s*["']get["']""".r

  private def removeDefaultFormMethod(html: String): String =
    FormMethod.replaceAllIn(html, "$1")

  // <input type="text"> → <input>
  private val InputType = """(<input[^>]*)\s+type\s*=\s*["']text["']""".r

  private def removeDefaultInputType(html: String): String =
    InputType.replaceAllIn(html, "$1")

  // -- Protocol removal --

  private val JavascriptProtocol = """href\s*=\s*["']javascript:""".r

  private def removeJavascriptProtocol(html: String): String =
    JavascriptProtocol.replaceAllIn(html, """href="""")

  private val HttpProtocol = """((?:href|src|action)\s*=\s*["'])http://""".r

  private def removeHttpProtocol(html: String): String =
    HttpProtocol.replaceAllIn(html, "$1//")

  private val HttpsProtocol = """((?:href|src|action)\s*=\s*["'])https://""".r

  private def removeHttpsProtocol(html: String): String =
    HttpsProtocol.replaceAllIn(html, "$1//")

  // -- Inline CSS/JS compression --

  // -- Inline CSS/JS compression --
  // State-machine based to avoid negative lookahead (not supported on Scala Native re2).

  private def compressInlineCss(html: String): String =
    compressTagContent(html, "style", (content, _) => CssMinifier.minify(content))

  private def compressInlineJs(html: String, jsCompressor: JsCompressor): String =
    compressTagContent(
      html,
      "script",
      (content, attrs) =>
        // Don't compress script tags with src attribute (they load external files)
        if (attrs.toLowerCase.contains("src")) {
          content
        } else if (content.trim.isEmpty) {
          content
        } else {
          jsCompressor.compress(content)
        }
    )

  /** Find <tag ...>content</tag> blocks and apply a transform to the content. */
  private def compressTagContent(
    html:      String,
    tag:       String,
    transform: (String, String) => String
  ): String = {
    val sb       = new StringBuilder(html.length)
    var i        = 0
    val len      = html.length
    val openTag  = s"<$tag"
    val closeTag = s"</$tag>"

    while (i < len)
      if (i + openTag.length < len && html.regionMatches(true, i, openTag, 0, openTag.length)) {
        val nextChar = html.charAt(i + openTag.length)
        if (nextChar == ' ' || nextChar == '>' || nextChar == '\t' || nextChar == '\n') {
          // Find end of opening tag
          val gtIdx = html.indexOf('>', i + openTag.length)
          if (gtIdx >= 0) {
            val attrs        = html.substring(i + openTag.length, gtIdx)
            val contentStart = gtIdx + 1
            // Find closing tag (case-insensitive)
            val closeIdx = findCaseInsensitive(html, closeTag, contentStart)
            if (closeIdx >= 0) {
              val content     = html.substring(contentStart, closeIdx)
              val transformed = transform(content, attrs)
              sb.append(html.substring(i, contentStart)) // opening tag as-is
              sb.append(transformed)
              sb.append(closeTag)
              i = closeIdx + closeTag.length
            } else {
              sb.append(html.charAt(i))
              i += 1
            }
          } else {
            sb.append(html.charAt(i))
            i += 1
          }
        } else {
          sb.append(html.charAt(i))
          i += 1
        }
      } else {
        sb.append(html.charAt(i))
        i += 1
      }

    sb.toString()
  }

  /** Find a substring case-insensitively. */
  private def findCaseInsensitive(html: String, target: String, start: Int): Int =
    boundary[Int] {
      var i   = start
      val len = html.length
      while (i + target.length <= len) {
        if (html.regionMatches(true, i, target, 0, target.length)) {
          break(i)
        }
        i += 1
      }
      -1
    }
}
