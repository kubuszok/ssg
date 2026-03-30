/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionListBlockPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.Nullable
import ssg.md.parser.Parser
import ssg.md.parser.block.{BlockPreProcessor, BlockPreProcessorFactory, ParserState}
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

class DefinitionListBlockPreProcessor(options: DataHolder) extends BlockPreProcessor {

  private val defOptions: DefinitionOptions = new DefinitionOptions(options)

  override def preProcess(state: ParserState, block: Block): Unit = {
    val blankLinesInAST = Parser.BLANK_LINES_IN_AST.get(state.properties)

    block match {
      case definitionList: DefinitionList =>
        // need to propagate loose/tight
        var isTight = definitionList.isTight
        if (defOptions.autoLoose && isTight) {
          for (child <- definitionList.children.asScala) {
            child match {
              case defItem: DefinitionItem =>
                if (defItem.isLoose) {
                  isTight = false
                  if (!blankLinesInAST) {} // would break in Java; in Scala we just continue
                }
                if (blankLinesInAST) {
                  // transfer its trailing blank lines to uppermost level
                  child.moveTrailingBlankLines()
                }
              case _ => ()
            }
          }
          definitionList.tight_=(isTight)
        }

        if (blankLinesInAST) {
          definitionList.moveTrailingBlankLines()
        }
      case _ => ()
    }
  }
}

object DefinitionListBlockPreProcessor {

  class Factory extends BlockPreProcessorFactory {

    override def getBlockTypes: Set[Class[? <: Block]] = {
      Set[Class[? <: Block]](classOf[DefinitionList])
    }

    override def afterDependents: Nullable[Set[Class[?]]] = {
      Nullable(Set[Class[?]](classOf[DefinitionListItemBlockPreProcessor.Factory]))
    }

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = true

    override def apply(state: ParserState): BlockPreProcessor = new DefinitionListBlockPreProcessor(state.properties)
  }
}
