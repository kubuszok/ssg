/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/FencedCodeBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core

import ssg.md.ast.{ CodeBlock, FencedCodeBlock, Text }
import ssg.md.parser.block._
import ssg.md.parser.Parser
import ssg.md.util.ast.BlockContent
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.{ BasedSequence, SegmentedSequence }

import java.util.regex.Pattern
import scala.language.implicitConversions

class FencedCodeBlockParser(options: DataHolder, val fenceChar: Char, val fenceLength: Int, val fenceIndent: Int, fenceMarkerIndent: Int) extends AbstractBlockParser {

  private val _block:           FencedCodeBlock        = FencedCodeBlock()
  private var content:          Nullable[BlockContent] = Nullable(BlockContent())
  private val matchingCloser:   Boolean                = Parser.MATCH_CLOSING_FENCE_CHARACTERS.get(options)
  private val codeContentBlock: Boolean                = Parser.FENCED_CODE_CONTENT_BLOCK.get(options)

  override def getBlock: FencedCodeBlock = _block

  override def getBlockContent: Nullable[BlockContent] = content

  override def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean = false

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val nextNonSpace = state.nextNonSpaceIndex
    var newIndex     = state.getIndex
    val line         = state.line

    val matches = state.indent <= 3 &&
      nextNonSpace < line.length() &&
      (!matchingCloser || line.charAt(nextNonSpace) == fenceChar)

    if (matches) {
      val trySequence = line.subSequence(nextNonSpace, line.length())
      val matcher     = FencedCodeBlockParser.CLOSING_FENCE.matcher(trySequence)
      if (matcher.find()) {
        val foundFenceLength = matcher.group(0).length
        if (foundFenceLength >= fenceLength) {
          // closing fence
          _block.closingMarker = trySequence.subSequence(0, foundFenceLength)
          return Nullable(BlockContinue.finished()) // TODO: replace with boundary/break when refactoring
        }
      }
    }

    // skip optional spaces of fence indent
    var i = fenceIndent
    while (i > 0 && newIndex < line.length() && line.charAt(newIndex) == ' ') {
      newIndex += 1
      i -= 1
    }
    Nullable(BlockContinue.atIndex(newIndex))
  }

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.foreach(_.add(line, state.indent))

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      // first line, if not blank, has the info string
      val lines = c.lines
      if (lines.size > 0) {
        val infoLine = lines.get(0)
        if (!infoLine.isBlank()) {
          _block.info = infoLine.trim()
        }

        val charsSeq     = c.spanningChars
        val spanningChars = charsSeq.baseSubSequence(charsSeq.startOffset, lines.get(0).endOffset)

        if (lines.size > 1) {
          // have more lines
          val segments = lines.subList(1, lines.size)
          _block.setContent(spanningChars, segments)
          if (codeContentBlock) {
            val codeBlock = CodeBlock()
            codeBlock.setContent(segments)
            codeBlock.setCharsFromContent()
            _block.appendChild(codeBlock)
          } else {
            val text = Text(SegmentedSequence.create(charsSeq, segments))
            _block.appendChild(text)
          }
        } else {
          _block.setContent(spanningChars, BasedSequence.EMPTY_LIST)
        }
      } else {
        _block.setContent(c)
      }

      _block.setCharsFromContent()
    }
    content = Nullable.empty
  }
}

object FencedCodeBlockParser {

  val OPENING_FENCE: Pattern = Pattern.compile("^`{3,}(?!.*`)|^~{3,}(?!.*~)")
  val CLOSING_FENCE: Pattern = Pattern.compile("^(?:`{3,}|~{3,})(?=[ \t]*$)")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[BlockQuoteParser.Factory],
        classOf[HeadingParser.Factory]
      )
    )

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
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
      val indent = state.indent
      if (indent >= 4) {
        BlockStart.none()
      } else {
        val nextNonSpace = state.nextNonSpaceIndex
        val line         = state.line
        val trySequence  = line.subSequence(nextNonSpace, line.length())
        val matcher      = OPENING_FENCE.matcher(trySequence)
        if (matcher.find()) {
          val fenceLength = matcher.group(0).length
          val fenceChar   = matcher.group(0).charAt(0)
          val parser      = FencedCodeBlockParser(state.properties, fenceChar, fenceLength, indent, nextNonSpace)
          parser.getBlock.openingMarker = trySequence.subSequence(0, fenceLength)
          Nullable(BlockStart.of(parser).atIndex(nextNonSpace + fenceLength))
        } else {
          BlockStart.none()
        }
      }
    }
  }
}
