/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/SimTocBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/SimTocBlockParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.Nullable
import ssg.md.ast.{ Heading, HtmlBlock, ListBlock }
import ssg.md.ast.util.Parsing
import ssg.md.parser.block.*
import ssg.md.util.ast.{ Block, Node }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions

class SimTocBlockParser(options: DataHolder, tocChars: BasedSequence, styleChars: BasedSequence, titleChars: BasedSequence) extends AbstractBlockParser {

  private val tocOptions:      TocOptions    = TocOptions.fromOptions(options, true)
  private val block:           SimTocBlock   = new SimTocBlock(tocChars, styleChars, titleChars)
  private var haveChildren:    Int           = 0
  private var blankLineSpacer: BasedSequence = BasedSequence.NULL

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    // we stop on a blank line if blank line spacer is not enabled or we already had one
    if ((!tocOptions.isBlankLineSpacer || haveChildren != 0) && state.isBlank) {
      BlockContinue.none()
    } else {
      if (state.isBlank) {
        haveChildren |= SimTocBlockParser.HAVE_BLANK_LINE
        blankLineSpacer = state.line
      }
      Nullable(BlockContinue.atIndex(state.getIndex))
    }

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean =
    if (block.isInstanceOf[HtmlBlock]) {
      if ((haveChildren & ~SimTocBlockParser.HAVE_BLANK_LINE) == 0) {
        haveChildren |= SimTocBlockParser.HAVE_HTML
        true
      } else false
    } else if (block.isInstanceOf[Heading]) {
      if ((haveChildren & ~SimTocBlockParser.HAVE_BLANK_LINE) == 0) {
        haveChildren |= SimTocBlockParser.HAVE_HEADING
        true
      } else false
    } else if (block.isInstanceOf[ListBlock]) {
      if ((haveChildren & (SimTocBlockParser.HAVE_HTML | SimTocBlockParser.HAVE_LIST)) == 0) {
        haveChildren |= SimTocBlockParser.HAVE_LIST
        true
      } else false
    } else {
      false
    }

  override def isContainer: Boolean = true

  override def closeBlock(state: ParserState): Unit = {
    if (block.hasChildren) {
      // move the children to a SimTocContent node
      val tocContent = new SimTocContent()
      tocContent.takeChildren(block)
      tocContent.setCharsFromContent()

      if (blankLineSpacer.isNotNull) {
        // need to extend the content node start to include the blank line
        tocContent.chars = Node.spanningChars(blankLineSpacer, tocContent.chars)
      }

      block.appendChild(tocContent)
      block.setCharsFromContent()
      state.blockAddedWithChildren(tocContent)
    }

    // now add the options list and options with their text
    if (tocOptions.isAstAddOptions && !block.style.isEmpty) {
      val optionsParser = new SimTocOptionsParser()
      val pair          = optionsParser.parseOption(block.style, TocOptions.DEFAULT, Nullable.empty)
      val parsedOptions = pair.second.get
      if (!parsedOptions.isEmpty) {
        val optionsNode = new SimTocOptionList()
        val it          = parsedOptions.iterator()
        while (it.hasNext) {
          val option     = it.next()
          val optionNode = new SimTocOption(option.source)
          optionsNode.appendChild(optionNode)
        }
        optionsNode.setCharsFromContent()
        block.prependChild(optionsNode)
      }
    }

    block.setCharsFromContent()
  }
}

object SimTocBlockParser {

  val HAVE_HTML:       Int = 1
  val HAVE_HEADING:    Int = 2
  val HAVE_LIST:       Int = 4
  val HAVE_BLANK_LINE: Int = 8

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val tocPattern: Pattern = {
      val parsing       = new Parsing(options)
      val caseSensitive = TocExtension.CASE_SENSITIVE_TOC_TAG.get(options)
      if (caseSensitive) Pattern.compile("^\\[TOC(?:\\s+([^\\]]+))?]:\\s*#(?:\\s+(" + parsing.LINK_TITLE_STRING + "))?\\s*$")
      // Cross-platform: (?i:TOC) inline flag not supported on Scala Native re2.
      // Original: "^\\[(?i:TOC)(?:\\s+([^\\]]+))?]:\\s*#(?:\\s+(" + parsing.LINK_TITLE_STRING + "))?\\s*$"
      // Revert when scala-native#4810 ships.
      else Pattern.compile("^\\[[Tt][Oo][Cc](?:\\s+([^\\]]+))?]:\\s*#(?:\\s+(" + parsing.LINK_TITLE_STRING + "))?\\s*$")
    }

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val line         = state.line
        val nextNonSpace = state.nextNonSpaceIndex
        val trySequence  = line.subSequence(nextNonSpace, line.length())
        val matcher      = tocPattern.matcher(line)
        if (matcher.matches()) {
          val tocChars = state.lineWithEOL
          val styleChars: BasedSequence =
            if (matcher.start(1) != -1) trySequence.subSequence(matcher.start(1), matcher.end(1))
            else null.asInstanceOf[BasedSequence] // @nowarn - Java interop: TocBlockBase accepts null
          val titleChars: BasedSequence =
            if (matcher.start(2) != -1) trySequence.subSequence(matcher.start(2), matcher.end(2))
            else null.asInstanceOf[BasedSequence] // @nowarn - Java interop: SimTocBlock accepts null

          val simTocBlockParser = new SimTocBlockParser(state.properties, tocChars, styleChars, titleChars)
          Nullable(BlockStart.of(simTocBlockParser).atIndex(state.lineEndIndex + state.lineEolLength))
        } else {
          BlockStart.none()
        }
      }
  }
}
