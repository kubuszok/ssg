/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacroDefinitionBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacroDefinitionBlockParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.Nullable
import ssg.md.ext.gitlab.GitLabBlockQuote
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions
import java.util.regex.Pattern

class MacroDefinitionBlockParser(options: DataHolder, openMarker: BasedSequence, name: BasedSequence, openTrailing: BasedSequence) extends AbstractBlockParser {

  private val block_ = new MacroDefinitionBlock()
  block_.openingMarker = openMarker
  block_.name = name
  block_.openingTrailing = openTrailing

  private var content  = new BlockContent()
  private var hadClose = false

  override def getBlock: Block = block_

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    if (hadClose) {
      BlockContinue.none()
    } else {
      val index = state.getIndex

      val line    = state.lineWithEOL
      val matcher = MacroDefinitionBlockParser.MACRO_BLOCK_END.matcher(line)
      if (!matcher.matches()) {
        Nullable(BlockContinue.atIndex(index))
      } else {
        // if have open gitlab block quote last child then let them handle it
        val lastChild = block_.lastChild
        if (lastChild.exists(_.isInstanceOf[GitLabBlockQuote])) {
          val blockQuote = lastChild.get.asInstanceOf[GitLabBlockQuote]
          if (blockQuote.closingMarker.isEmpty) {
            Nullable(BlockContinue.atIndex(index))
          } else {
            hadClose = true
            block_.closingMarker = state.line.subSequence(index, index + 3)
            block_.closingTrailing = state.lineWithEOL.subSequence(matcher.start(1), matcher.end(1))
            Nullable(BlockContinue.atIndex(state.lineEndIndex))
          }
        } else {
          hadClose = true
          block_.closingMarker = state.line.subSequence(index, index + 3)
          block_.closingTrailing = state.lineWithEOL.subSequence(matcher.start(1), matcher.end(1))
          Nullable(BlockContinue.atIndex(state.lineEndIndex))
        }
      }
    }

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.add(line, state.indent)

  override def closeBlock(state: ParserState): Unit = {
    block_.setContent(content)
    block_.setCharsFromContent()
    content = null

    // set the footnote from closingMarker to end
    block_.setCharsFromContent()

    // add it to the map
    val macrosRepository = MacrosExtension.MACRO_DEFINITIONS.get(state.properties)
    macrosRepository.put(macrosRepository.normalizeKey(block_.name), block_)
  }

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def parseInlines(inlineParser: InlineParser): Unit = {}
}

object MacroDefinitionBlockParser {
  val MACRO_BLOCK_START:          Pattern = Pattern.compile(">>>([\\w_-]+)(\\s*$)")
  val MACRO_BLOCK_START_INTELLIJ: Pattern = Pattern.compile(">>>([\u001f\\w_-]+)(\\s*$)")
  val MACRO_BLOCK_END:            Pattern = Pattern.compile("<<<(\\s*$)")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] =
      Nullable(Set[Class[?]](classOf[ssg.md.ext.gitlab.internal.GitLabBlockQuoteParser.Factory]))

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private def haveBlockQuoteParser(state: ParserState): Boolean =
      state.activeBlockParsers.exists(_.isInstanceOf[MacroDefinitionBlockParser])

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.getIndex == 0 && !haveBlockQuoteParser(state)) {
        val line    = state.lineWithEOL
        val pattern = if (state.parsing.intellijDummyIdentifier) MACRO_BLOCK_START_INTELLIJ else MACRO_BLOCK_START
        val matcher = pattern.matcher(line)
        if (matcher.matches()) {
          Nullable(
            BlockStart
              .of(
                new MacroDefinitionBlockParser(
                  state.properties,
                  line.subSequence(0, 3),
                  line.subSequence(matcher.start(1), matcher.end(1)),
                  line.subSequence(matcher.start(2), matcher.end(1))
                )
              )
              .atIndex(state.lineEndIndex)
          )
        } else {
          BlockStart.none()
        }
      } else {
        BlockStart.none()
      }
  }
}
