/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkType.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkType.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html
package renderer

class LinkType(val name: String) {

  override def equals(o: Any): Boolean = this eq o.asInstanceOf[AnyRef]

  override def hashCode(): Int = super.hashCode()
}

object LinkType {
  val LINK:      LinkType = new LinkType("LINK")
  val IMAGE:     LinkType = new LinkType("IMAGE")
  val LINK_REF:  LinkType = new LinkType("LINK_REF")
  val IMAGE_REF: LinkType = new LinkType("IMAGE_REF")
}
