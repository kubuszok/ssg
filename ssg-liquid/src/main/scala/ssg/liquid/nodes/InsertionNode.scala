/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/InsertionNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/InsertionNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import java.util.{ List => JList }

class InsertionNode(
  private val tagName:   String,
  private val insertion: Insertion,
  private val tokens:    Array[LNode]
) extends LNode {

  def this(insertion: Insertion, tokens: Array[LNode]) =
    this(if (insertion != null) insertion.name else null, insertion, tokens)

  def this(insertion: Insertion, tokens: JList[LNode]) =
    this(insertion, tokens.toArray(new Array[LNode](0)))

  if (tagName == null) {
    throw new IllegalArgumentException("tagName == null")
  }
  if (insertion == null) {
    throw new IllegalArgumentException("no tag available named: " + tagName)
  }

  override def render(context: TemplateContext): Any =
    insertion.render(context, tokens)
}
