/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/PhasedNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/PhasedNodeFormatter.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package formatter

import ssg.md.util.ast.Document

/** A renderer for a document node for a specific rendering phase
  */
trait PhasedNodeFormatter extends NodeFormatter {
  def getFormattingPhases: Nullable[Set[FormattingPhase]]

  /** Render the specified node.
    *
    * @param context
    *   node renderer context instance
    * @param markdown
    *   markdown writer instance
    * @param document
    *   the document node to render
    * @param phase
    *   rendering phase for which to generate the output
    */
  def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit
}
