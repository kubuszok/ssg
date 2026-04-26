/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslatingSpanRender.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslatingSpanRender.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package formatter

trait TranslatingSpanRender {
  def render(context: NodeFormatterContext, markdown: MarkdownWriter): Unit
}
