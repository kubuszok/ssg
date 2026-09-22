/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: the link extractor is org.nibor.autolink's own, as flexmark's is — the autolink jar exists here.
 * Scala.js and Scala Native have no such jar and answer with `RegexLinkExtractor`. */
package ssg
package md
package ext
package autolink
package internal

object PlatformLinkExtractor {

  private final class Span(span: org.nibor.autolink.LinkSpan) extends LinkSpan {
    def getType(): LinkType =
      span.getType() match {
        case org.nibor.autolink.LinkType.URL   => LinkType.URL
        case org.nibor.autolink.LinkType.WWW   => LinkType.WWW
        case org.nibor.autolink.LinkType.EMAIL => LinkType.EMAIL
      }
    def getBeginIndex(): Int = span.getBeginIndex()
    def getEndIndex():   Int = span.getEndIndex()
  }

  def create(types: java.util.Set[LinkType]): LinkExtractor = {
    val niborTypes = java.util.EnumSet.noneOf(classOf[org.nibor.autolink.LinkType])
    types.forEach {
      case LinkType.URL   => niborTypes.add(org.nibor.autolink.LinkType.URL)
      case LinkType.WWW   => niborTypes.add(org.nibor.autolink.LinkType.WWW)
      case LinkType.EMAIL => niborTypes.add(org.nibor.autolink.LinkType.EMAIL)
    }
    val nibor = org.nibor.autolink.LinkExtractor.builder().linkTypes(niborTypes).build()
    new LinkExtractor {
      def extractLinks(input: CharSequence): java.lang.Iterable[LinkSpan] = {
        val out = new java.util.ArrayList[LinkSpan]()
        nibor.extractLinks(input).forEach(span => out.add(new Span(span)))
        out
      }
    }
  }
}
