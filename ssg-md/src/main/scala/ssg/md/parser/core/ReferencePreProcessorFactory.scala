/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ReferencePreProcessorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ReferencePreProcessorFactory.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package core

import ssg.md.parser.block.{ ParagraphPreProcessor, ParagraphPreProcessorFactory, ParserState }
import ssg.md.parser.internal.InlineParserImpl

class ReferencePreProcessorFactory extends ParagraphPreProcessorFactory {

  override def affectsGlobalScope: Boolean = true

  override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def apply(state: ParserState): ParagraphPreProcessor =
    state.inlineParser.asInstanceOf[InlineParserImpl]
}
