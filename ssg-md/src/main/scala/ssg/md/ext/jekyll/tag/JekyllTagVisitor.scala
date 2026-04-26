/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTagVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTagVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package jekyll
package tag

trait JekyllTagVisitor {
  def visit(node: JekyllTag):      Unit
  def visit(node: JekyllTagBlock): Unit
}
