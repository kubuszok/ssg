/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/internal/YamlFrontMatterBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/internal/YamlFrontMatterBlockParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package yaml
package front
package matter
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.block.*
import ssg.md.parser.core.DocumentBlockParser
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.{ BasedSequence, PrefixedSubSequence, SegmentedSequence }

import scala.language.implicitConversions
import java.util.regex.Pattern
import java.util.{ ArrayList, List as JList }

class YamlFrontMatterBlockParser extends AbstractBlockParser {

  private var inYAMLBlock:   Boolean                 = true
  private var inLiteral:     Boolean                 = false
  private var currentKey:    Nullable[BasedSequence] = Nullable.empty
  private var currentValues: JList[BasedSequence]    = new ArrayList[BasedSequence]()
  private val block:         YamlFrontMatterBlock    = new YamlFrontMatterBlock()
  private var content:       Nullable[BlockContent]  = Nullable(new BlockContent())

  override def getBlock: Block = block

  override def isContainer: Boolean = false

  override def addLine(state: ParserState, line: BasedSequence): Unit =
    content.foreach(_.add(line, state.indent))

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      block.setContent(c.lines.subList(0, c.lineCount))
      block.setCharsFromContent()
    }
    content = Nullable.empty
  }

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val line = state.line

    if (inYAMLBlock) {
      if (YamlFrontMatterBlockParser.REGEX_END.matcher(line).matches()) {
        currentKey.foreach { key =>
          val child = new YamlFrontMatterNode(key, currentValues)
          child.setCharsFromContent()
          block.appendChild(child)
        }
        // add the last line
        addLine(state, line)
        BlockContinue.finished()
      } else {
        val matcher = YamlFrontMatterBlockParser.REGEX_METADATA.matcher(line)
        if (matcher.matches()) {
          currentKey.foreach { key =>
            val child = new YamlFrontMatterNode(key, currentValues)
            child.setCharsFromContent()
            block.appendChild(child)
          }

          inLiteral = false
          currentKey = Nullable(line.subSequence(matcher.start(1), matcher.end(1)))
          currentValues = new ArrayList[BasedSequence]()
          if ("|" == matcher.group(2)) {
            inLiteral = true
          } else if ("" != matcher.group(2)) {
            currentValues.add(line.subSequence(matcher.start(2), matcher.end(2)))
          }

          BlockContinue.atIndex(state.getIndex)
        } else {
          if (inLiteral) {
            val litMatcher = YamlFrontMatterBlockParser.REGEX_METADATA_LITERAL.matcher(line)
            if (litMatcher.matches()) {
              if (currentValues.size() == 1) {
                val combined = SegmentedSequence.create(
                  currentValues.get(0),
                  PrefixedSubSequence.prefixOf("\n", line.subSequence(litMatcher.start(1), litMatcher.end(1)).trim())
                )
                currentValues.set(0, combined)
              } else {
                currentValues.add(line.subSequence(litMatcher.start(1), litMatcher.end(1)).trim())
              }
            }
          } else {
            val listMatcher = YamlFrontMatterBlockParser.REGEX_METADATA_LIST.matcher(line)
            if (listMatcher.matches()) {
              currentValues.add(line.subSequence(listMatcher.start(1), listMatcher.end(1)))
            }
          }

          BlockContinue.atIndex(state.getIndex)
        }
      }
    } else if (YamlFrontMatterBlockParser.REGEX_BEGIN.matcher(line).matches()) {
      inYAMLBlock = true
      BlockContinue.atIndex(state.getIndex)
    } else {
      BlockContinue.none()
    }
  }

  override def parseInlines(inlineParser: InlineParser): Unit = {}
}

object YamlFrontMatterBlockParser {

  private val REGEX_METADATA:         Pattern = Pattern.compile("^[ ]{0,3}([A-Za-z0-9_\\-.]+):\\s*(.*)")
  private val REGEX_METADATA_LIST:    Pattern = Pattern.compile("^[ ]+-\\s*(.*)")
  private val REGEX_METADATA_LITERAL: Pattern = Pattern.compile("^\\s*(.*)")
  private val REGEX_BEGIN:            Pattern = Pattern.compile("^-{3}(\\s.*)?")
  private val REGEX_END:              Pattern = Pattern.compile("^(-{3}|\\.{3})(\\s.*)?")

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
      // check whether this line is the first line of whole document or not
      if (
        parentParser.isInstanceOf[DocumentBlockParser] && parentParser.getBlock.firstChild.isEmpty &&
        REGEX_BEGIN.matcher(line).matches()
      ) {
        BlockStart.of(new YamlFrontMatterBlockParser()).atIndex(state.nextNonSpaceIndex)
      } else {
        BlockStart.none()
      }
    }
  }
}
