/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockPreProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.util.ast.Block

trait BlockPreProcessor {

  /** Called on block nodes of interest as given by the NodePreProcessorFactory after all blocks are closed but before inline processing is performed.
    *
    * @param state
    *   parser state
    * @param block
    *   the block node to pre-process
    */
  def preProcess(state: ParserState, block: Block): Unit
}
