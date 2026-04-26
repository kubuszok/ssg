/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Preserved block extraction/restoration for HTML minification.
 * Extracts content from <pre>, <textarea>, <script>, <style>, and
 * user-supplied preserve patterns, replacing them with placeholders
 * that survive the minification pipeline.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (htmlcompressor gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-ruby-reference: jekyll-minifier lib/jekyll-minifier.rb (htmlcompressor gem)
 * Covenant-verified: 2026-04-26
 */
package ssg
package minify
package html

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break
import scala.util.matching.Regex

object PreservedBlock {

  private val PlaceholderPrefix = "\u0000SSG_HTML_"

  /** Extract all preserved blocks from HTML, replacing them with placeholders.
    *
    * @param preservePatterns
    *   user-supplied regex patterns to preserve
    * @param preservedTags
    *   HTML tag names whose content is preserved (default: pre, textarea, script, style)
    * @return
    *   (modified HTML with placeholders, array of preserved content strings)
    */
  def extract(
    html:             String,
    preservePatterns: List[Regex] = Nil,
    preservedTags:    List[String] = HtmlMinifyOptions.DefaultPreservedTags
  ): (String, Array[String]) = {
    val preserved = ArrayBuffer[String]()
    var result    = html

    // First extract user-supplied preserve patterns
    for (pattern <- preservePatterns)
      result = extractPattern(result, pattern, preserved)

    // Then extract preserved tag blocks
    for (tag <- preservedTags)
      result = extractTag(result, tag, preserved)

    (result, preserved.toArray)
  }

  /** Restore all placeholders with their original content. */
  def restore(html: String, preserved: Array[String]): String =
    if (preserved.isEmpty) {
      html
    } else {
      val sb  = new StringBuilder(html.length + preserved.map(_.length).sum)
      var i   = 0
      val len = html.length
      while (i < len)
        if (html.charAt(i) == '\u0000' && html.regionMatches(i, PlaceholderPrefix, 0, PlaceholderPrefix.length)) {
          i += PlaceholderPrefix.length
          val numStart = i
          while (i < len && html.charAt(i).isDigit)
            i += 1
          val idx = html.substring(numStart, i).toInt
          if (i < len && html.charAt(i) == '\u0000') i += 1
          sb.append(preserved(idx))
        } else {
          sb.append(html.charAt(i))
          i += 1
        }
      sb.toString()
    }

  /** Extract matches of a regex pattern. */
  private def extractPattern(html: String, pattern: Regex, preserved: ArrayBuffer[String]): String =
    pattern.replaceAllIn(html,
                         m => {
                           val idx = preserved.size
                           preserved += m.matched
                           s"${PlaceholderPrefix}${idx}\u0000"
                         }
    )

  /** Extract a specific HTML tag and its content. Case-insensitive. */
  private def extractTag(html: String, tag: String, preserved: ArrayBuffer[String]): String = {
    val sb       = new StringBuilder(html.length)
    var i        = 0
    val len      = html.length
    val openTag  = s"<$tag"
    val closeTag = s"</$tag>"

    while (i < len)
      if (i + openTag.length < len && html.regionMatches(true, i, openTag, 0, openTag.length)) {
        val nextChar = if (i + openTag.length < len) html.charAt(i + openTag.length) else '\u0000'
        // Ensure it's actually a tag start (followed by space, >, or /)
        if (nextChar == ' ' || nextChar == '>' || nextChar == '/' || nextChar == '\t' || nextChar == '\n') {
          // Find the closing tag
          val closeIdx = findClosingTag(html, i, len, closeTag)
          if (closeIdx >= 0) {
            val endIdx = closeIdx + closeTag.length
            val block  = html.substring(i, endIdx)
            val idx    = preserved.size
            preserved += block
            sb.append(PlaceholderPrefix)
            sb.append(idx)
            sb.append('\u0000')
            i = endIdx
          } else {
            // No closing tag found — preserve from open tag to end
            val block = html.substring(i)
            val idx   = preserved.size
            preserved += block
            sb.append(PlaceholderPrefix)
            sb.append(idx)
            sb.append('\u0000')
            i = len
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

  /** Find the position of the closing tag, case-insensitive. */
  private def findClosingTag(html: String, start: Int, len: Int, closeTag: String): Int =
    boundary[Int] {
      var i = start + 1
      while (i + closeTag.length <= len) {
        if (html.regionMatches(true, i, closeTag, 0, closeTag.length)) {
          break(i)
        }
        i += 1
      }
      -1
    }
}
