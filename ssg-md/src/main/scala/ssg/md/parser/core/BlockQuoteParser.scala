/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/BlockQuoteParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/BlockQuoteParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package core

import ssg.md.ast.BlockQuote
import ssg.md.parser.Parser
import ssg.md.parser.block._
import ssg.md.util.ast.{ BlankLineContainer, Block }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class BlockQuoteParser(options: DataHolder, marker: BasedSequence) extends AbstractBlockParser, BlankLineContainer {

  private val _block: BlockQuote = {
    val bq = BlockQuote()
    bq.openingMarker = marker
    bq
  }

  private val continueToBlankLine:                   Boolean = Parser.BLOCK_QUOTE_EXTEND_TO_BLANK_LINE.get(options)
  private val allowLeadingSpace:                     Boolean = Parser.BLOCK_QUOTE_ALLOW_LEADING_SPACE.get(options)
  private val ignoreBlankLine:                       Boolean = Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE.get(options)
  private val interruptsParagraph:                   Boolean = Parser.BLOCK_QUOTE_INTERRUPTS_PARAGRAPH.get(options)
  private val interruptsItemParagraph:               Boolean = Parser.BLOCK_QUOTE_INTERRUPTS_ITEM_PARAGRAPH.get(options)
  private val withLeadSpacesInterruptsItemParagraph: Boolean = Parser.BLOCK_QUOTE_WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH.get(options)
  private var lastWasBlankLine:                      Int     = 0

  override def isContainer: Boolean = true

  override def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean = false

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def getBlock: BlockQuote = _block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val nextNonSpace   = state.nextNonSpaceIndex
    val isMarkerResult = BlockQuoteParser.isMarker(
      state,
      nextNonSpace,
      false,
      false,
      allowLeadingSpace,
      interruptsParagraph,
      interruptsItemParagraph,
      withLeadSpacesInterruptsItemParagraph
    )

    if (!state.isBlank && (isMarkerResult || (continueToBlankLine && lastWasBlankLine == 0))) {
      var newColumn = state.column + state.indent
      lastWasBlankLine = 0

      if (isMarkerResult) {
        newColumn += 1
        // optional following space or tab
        if (BlockQuoteParser.Parsing.isSpaceOrTab(state.line, nextNonSpace + 1)) {
          newColumn += 1
        }
      }
      Nullable(BlockContinue.atColumn(newColumn))
    } else {
      if (ignoreBlankLine && state.isBlank) {
        lastWasBlankLine += 1
        val newColumn = state.column + state.indent
        Nullable(BlockContinue.atColumn(newColumn))
      } else {
        BlockContinue.none()
      }
    }
  }

  override def closeBlock(state: ParserState): Unit = {
    _block.setCharsFromContent()

    if (!Parser.BLANK_LINES_IN_AST.get(state.properties)) {
      removeBlankLines()
    }
  }
}

object BlockQuoteParser {

  val MARKER_CHAR: Char = '>'

  def isMarker(
    state:                                 ParserState,
    index:                                 Int,
    inParagraph:                           Boolean,
    inParagraphListItem:                   Boolean,
    allowLeadingSpace:                     Boolean,
    interruptsParagraph:                   Boolean,
    interruptsItemParagraph:               Boolean,
    withLeadSpacesInterruptsItemParagraph: Boolean
  ): Boolean = {
    val line = state.line
    if ((!inParagraph || interruptsParagraph) && index < line.length() && line.charAt(index) == MARKER_CHAR) {
      if ((allowLeadingSpace || state.indent == 0) && (!inParagraphListItem || interruptsItemParagraph)) {
        if (inParagraphListItem && !withLeadSpacesInterruptsItemParagraph) {
          state.indent == 0
        } else {
          state.indent < state.parsing.CODE_BLOCK_INDENT
        }
      } else {
        false
      }
    } else {
      false
    }
  }

  private object Parsing {
    def isSpaceOrTab(line: BasedSequence, index: Int): Boolean =
      if (index < line.length()) {
        val c = line.charAt(index)
        c == ' ' || c == '\t'
      } else {
        false
      }
  }

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[HeadingParser.Factory],
        classOf[FencedCodeBlockParser.Factory],
        classOf[HtmlBlockParser.Factory],
        classOf[ThematicBreakParser.Factory],
        classOf[ListBlockParser.Factory],
        classOf[IndentedCodeBlockParser.Factory]
      )
    )

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = {
      val nextNonSpace = state.nextNonSpaceIndex
      val line         = state.line
      if (state.indent < 4 && nextNonSpace < line.length() && line.charAt(nextNonSpace) == '>') {
        var newColumn = state.column + state.indent + 1
        // optional following space or tab
        if (Parsing.isSpaceOrTab(line, nextNonSpace + 1)) {
          newColumn += 1
        }
        Nullable(BlockStart.of(BlockQuoteParser(options, line.subSequence(nextNonSpace, nextNonSpace + 1))).atColumn(newColumn))
      } else {
        BlockStart.none()
      }
    }
  }
}
