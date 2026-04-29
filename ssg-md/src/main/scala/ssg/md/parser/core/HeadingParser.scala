/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/HeadingParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/HeadingParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package core

import ssg.md.ast.Heading
import ssg.md.parser.block._
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.mappers.{ SpecialLeadInCharsHandler, SpecialLeadInHandler, SpecialLeadInStartsWithCharsHandler }

import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

class HeadingParser(val level: Int) extends AbstractBlockParser {

  private val _block: Heading = Heading()
  _block.level = level

  override def getBlock: Heading = _block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    // heading can never contain > 1 line, so fail to match
    BlockContinue.none()

  override def parseInlines(inlineParser: InlineParser): Unit =
    inlineParser.parse(_block.text, _block)

  override def closeBlock(state: ParserState): Unit = ()
}

object HeadingParser {

  private[core] object HeadingLeadInHandler {
    val HANDLER_NO_SPACE: SpecialLeadInHandler = SpecialLeadInStartsWithCharsHandler.create('#')
    val HANDLER_SPACE:    SpecialLeadInHandler = SpecialLeadInCharsHandler.create('#')
  }

  val ATX_HEADING:    Pattern = Pattern.compile("^#{1,6}(?:[ \\t]+|$)")
  val ATX_TRAILING:   Pattern = Pattern.compile("(^|[ \\t])#+[ \\t]*$")
  val SETEXT_HEADING: Pattern = Pattern.compile("^(?:=+|-+)[ \\t]*$")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[BlockQuoteParser.Factory]
      )
    )

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[FencedCodeBlockParser.Factory],
        classOf[HtmlBlockParser.Factory],
        classOf[ThematicBreakParser.Factory],
        classOf[ListBlockParser.Factory],
        classOf[IndentedCodeBlockParser.Factory]
      )
    )

    override def affectsGlobalScope: Boolean = false

    override def getLeadInHandler(options: DataHolder): Nullable[SpecialLeadInHandler] = {
      val noAtxSpace = Parser.ESCAPE_HEADING_NO_ATX_SPACE.get(Nullable(options)) || Parser.HEADING_NO_ATX_SPACE.get(Nullable(options))
      if (noAtxSpace) Nullable(HeadingParser.HeadingLeadInHandler.HANDLER_NO_SPACE)
      else Nullable(HeadingParser.HeadingLeadInHandler.HANDLER_SPACE)
    }

    override def apply(options: DataHolder): BlockParserFactory = BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val line         = state.line
        val nextNonSpace = state.nextNonSpaceIndex
        val input        = line.subSequence(nextNonSpace, line.length())

        val atxMatcher = ATX_HEADING.matcher(input)
        if (atxMatcher.find()) {
          val openingStart  = atxMatcher.start()
          val openingEnd    = atxMatcher.end()
          val openingMarker = input.subSequence(openingStart, openingEnd).trim()
          val level         = openingMarker.length() // number of #s

          var headerText = input.subSequence(openingEnd)
          var closingMarker: Nullable[ssg.md.util.sequence.BasedSequence] = Nullable.empty
          val trailingMatcher = ATX_TRAILING.matcher(headerText)
          if (trailingMatcher.find()) {
            // remove trailing ###s:
            val closingStart = trailingMatcher.start()
            val closingEnd   = trailingMatcher.end()
            closingMarker = Nullable(headerText.subSequence(closingStart, closingEnd).trim())
            headerText = headerText.subSequence(0, closingStart)
          }

          val parser = HeadingParser(level)
          parser.getBlock.openingMarker = openingMarker
          parser.getBlock.text = headerText.trim()
          closingMarker.foreach { cm => parser.getBlock.closingMarker = cm }
          parser.getBlock.setCharsFromContent()

          Nullable(BlockStart.of(parser).atIndex(line.length()))
        } else {
          // Check for setext heading
          val setextMatcher = SETEXT_HEADING.matcher(input)
          if (setextMatcher.find()) {
            val paragraph = matchedBlockParser.paragraphContent
            if (paragraph.isDefined) {
              // setext heading line
              val level = if (setextMatcher.group(0).charAt(0) == '=') 1 else 2

              val contentVal = ssg.md.util.ast.BlockContent()
              contentVal.addAll(matchedBlockParser.paragraphLines.get.asJava, matchedBlockParser.paragraphEolLengths.get.map(Integer.valueOf).asJava)
              val headingText    = contentVal.contents.trim()
              val closingMarker2 = line.trim()

              val headingParser = HeadingParser(level)
              headingParser.getBlock.text = headingText
              headingParser.getBlock.closingMarker = closingMarker2
              headingParser.getBlock.setCharsFromContent()

              Nullable(BlockStart.of(headingParser).atIndex(line.length()).replaceActiveBlockParser())
            } else {
              BlockStart.none()
            }
          } else {
            BlockStart.none()
          }
        }
      }
  }
}
