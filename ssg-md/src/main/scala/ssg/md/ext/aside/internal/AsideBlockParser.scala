/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/internal/AsideBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package aside
package internal

import ssg.md.Nullable
import ssg.md.ast.ListItem
import ssg.md.ast.util.Parsing
import ssg.md.parser.Parser
import ssg.md.parser.block.*
import ssg.md.parser.core.*
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.mappers.{ SpecialLeadInHandler, SpecialLeadInStartsWithCharsHandler }

import scala.language.implicitConversions

class AsideBlockParser(options: DataHolder, marker: BasedSequence) extends AbstractBlockParser {

  private val block_ = new AsideBlock()
  block_.openingMarker = marker

  private val allowLeadingSpace:                     Boolean = AsideExtension.ALLOW_LEADING_SPACE.get(options)
  private val continueToBlankLine:                   Boolean = AsideExtension.EXTEND_TO_BLANK_LINE.get(options)
  private val ignoreBlankLine:                       Boolean = AsideExtension.IGNORE_BLANK_LINE.get(options)
  private val interruptsParagraph:                   Boolean = AsideExtension.INTERRUPTS_PARAGRAPH.get(options)
  private val interruptsItemParagraph:               Boolean = AsideExtension.INTERRUPTS_ITEM_PARAGRAPH.get(options)
  private val withLeadSpacesInterruptsItemParagraph: Boolean = AsideExtension.WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH.get(options)
  private var lastWasBlankLine:                      Int     = 0

  override def isContainer: Boolean = true

  override def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean = false

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def getBlock: AsideBlock = block_

  override def closeBlock(state: ParserState): Unit = {
    block_.setCharsFromContent()

    if (!Parser.BLANK_LINES_IN_AST.get(state.properties)) {
      removeBlankLines()
    }
  }

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val nextNonSpace = state.nextNonSpaceIndex
    if (!state.isBlank) {
      val isMarkerResult = AsideBlockParser.isMarker(
        state,
        nextNonSpace,
        false,
        false,
        allowLeadingSpace,
        interruptsParagraph,
        interruptsItemParagraph,
        withLeadSpacesInterruptsItemParagraph
      )
      if (isMarkerResult || (continueToBlankLine && lastWasBlankLine == 0)) {
        var newColumn = state.column + state.indent
        lastWasBlankLine = 0

        if (isMarkerResult) {
          newColumn += 1
          // optional following space or tab
          if (Parsing.isSpaceOrTab(state.line, nextNonSpace + 1)) {
            newColumn += 1
          }
        }
        BlockContinue.atColumn(newColumn)
      } else {
        BlockContinue.none()
      }
    } else {
      if (ignoreBlankLine && state.isBlank) {
        lastWasBlankLine += 1
        val newColumn = state.column + state.indent
        BlockContinue.atColumn(newColumn)
      } else {
        BlockContinue.none()
      }
    }
  }
}

object AsideBlockParser {

  val MARKER_CHAR: Char = '|'

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

  def endsWithMarker(line: BasedSequence): Boolean = {
    val tailBlanks = line.countTrailing(CharPredicate.WHITESPACE_NBSP)
    tailBlanks + 1 < line.length() && line.charAt(line.length() - tailBlanks - 1) == MARKER_CHAR
  }

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] =
      Nullable(Set.empty[Class[?]])

    override def beforeDependents: Nullable[Set[Class[?]]] =
      Nullable(
        Set[Class[?]](
          classOf[HeadingParser.Factory],
          classOf[FencedCodeBlockParser.Factory],
          classOf[HtmlBlockParser.Factory],
          classOf[ThematicBreakParser.Factory],
          classOf[ListBlockParser.Factory],
          classOf[IndentedCodeBlockParser.Factory]
        )
      )

    override def getLeadInHandler(options: DataHolder): Nullable[SpecialLeadInHandler] =
      Nullable(SpecialLeadInStartsWithCharsHandler.create('|'))

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val allowLeadingSpace:                     Boolean = AsideExtension.ALLOW_LEADING_SPACE.get(options)
    private val interruptsParagraph:                   Boolean = AsideExtension.INTERRUPTS_PARAGRAPH.get(options)
    private val interruptsItemParagraph:               Boolean = AsideExtension.INTERRUPTS_ITEM_PARAGRAPH.get(options)
    private val withLeadSpacesInterruptsItemParagraph: Boolean = AsideExtension.WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH.get(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = {
      val nextNonSpace        = state.nextNonSpaceIndex
      val matched             = matchedBlockParser.blockParser
      val inParagraph         = matched.isParagraphParser
      val inParagraphListItem = inParagraph && matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) && matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

      if (
        !endsWithMarker(state.line) && isMarker(
          state,
          nextNonSpace,
          inParagraph,
          inParagraphListItem,
          allowLeadingSpace,
          interruptsParagraph,
          interruptsItemParagraph,
          withLeadSpacesInterruptsItemParagraph
        )
      ) {
        var newColumn = state.column + state.indent + 1
        // optional following space or tab
        if (Parsing.isSpaceOrTab(state.line, nextNonSpace + 1)) {
          newColumn += 1
        }
        Nullable(
          BlockStart.of(new AsideBlockParser(state.properties, state.line.subSequence(nextNonSpace, nextNonSpace + 1))).atColumn(newColumn)
        )
      } else {
        BlockStart.none()
      }
    }
  }
}
