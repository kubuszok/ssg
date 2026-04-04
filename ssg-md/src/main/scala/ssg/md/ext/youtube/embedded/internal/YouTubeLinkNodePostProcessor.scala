/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/internal/YouTubeLinkNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package youtube
package embedded
package internal

import ssg.md.ast.{ Link, Text }
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ Document, Node, NodeTracker }
import ssg.md.util.data.DataHolder
import ssg.md.util.misc.CharPredicate

class YouTubeLinkNodePostProcessor(options: DataHolder) extends NodePostProcessor {

  override def process(state: NodeTracker, node: Node): Unit =
    if (node.isInstanceOf[Link]) {
      val previous = node.previous

      if (previous.isDefined && previous.get.isInstanceOf[Text]) {
        val prev  = previous.get
        val chars = prev.chars
        if (chars.endsWith("@") && chars.isContinuedBy(node.chars)) {
          val prevBackslash = chars.subSequence(0, chars.length() - 1).countTrailing(CharPredicate.BACKSLASH)
          if ((prevBackslash & 1) == 0) {
            // trim previous chars to remove '@'
            prev.chars = chars.subSequence(0, chars.length() - 1)

            val youTubeLink = new YouTubeLink(node.asInstanceOf[Link])
            youTubeLink.takeChildren(node)
            node.unlink()
            prev.insertAfter(youTubeLink)
            state.nodeRemoved(node)
            state.nodeAddedWithChildren(youTubeLink)
          }
        }
      }
    }
}

object YouTubeLinkNodePostProcessor {

  class Factory(options: DataHolder) extends NodePostProcessorFactory(false) {
    addNodes(classOf[Link])

    override def apply(document: Document): NodePostProcessor =
      new YouTubeLinkNodePostProcessor(document)
  }
}
