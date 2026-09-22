/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * The shape of org.nibor.autolink that flexmark's AutolinkNodePostProcessor uses — a link type, a span
 * and an extractor built by a builder. The generated code is re-pointed here (ssg-md/port/ext.conf)
 * because the autolink jar is JVM-only: on the JVM `PlatformLinkExtractor` wraps nibor's own
 * extractor, so links are found exactly as flexmark finds them; on Scala.js and Scala Native it is
 * the regex extractor below, which is what the hand-written port of this extension used.
 * Member names are nibor's, so the generated call sites read unchanged. */
package ssg
package md
package ext
package autolink
package internal

import java.util.regex.Pattern

import scala.util.boundary
import scala.util.boundary.break

enum LinkType extends java.lang.Enum[LinkType] {
  case URL, WWW, EMAIL
}

trait LinkSpan {
  def getType():       LinkType
  def getBeginIndex(): Int
  def getEndIndex():   Int
}

abstract class LinkExtractor {
  def extractLinks(input: CharSequence): java.lang.Iterable[LinkSpan]
}

object LinkExtractor {
  def builder(): LinkExtractorBuilder = new LinkExtractorBuilder()
}

final class LinkExtractorBuilder {

  // nibor's default is every type; spelled out because `EnumSet.allOf` needs the enum reflection Scala Native does not have
  private var types: java.util.Set[LinkType] = {
    val all = new java.util.HashSet[LinkType]()
    LinkType.values.foreach(t => all.add(t))
    all
  }

  def linkTypes(linkTypes: java.util.Set[LinkType]): LinkExtractorBuilder = {
    types = linkTypes
    this
  }

  def build(): LinkExtractor = PlatformLinkExtractor.create(types)
}

/** Links found by regular expressions, with nibor's trailing-punctuation rule restated: sentence punctuation is not part of a URL, and a closing parenthesis is only when it is balanced. */
final class RegexLinkExtractor(types: java.util.Set[LinkType]) extends LinkExtractor {
  import RegexLinkExtractor.*

  final private class Span(linkType: LinkType, beginIndex: Int, endIndex: Int) extends LinkSpan {
    def getType():       LinkType = linkType
    def getBeginIndex(): Int      = beginIndex
    def getEndIndex():   Int      = endIndex
  }

  def extractLinks(input: CharSequence): java.lang.Iterable[LinkSpan] = {
    val out = new java.util.ArrayList[LinkSpan]()
    if (types.contains(LinkType.URL) || types.contains(LinkType.WWW)) {
      val urls = URL_PATTERN.matcher(input)
      while (urls.find()) {
        val kind = if (urls.group().startsWith("www.")) LinkType.WWW else LinkType.URL
        if (types.contains(kind)) {
          val end = adjustUrlEnd(input, urls.start(), urls.end())
          if (end > urls.start()) {
            out.add(new Span(kind, urls.start(), end))
          }
        }
      }
    }
    if (types.contains(LinkType.EMAIL)) {
      val emails = EMAIL_PATTERN.matcher(input)
      while (emails.find()) {
        var overlaps = false
        val existing = out.iterator()
        while (existing.hasNext && !overlaps) {
          val span = existing.next()
          overlaps = emails.start() >= span.getBeginIndex() && emails.start() < span.getEndIndex()
        }
        if (!overlaps) {
          out.add(new Span(LinkType.EMAIL, emails.start(), emails.end()))
        }
      }
    }
    out.sort((a, b) => Integer.compare(a.getBeginIndex(), b.getBeginIndex()))
    out
  }
}

object RegexLinkExtractor {

  private val URL_PATTERN: Pattern = Pattern.compile(
    "\\b(?:" +
      "[a-zA-Z][a-zA-Z0-9+.\\-]*://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+" + // URLs with any scheme
      "|" +
      "www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+" + // www. URLs
      ")"
  )

  private val EMAIL_PATTERN: Pattern = Pattern.compile("\\b[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}\\b")

  private def adjustUrlEnd(text: CharSequence, start: Int, end: Int): Int = boundary {
    var e = end
    while (e > start) {
      val c = text.charAt(e - 1)
      if (c == '.' || c == ',' || c == ';' || c == '?' || c == '!' || c == '"' || c == '\'' || c == ':') {
        e -= 1
      } else if (c == ')') {
        var openCount  = 0
        var closeCount = 0
        var i          = start
        while (i < e) {
          val ch = text.charAt(i)
          if (ch == '(') openCount += 1
          else if (ch == ')') closeCount += 1
          i += 1
        }
        if (closeCount > openCount) {
          e -= 1
        } else {
          break(e)
        }
      } else {
        break(e)
      }
    }
    e
  }
}
