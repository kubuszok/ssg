/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceNodePostProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.ast.Heading
import ssg.md.ext.attributes.{ AttributeNode, AttributesNode }
import ssg.md.html.renderer.{ HeaderIdGenerator, HtmlIdGenerator }
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ Document, Node, NodeTracker }

import scala.language.implicitConversions

class EnumeratedReferenceNodePostProcessor(document: Document) extends NodePostProcessor {

  private val enumeratedReferences: EnumeratedReferences = EnumeratedReferenceExtension.ENUMERATED_REFERENCE_ORDINALS.get(document)
  private val headerIdGenerator:    HtmlIdGenerator      = new HeaderIdGenerator.Factory().create()
  headerIdGenerator.generateIds(document)

  override def process(state: NodeTracker, node: Node): Unit =
    if (node.isInstanceOf[AttributesNode]) {
      val attributesNode = node.asInstanceOf[AttributesNode]

      val iter  = attributesNode.children.iterator()
      var found = false
      while (iter.hasNext && !found) {
        val attributeNode = iter.next()
        if (attributeNode.isInstanceOf[AttributeNode]) {
          if (attributeNode.asInstanceOf[AttributeNode].isId) {
            val text = attributeNode.asInstanceOf[AttributeNode].value.toString
            enumeratedReferences.add(text)
            found = true
          }
        }
      }
    } else if (node.isInstanceOf[Heading]) {
      // see if it has bare enum reference text
      val iter = node.children.iterator()
      while (iter.hasNext) {
        val child = iter.next()
        if (child.isInstanceOf[EnumeratedReferenceText]) {
          val text    = child.asInstanceOf[EnumeratedReferenceText].text
          val typeStr = EnumeratedReferenceRepository.getType(text.toString)
          if (typeStr.isEmpty || text.equals(typeStr + ":")) {
            val id = (if (typeStr.isEmpty) text.toString else typeStr) + ":" + headerIdGenerator.getId(node)
            enumeratedReferences.add(id)
          }
        }
      }
    }
}

object EnumeratedReferenceNodePostProcessor {

  class Factory extends NodePostProcessorFactory(false) {
    addNodes(classOf[AttributesNode], classOf[Heading])

    override def apply(document: Document): NodePostProcessor =
      new EnumeratedReferenceNodePostProcessor(document)
  }
}
