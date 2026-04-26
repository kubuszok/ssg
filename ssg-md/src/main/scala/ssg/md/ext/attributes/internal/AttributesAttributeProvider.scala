/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesAttributeProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesAttributeProvider.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.ast.AnchorRefTarget
import ssg.md.html.{ AttributeProvider, IndependentAttributeProviderFactory }
import ssg.md.html.renderer.{ AttributablePart, CoreNodeRenderer, LinkResolverContext }
import ssg.md.util.ast.Node
import ssg.md.util.html.{ Attribute, MutableAttributes }

import scala.language.implicitConversions

class AttributesAttributeProvider(context: LinkResolverContext) extends AttributeProvider {

  private val attributeOptions:        AttributesOptions       = new AttributesOptions(context.getOptions)
  private val nodeAttributeRepository: NodeAttributeRepository = AttributesExtension.NODE_ATTRIBUTES.get(context.getOptions)

  override def setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes): Unit = {
    // regression bug, issue #372, add option, default to both as before
    val shouldApply =
      if (part eq CoreNodeRenderer.CODE_CONTENT) attributeOptions.fencedCodeAddAttributes.addToCode
      else attributeOptions.fencedCodeAddAttributes.addToPre

    if (shouldApply) {
      val nodeAttributesList = nodeAttributeRepository.get(node)
      if (nodeAttributesList != null) { // @nowarn - Java Map.get returns null
        // add these as attributes
        val it = nodeAttributesList.iterator()
        while (it.hasNext) {
          val nodeAttributes = it.next()
          val childIt        = nodeAttributes.children.iterator()
          while (childIt.hasNext) {
            val attribute = childIt.next()
            attribute match {
              case attributeNode: AttributeNode =>
                if (!attributeNode.isImplicitName) {
                  val attributeNodeName = attributeNode.name
                  if (attributeNodeName.isNotNull && !attributeNodeName.isBlank()) {
                    if (!attributeNodeName.equals(Attribute.CLASS_ATTR)) {
                      attributes.remove(attributeNodeName)
                    }
                    attributes.addValue(attributeNodeName, attributeNode.value)
                  }
                  // else empty then ignore
                } else {
                  // implicit
                  if (attributeNode.isClass) {
                    attributes.addValue(Attribute.CLASS_ATTR, attributeNode.value)
                  } else if (attributeNode.isId) {
                    if (node.isInstanceOf[AnchorRefTarget]) {
                      // was already provided via setAnchorRefId
                    } else {
                      attributes.remove(Attribute.ID_ATTR)
                      attributes.addValue(Attribute.ID_ATTR, attributeNode.value)
                    }
                  } else {
                    // unknown
                    throw new IllegalStateException("Implicit attribute yet not class or id")
                  }
                }
              case _ => // not an AttributeNode, continue
            }
          }
        }
      }
    }
  }
}

object AttributesAttributeProvider {

  class Factory extends IndependentAttributeProviderFactory {

    override def apply(context: LinkResolverContext): AttributeProvider = new AttributesAttributeProvider(context)
  }
}
