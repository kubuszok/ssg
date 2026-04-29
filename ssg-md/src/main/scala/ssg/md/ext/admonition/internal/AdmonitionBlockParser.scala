/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionBlockParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package admonition
package internal

import ssg.md.Nullable
import ssg.md.ast.ListItem
import ssg.md.parser.block.*
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.mappers.SpecialLeadInHandler

import java.util.regex.Pattern
import scala.language.implicitConversions

class AdmonitionBlockParser(options: AdmonitionOptions, contentIndent: Int) extends AbstractBlockParser {

  val block:                AdmonitionBlock = new AdmonitionBlock()
  private var hadBlankLine: Boolean         = false

  override def getBlock: Block = block

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val nonSpaceIndex = state.nextNonSpaceIndex
    if (state.isBlank) {
      hadBlankLine = true
      Nullable(BlockContinue.atIndex(nonSpaceIndex))
    } else if (!hadBlankLine && options.allowLazyContinuation) {
      Nullable(BlockContinue.atIndex(nonSpaceIndex))
    } else if (state.indent >= options.contentIndent) {
      val ci = state.column + options.contentIndent
      Nullable(BlockContinue.atColumn(ci))
    } else {
      BlockContinue.none()
    }
  }

  override def closeBlock(state: ParserState): Unit =
    block.setCharsFromContent()
}

object AdmonitionBlockParser {

  private val ADMONITION_START_FORMAT: String = "^(\\?{3}\\+|\\?{3}|!{3})\\s+(%s)(?:\\s+(%s))?\\s*$"

  def isMarker(state: ParserState, index: Int, inParagraph: Boolean, inParagraphListItem: Boolean, options: AdmonitionOptions): Boolean =
    if (!inParagraph || options.interruptsParagraph) {
      if ((options.allowLeadingSpace || state.indent == 0) && (!inParagraphListItem || options.interruptsItemParagraph)) {
        if (inParagraphListItem && !options.withSpacesInterruptsItemParagraph) {
          state.indent == 0
        } else {
          state.indent < state.parsing.CODE_BLOCK_INDENT
        }
      } else false
    } else false

  class AdmonitionLeadInHandler extends SpecialLeadInHandler {
    override def escape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean =
      if ((sequence.length() == 3 || sequence.length() == 4 && sequence.charAt(3) == '+') && (sequence.startsWith("???") || sequence.startsWith("!!!"))) {
        consumer("\\")
        consumer(sequence)
        true
      } else false

    override def unEscape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean =
      if ((sequence.length() == 4 || sequence.length() == 5 && sequence.charAt(4) == '+') && (sequence.startsWith("\\???") || sequence.startsWith("\\!!!"))) {
        consumer(sequence.subSequence(1))
        true
      } else false
  }

  object AdmonitionLeadInHandler {
    val HANDLER: SpecialLeadInHandler = new AdmonitionLeadInHandler()
  }

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getLeadInHandler(options: DataHolder): Nullable[SpecialLeadInHandler] = Nullable(AdmonitionLeadInHandler.HANDLER)

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val admonitionOptions: AdmonitionOptions = new AdmonitionOptions(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val nextNonSpace        = state.nextNonSpaceIndex
        val matched             = matchedBlockParser.blockParser
        val inParagraph         = matched.isParagraphParser
        val inParagraphListItem = inParagraph && matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) && matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

        if (isMarker(state, nextNonSpace, inParagraph, inParagraphListItem, admonitionOptions)) {
          val line         = state.line
          val trySequence  = line.subSequence(nextNonSpace, line.length())
          val parsing      = state.parsing
          val startPattern = Pattern.compile(String.format(ADMONITION_START_FORMAT, parsing.ATTRIBUTENAME, parsing.LINK_TITLE_STRING))
          val matcher      = startPattern.matcher(trySequence)

          if (matcher.find()) {
            // admonition block
            val openingMarker = line.subSequence(nextNonSpace + matcher.start(1), nextNonSpace + matcher.end(1))
            val info          = line.subSequence(nextNonSpace + matcher.start(2), nextNonSpace + matcher.end(2))
            val titleChars    =
              if (matcher.group(3) == null) BasedSequence.NULL // @nowarn - regex group may be null
              else line.subSequence(nextNonSpace + matcher.start(3), nextNonSpace + matcher.end(3))

            val contentOffset = admonitionOptions.contentIndent

            val admonitionBlockParser = new AdmonitionBlockParser(admonitionOptions, contentOffset)
            admonitionBlockParser.block.openingMarker = openingMarker
            admonitionBlockParser.block.info = info
            admonitionBlockParser.block.titleChars = titleChars

            Nullable(BlockStart.of(admonitionBlockParser).atIndex(line.length()))
          } else {
            BlockStart.none()
          }
        } else {
          BlockStart.none()
        }
      }
  }
}
