/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationBlockParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions

class AbbreviationBlockParser extends AbstractBlockParser {

  val block: AbbreviationBlock = new AbbreviationBlock()

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    BlockContinue.none()

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    throw new IllegalStateException("Abbreviation Blocks hold a single line")

  override def closeBlock(state: ParserState): Unit = {
    // add it to the map
    val abbreviationMap = AbbreviationExtension.ABBREVIATIONS.get(state.properties)
    abbreviationMap.put(abbreviationMap.normalizeKey(block.text), block)
  }

  override def parseInlines(inlineParser: InlineParser): Unit = {
    // no inlines in text or abbreviation
  }

  override def isContainer: Boolean = true
}

object AbbreviationBlockParser {

  val ABBREVIATION_BLOCK: Pattern = Pattern.compile("^\\*\\[\\s*.*\\s*\\]:")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val line         = state.line
        val nextNonSpace = state.nextNonSpaceIndex

        val trySequence = line.subSequence(nextNonSpace, line.length())
        val matcher     = ABBREVIATION_BLOCK.matcher(trySequence)
        if (matcher.find()) {
          // abbreviation definition
          val openingStart  = nextNonSpace + matcher.start()
          val openingEnd    = nextNonSpace + matcher.end()
          val openingMarker = trySequence.subSequence(openingStart, openingStart + 2)
          val text          = trySequence.subSequence(openingStart + 2, openingEnd - 2).trim()
          val closingMarker = trySequence.subSequence(openingEnd - 2, openingEnd)

          val abbreviationBlock = new AbbreviationBlockParser()
          abbreviationBlock.block.openingMarker = openingMarker
          abbreviationBlock.block.text = text
          abbreviationBlock.block.closingMarker = closingMarker
          abbreviationBlock.block.abbreviation = trySequence.subSequence(matcher.end()).trim()
          abbreviationBlock.block.setCharsFromContent()

          Nullable(BlockStart.of(abbreviationBlock).atIndex(line.length()))
        } else {
          BlockStart.none()
        }
      }
  }
}
