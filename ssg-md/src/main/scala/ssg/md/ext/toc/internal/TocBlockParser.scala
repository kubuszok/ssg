/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocBlockParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.Nullable
import ssg.md.parser.block.*
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions

class TocBlockParser(tocChars: BasedSequence, styleChars: BasedSequence) extends AbstractBlockParser {

  private val block: TocBlock = new TocBlock(tocChars, styleChars)

  override def getBlock: Block = block

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = BlockContinue.none()

  override def closeBlock(state: ParserState): Unit = {}
}

object TocBlockParser {

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val tocPattern: Pattern = {
      val caseSensitive = TocExtension.CASE_SENSITIVE_TOC_TAG.get(options)
      if (caseSensitive) Pattern.compile("^\\[TOC(?:\\s+([^\\]]+))?]\\s*$")
      // Cross-platform: (?i:TOC) inline flag not supported on Scala Native re2.
      // Original: "^\\[(?i:TOC)(?:\\s+([^\\]]+))?]\\s*$"
      // Revert when scala-native#4810 ships.
      else Pattern.compile("^\\[[Tt][Oo][Cc](?:\\s+([^\\]]+))?]\\s*$")
    }

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.indent >= 4) {
        BlockStart.none()
      } else {
        val line    = state.line
        val matcher = tocPattern.matcher(line)
        if (matcher.matches()) {
          val tocChars = state.lineWithEOL
          val styleChars: BasedSequence =
            if (matcher.start(1) != -1) line.subSequence(matcher.start(1), matcher.end(1))
            else null.asInstanceOf[BasedSequence] // @nowarn - Java interop: TocBlockBase accepts null
          val tocBlockParser = new TocBlockParser(tocChars, styleChars)
          Nullable(BlockStart.of(tocBlockParser).atIndex(state.getIndex))
        } else {
          BlockStart.none()
        }
      }
  }
}
