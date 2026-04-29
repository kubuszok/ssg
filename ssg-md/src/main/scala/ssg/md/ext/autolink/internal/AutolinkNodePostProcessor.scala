/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-autolink/src/main/java/com/vladsch/flexmark/ext/autolink/internal/AutolinkNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * NOTE: The original used org.nibor.autolink for URL detection. This port uses
 * regex-based URL/email detection to avoid the external dependency, making it
 * cross-platform compatible (JVM, JS, Native).
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-autolink/src/main/java/com/vladsch/flexmark/ext/autolink/internal/AutolinkNodePostProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package autolink
package internal

import ssg.md.Nullable
import ssg.md.ast.*
import ssg.md.parser.Parser
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.*
import ssg.md.util.sequence.*

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break
import java.util.ArrayList
import java.util.regex.Pattern

class AutolinkNodePostProcessor(document: Document) extends NodePostProcessor {

  private val ignoredLinks: Nullable[Pattern] = {
    val ignoreLinks = AutolinkExtension.IGNORE_LINKS.get(document)
    if (ignoreLinks.isEmpty) Nullable.empty
    else Nullable(Pattern.compile(ignoreLinks))
  }

  private val intellijDummyIdentifier: Boolean = Parser.INTELLIJ_DUMMY_IDENTIFIER.get(document)

  def isIgnoredLinkPrefix(url: CharSequence): Boolean =
    ignoredLinks.exists(_.matcher(url).matches())

  override def process(state: NodeTracker, node: Node): Unit = boundary {
    // TODO: figure out why optimization does not work after AutoLink inserted by inline parser
    if (node.ancestorOfType(classOf[DoNotDecorate], classOf[DoNotLinkDecorate]).isDefined) {
      break(())
    }

    var combined  = node.chars
    var original  = combined
    val firstNode = node
    var lastNode  = node

    val htmlEntities = new ArrayList[Range]()

    val nodeNext = node.next
    if (nodeNext.isDefined && (nodeNext.get.isInstanceOf[TypographicText] || nodeNext.get.isInstanceOf[HtmlEntity])) {
      // we absorb this, just in case it is part of the link
      if (nodeNext.get.chars.isContinuationOf(combined)) {
        var typoGraphic: Nullable[Node] = nodeNext
        val combinedSequences = new ArrayList[BasedSequence]()
        combinedSequences.add(combined)

        while (typoGraphic.isDefined && (typoGraphic.get.isInstanceOf[TypographicText] || typoGraphic.get.isInstanceOf[HtmlEntity] || typoGraphic.get.isInstanceOf[Text]))
          if (!typoGraphic.get.chars.isContinuationOf(combined) || typoGraphic.get.chars.startsWith(" ") || combined.endsWith(" ")) {
            // break equivalent
            typoGraphic = Nullable.empty
          } else {
            combined = typoGraphic.get.chars
            if (typoGraphic.get.isInstanceOf[HtmlEntity]) {
              htmlEntities.add(Range.of(combined.startOffset, combined.endOffset))
            }
            combinedSequences.add(combined)
            lastNode = typoGraphic.get
            typoGraphic = typoGraphic.get.next
          }

        original = SegmentedSequence.create(node.chars, combinedSequences)
      }
    }

    val textMapper    = new ReplacedTextMapper(original)
    var unescapedHtml = original

    if (!htmlEntities.isEmpty) {
      // need to replace all HTML entities in html entity regions
      unescapedHtml = Escaping.unescapeHtml(original, htmlEntities, textMapper)
    }

    var literal = Escaping.unescape(unescapedHtml, textMapper)

    if (intellijDummyIdentifier) {
      literal = Escaping.removeAll(literal, "\u001f", textMapper)
    }

    // Use regex-based URL detection instead of org.nibor.autolink
    val linksList = new ArrayList[AutolinkNodePostProcessor.LinkSpan]()
    AutolinkNodePostProcessor.extractLinks(literal, linksList)

    val uriMatcher = AutolinkNodePostProcessor.URI_PREFIX.matcher(literal)
    while (uriMatcher.find()) {
      val start = uriMatcher.start(1)
      val end   = uriMatcher.end(1)

      if (linksList.isEmpty) {
        linksList.add(new AutolinkNodePostProcessor.LinkSpan(AutolinkNodePostProcessor.LinkType.URL, start, end))
      } else {
        val iMax = linksList.size()
        var skip = false
        var i    = 0

        while (i < iMax && !skip) {
          val link = linksList.get(i)
          if (end < link.beginIndex) {
            linksList.add(i, new AutolinkNodePostProcessor.LinkSpan(AutolinkNodePostProcessor.LinkType.URL, start, end))
            skip = true
          } else if (start >= link.beginIndex && end <= link.endIndex) {
            skip = true
          }
          i += 1
        }

        if (!skip) {
          linksList.add(new AutolinkNodePostProcessor.LinkSpan(AutolinkNodePostProcessor.LinkType.URL, start, end))
        }
      }
    }

    var lastEscaped    = 0
    val wrapInTextBase = !(node.parent.isDefined && node.parent.get.isInstanceOf[TextBase])
    var textBase: Nullable[TextBase] = if (wrapInTextBase || !(node.parent.isDefined && node.parent.get.isInstanceOf[TextBase])) Nullable.empty else Nullable(node.parent.get.asInstanceOf[TextBase])
    var processedNode     = false
    var wrappedInTextBase = false

    val iter = linksList.iterator()
    while (iter.hasNext) {
      val link     = iter.next()
      val linkText = literal.subSequence(link.beginIndex, link.endIndex).trimEnd()
      if (!isIgnoredLinkPrefix(linkText)) {
        val startOffset = textMapper.originalOffset(link.beginIndex)
        processedNode = true

        if (lastEscaped == 0 && firstNode != lastNode) {
          // need to see if we need to abort because the first link is not in the first text node
          if (startOffset >= node.chars.length()) {
            // skip this, it will be processed by next Text node processor
            break(())
          }
        }

        if (wrapInTextBase && !wrappedInTextBase) {
          wrappedInTextBase = true
          val tb = new TextBase(original)
          node.insertBefore(tb)
          state.nodeAdded(tb)
          textBase = Nullable(tb)
        }

        if (startOffset > lastEscaped) {
          val escapedChars = original.subSequence(lastEscaped, startOffset)
          val node1        = new Text(escapedChars)
          textBase.fold(node.insertBefore(node1))(_.appendChild(node1))
          state.nodeAdded(node1)
        }

        val linkChars   = linkText.baseSubSequence(linkText.startOffset, linkText.endOffset)
        val contentNode = new Text(linkChars)
        val linkNode: LinkNode = if (link.linkType == AutolinkNodePostProcessor.LinkType.EMAIL) {
          val ml = new MailLink()
          ml.text = linkChars
          ml
        } else {
          val al = new AutoLink()
          al.text = linkChars
          al.setUrlChars(linkChars)
          al
        }

        linkNode.setCharsFromContent()
        linkNode.appendChild(contentNode)
        textBase.fold(node.insertBefore(linkNode))(_.appendChild(linkNode))
        state.nodeAddedWithChildren(linkNode)

        lastEscaped = textMapper.originalOffset(link.beginIndex + linkText.length())
      }
    }

    if (lastEscaped > 0) {
      if (firstNode != lastNode) {
        // remove all typographic nodes already processed and truncate sequence to exclude ones not processed
        var removeNode = firstNode.next
        var length     = node.chars.length()

        while (removeNode.isDefined)
          if (length >= lastEscaped) {
            // we are done, the rest should be excluded
            original = original.subSequence(0, length)
            removeNode = Nullable.empty // break
          } else {
            length += removeNode.get.chars.length()
            val nextNode = removeNode.get.next
            removeNode.get.unlink()
            state.nodeRemoved(removeNode.get)

            if (removeNode.contains(lastNode)) {
              removeNode = Nullable.empty // break
            } else {
              removeNode = nextNode
            }
          }
      }

      if (lastEscaped < original.length()) {
        val escapedChars = original.subSequence(lastEscaped, original.length())
        val node1        = new Text(escapedChars)
        textBase.fold(node.insertBefore(node1))(_.appendChild(node1))
        state.nodeAdded(node1)
      }
    }

    if (processedNode) {
      node.unlink()
      state.nodeRemoved(node)
    }
  }
}

object AutolinkNodePostProcessor {

  private val URI_PREFIX: Pattern = Pattern.compile("\\b([a-z][a-z0-9+.-]*://)(?:\\s|$)")

  // Regex-based URL/email detection replacing org.nibor.autolink
  private val URL_PATTERN: Pattern = Pattern.compile(
    "\\b(?:" +
      "[a-zA-Z][a-zA-Z0-9+.\\-]*://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+" + // URLs with any scheme
      "|" +
      "www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+" + // www. URLs
      ")"
  )

  private val EMAIL_PATTERN: Pattern = Pattern.compile(
    "\\b[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}\\b"
  )

  enum LinkType {
    case URL, EMAIL
  }

  final class LinkSpan(val linkType: LinkType, val beginIndex: Int, val endIndex: Int)

  /** Strip trailing punctuation from a URL match, emulating org.nibor.autolink behavior. Keeps balanced parentheses (e.g., `foo_(bar)` retains trailing `)`).
    */
  private def adjustUrlEnd(text: CharSequence, start: Int, end: Int): Int = boundary {
    var e = end
    // Strip trailing sentence-ending punctuation that is not part of the URL
    while (e > start) {
      val c = text.charAt(e - 1)
      if (c == '.' || c == ',' || c == ';' || c == '?' || c == '!' || c == '"' || c == '\'' || c == ':') {
        e -= 1
      } else if (c == ')') {
        // Only strip closing paren if it has no matching open paren in the URL
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
          // balanced — stop stripping
          break(e)
        }
      } else {
        break(e)
      }
    }
    e
  }

  private def extractLinks(text: BasedSequence, out: ArrayList[LinkSpan]): Unit = {
    // Extract URLs
    val urlMatcher = URL_PATTERN.matcher(text)
    while (urlMatcher.find()) {
      val adjustedEnd = adjustUrlEnd(text, urlMatcher.start(), urlMatcher.end())
      if (adjustedEnd > urlMatcher.start()) {
        out.add(new LinkSpan(LinkType.URL, urlMatcher.start(), adjustedEnd))
      }
    }

    // Extract emails
    val emailMatcher = EMAIL_PATTERN.matcher(text)
    while (emailMatcher.find()) {
      var overlaps = false
      val iter     = out.iterator()
      while (iter.hasNext && !overlaps) {
        val existing = iter.next()
        if (emailMatcher.start() >= existing.beginIndex && emailMatcher.start() < existing.endIndex) {
          overlaps = true
        }
      }
      if (!overlaps) {
        out.add(new LinkSpan(LinkType.EMAIL, emailMatcher.start(), emailMatcher.end()))
      }
    }

    // Sort by start position
    out.sort((a, b) => Integer.compare(a.beginIndex, b.beginIndex))
  }

  class Factory extends NodePostProcessorFactory(false) {
    // TODO: figure out why optimization does not work after AutoLink inserted by inline parser
    // addNodeWithExclusions(Text.class, DoNotDecorate.class, DoNotLinkDecorate.class);
    addNodes(classOf[Text])

    override def apply(document: Document): NodePostProcessor = new AutolinkNodePostProcessor(document)
  }
}
