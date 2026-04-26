/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package gfm
package tasklist

import ssg.md.ext.gfm.tasklist.internal.{ TaskListItemBlockPreProcessor, TaskListNodeFormatter, TaskListNodeRenderer }
import ssg.md.formatter.{ Formatter, NodeFormatter, NodeFormatterFactory }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.{ ListOptions, Parser }
import ssg.md.util.data.{ DataKey, MutableDataHolder }

import java.util.{ HashMap, Map as JMap }

/** Extension for GFM style task list items.
  *
  * Create it with [[TaskListExtension.create]] and then configure it on the builders.
  *
  * The bullet list items that begin with [ ], [x] or [X] are turned into TaskListItem nodes.
  */
class TaskListExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Formatter.FormatterExtension {

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(
      new NodeFormatterFactory {
        override def create(options: ssg.md.util.data.DataHolder): NodeFormatter = new TaskListNodeFormatter(options)
      }
    )

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit =
    ListOptions.addItemMarkerSuffixes(options, "[ ]", "[x]", "[X]")

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.blockPreProcessorFactory(new TaskListItemBlockPreProcessor.Factory())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit =
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new TaskListNodeRenderer.Factory())
    } else if (htmlRendererBuilder.isRendererType("JIRA")) {
      // no-op
    }
}

object TaskListExtension {

  val DEFAULT_PRIORITIES: JMap[Character, Integer] = {
    val map = new HashMap[Character, Integer]()
    map.put('+', 1)
    map.put('*', 0)
    map.put('-', -1)
    map
  }

  val ITEM_DONE_MARKER: DataKey[String] = new DataKey[String](
    "ITEM_DONE_MARKER",
    "<input type=\"checkbox\" class=\"task-list-item-checkbox\" checked=\"checked\" disabled=\"disabled\" readonly=\"readonly\" />&nbsp;"
  )
  val ITEM_NOT_DONE_MARKER: DataKey[String] = new DataKey[String](
    "ITEM_NOT_DONE_MARKER",
    "<input type=\"checkbox\" class=\"task-list-item-checkbox\" disabled=\"disabled\" readonly=\"readonly\" />&nbsp;"
  )
  val TIGHT_ITEM_CLASS:    DataKey[String] = new DataKey[String]("TIGHT_ITEM_CLASS", "task-list-item")
  val LOOSE_ITEM_CLASS:    DataKey[String] = new DataKey[String]("LOOSE_ITEM_CLASS", TIGHT_ITEM_CLASS)
  val PARAGRAPH_CLASS:     DataKey[String] = new DataKey[String]("PARAGRAPH_CLASS", "")
  val ITEM_DONE_CLASS:     DataKey[String] = new DataKey[String]("ITEM_DONE_CLASS", "")
  val ITEM_NOT_DONE_CLASS: DataKey[String] = new DataKey[String]("ITEM_NOT_DONE_CLASS", "")

  // formatting options
  val FORMAT_LIST_ITEM_CASE:             DataKey[TaskListItemCase]      = new DataKey[TaskListItemCase]("FORMAT_LIST_ITEM_CASE", TaskListItemCase.AS_IS)
  val FORMAT_LIST_ITEM_PLACEMENT:        DataKey[TaskListItemPlacement] = new DataKey[TaskListItemPlacement]("FORMAT_LIST_ITEM_PLACEMENT", TaskListItemPlacement.AS_IS)
  val FORMAT_ORDERED_TASK_ITEM_PRIORITY: DataKey[Integer]               = new DataKey[Integer]("FORMAT_ORDERED_TASK_ITEM_PRIORITY", 0)
  val FORMAT_DEFAULT_TASK_ITEM_PRIORITY: DataKey[Integer]               = new DataKey[Integer]("FORMAT_DEFAULT_TASK_ITEM_PRIORITY", 0)
  val FORMAT_PRIORITIZED_TASK_ITEMS:     DataKey[Boolean]               = new DataKey[Boolean]("FORMAT_PRIORITIZED_TASK_ITEMS", false)

  /** Priorities corresponding to Parser.LISTS_ITEM_PREFIX_CHARS. If shorter than item prefix chars then any missing priorities are set to 0.
    */
  val FORMAT_TASK_ITEM_PRIORITIES: DataKey[JMap[Character, Integer]] = new DataKey[JMap[Character, Integer]]("FORMAT_TASK_ITEM_PRIORITIES", DEFAULT_PRIORITIES)

  def create(): TaskListExtension = new TaskListExtension()
}
