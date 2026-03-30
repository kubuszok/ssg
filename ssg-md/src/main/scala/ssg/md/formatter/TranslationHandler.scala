/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslationHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.ast.Document

trait TranslationHandler extends TranslationContext {
  def beginRendering(node: Document, context: NodeFormatterContext, out: MarkdownWriter): Unit

  def getTranslatingTexts:                                          List[String]
  def setTranslatedTexts(translatedTexts: List[? <: CharSequence]): Unit
  def setRenderPurpose(renderPurpose:     RenderPurpose):           Unit

  def setMergeContext(context: MergeContext): Unit
}
