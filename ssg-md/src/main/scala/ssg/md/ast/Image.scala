/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/Image.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

class Image extends InlineLinkNode {

  var urlContent: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(
    textOpenMarker:   BasedSequence,
    text:             BasedSequence,
    textCloseMarker:  BasedSequence,
    linkOpenMarker:   BasedSequence,
    url:              BasedSequence,
    titleOpenMarker:  BasedSequence,
    title:            BasedSequence,
    titleCloseMarker: BasedSequence,
    linkCloseMarker:  BasedSequence
  ) = {
    this()
    this.textOpeningMarker = textOpenMarker
    this.text = text.trim()
    this.textClosingMarker = textCloseMarker
    this.linkOpeningMarker = linkOpenMarker
    this.url = url
    this.titleOpeningMarker = titleOpenMarker
    this.title = title
    this.titleClosingMarker = titleCloseMarker
    this.linkClosingMarker = linkCloseMarker
  }

  def this(
    chars:            BasedSequence,
    textOpenMarker:   BasedSequence,
    text:             BasedSequence,
    textCloseMarker:  BasedSequence,
    linkOpenMarker:   BasedSequence,
    url:              BasedSequence,
    titleOpenMarker:  BasedSequence,
    title:            BasedSequence,
    titleCloseMarker: BasedSequence,
    linkCloseMarker:  BasedSequence
  ) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpenMarker
    this.text = text.trim()
    this.textClosingMarker = textCloseMarker
    this.linkOpeningMarker = linkOpenMarker
    this.url = url
    this.titleOpeningMarker = titleOpenMarker
    this.title = title
    this.titleClosingMarker = titleCloseMarker
    this.linkClosingMarker = linkCloseMarker
  }

  def this(
    textOpenMarker:  BasedSequence,
    text:            BasedSequence,
    textCloseMarker: BasedSequence,
    linkOpenMarker:  BasedSequence,
    url:             BasedSequence,
    linkCloseMarker: BasedSequence
  ) = {
    this()
    this.textOpeningMarker = textOpenMarker
    this.text = text.trim()
    this.textClosingMarker = textCloseMarker
    this.linkOpeningMarker = linkOpenMarker
    this.url = url
    this.linkClosingMarker = linkCloseMarker
  }

  def this(
    chars:           BasedSequence,
    textOpenMarker:  BasedSequence,
    text:            BasedSequence,
    textCloseMarker: BasedSequence,
    linkOpenMarker:  BasedSequence,
    url:             BasedSequence,
    linkCloseMarker: BasedSequence
  ) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpenMarker
    this.text = text.trim()
    this.textClosingMarker = textCloseMarker
    this.linkOpeningMarker = linkOpenMarker
    this.url = url
    this.linkClosingMarker = linkCloseMarker
  }

  override def segments: Array[BasedSequence] =
    Array(
      textOpeningMarker,
      text,
      textClosingMarker,
      linkOpeningMarker,
      urlOpeningMarker,
      url,
      pageRef,
      anchorMarker,
      anchorRef,
      urlClosingMarker,
      urlContent,
      titleOpeningMarker,
      titleOpeningMarker,
      title,
      titleClosingMarker,
      linkClosingMarker
    )

  override def astExtra(out: StringBuilder): Unit = {
    Node.delimitedSegmentSpanChars(out, textOpeningMarker, text, textClosingMarker, "text")
    Node.segmentSpanChars(out, linkOpeningMarker, "linkOpen")
    Node.delimitedSegmentSpanChars(out, urlOpeningMarker, url, urlClosingMarker, "url")
    if (pageRef.isNotNull) Node.segmentSpanChars(out, pageRef, "pageRef")
    if (anchorMarker.isNotNull) Node.segmentSpanChars(out, anchorMarker, "anchorMarker")
    if (anchorRef.isNotNull) Node.segmentSpanChars(out, anchorRef, "anchorRef")
    if (urlContent.isNotNull) Node.segmentSpanChars(out, urlContent, "urlContent")
    Node.delimitedSegmentSpanChars(out, titleOpeningMarker, title, titleClosingMarker, "title")
    Node.segmentSpanChars(out, linkClosingMarker, "linkClose")
  }

  override def setTextChars(textChars: BasedSequence): Unit = {
    val textCharsLength = textChars.length
    this.textOpeningMarker = textChars.subSequence(0, 2)
    this.text = textChars.subSequence(2, textCharsLength - 1).trim()
    this.textClosingMarker = textChars.subSequence(textCharsLength - 1, textCharsLength)
  }
}
