/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/internal/AnchorLinkNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package anchorlink
package internal

import ssg.md.Nullable
import ssg.md.ast.{ BlockQuote, Heading }
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ Document, Node, NodeTracker }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AnchorLinkNodePostProcessor(options: DataHolder) extends NodePostProcessor {

  private val anchorLinkOptions = new AnchorLinkOptions(options)

  override def process(state: NodeTracker, node: Node): Unit =
    node match {
      case heading: Heading =>
        if (heading.text.isNotNull) {
          val anchor = new AnchorLink()

          if (!anchorLinkOptions.wrapText) {
            if (heading.firstChild.isEmpty) {
              anchor.chars = heading.text.subSequence(0, 0)
              heading.appendChild(anchor)
            } else {
              anchor.chars = heading.firstChild.get.chars.subSequence(0, 0)
              heading.firstChild.get.insertBefore(anchor)
            }
          } else {
            anchor.takeChildren(heading)
            heading.appendChild(anchor)
          }

          anchor.setCharsFromContent()
          state.nodeAdded(anchor)
        }
      case _ => // do nothing
    }
}

object AnchorLinkNodePostProcessor {

  class Factory(options: DataHolder) extends NodePostProcessorFactory(false) {

    if (AnchorLinkExtension.ANCHORLINKS_NO_BLOCK_QUOTE.get(options)) {
      addNodeWithExclusions(classOf[Heading], classOf[BlockQuote])
    } else {
      addNodes(classOf[Heading])
    }

    override def apply(document: Document): NodePostProcessor =
      new AnchorLinkNodePostProcessor(document)
  }
}
