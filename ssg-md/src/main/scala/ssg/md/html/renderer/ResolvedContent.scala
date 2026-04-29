/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/ResolvedContent.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/ResolvedContent.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.Nullable

class ResolvedContent(
  val resolvedLink: ResolvedLink,
  val status:       LinkStatus,
  val content:      Nullable[Array[Byte]]
) {

  def withStatus(status: LinkStatus): ResolvedContent =
    if (status eq this.status) this
    else new ResolvedContent(resolvedLink, status, content)

  def withContent(content: Nullable[Array[Byte]]): ResolvedContent = {
    val same = (this.content.isEmpty && content.isEmpty) ||
      (this.content.isDefined && content.isDefined &&
        java.util.Arrays.equals(this.content.get, content.get))
    if (same) this
    else new ResolvedContent(resolvedLink, status, content)
  }

  override def equals(o: Any): Boolean = o match {
    case that: ResolvedContent =>
      (this eq that) ||
      (resolvedLink == that.resolvedLink &&
        status == that.status &&
        ((content.isEmpty && that.content.isEmpty) ||
          (content.isDefined && that.content.isDefined &&
            java.util.Arrays.equals(content.get, that.content.get))))
    case _ => false
  }

  override def hashCode(): Int = {
    var result = resolvedLink.hashCode()
    result = 31 * result + status.hashCode()
    result = 31 * result + content.fold(0)(java.util.Arrays.hashCode)
    result
  }
}
