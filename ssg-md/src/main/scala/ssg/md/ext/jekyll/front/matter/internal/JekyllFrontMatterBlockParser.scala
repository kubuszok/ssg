/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/internal/JekyllFrontMatterBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/internal/JekyllFrontMatterBlockParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package jekyll
package front
package matter
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.parser.core.DocumentBlockParser
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions
import java.util.regex.Pattern

class JekyllFrontMatterBlockParser(options: DataHolder, openingMarker: BasedSequence) extends AbstractBlockParser {

  private val block:       JekyllFrontMatterBlock = new JekyllFrontMatterBlock()
  private var content:     Nullable[BlockContent] = Nullable(new BlockContent())
  private var inYAMLBlock: Boolean                = true

  block.openingMarker = openingMarker

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val line = state.line
    if (inYAMLBlock) {
      val matcher = JekyllFrontMatterBlockParser.JEKYLL_FRONT_MATTER_BLOCK_END.matcher(line)
      if (matcher.matches()) {
        block.closingMarker = line.subSequence(matcher.start(1), matcher.end(1))
        Nullable(BlockContinue.finished())
      } else {
        Nullable(BlockContinue.atIndex(state.getIndex))
      }
    } else if (JekyllFrontMatterBlockParser.JEKYLL_FRONT_MATTER_BLOCK_START.matcher(line).matches()) {
      inYAMLBlock = true
      Nullable(BlockContinue.atIndex(state.getIndex))
    } else {
      BlockContinue.none()
    }
  }

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.foreach(_.add(line, state.indent))

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      block.setContent(c.lines.subList(1, c.lineCount))
      block.setCharsFromContent()
    }
    content = Nullable.empty
  }

  override def parseInlines(inlineParser: InlineParser): Unit = {}
}

object JekyllFrontMatterBlockParser {

  val JEKYLL_FRONT_MATTER_BLOCK_START: Pattern = Pattern.compile("^-{3}(\\s.*)?")
  val JEKYLL_FRONT_MATTER_BLOCK_END:   Pattern = Pattern.compile("^(-{3}|\\.{3})(\\s.*)?")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = {
      val line         = state.line
      val parentParser = matchedBlockParser.blockParser
      if (parentParser.isInstanceOf[DocumentBlockParser] && parentParser.getBlock.firstChild.isEmpty) {
        val matcher = JEKYLL_FRONT_MATTER_BLOCK_START.matcher(line)
        if (matcher.matches()) {
          val openingMarker = line.subSequence(0, 3)
          val parser        = new JekyllFrontMatterBlockParser(state.properties, openingMarker)
          Nullable(BlockStart.of(parser).atIndex(-1))
        } else {
          BlockStart.none()
        }
      } else {
        BlockStart.none()
      }
    }
  }
}
