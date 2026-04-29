/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/YouTubeLink.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/YouTubeLink.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package youtube
package embedded

import ssg.md.ast.{ InlineLinkNode, Link }
import ssg.md.util.sequence.BasedSequence

class YouTubeLink() extends InlineLinkNode {

  def this(other: Link) = {
    this()
    this.chars = other.baseSubSequence(other.startOffset - 1, other.endOffset)
    this.textOpeningMarker = other.baseSubSequence(other.startOffset - 1, other.textOpeningMarker.endOffset)
    this.text = other.text
    this.textClosingMarker = other.textClosingMarker
    this.linkOpeningMarker = other.linkOpeningMarker
    this.url = other.url
    this.titleOpeningMarker = other.titleOpeningMarker
    this.title = other.title
    this.titleClosingMarker = other.titleClosingMarker
    this.linkClosingMarker = other.linkClosingMarker
  }

  override def setTextChars(textChars: BasedSequence): Unit = {
    val textCharsLength = textChars.length()
    this.textOpeningMarker = textChars.subSequence(0, 1)
    this.text = textChars.subSequence(1, textCharsLength - 1).trim()
    this.textClosingMarker = textChars.subSequence(textCharsLength - 1, textCharsLength)
  }
}
