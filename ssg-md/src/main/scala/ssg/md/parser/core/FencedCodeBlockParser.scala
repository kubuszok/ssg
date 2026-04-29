/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/FencedCodeBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/FencedCodeBlockParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
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
import scala.util.boundary
import scala.util.boundary.break

class FencedCodeBlockParser(options: DataHolder, val fenceChar: Char, val fenceLength: Int, val fenceIndent: Int, fenceMarkerIndent: Int) extends AbstractBlockParser {

  private val _block:           FencedCodeBlock        = FencedCodeBlock()
  private var content:          Nullable[BlockContent] = Nullable(BlockContent())
  private val matchingCloser:   Boolean                = Parser.MATCH_CLOSING_FENCE_CHARACTERS.get(options)
  private val codeContentBlock: Boolean                = Parser.FENCED_CODE_CONTENT_BLOCK.get(options)

  override def getBlock: FencedCodeBlock = _block

  override def getBlockContent: Nullable[BlockContent] = content

  override def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean = false

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = boundary {
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
        // Cross-platform: CLOSING_FENCE now captures fence chars in group(1),
        // with trailing whitespace consumed but not in the group.
        val foundFenceLength = matcher.group(1).length
        if (foundFenceLength >= fenceLength) {
          // closing fence
          _block.closingMarker = trySequence.subSequence(0, foundFenceLength)
          break(Nullable(BlockContinue.finished()))
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

        val charsSeq      = c.spanningChars
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

  // Cross-platform: original Java regex used lookaheads which are unavailable on
  // Scala.js and Scala Native.
  // OPENING_FENCE original: "^`{3,}(?!.*`)|^~{3,}(?!.*~)"
  //   Negative lookahead prevented matching when more fence chars appear later on
  //   the line. Rewritten to match fence chars only; the "no fence char in rest
  //   of line" check is performed programmatically in BlockFactory.tryStart.
  // CLOSING_FENCE original: "^(?:`{3,}|~{3,})(?=[ \t]*$)"
  //   Lookahead asserted only whitespace to EOL. Rewritten to consume the
  //   trailing whitespace: the caller uses group(0).length for fenceLength which
  //   now needs trimming — but actually we use a capturing group for just the
  //   fence chars and the full match includes trailing whitespace.
  // Revert to originals if/when Scala.js and Scala Native add full java.util.regex support.
  val OPENING_FENCE: Pattern = Pattern.compile("^(`{3,})|^(~{3,})")
  val CLOSING_FENCE: Pattern = Pattern.compile("^(`{3,}|~{3,})[ \t]*$")

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
          // Cross-platform: OPENING_FENCE no longer uses negative lookahead to
          // reject fence chars in the rest of the line. Check programmatically.
          val fenceStr    = if (matcher.group(1) != null) matcher.group(1) else matcher.group(2) // @nowarn -- Java regex group returns null when not matched
          val fenceLength = fenceStr.length
          val fenceChar   = fenceStr.charAt(0)
          val restOfLine  = trySequence.subSequence(fenceLength, trySequence.length())
          val restStr     = restOfLine.toString
          if (restStr.indexOf(fenceChar) >= 0) {
            // Rest of line contains the fence character — not a valid opening fence
            BlockStart.none()
          } else {
            val parser = FencedCodeBlockParser(state.properties, fenceChar, fenceLength, indent, nextNonSpace)
            parser.getBlock.openingMarker = trySequence.subSequence(0, fenceLength)
            Nullable(BlockStart.of(parser).atIndex(nextNonSpace + fenceLength))
          }
        } else {
          BlockStart.none()
        }
      }
    }
  }
}
