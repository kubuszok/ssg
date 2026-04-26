/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockContinue.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockContinue.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package block

import ssg.md.parser.internal.BlockContinueImpl

/** Result object for continuing parsing of a block, see companion object methods for constructors.
  */
class BlockContinue protected () {}

object BlockContinue {

  def none(): Nullable[BlockContinue] =
    Nullable.empty[BlockContinue]

  def atIndex(newIndex: Int): BlockContinue =
    BlockContinueImpl(newIndex, -1, false)

  def atColumn(newColumn: Int): BlockContinue =
    BlockContinueImpl(-1, newColumn, false)

  def finished(): BlockContinue =
    BlockContinueImpl(-1, -1, true)
}
