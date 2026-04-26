/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/AttributeNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/nodes/AttributeNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package nodes

class AttributeNode(private val key: LNode, private val value: LNode) extends LNode {

  override def render(context: TemplateContext): Any =
    Array[Any](key.render(context), value.render(context))
}
