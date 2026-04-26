/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockStart.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockStart.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package block

import ssg.md.parser.internal.BlockStartImpl

/** Result object for starting parsing of a block, see companion object methods for constructors.
  */
abstract class BlockStart protected () {

  def atIndex(newIndex: Int): BlockStart

  def atColumn(newColumn: Int): BlockStart

  def replaceActiveBlockParser(): BlockStart
}

object BlockStart {

  def none(): Nullable[BlockStart] =
    Nullable.empty[BlockStart]

  def of(blockParsers: BlockParser*): BlockStart =
    BlockStartImpl(blockParsers*)
}
