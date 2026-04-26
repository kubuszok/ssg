/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockParserFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockParserFactory.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package block

/** Parser factory for a block node for determining when a block starts.
  *
  * Implementations should subclass [[AbstractBlockParserFactory]] instead of implementing this directly.
  */
trait BlockParserFactory {

  def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart]
}
