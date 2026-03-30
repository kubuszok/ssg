/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/internal/TaskListFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package tasklist
package internal

import ssg.md.util.data.{DataHolder, MutableDataHolder, MutableDataSetter}

import scala.language.implicitConversions

import java.util.{Map as JMap}

class TaskListFormatOptions(options: DataHolder) extends MutableDataSetter {

  val taskListItemCase: TaskListItemCase = TaskListExtension.FORMAT_LIST_ITEM_CASE.get(options)
  val taskListItemPlacement: TaskListItemPlacement = TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT.get(options)
  val formatOrderedTaskItemPriority: Int = TaskListExtension.FORMAT_ORDERED_TASK_ITEM_PRIORITY.get(options)
  val formatDefaultTaskItemPriority: Int = TaskListExtension.FORMAT_DEFAULT_TASK_ITEM_PRIORITY.get(options)
  val formatTaskItemPriorities: JMap[Character, Integer] = TaskListExtension.FORMAT_TASK_ITEM_PRIORITIES.get(options)
  val formatPrioritizedTaskItems: Boolean = TaskListExtension.FORMAT_PRIORITIZED_TASK_ITEMS.get(options)

  def this() = this(null)

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(TaskListExtension.FORMAT_LIST_ITEM_CASE, taskListItemCase)
    dataHolder.set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, taskListItemPlacement)
    dataHolder.set(TaskListExtension.FORMAT_ORDERED_TASK_ITEM_PRIORITY, formatOrderedTaskItemPriority)
    dataHolder.set(TaskListExtension.FORMAT_TASK_ITEM_PRIORITIES, formatTaskItemPriorities)
    dataHolder.set(TaskListExtension.FORMAT_PRIORITIZED_TASK_ITEMS, formatPrioritizedTaskItems)
    dataHolder
  }
}

