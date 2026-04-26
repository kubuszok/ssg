/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ParagraphParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ParagraphParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package core

import ssg.md.ast.Paragraph
import ssg.md.parser.InlineParser
import ssg.md.parser.block._
import ssg.md.util.ast.BlockContent
import ssg.md.util.sequence.{ BasedSequence, PrefixedSubSequence }

import scala.language.implicitConversions

class ParagraphParser extends AbstractBlockParser {

  private val _block:  Paragraph              = Paragraph()
  private var content: Nullable[BlockContent] = Nullable(BlockContent())

  override def getBlockContent: Nullable[BlockContent] = content

  override def getBlock: Paragraph = _block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    if (!state.isBlank) {
      // NOTE: here we can continue with any indent, unless the parent is a list item and the indent is >= code indent
      Nullable(BlockContinue.atIndex(state.getIndex))
    } else {
      val blankLine = state.isBlankLine
      _block.trailingBlankLine = blankLine
      BlockContinue.none()
    }

  override def addLine(state: ParserState, line: BasedSequence): Unit = {
    val indent = state.indent
    if (indent > 0) {
      content.get.add(PrefixedSubSequence.repeatOf(' ', indent, line), indent)
    } else {
      content.get.add(line, indent)
    }
  }

  override def isParagraphParser: Boolean = true

  override def isInterruptible: Boolean = true

  override def closeBlock(state: ParserState): Unit = {
    _block.setContent(content.get)
    content = Nullable.empty
  }

  override def parseInlines(inlineParser: InlineParser): Unit =
    inlineParser.parse(getBlock.contentChars, getBlock)
}

object ParagraphParser {

  class Factory extends BlockParserFactory {
    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      Nullable.empty
  }
}
