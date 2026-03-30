/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/BlockStartImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package internal

import ssg.md.parser.block.{ BlockParser, BlockStart }

class BlockStartImpl(val blockParsers: BlockParser*) extends BlockStart {

  var newIndex:                   Int     = -1
  var newColumn:                  Int     = -1
  var isReplaceActiveBlockParser: Boolean = false

  override def atIndex(newIndex: Int): BlockStart = {
    this.newIndex = newIndex
    this
  }

  override def atColumn(newColumn: Int): BlockStart = {
    this.newColumn = newColumn
    this
  }

  override def replaceActiveBlockParser(): BlockStart = {
    this.isReplaceActiveBlockParser = true
    this
  }
}
