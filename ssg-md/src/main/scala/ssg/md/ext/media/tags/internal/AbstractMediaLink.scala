/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/internal/AbstractMediaLink.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/internal/AbstractMediaLink.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package media
package tags
package internal

import ssg.md.ast.{ InlineLinkNode, Link }
import ssg.md.util.sequence.BasedSequence

abstract class AbstractMediaLink(val prefix: String, val typeName: String) extends InlineLinkNode {

  def this(prefix: String, typeName: String, other: Link) = {
    this(prefix, typeName)
    this.chars = other.baseSubSequence(other.startOffset - prefix.length, other.endOffset)
    this.textOpeningMarker = other.baseSubSequence(other.startOffset - prefix.length, other.textOpeningMarker.endOffset)
    this.text = other.text
    this.textClosingMarker = other.textClosingMarker
    this.linkOpeningMarker = other.linkOpeningMarker
    this.url = other.url
    this.titleOpeningMarker = other.titleOpeningMarker
    this.title = other.title
    this.titleClosingMarker = other.titleClosingMarker
    this.linkClosingMarker = other.linkClosingMarker

    verifyBasedSequence(other.chars, other.startOffset - prefix.length)
  }

  override def setTextChars(textChars: BasedSequence): Unit = {
    verifyBasedSequence(textChars, 0)

    val textCharsLength = textChars.length()
    textOpeningMarker = textChars.subSequence(0, prefix.length + 1) // grab n characters, n - 1 for the PREFIX and 1 for the opener
    text = textChars.subSequence(prefix.length + 2, textCharsLength - 1).trim()
    textClosingMarker = textChars.subSequence(textCharsLength - 1, textCharsLength)
  }

  final protected def verifyBasedSequence(chars: BasedSequence, startOffset: Int): Unit =
    if (!chars.baseSubSequence(startOffset, startOffset + prefix.length).matches(prefix)) {
      throw new IllegalArgumentException(s"$typeName Link's CharSequence MUST start with an '$prefix'!")
    }
}
