/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package tag
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.ast.util.Parsing
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.util.ast.{Block, BlockContent}
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class JekyllTagBlockParser(options: DataHolder) extends AbstractBlockParser {

  val block: JekyllTagBlock = new JekyllTagBlock()
  private var content: Nullable[BlockContent] = Nullable(new BlockContent())

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = BlockContinue.none()

  override def addLine(state: ParserState, line: BasedSequence): Unit = {
    content.foreach(_.add(line, state.indent))
  }

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      block.setContent(c)
      //block.setCharsFromContent();
    }
    content = Nullable.empty
  }

  override def parseInlines(inlineParser: InlineParser): Unit = {}
}

object JekyllTagBlockParser {

  val INCLUDE_TAG: String = "include"

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val parsing: JekyllTagParsing = new JekyllTagParsing(new Parsing(options))
    private val listIncludesOnly: Boolean = JekyllTagExtension.LIST_INCLUDES_ONLY.get(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = {
      val line = state.line
      val currentIndent = state.indent
      if (currentIndent == 0 && !matchedBlockParser.blockParser.getBlock.isInstanceOf[Paragraph]) {
        val tryLine = line.subSequence(state.getIndex)
        val matcher = parsing.MACRO_OPEN.matcher(tryLine)

        if (matcher.find()) {
          // see if it closes on the same line, then we create a block and close it
          val tagSeq = tryLine.subSequence(0, matcher.end())
          val tagName = line.subSequence(matcher.start(1), matcher.end(1))
          val parameters = tryLine.subSequence(matcher.end(1), matcher.end() - 2).trim()

          val tagNode = new JekyllTag(tagSeq.subSequence(0, 2), tagName, parameters, tagSeq.endSequence(2))
          tagNode.setCharsFromContent()

          val parser = new JekyllTagBlockParser(state.properties)
          parser.block.appendChild(tagNode)

          if (!listIncludesOnly || tagName.equals(INCLUDE_TAG)) {
            val tagList = JekyllTagExtension.TAG_LIST.get(state.properties)
            tagList.add(tagNode)
          }

          Nullable(BlockStart.of(parser).atIndex(state.lineEndIndex))
        } else {
          BlockStart.none()
        }
      } else {
        BlockStart.none()
      }
    }
  }
}
