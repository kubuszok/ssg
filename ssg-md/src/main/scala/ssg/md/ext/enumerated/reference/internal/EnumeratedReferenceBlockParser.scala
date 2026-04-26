/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceBlockParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions
import java.util.regex.Pattern

class EnumeratedReferenceBlockParser(options: EnumeratedReferenceOptions, contentOffset: Int) extends AbstractBlockParser {

  val block_          = new EnumeratedReferenceBlock()
  private var content = new BlockContent()

  def blockContent: BlockContent = content

  override def getBlock: Block = block_

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    BlockContinue.none()

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    throw new IllegalStateException("Abbreviation Blocks hold a single line")

  override def closeBlock(state: ParserState): Unit = {
    // set the enumeratedReference from closingMarker to end
    block_.setCharsFromContent()
    block_.enumeratedReference = block_.chars.subSequence(block_.closingMarker.endOffset - block_.startOffset).trimStart()
    content = null

    // add block to reference repository
    val enumeratedReferences = EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(state.properties)
    enumeratedReferences.put(block_.text.toString, block_)
  }

  override def parseInlines(inlineParser: InlineParser): Unit = {
    val paragraph = block_.firstChild
    if (paragraph.isDefined) {
      inlineParser.parse(paragraph.get.chars, paragraph.get)
    }
  }

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean =
    blockParser.isParagraphParser
}

object EnumeratedReferenceBlockParser {
  val ENUM_REF_ID:          String  = "(?:[^0-9].*)?";
  val ENUM_REF_ID_PATTERN:  Pattern = Pattern.compile("\\[[\\@|#]\\s*(" + ENUM_REF_ID + ")\\s*\\]")
  val ENUM_REF_DEF_PATTERN: Pattern = Pattern.compile("^(\\[[\\@]\\s*(" + ENUM_REF_ID + ")\\s*\\]:)\\s+")

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {
    private val options_ = new EnumeratedReferenceOptions(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val line         = state.lineWithEOL
        val nextNonSpace = state.nextNonSpaceIndex

        val trySequence = line.subSequence(nextNonSpace, line.length())
        val matcher     = ENUM_REF_DEF_PATTERN.matcher(trySequence)
        if (matcher.find()) {
          // abbreviation definition
          val openingStart  = nextNonSpace + matcher.start(1)
          val openingEnd    = nextNonSpace + matcher.end(1)
          val openingMarker = line.subSequence(openingStart, openingStart + 2)
          val text          = line.subSequence(matcher.start(2), matcher.end(2))
          val closingMarker = line.subSequence(openingEnd - 2, openingEnd)

          val contentOffset = options_.contentIndent

          val enumeratedReferenceBlockParser = new EnumeratedReferenceBlockParser(options_, contentOffset)
          enumeratedReferenceBlockParser.block_.openingMarker = openingMarker
          enumeratedReferenceBlockParser.block_.text = text
          enumeratedReferenceBlockParser.block_.closingMarker = closingMarker
          val enumeratedReference = trySequence.subSequence(matcher.end())
          enumeratedReferenceBlockParser.block_.enumeratedReference = enumeratedReference
          val paragraph = new Paragraph(enumeratedReference)
          enumeratedReferenceBlockParser.block_.appendChild(paragraph)
          enumeratedReferenceBlockParser.block_.setCharsFromContent()

          Nullable(BlockStart.of(enumeratedReferenceBlockParser).atIndex(line.length()))
        } else {
          BlockStart.none()
        }
      }
  }
}
