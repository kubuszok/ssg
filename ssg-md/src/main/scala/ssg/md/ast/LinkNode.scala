/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/LinkNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.DoNotLinkDecorate
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.Escaping
import ssg.md.util.sequence.ReplacedTextMapper
import ssg.md.util.sequence.builder.ISequenceBuilder

abstract class LinkNode extends LinkNodeBase with DoNotLinkDecorate with TextContainer {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    val urlType = flags & TextContainer.F_LINK_TEXT_TYPE

    val urlSeq: BasedSequence = urlType match {
      case TextContainer.F_LINK_PAGE_REF  => pageRef
      case TextContainer.F_LINK_ANCHOR    => anchorRef
      case TextContainer.F_LINK_URL       => url
      case TextContainer.F_LINK_NODE_TEXT => BasedSequence.NULL // not used
      case TextContainer.F_LINK_TEXT      => BasedSequence.NULL // sentinel for "return true"
      case _                              => BasedSequence.NULL // sentinel for "return true"
    }

    if (
      urlType == TextContainer.F_LINK_TEXT || (urlType != TextContainer.F_LINK_NODE_TEXT && urlType != TextContainer.F_LINK_PAGE_REF && urlType != TextContainer.F_LINK_ANCHOR && urlType != TextContainer.F_LINK_URL)
    ) {
      true
    } else if (urlType == TextContainer.F_LINK_NODE_TEXT) {
      out.append(chars)
      false
    } else {
      val textMapper     = new ReplacedTextMapper(urlSeq)
      val unescaped      = Escaping.unescape(urlSeq, textMapper)
      val percentDecoded = Escaping.percentDecodeUrl(unescaped)
      out.append(percentDecoded)
      false
    }
  }
}
