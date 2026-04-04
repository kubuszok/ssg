/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/AtomNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Idiom: Singleton sentinels as object vals
 */
package ssg
package liquid
package nodes

class AtomNode(private val value: Any) extends LNode {

  override def render(context: TemplateContext): Any = value
}

object AtomNode {

  /** Sentinel for Liquid's `empty` keyword. */
  val EMPTY: AtomNode = new AtomNode(new Object() {
    override def toString: String = ""
  })

  /** Sentinel for Liquid's `blank` keyword. */
  val BLANK: AtomNode = new AtomNode(new Object() {
    override def toString: String = ""
  })

  /** Returns true if the object is the EMPTY sentinel. */
  def isEmpty(o: Any): Boolean = o.asInstanceOf[AnyRef] eq EMPTY.value.asInstanceOf[AnyRef]

  /** Returns true if the object is the BLANK sentinel. */
  def isBlank(o: Any): Boolean = o.asInstanceOf[AnyRef] eq BLANK.value.asInstanceOf[AnyRef]
}
