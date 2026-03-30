/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/VideoLink.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package media
package tags

import ssg.md.ast.Link
import ssg.md.ext.media.tags.internal.AbstractMediaLink

class VideoLink() extends AbstractMediaLink(VideoLink.PREFIX, VideoLink.TYPE) {

  def this(other: Link) = {
    this()
    this.chars = other.baseSubSequence(other.startOffset - VideoLink.PREFIX.length, other.endOffset)
    this.textOpeningMarker = other.baseSubSequence(other.startOffset - VideoLink.PREFIX.length, other.textOpeningMarker.endOffset)
    this.text = other.text
    this.textClosingMarker = other.textClosingMarker
    this.linkOpeningMarker = other.linkOpeningMarker
    this.url = other.url
    this.titleOpeningMarker = other.titleOpeningMarker
    this.title = other.title
    this.titleClosingMarker = other.titleClosingMarker
    this.linkClosingMarker = other.linkClosingMarker
    verifyBasedSequence(other.chars, other.startOffset - VideoLink.PREFIX.length)
  }

  // This class leaves room for specialization, should we need it.
  // Additionally, it makes managing different Node types easier for users.
}

object VideoLink {
  val PREFIX: String = "!V"
  private val TYPE: String = "Video"
}
