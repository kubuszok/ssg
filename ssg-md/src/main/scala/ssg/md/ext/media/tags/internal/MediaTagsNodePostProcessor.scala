/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/internal/MediaTagsNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/internal/MediaTagsNodePostProcessor.java
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

import ssg.md.Nullable
import ssg.md.ast.{ Link, Text }
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ Document, Node, NodeTracker }
import ssg.md.util.data.DataHolder
import ssg.md.util.misc.CharPredicate

import scala.language.implicitConversions

class MediaTagsNodePostProcessor(options: DataHolder) extends NodePostProcessor {

  override def process(state: NodeTracker, node: Node): Unit =
    if (node.isInstanceOf[Link]) {
      val previous = node.previous

      if (previous.isDefined && previous.get.isInstanceOf[Text]) {
        val prev  = previous.get
        val chars = prev.chars
        if (chars.isContinuedBy(node.chars)) {
          val mediaLinkOpt: Nullable[AbstractMediaLink] =
            if (chars.endsWith(AudioLink.PREFIX) && !isEscaped(chars, AudioLink.PREFIX)) {
              Nullable(new AudioLink(node.asInstanceOf[Link]))
            } else if (chars.endsWith(EmbedLink.PREFIX) && !isEscaped(chars, EmbedLink.PREFIX)) {
              Nullable(new EmbedLink(node.asInstanceOf[Link]))
            } else if (chars.endsWith(PictureLink.PREFIX) && !isEscaped(chars, PictureLink.PREFIX)) {
              Nullable(new PictureLink(node.asInstanceOf[Link]))
            } else if (chars.endsWith(VideoLink.PREFIX) && !isEscaped(chars, VideoLink.PREFIX)) {
              Nullable(new VideoLink(node.asInstanceOf[Link]))
            } else {
              // None of the Above, abort postprocess
              Nullable.empty
            }

          mediaLinkOpt.foreach { mediaLink =>
            mediaLink.takeChildren(node)
            node.unlink()
            state.nodeRemoved(node)
            prev.insertAfter(mediaLink)
            state.nodeAddedWithChildren(mediaLink)
            prev.chars = chars.subSequence(0, chars.length() - mediaLink.prefix.length)
            if (prev.chars.length() == 0) {
              prev.unlink()
              state.nodeRemoved(prev)
            }
          }
        }
      }
    }

  private def isEscaped(chars: ssg.md.util.sequence.BasedSequence, prefix: String): Boolean = {
    val backslashCount = chars.subSequence(0, chars.length() - prefix.length).countTrailing(CharPredicate.BACKSLASH)
    (backslashCount & 1) != 0
  }
}

object MediaTagsNodePostProcessor {

  class Factory(options: DataHolder) extends NodePostProcessorFactory(false) {
    addNodes(classOf[Link])

    override def apply(document: Document): NodePostProcessor =
      new MediaTagsNodePostProcessor(document)
  }
}
