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
 *   Idiom: Singleton sentinels as DataView.EMPTY/DataView.BLANK
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/AtomNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView

class AtomNode(private val value: DataView) extends LNode {

  override def render(context: TemplateContext): DataView = value
}

object AtomNode {

  /** Sentinel for Liquid's `empty` keyword. */
  val EMPTY: AtomNode = new AtomNode(DataView.EMPTY)

  /** Sentinel for Liquid's `blank` keyword. */
  val BLANK: AtomNode = new AtomNode(DataView.BLANK)

  /** Returns true if the DataView is the EMPTY sentinel. */
  def isEmpty(o: DataView): Boolean = o eq DataView.EMPTY

  /** Returns true if the DataView is the BLANK sentinel. */
  def isBlank(o: DataView): Boolean = o eq DataView.BLANK
}
