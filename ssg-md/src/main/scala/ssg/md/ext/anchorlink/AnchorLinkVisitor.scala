/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/AnchorLinkVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/AnchorLinkVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package anchorlink

trait AnchorLinkVisitor {
  def visit(node: AnchorLink): Unit
}
