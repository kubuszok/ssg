/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabBlockQuoteParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabBlockQuoteParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gitlab
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions

class GitLabBlockQuoteParser(options: DataHolder, openMarker: BasedSequence, openTrailing: BasedSequence) extends AbstractBlockParser {

  private val block:    GitLabBlockQuote       = new GitLabBlockQuote()
  private var content:  Nullable[BlockContent] = Nullable(new BlockContent())
  private var hadClose: Boolean                = false

  block.openingMarker = openMarker
  block.openingTrailing = openTrailing

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    if (hadClose) {
      BlockContinue.none()
    } else {
      val index   = state.getIndex
      val line    = state.lineWithEOL
      val matcher = GitLabBlockQuoteParser.GIT_LAB_BLOCK_END.matcher(line.subSequence(index))
      if (!matcher.matches()) {
        Nullable(BlockContinue.atIndex(index))
      } else {
        // if have open gitlab block quote last child then let them handle it
        val lastChild = block.lastChild
        if (lastChild.exists(_.isInstanceOf[GitLabBlockQuote])) {
          val lc     = lastChild.get.asInstanceOf[GitLabBlockQuote]
          val parser = state.getActiveBlockParser(lc.asInstanceOf[Block])
          parser match {
            case glParser: GitLabBlockQuoteParser if !glParser.hadClose =>
              // let the child handle it
              Nullable(BlockContinue.atIndex(index))
            case _ =>
              closeAndReturn(state, index, matcher)
          }
        } else {
          closeAndReturn(state, index, matcher)
        }
      }
    }

  private def closeAndReturn(state: ParserState, index: Int, matcher: java.util.regex.Matcher): Nullable[BlockContinue] = {
    hadClose = true
    block.closingMarker = state.line.subSequence(index, index + 3)
    block.closingTrailing = state.lineWithEOL.subSequence(matcher.start(1), matcher.end(1))
    Nullable(BlockContinue.atIndex(state.lineEndIndex))
  }

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.foreach(_.add(line, state.indent))

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      block.setContent(c)
      block.setCharsFromContent()
    }
    content = Nullable.empty
  }

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def parseInlines(inlineParser: InlineParser): Unit = {}
}

object GitLabBlockQuoteParser {

  val GIT_LAB_BLOCK_START: Pattern = Pattern.compile(">>>(\\s*$)")
  val GIT_LAB_BLOCK_END:   Pattern = Pattern.compile(">>>(\\s*$)")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val gitLabOptions: GitLabOptions = new GitLabOptions(options)

    private def haveBlockQuoteParser(state: ParserState): Boolean = {
      val parsers = state.activeBlockParsers
      parsers.exists(_.isInstanceOf[GitLabBlockQuoteParser])
    }

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (gitLabOptions.nestedBlockQuotes || !haveBlockQuoteParser(state)) {
        val line    = state.lineWithEOL
        val matcher = GIT_LAB_BLOCK_START.matcher(line)
        if (matcher.matches()) {
          Nullable(
            BlockStart.of(new GitLabBlockQuoteParser(state.properties, line.subSequence(0, 3), line.subSequence(matcher.start(1), matcher.end(1)))).atIndex(state.lineEndIndex)
          )
        } else {
          BlockStart.none()
        }
      } else {
        BlockStart.none()
      }
  }
}
