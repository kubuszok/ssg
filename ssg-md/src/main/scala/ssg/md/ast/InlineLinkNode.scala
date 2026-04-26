/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/InlineLinkNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/InlineLinkNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

abstract class InlineLinkNode extends LinkNode {
  var textOpeningMarker: BasedSequence = BasedSequence.NULL
  var text:              BasedSequence = BasedSequence.NULL
  var textClosingMarker: BasedSequence = BasedSequence.NULL
  var linkOpeningMarker: BasedSequence = BasedSequence.NULL
  var linkClosingMarker: BasedSequence = BasedSequence.NULL

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
      titleOpeningMarker,
      title,
      titleClosingMarker,
      linkClosingMarker
    )

  override def segmentsForChars: Array[BasedSequence] =
    Array(
      textOpeningMarker,
      text,
      textClosingMarker,
      linkOpeningMarker,
      urlOpeningMarker,
      pageRef,
      anchorMarker,
      anchorRef,
      urlClosingMarker,
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
    Node.delimitedSegmentSpanChars(out, titleOpeningMarker, title, titleClosingMarker, "title")
    Node.segmentSpanChars(out, linkClosingMarker, "linkClose")
  }

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(
    textOpeningMarker:  BasedSequence,
    text:               BasedSequence,
    textClosingMarker:  BasedSequence,
    linkOpeningMarker:  BasedSequence,
    url:                BasedSequence,
    titleOpeningMarker: BasedSequence,
    title:              BasedSequence,
    titleClosingMarker: BasedSequence,
    linkClosingMarker:  BasedSequence
  ) = {
    this()
    this.textOpeningMarker = textOpeningMarker
    this.text = text.trim()
    this.textClosingMarker = textClosingMarker
    this.linkOpeningMarker = linkOpeningMarker
    this.url = url
    this.titleOpeningMarker = titleOpeningMarker
    this.title = title
    this.titleClosingMarker = titleClosingMarker
    this.linkClosingMarker = linkClosingMarker
  }

  def this(
    chars:              BasedSequence,
    textOpeningMarker:  BasedSequence,
    text:               BasedSequence,
    textClosingMarker:  BasedSequence,
    linkOpeningMarker:  BasedSequence,
    url:                BasedSequence,
    titleOpeningMarker: BasedSequence,
    title:              BasedSequence,
    titleClosingMarker: BasedSequence,
    linkClosingMarker:  BasedSequence
  ) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpeningMarker
    this.text = text.trim()
    this.textClosingMarker = textClosingMarker
    this.linkOpeningMarker = linkOpeningMarker
    this.url = url
    this.titleOpeningMarker = titleOpeningMarker
    this.title = title
    this.titleClosingMarker = titleClosingMarker
    this.linkClosingMarker = linkClosingMarker
  }

  def this(
    textOpeningMarker: BasedSequence,
    text:              BasedSequence,
    textClosingMarker: BasedSequence,
    linkOpeningMarker: BasedSequence,
    url:               BasedSequence,
    linkClosingMarker: BasedSequence
  ) = {
    this()
    this.textOpeningMarker = textOpeningMarker
    this.text = text.trim()
    this.textClosingMarker = textClosingMarker
    this.linkOpeningMarker = linkOpeningMarker
    this.url = url
    this.linkClosingMarker = linkClosingMarker
  }

  def this(
    chars:             BasedSequence,
    textOpeningMarker: BasedSequence,
    text:              BasedSequence,
    textClosingMarker: BasedSequence,
    linkOpeningMarker: BasedSequence,
    url:               BasedSequence,
    linkClosingMarker: BasedSequence
  ) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpeningMarker
    this.text = text.trim()
    this.textClosingMarker = textClosingMarker
    this.linkOpeningMarker = linkOpeningMarker
    this.url = url
    this.linkClosingMarker = linkClosingMarker
  }

  def setUrl(linkOpeningMarker: BasedSequence, url: BasedSequence, linkClosingMarker: BasedSequence): Unit = {
    this.linkOpeningMarker = linkOpeningMarker
    setUrlChars(url)
    this.linkClosingMarker = linkClosingMarker
  }

  def setTextChars(textChars: BasedSequence): Unit

  override protected def toStringAttributes: String =
    "text=" + text + ", url=" + url + ", title=" + title
}
