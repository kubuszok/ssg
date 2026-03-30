/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkStatus.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html
package renderer

class LinkStatus(val name: String) {

  override def equals(o: Any): Boolean = o match {
    case that: LinkStatus => (this eq that) || name == that.name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode

  def isStatus(status: CharSequence): Boolean = {
    val s = status match {
      case str: String => str
      case cs => String.valueOf(cs)
    }
    name == s
  }
}

object LinkStatus {
  val UNKNOWN:   LinkStatus = new LinkStatus("UNKNOWN")
  val VALID:     LinkStatus = new LinkStatus("VALID")
  val INVALID:   LinkStatus = new LinkStatus("INVALID")
  val UNCHECKED: LinkStatus = new LinkStatus("UNCHECKED")
  val NOT_FOUND: LinkStatus = new LinkStatus("NOT_FOUND")
}
