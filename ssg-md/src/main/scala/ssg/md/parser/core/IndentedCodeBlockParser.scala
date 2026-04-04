/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/IndentedCodeBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core

import ssg.md.ast.{ CodeBlock, IndentedCodeBlock }
import ssg.md.parser.Parser
import ssg.md.parser.block._
import ssg.md.util.ast.BlockContent
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class IndentedCodeBlockParser(options: DataHolder) extends AbstractBlockParser {

  private val _block:                 IndentedCodeBlock      = IndentedCodeBlock()
  private var content:                Nullable[BlockContent] = Nullable(BlockContent())
  private val trimTrailingBlankLines: Boolean                = Parser.INDENTED_CODE_NO_TRAILING_BLANK_LINES.get(options)
  private val codeContentBlock:       Boolean                = Parser.FENCED_CODE_CONTENT_BLOCK.get(options)

  override def getBlock: IndentedCodeBlock = _block

  override def getBlockContent: Nullable[BlockContent] = content

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    if (state.indent >= Parser.CODE_BLOCK_INDENT.get(state.properties)) {
      Nullable(BlockContinue.atColumn(state.column + Parser.CODE_BLOCK_INDENT.get(state.properties)))
    } else if (state.isBlank) {
      Nullable(BlockContinue.atIndex(state.nextNonSpaceIndex))
    } else {
      BlockContinue.none()
    }

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.foreach(_.add(line, state.indent))

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      // trim trailing blank lines out of the block
      if (trimTrailingBlankLines) {
        val lines              = c.lines
        val lineCount          = lines.size
        var trailingBlankLines = 0
        var i                  = lineCount - 1
        while (i >= 0 && lines.get(i).isBlank()) {
          trailingBlankLines += 1
          i -= 1
        }

        if (trailingBlankLines > 0) {
          _block.setContent(lines.subList(0, lineCount - trailingBlankLines))
        } else {
          _block.setContent(c)
        }
      } else {
        _block.setContent(c)
      }

      if (codeContentBlock) {
        val codeBlock = CodeBlock(_block.chars, _block.contentLines)
        _block.appendChild(codeBlock)
      }
    }
    content = Nullable.empty
  }
}

object IndentedCodeBlockParser {

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[BlockQuoteParser.Factory],
        classOf[HeadingParser.Factory],
        classOf[FencedCodeBlockParser.Factory],
        classOf[HtmlBlockParser.Factory],
        classOf[ThematicBreakParser.Factory],
        classOf[ListBlockParser.Factory]
      )
    )

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = BlockFactory(options)
  }

  private[core] class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= Parser.CODE_BLOCK_INDENT.get(state.properties) && !state.isBlank && !state.activeBlockParser.isParagraphParser) {
        val parser = IndentedCodeBlockParser(state.properties)
        Nullable(BlockStart.of(parser).atColumn(state.column + Parser.CODE_BLOCK_INDENT.get(state.properties)))
      } else {
        BlockStart.none()
      }
  }
}
