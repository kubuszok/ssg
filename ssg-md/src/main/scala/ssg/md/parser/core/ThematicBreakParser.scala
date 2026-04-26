/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ThematicBreakParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ThematicBreakParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package core

import ssg.md.ast.ThematicBreak
import ssg.md.parser.Parser
import ssg.md.parser.block._
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions

class ThematicBreakParser(line: BasedSequence) extends AbstractBlockParser {

  private val _block: ThematicBreak = ThematicBreak()
  _block.chars = line

  override def getBlock: Block = _block

  override def closeBlock(state: ParserState): Unit =
    _block.setCharsFromContent()

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    // a horizontal rule can never contain > 1 line, so fail to match
    BlockContinue.none()
}

object ThematicBreakParser {

  val PATTERN: Pattern = Pattern.compile("^(?:(?:\\*[ \\t]*){3,}|(?:_[ \\t]*){3,}|(?:-[ \\t]*){3,})[ \\t]*$")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[BlockQuoteParser.Factory],
        classOf[HeadingParser.Factory],
        classOf[FencedCodeBlockParser.Factory],
        classOf[HtmlBlockParser.Factory]
      )
    )

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[ListBlockParser.Factory],
        classOf[IndentedCodeBlockParser.Factory]
      )
    )

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val thematicBreakOptions: ThematicBreakOptions = ThematicBreakOptions(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4 || (matchedBlockParser.blockParser.isParagraphParser && !thematicBreakOptions.relaxedStart)) {
        BlockStart.none()
      } else {
        val line  = state.line
        val input = line.subSequence(state.nextNonSpaceIndex, line.length())
        if (PATTERN.matcher(input).matches()) {
          Nullable(BlockStart.of(ThematicBreakParser(line.subSequence(state.getIndex))).atIndex(line.length()))
        } else {
          BlockStart.none()
        }
      }
  }

  private class ThematicBreakOptions(options: DataHolder) {
    val relaxedStart: Boolean = Parser.THEMATIC_BREAK_RELAXED_START.get(options)
  }
}
