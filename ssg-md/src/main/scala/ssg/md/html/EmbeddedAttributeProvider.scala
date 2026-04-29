/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/EmbeddedAttributeProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/EmbeddedAttributeProvider.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html

import ssg.md.html.renderer.{ AttributablePart, LinkResolverContext }
import ssg.md.util.ast.Node
import ssg.md.util.html.{ Attributes, MutableAttributes }
import ssg.md.util.sequence.BasedSequence

/** Provider which will provide attributes stored in the node's first EmbeddedNodeAttributes of the node's children
  */
class EmbeddedAttributeProvider extends AttributeProvider {

  override def setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes): Unit =
    if (part == AttributablePart.NODE) {
      val firstChild = node.childOfType(classOf[EmbeddedAttributeProvider.EmbeddedNodeAttributes])
      firstChild.foreach {
        case embedded: EmbeddedAttributeProvider.EmbeddedNodeAttributes =>
          attributes.addValues(embedded.attributes)
        case _ => ()
      }
    }
}

object EmbeddedAttributeProvider {

  val Factory: IndependentAttributeProviderFactory = new IndependentAttributeProviderFactory {
    override def apply(context: LinkResolverContext): AttributeProvider =
      new EmbeddedAttributeProvider()
  }

  // so we can attach attributes to any node in the AST and have a generic attribute provider serve them up
  class EmbeddedNodeAttributes(parent: Node, val attributes: Attributes) extends Node(parent.chars.subSequence(0, 0)) {

    override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

    override def astString(out: StringBuilder, withExtra: Boolean): Unit = {
      out.append("EmbeddedNodeAttributes")
      out.append("[").append(startOffset).append(", ").append(endOffset).append("]")
      out.append(", attributes: ").append(attributes.toString)
      if (withExtra) astExtra(out)
    }

    override def astExtraChars(out: StringBuilder): Unit = {}
  }
}
