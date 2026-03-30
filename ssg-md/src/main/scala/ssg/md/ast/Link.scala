/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/Link.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.sequence.BasedSequence

class Link extends InlineLinkNode {

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

  override def setTextChars(textChars: BasedSequence): Unit = {
    val textCharsLength = textChars.length
    this.textOpeningMarker = textChars.subSequence(0, 1)
    this.text = textChars.subSequence(1, textCharsLength - 1).trim()
    this.textClosingMarker = textChars.subSequence(textCharsLength - 1, textCharsLength)
  }
}
