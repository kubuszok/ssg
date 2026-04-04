/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.Nullable
import ssg.md.parser.block.*
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions

class FootnoteBlockParser(options: FootnoteOptions, contentOffset: Int) extends AbstractBlockParser {

  private val block:   FootnoteBlock          = new FootnoteBlock()
  private var content: Nullable[BlockContent] = Nullable(new BlockContent())

  def blockContent: BlockContent = content.get

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val nonSpaceIndex = state.nextNonSpaceIndex
    if (state.isBlank) {
      if (block.firstChild.isEmpty) {
        // Blank line after empty list item
        BlockContinue.none()
      } else {
        Nullable(BlockContinue.atIndex(nonSpaceIndex))
      }
    } else if (state.indent >= options.contentIndent) {
      val contentIndent = state.getIndex + options.contentIndent
      Nullable(BlockContinue.atIndex(contentIndent))
    } else {
      BlockContinue.none()
    }
  }

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.foreach(_.add(line, state.indent))

  override def closeBlock(state: ParserState): Unit = {
    // set the footnote from closingMarker to end
    block.setCharsFromContent()
    block.footnote = block.chars.subSequence(block.closingMarker.endOffset - block.startOffset).trimStart()
    // add it to the map
    val footnoteMap = FootnoteExtension.FOOTNOTES.get(state.properties)
    footnoteMap.put(footnoteMap.normalizeKey(block.text), block)
    content = Nullable.empty
  }

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true
}

object FootnoteBlockParser {

  val FOOTNOTE_ID:          String  = ".*"
  val FOOTNOTE_ID_PATTERN:  Pattern = Pattern.compile("\\[\\^\\s*(" + FOOTNOTE_ID + ")\\s*\\]")
  val FOOTNOTE_DEF_PATTERN: Pattern = Pattern.compile("^\\[\\^\\s*(" + FOOTNOTE_ID + ")\\s*\\]:")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val footnoteOptions: FootnoteOptions = new FootnoteOptions(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val line         = state.line
        val nextNonSpace = state.nextNonSpaceIndex

        val trySequence = line.subSequence(nextNonSpace, line.length())
        val matcher     = FOOTNOTE_DEF_PATTERN.matcher(trySequence)
        if (matcher.find()) {
          // abbreviation definition
          val openingStart  = nextNonSpace + matcher.start()
          val openingEnd    = nextNonSpace + matcher.end()
          val openingMarker = line.subSequence(openingStart, openingStart + 2)
          val text          = line.subSequence(openingStart + 2, openingEnd - 2).trim()
          val closingMarker = line.subSequence(openingEnd - 2, openingEnd)

          val contentOffset = footnoteOptions.contentIndent

          val footnoteBlockParser = new FootnoteBlockParser(footnoteOptions, contentOffset)
          footnoteBlockParser.block.openingMarker = openingMarker
          footnoteBlockParser.block.text = text
          footnoteBlockParser.block.closingMarker = closingMarker

          Nullable(BlockStart.of(footnoteBlockParser).atIndex(openingEnd))
        } else {
          BlockStart.none()
        }
      }
  }
}
