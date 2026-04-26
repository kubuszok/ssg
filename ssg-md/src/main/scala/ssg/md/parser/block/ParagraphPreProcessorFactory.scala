/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/ParagraphPreProcessorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/ParagraphPreProcessorFactory.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package block

import ssg.md.util.dependency.Dependent

trait ParagraphPreProcessorFactory extends (ParserState => ParagraphPreProcessor), Dependent {

  /** Create a paragraph pre processor for the document.
    *
    * @param state
    *   parser state, document blocks have already been parsed at this stage
    * @return
    *   block pre-processor
    */
  override def apply(state: ParserState): ParagraphPreProcessor
}
