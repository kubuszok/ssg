/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/AttributablePart.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/AttributablePart.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

/** Specifies the node part for which attributes can be provided
  */
class AttributablePart(val name: String) {

  override def equals(o: Any): Boolean = this eq o.asInstanceOf[AnyRef]

  override def hashCode(): Int = super.hashCode()
}

object AttributablePart {
  val NODE:          AttributablePart = new AttributablePart("NODE")
  val NODE_POSITION: AttributablePart = new AttributablePart("NODE_POSITION")
  val LINK:          AttributablePart = new AttributablePart("LINK")
  val ID:            AttributablePart = new AttributablePart("ID")
}
