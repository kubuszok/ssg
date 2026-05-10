/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains a list of utility functions which are useful in other
 * files.
 *
 * Original source: katex src/utils.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package util

import scala.util.boundary
import scala.util.boundary.break
import scala.util.matching.Regex

import ssg.commons.Nullable
import ssg.katex.parse.AnyParseNode

/** This file contains a list of utility functions which are useful in other files.
  */
object Utils {

  // hyphenate and escape adapted from Facebook's React under Apache 2 license
  private val uppercase: Regex = """([A-Z])""".r

  def hyphenate(str: String): String =
    uppercase.replaceAllIn(str, m => "-" + m.group(1).toLowerCase)

  private val ESCAPE_LOOKUP: Map[String, String] = Map(
    "&" -> "&amp;",
    ">" -> "&gt;",
    "<" -> "&lt;",
    "\"" -> "&quot;",
    "'" -> "&#x27;"
  )

  private val ESCAPE_REGEX: Regex = """[&><"']""".r

  /** Escapes text to prevent scripting attacks.
    */
  def escape(text: String): String =
    ESCAPE_REGEX.replaceAllIn(text, m => Regex.quoteReplacement(ESCAPE_LOOKUP(m.matched)))

  /** Sometimes we want to pull out the innermost element of a group. In most cases, this will just be the group itself, but when ordgroups and colors have a single element, we want to pull that out.
    */
  def getBaseElem(group: AnyParseNode): AnyParseNode =
    if (group.nodeType == "ordgroup") {
      if (group.bodyNodes.length == 1) {
        getBaseElem(group.bodyNodes(0))
      } else {
        group
      }
    } else if (group.nodeType == "color") {
      if (group.bodyNodes.length == 1) {
        getBaseElem(group.bodyNodes(0))
      } else {
        group
      }
    } else if (group.nodeType == "font") {
      getBaseElem(group.bodyNode.get)
    } else {
      group
    }

  private val characterNodesTypes: Set[String] = Set("mathord", "textord", "atom")

  /** TeXbook algorithms often reference "character boxes", which are simply groups with a single character in them. To decide if something is a character box, we find its innermost group, and see if
    * it is a single character.
    */
  def isCharacterBox(group: AnyParseNode): Boolean =
    characterNodesTypes.contains(getBaseElem(group).nodeType)

  // Check for possible leading protocol.
  // https://url.spec.whatwg.org/#url-parsing strips leading whitespace
  // (U+20) or C0 control (U+00-U+1F) characters.
  // eslint-disable-next-line no-control-regex
  private val protocolRegex: Regex =
    new Regex("(?i)^[\\x00-\\x20]*([^\\\\/#?]*?)(:|&#0*58|&#x0*3a|&colon)")

  // Validate scheme according to
  // https://datatracker.ietf.org/doc/html/rfc3986#section-3.1
  private val schemeRegex: Regex = """^[a-zA-Z][a-zA-Z0-9+\-.]*$""".r

  /** Return the protocol of a URL, or "_relative" if the URL does not specify a protocol (and thus is relative), or `null` if URL has invalid protocol (so should be outright rejected).
    */
  def protocolFromUrl(url: String): Nullable[String] = boundary {
    val matchOpt = protocolRegex.findFirstMatchIn(url)
    matchOpt match {
      case None =>
        // No protocol found — URL is relative
        break(Nullable("_relative"))
      case Some(m) =>
        // Reject weird colons
        if (m.group(2) != ":") {
          break(Nullable.Null)
        }
        // Reject invalid characters in scheme according to
        // https://datatracker.ietf.org/doc/html/rfc3986#section-3.1
        if (schemeRegex.findFirstIn(m.group(1)).isEmpty) {
          break(Nullable.Null)
        }
        // Lowercase the protocol
        Nullable(m.group(1).toLowerCase)
    }
  }
}
