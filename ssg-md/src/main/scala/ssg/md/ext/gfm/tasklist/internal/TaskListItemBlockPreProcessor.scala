/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/internal/TaskListItemBlockPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package tasklist
package internal

import ssg.md.Nullable
import ssg.md.ast.{ BulletListItem, ListItem, OrderedListItem }
import ssg.md.parser.block.{ BlockPreProcessor, BlockPreProcessorFactory, ParserState }
import ssg.md.util.ast.Block
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class TaskListItemBlockPreProcessor(options: DataHolder) extends BlockPreProcessor {

  override def preProcess(state: ParserState, block: Block): Unit =
    if (block.isInstanceOf[BulletListItem] || block.isInstanceOf[OrderedListItem]) {
      // we chop up the previous paragraph into definition terms and add the definition item to the last one
      // we add all these to the previous DefinitionList or add a new one if there isn't one
      val listItem     = block.asInstanceOf[ListItem]
      val markerSuffix = listItem.markerSuffix

      if (markerSuffix.matches("[ ]") || markerSuffix.matches("[x]") || markerSuffix.matches("[X]")) {
        val taskListItem = new TaskListItem(listItem)
        taskListItem.tight_=(listItem.isOwnTight)
        listItem.insertBefore(taskListItem)
        listItem.unlink()
        state.blockAdded(taskListItem)
        state.blockRemoved(listItem)
      }
    }
}

object TaskListItemBlockPreProcessor {

  class Factory extends BlockPreProcessorFactory {

    override def getBlockTypes: Set[Class[? <: Block]] =
      Set[Class[? <: Block]](classOf[BulletListItem], classOf[OrderedListItem])

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = true

    override def apply(state: ParserState): BlockPreProcessor = new TaskListItemBlockPreProcessor(state.properties)
  }
}
