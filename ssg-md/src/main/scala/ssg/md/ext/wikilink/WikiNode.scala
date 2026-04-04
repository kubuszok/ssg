/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink

import ssg.md.ast.LinkRefDerived
import ssg.md.util.ast.{ DoNotDecorate, Node, NodeVisitor, TextContainer }
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.{ BasedSequence, Escaping, ReplacedTextMapper }
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions

class WikiNode(val linkIsFirst: Boolean) extends Node, DoNotDecorate, TextContainer, LinkRefDerived {

  var openingMarker:       BasedSequence = BasedSequence.NULL
  var link:                BasedSequence = BasedSequence.NULL
  var pageRef:             BasedSequence = BasedSequence.NULL
  var anchorMarker:        BasedSequence = BasedSequence.NULL
  var anchorRef:           BasedSequence = BasedSequence.NULL
  var textSeparatorMarker: BasedSequence = BasedSequence.NULL
  var text:                BasedSequence = BasedSequence.NULL
  var closingMarker:       BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence, linkIsFirst: Boolean, allowAnchors: Boolean, canEscapePipe: Boolean, canEscapeAnchor: Boolean) = {
    this(linkIsFirst)
    this.chars = chars
    setLinkChars(chars, allowAnchors, canEscapePipe, canEscapeAnchor)
  }

  override def segments: Array[BasedSequence] =
    if (linkIsFirst) {
      Array(openingMarker, link, pageRef, anchorMarker, anchorRef, textSeparatorMarker, text, closingMarker)
    } else {
      Array(openingMarker, text, textSeparatorMarker, link, pageRef, anchorMarker, anchorRef, closingMarker)
    }

  override def astExtra(out: StringBuilder): Unit =
    if (linkIsFirst) {
      Node.segmentSpanChars(out, openingMarker, "linkOpen")
      Node.segmentSpanChars(out, text, "text")
      Node.segmentSpanChars(out, textSeparatorMarker, "textSep")
      Node.segmentSpanChars(out, link, "link")
      if (pageRef.isNotNull) Node.segmentSpanChars(out, pageRef, "pageRef")
      if (anchorMarker.isNotNull) Node.segmentSpanChars(out, anchorMarker, "anchorMarker")
      if (anchorRef.isNotNull) Node.segmentSpanChars(out, anchorRef, "anchorRef")
      Node.segmentSpanChars(out, closingMarker, "linkClose")
    } else {
      Node.segmentSpanChars(out, openingMarker, "linkOpen")
      Node.segmentSpanChars(out, link, "link")
      if (pageRef.isNotNull) Node.segmentSpanChars(out, pageRef, "pageRef")
      if (anchorMarker.isNotNull) Node.segmentSpanChars(out, anchorMarker, "anchorMarker")
      if (anchorRef.isNotNull) Node.segmentSpanChars(out, anchorRef, "anchorRef")
      Node.segmentSpanChars(out, textSeparatorMarker, "textSep")
      Node.segmentSpanChars(out, text, "text")
      Node.segmentSpanChars(out, closingMarker, "linkClose")
    }

  /** @return true if this node will be rendered as text because it depends on a reference which is not defined. */
  override def isTentative: Boolean = false

  def setLink(linkChars: BasedSequence, allowAnchors: Boolean, canEscapeAnchor: Boolean): Unit = {
    // now parse out the # from the link
    this.link = linkChars

    if (!allowAnchors) {
      this.pageRef = linkChars
    } else {
      var pos      = -1
      var continue = true
      while (continue) {
        pos = linkChars.indexOf('#', pos + 1)
        if (pos == -1) {
          continue = false
        } else if (canEscapeAnchor && pos > 0 && linkChars.charAt(pos - 1) == '\\' && (linkChars.subSequence(0, pos).countTrailing(CharPredicate.BACKSLASH) & 1) == 1) {
          // escaped, keep searching
        } else {
          continue = false
        }
      }

      if (pos < 0) {
        this.pageRef = linkChars
      } else {
        this.pageRef = linkChars.subSequence(0, pos)
        this.anchorMarker = linkChars.subSequence(pos, pos + 1)
        this.anchorRef = linkChars.subSequence(pos + 1)
      }
    }
  }

  def setLinkChars(linkChars: BasedSequence, allowAnchors: Boolean, canEscapePipe: Boolean, canEscapeAnchor: Boolean): Unit = {
    val length = linkChars.length()
    val start  = if (this.isInstanceOf[WikiImage]) 3 else 2
    openingMarker = linkChars.subSequence(0, start)
    closingMarker = linkChars.subSequence(length - 2, length)

    var pos: Int = 0
    if (linkIsFirst) {
      pos = linkChars.length() - 2
      var continue = true
      while (continue) {
        pos = linkChars.lastIndexOf(WikiNode.SEPARATOR_CHAR, pos - 1)
        if (pos == -1) {
          continue = false
        } else if (canEscapePipe && pos > 0 && linkChars.charAt(pos - 1) == '\\' && (linkChars.subSequence(0, pos).countTrailing(CharPredicate.BACKSLASH) & 1) == 1) {
          // escaped, keep searching
        } else {
          continue = false
        }
      }
    } else {
      pos = -1
      var continue = true
      while (continue) {
        pos = linkChars.indexOf(WikiNode.SEPARATOR_CHAR, pos + 1)
        if (pos == -1) {
          continue = false
        } else if (canEscapePipe && pos > 0 && linkChars.charAt(pos - 1) == '\\' && (linkChars.subSequence(0, pos).countTrailing(CharPredicate.BACKSLASH) & 1) == 1) {
          // escaped, keep searching
        } else {
          continue = false
        }
      }
    }

    var parsedLink: BasedSequence = BasedSequence.NULL
    if (pos < 0) {
      parsedLink = linkChars.subSequence(start, length - 2)
    } else {
      textSeparatorMarker = linkChars.subSequence(pos, pos + 1)
      if (linkIsFirst) {
        parsedLink = linkChars.subSequence(start, pos)
        text = linkChars.subSequence(pos + 1, length - 2)
      } else {
        text = linkChars.subSequence(start, pos)
        parsedLink = linkChars.subSequence(pos + 1, length - 2)
      }
    }

    setLink(parsedLink, allowAnchors, canEscapeAnchor)

    if (text.isNull && allowAnchors && !anchorMarker.isNull) {
      // have anchor ref, remove it from text
      text = pageRef
    }
  }

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    val urlType = flags & TextContainer.F_LINK_TEXT_TYPE

    val textSeq: BasedSequence = urlType match {
      case TextContainer.F_LINK_PAGE_REF  => pageRef
      case TextContainer.F_LINK_ANCHOR    => anchorRef
      case TextContainer.F_LINK_URL       => link
      case TextContainer.F_LINK_NODE_TEXT => BasedSequence.NULL // not used
      case _                              => // F_LINK_TEXT or default
        // return true to collect text from children
        BasedSequence.NULL // sentinel
    }

    if (
      urlType != TextContainer.F_LINK_TEXT && urlType != TextContainer.F_LINK_PAGE_REF && urlType != TextContainer.F_LINK_ANCHOR && urlType != TextContainer.F_LINK_URL && urlType != TextContainer.F_LINK_NODE_TEXT
    ) {
      // default case: F_LINK_TEXT
      true
    } else if (urlType == TextContainer.F_LINK_NODE_TEXT) {
      out.append(chars)
      false
    } else if (urlType == TextContainer.F_LINK_TEXT) {
      true
    } else {
      val textMapper = new ReplacedTextMapper(textSeq)
      val unescaped  = Escaping.unescape(textSeq, textMapper)
      out.append(unescaped)
      false
    }
  }
}

object WikiNode {
  val SEPARATOR_CHAR: Char = '|'
}
