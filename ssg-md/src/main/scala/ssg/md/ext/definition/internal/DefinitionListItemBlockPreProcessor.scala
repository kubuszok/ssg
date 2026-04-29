/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionListItemBlockPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionListItemBlockPreProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.parser.Parser
import ssg.md.parser.block.{ BlockPreProcessor, BlockPreProcessorFactory, ParserState }
import ssg.md.parser.core.ParagraphParser
import ssg.md.util.ast.{ BlankLine, Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

class DefinitionListItemBlockPreProcessor(options: DataHolder) extends BlockPreProcessor {

  private val defOptions:      DefinitionOptions = new DefinitionOptions(options)
  private val blankLinesInAst: Boolean           = Parser.BLANK_LINES_IN_AST.get(options)

  override def preProcess(state: ParserState, block: Block): Unit = {
    block match {
      case definitionItem: DefinitionItem =>
        // we chop up the previous paragraph into definition terms and add the definition item to the last one
        // we add all these to the previous DefinitionList or add a new one if there isn't one
        val previous = block.previousAnyNot(classOf[BlankLine])

        val trailingBlankLines = new DefinitionList()

        val blankLine = definitionItem.next
        blankLine.foreach { bl =>
          if (bl.isInstanceOf[BlankLine]) {
            bl.extractChainTo(trailingBlankLines)
          }
        }

        if (previous.exists(_.isInstanceOf[Paragraph])) {
          val paragraph             = previous.get.asInstanceOf[Paragraph]
          val afterParagraph        = paragraph.next
          val paragraphPrevNonBlank = paragraph.previousAnyNot(classOf[BlankLine])
          val paragraphPrevious     = paragraph.previous
          val paragraphParent       = paragraph.parent

          definitionItem.unlink()
          paragraph.unlink()
          state.blockRemovedWithChildren(paragraph)

          val hadPreviousList: Boolean =
            if (defOptions.doubleBlankLineBreaksList) {
              // intervening characters between previous paragraph and definition terms
              val interSpace = paragraphPrevNonBlank.fold(BasedSequence.NULL) { prev =>
                BasedSequence.of(prev.baseSubSequence(prev.endOffset, paragraph.startOffset).normalizeEOL())
              }
              paragraphPrevNonBlank.exists(_.isInstanceOf[DefinitionList]) && interSpace.countLeading(ssg.md.util.misc.CharPredicate.EOL) < 2
            } else {
              paragraphPrevNonBlank.exists(_.isInstanceOf[DefinitionList])
            }

          val definitionList = new DefinitionList()
          definitionList.tight_=(true)

          val lines = paragraph.contentLines

          var lineIndex = 0
          val lineIter  = lines.iterator()
          while (lineIter.hasNext) {
            val line = lineIter.next()
            val dt   = new DefinitionTerm()

            val parser  = new ParagraphParser()
            val content = new BlockContent()
            content.add(line, paragraph.getLineIndent(lineIndex))
            lineIndex += 1
            parser.getBlock.setContent(content)
            parser.getBlock.setCharsFromContent()

            dt.appendChild(parser.getBlock)
            dt.setCharsFromContent()

            state.blockParserAdded(parser)

            definitionList.appendChild(dt)
            state.blockAdded(dt)
          }

          // if have blank lines after paragraph need to move them after the last term
          if (blankLinesInAst) {
            var ap = afterParagraph
            while (ap.exists(_.isInstanceOf[BlankLine])) {
              val next = ap.flatMap(_.next)
              ap.foreach { n =>
                n.unlink()
                definitionList.appendChild(n)
              }
              ap = next
            }
          }

          definitionList.appendChild(definitionItem)
          definitionList.takeChildren(trailingBlankLines)

          if (hadPreviousList) {
            val previousList = paragraphPrevNonBlank.get.asInstanceOf[DefinitionList]
            previousList.takeChildren(definitionList)
            for (node <- definitionList.children.asScala) {
              node.unlink()
              previousList.appendChild(node)
              state.blockAddedWithChildren(node.asInstanceOf[Block])
            }
            previousList.setCharsFromContent()
          } else {
            // insert new one, after paragraphPrevious
            if (paragraphPrevNonBlank.isDefined) {
              paragraphPrevious.foreach(_.insertAfter(definitionList))
            } else {
              paragraphParent.foreach { pp =>
                if (pp.firstChild.isDefined) {
                  pp.firstChild.get.insertBefore(definitionList)
                } else {
                  pp.appendChild(definitionList)
                }
              }
            }

            definitionList.setCharsFromContent()
            state.blockAddedWithChildren(definitionList)
          }

        } else if (previous.exists(_.isInstanceOf[DefinitionList])) {
          val defList = previous.get.asInstanceOf[DefinitionList]
          definitionItem.unlink()
          defList.appendChild(definitionItem)
          defList.takeChildren(trailingBlankLines)
          defList.setCharsFromContent()

        } else {
          ()
        }

      case _ => ()
    }
  }
}

object DefinitionListItemBlockPreProcessor {

  class Factory extends BlockPreProcessorFactory {

    override def getBlockTypes: Set[Class[? <: Block]] =
      Set[Class[? <: Block]](classOf[DefinitionItem])

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = true

    override def apply(state: ParserState): BlockPreProcessor = new DefinitionListItemBlockPreProcessor(state.properties)
  }
}
