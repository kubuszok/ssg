/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/ParagraphPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/ParagraphPreProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.ast.Paragraph

trait ParagraphPreProcessor {

  /** Process Paragraph Content on closing of the paragraph block to remove non-text lines.
    *
    * This is used by extensions to take leading lines from a paragraph and convert them to other blocks.
    *
    * By default leading lines that define references are removed and Reference nodes are inserted before.
    *
    * @param block
    *   paragraph node to process
    * @param state
    *   parser state
    * @return
    *   number of characters processed from the start of the block
    */
  def preProcessBlock(block: Paragraph, state: ParserState): Int
}
