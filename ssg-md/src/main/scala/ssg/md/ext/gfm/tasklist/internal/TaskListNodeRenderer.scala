/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/internal/TaskListNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package tasklist
package internal

import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.parser.ListOptions
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions


class TaskListNodeRenderer(options: DataHolder) extends NodeRenderer {

  val doneMarker: String = TaskListExtension.ITEM_DONE_MARKER.get(options)
  val notDoneMarker: String = TaskListExtension.ITEM_NOT_DONE_MARKER.get(options)
  private val tightItemClass: String = TaskListExtension.TIGHT_ITEM_CLASS.get(options)
  private val looseItemClass: String = TaskListExtension.LOOSE_ITEM_CLASS.get(options)
  private val itemDoneClass: String = TaskListExtension.ITEM_DONE_CLASS.get(options)
  private val itemNotDoneClass: String = TaskListExtension.ITEM_NOT_DONE_CLASS.get(options)
  val paragraphClass: String = TaskListExtension.PARAGRAPH_CLASS.get(options)
  private val listOptions: ListOptions = ListOptions.get(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += (new NodeRenderingHandler[TaskListItem](classOf[TaskListItem], (node, ctx, html) => render(node, ctx, html)))
    Nullable(set.toSet)
  }

  private[tasklist] def render(node: TaskListItem, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val sourceText: BasedSequence = if (context.getHtmlOptions.sourcePositionParagraphLines || node.firstChild.isEmpty) node.chars else node.firstChild.get.chars
    val itemDoneStatusClass = if (node.isItemDoneMarker) itemDoneClass else itemNotDoneClass
    if (listOptions.isTightListItem(node)) {
      if (!tightItemClass.isEmpty) html.attr("class", tightItemClass)
      if (!itemDoneStatusClass.isEmpty && itemDoneStatusClass != tightItemClass) html.attr("class", itemDoneStatusClass)
      html.srcPos(sourceText.startOffset, sourceText.endOffset).withAttr(CoreNodeRenderer.TIGHT_LIST_ITEM).withCondIndent().tagLine("li", () => {
        html.raw(if (node.isItemDoneMarker) doneMarker else notDoneMarker)
        context.renderChildren(node)
      })
    } else {
      if (!looseItemClass.isEmpty) html.attr("class", looseItemClass)
      if (!itemDoneStatusClass.isEmpty && itemDoneStatusClass != looseItemClass) html.attr("class", itemDoneStatusClass)
      html.withAttr(CoreNodeRenderer.LOOSE_LIST_ITEM).tagIndent("li", () => {
        if (!paragraphClass.isEmpty) html.attr("class", paragraphClass)
        html.srcPos(sourceText.startOffset, sourceText.endOffset).withAttr(TaskListNodeRenderer.TASK_ITEM_PARAGRAPH).tagLine("p", () => {
          html.raw(if (node.isItemDoneMarker) doneMarker else notDoneMarker)
          context.renderChildren(node)
        })
      })
    }
  }
}

object TaskListNodeRenderer {

  val TASK_ITEM_PARAGRAPH: AttributablePart = new AttributablePart("TASK_ITEM_PARAGRAPH")

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new TaskListNodeRenderer(options)
  }
}
