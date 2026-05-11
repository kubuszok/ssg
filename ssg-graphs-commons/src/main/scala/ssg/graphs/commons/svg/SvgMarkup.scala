/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Original source: mermaid
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces browser DOM serialization with server-side XML output
 *   Idiom: Utility object with pure functions for XML escaping and serialization
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package svg

import ssg.commons.Nullable

import scala.collection.mutable.LinkedHashMap

/** XML serialization utilities for SVG elements.
  *
  * Provides proper XML escaping for attribute values and text content, CDATA wrapping for `<style>` elements, and configurable indentation for readable output.
  */
object SvgMarkup {

  /** Default indentation string (2 spaces per level). */
  val DefaultIndent: String = "  "

  /** SVG namespace URI. */
  val SvgNamespace: String = "http://www.w3.org/2000/svg"

  /** XLink namespace URI (for `xlink:href` etc.). */
  val XlinkNamespace: String = "http://www.w3.org/1999/xlink"

  /** Elements whose text content should be wrapped in CDATA sections. */
  private val CdataTags: Set[String] = Set("style", "script")

  /** Escapes special XML characters in attribute values.
    *
    * Replaces `&`, `<`, `>`, `"` with their XML entity references.
    */
  def escapeAttrValue(value: String): String = {
    val sb = new StringBuilder(value.length)
    var i  = 0
    while (i < value.length) {
      value.charAt(i) match {
        case '&'  => sb.append("&amp;")
        case '<'  => sb.append("&lt;")
        case '>'  => sb.append("&gt;")
        case '"'  => sb.append("&quot;")
        case '\'' => sb.append("&apos;")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  /** Escapes special XML characters in text content.
    *
    * Replaces `&`, `<`, `>` with their XML entity references. Does not escape `"` since it is valid in text content.
    */
  def escapeTextContent(value: String): String = {
    val sb = new StringBuilder(value.length)
    var i  = 0
    while (i < value.length) {
      value.charAt(i) match {
        case '&' => sb.append("&amp;")
        case '<' => sb.append("&lt;")
        case '>' => sb.append("&gt;")
        case c   => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  /** Wraps content in a CDATA section for embedding in `<style>` or `<script>` elements.
    *
    * If the content already contains `]]>`, it is split across multiple CDATA sections to avoid premature closing.
    */
  def wrapCdata(content: String): String =
    if (content.contains("]]>")) {
      // Split at ]]> boundaries and rejoin across CDATA sections
      val parts = content.split("]]>", -1)
      parts.mkString("<![CDATA[", "]]]]><![CDATA[>", "]]>")
    } else {
      s"<![CDATA[$content]]>"
    }

  /** Serializes attributes from a LinkedHashMap to an XML attribute string.
    *
    * Preserves insertion order for deterministic output.
    */
  def serializeAttributes(attrs: LinkedHashMap[String, String]): String =
    if (attrs.isEmpty) {
      ""
    } else {
      val sb = new StringBuilder()
      attrs.foreach { case (name, value) =>
        sb.append(' ')
        sb.append(name)
        sb.append("=\"")
        sb.append(escapeAttrValue(value))
        sb.append('"')
      }
      sb.toString
    }

  /** Serializes an SvgElement to an XML string.
    *
    * @param element
    *   the element to serialize
    * @param indent
    *   the indentation string per level (e.g. " " for 2 spaces)
    * @param level
    *   the current indentation depth
    * @param pretty
    *   whether to add indentation and newlines for readability
    * @return
    *   the serialized XML string
    */
  def serialize(element: SvgElement, indent: String = DefaultIndent, level: Int = 0, pretty: Boolean = true): String = {
    val sb     = new StringBuilder()
    val prefix = if (pretty) indent * level else ""

    sb.append(prefix)
    sb.append('<')
    sb.append(element.tagName)
    sb.append(serializeAttributes(element.attributes))

    val hasChildren   = element.children.nonEmpty
    val hasText       = element.textContent.isDefined
    val hasHtml       = element.htmlContent.isDefined
    val isSelfClosing = !hasChildren && !hasText && !hasHtml

    if (isSelfClosing) {
      sb.append(" />")
    } else {
      sb.append('>')

      if (hasText) {
        val text = element.textContent.get
        if (CdataTags.contains(element.tagName)) {
          if (pretty) {
            sb.append('\n')
            sb.append(prefix)
            sb.append(indent)
          }
          sb.append(wrapCdata(text))
          if (pretty) {
            sb.append('\n')
            sb.append(prefix)
          }
        } else {
          // Inline text — no extra indentation
          sb.append(escapeTextContent(text))
        }
      } else if (hasHtml) {
        // HTML content is inserted raw (for foreignObject)
        val html = element.htmlContent.get
        if (pretty) {
          sb.append('\n')
          sb.append(prefix)
          sb.append(indent)
        }
        sb.append(html)
        if (pretty) {
          sb.append('\n')
          sb.append(prefix)
        }
      }

      if (hasChildren) {
        if (pretty) sb.append('\n')
        element.children.foreach { child =>
          sb.append(serialize(child, indent, level + 1, pretty))
          if (pretty) sb.append('\n')
        }
        if (pretty) sb.append(prefix)
      }

      sb.append("</")
      sb.append(element.tagName)
      sb.append('>')
    }

    sb.toString
  }
}
