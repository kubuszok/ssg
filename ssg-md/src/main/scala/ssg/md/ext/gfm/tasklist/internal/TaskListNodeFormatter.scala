/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/internal/TaskListNodeFormatter.java
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
import ssg.md.ast.*
import ssg.md.formatter.*
import ssg.md.parser.ListOptions
import ssg.md.util.ast.{ BlankLine, Block, Node }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break
import java.util.ArrayList

class TaskListNodeFormatter(options: DataHolder) extends NodeFormatter {

  private val taskListFormatOptions: TaskListFormatOptions = new TaskListFormatOptions(options)
  private val listOptions:           ListOptions           = ListOptions.get(options)

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[TaskListItem](classOf[TaskListItem], (node, ctx, md) => renderTaskItem(node, ctx, md)),
        new NodeFormattingHandler[BulletList](classOf[BulletList], (node, ctx, md) => renderBulletList(node, ctx, md)),
        new NodeFormattingHandler[OrderedList](classOf[OrderedList], (node, ctx, md) => renderOrderedList(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  private def renderTaskItem(node: TaskListItem, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.isTransformingText) {
      FormatterUtils.renderListItem(node, context, markdown, listOptions, node.markerSuffix, false)
    } else {
      var markerSuffix = node.markerSuffix
      taskListFormatOptions.taskListItemCase match {
        case TaskListItemCase.AS_IS     => // no-op
        case TaskListItemCase.LOWERCASE => markerSuffix = markerSuffix.toLowerCase()
        case TaskListItemCase.UPPERCASE => markerSuffix = markerSuffix.toUpperCase()
      }

      if (node.isItemDoneMarker) {
        taskListFormatOptions.taskListItemPlacement match {
          case TaskListItemPlacement.AS_IS | TaskListItemPlacement.INCOMPLETE_FIRST | TaskListItemPlacement.INCOMPLETE_NESTED_FIRST =>
          // no-op
          case TaskListItemPlacement.COMPLETE_TO_NON_TASK | TaskListItemPlacement.COMPLETE_NESTED_TO_NON_TASK =>
            markerSuffix = markerSuffix.getEmptySuffix
        }
      }

      if (markerSuffix.isNotEmpty() && taskListFormatOptions.formatPrioritizedTaskItems) {
        node.canChangeMarker = false
      }

      // task list item node overrides isParagraphWrappingDisabled which affects empty list item blank line rendering
      val forceLooseItem = node.isLoose && (node.hasChildren && node.firstChildAnyNot(classOf[BlankLine]).isDefined)
      FormatterUtils.renderListItem(
        node,
        context,
        markdown,
        listOptions,
        if (markerSuffix.isEmpty) markerSuffix
        else {
          val b = markerSuffix.getBuilder[ssg.md.util.sequence.builder.SequenceBuilder]
          b.append(markerSuffix).append(" ").append(markerSuffix.baseSubSequence(markerSuffix.endOffset + 1, markerSuffix.endOffset + 1)).toSequence
        },
        forceLooseItem
      )
    }

  private def renderBulletList(node: BulletList, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderList(node, context, markdown)

  private def renderOrderedList(node: OrderedList, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderList(node, context, markdown)

  def renderList(node: ListBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.isTransformingText) {
      context.renderChildren(node)
    } else {
      val itemList = new ArrayList[Node]()

      val taskListItemPlacement = taskListFormatOptions.taskListItemPlacement
      if (taskListItemPlacement != TaskListItemPlacement.AS_IS) {
        val incompleteTasks       = new ArrayList[ListItem]()
        val completeItems         = new ArrayList[ListItem]()
        val incompleteDescendants = taskListItemPlacement == TaskListItemPlacement.INCOMPLETE_NESTED_FIRST || taskListItemPlacement == TaskListItemPlacement.COMPLETE_NESTED_TO_NON_TASK

        var item = node.firstChild
        while (item.isDefined) {
          item.get match {
            case taskItem: TaskListItem =>
              if (!taskItem.isItemDoneMarker || (incompleteDescendants && TaskListNodeFormatter.hasIncompleteDescendants(item.get))) {
                incompleteTasks.add(taskItem)
              } else {
                completeItems.add(taskItem)
              }
            case listItem: ListItem =>
              if (incompleteDescendants && TaskListNodeFormatter.hasIncompleteDescendants(item.get)) {
                incompleteTasks.add(listItem)
              } else {
                completeItems.add(listItem)
              }
            case _ => // ignore non-ListItem children
          }
          item = item.get.next
        }

        if (taskListFormatOptions.formatPrioritizedTaskItems) {
          // have prioritized tasks
          incompleteTasks.forEach { li =>
            li.priority = itemPriority(li)
          }

          incompleteTasks.sort((o1, o2) => Integer.compare(o2.priority, o1.priority))
          itemList.addAll(incompleteTasks)
        } else {
          itemList.addAll(incompleteTasks)
        }

        itemList.addAll(completeItems)
      } else {
        var item = node.firstChild
        while (item.isDefined) {
          itemList.add(item.get)
          item = item.get.next
        }
      }

      FormatterUtils.renderList(node, context, markdown, itemList)
    }

  def taskItemPriority(node: Node): Int =
    node match {
      case tli: TaskListItem =>
        if (tli.isOrderedItem) {
          taskListFormatOptions.formatOrderedTaskItemPriority
        } else {
          val openingMarker = tli.asInstanceOf[ListItem].openingMarker
          if (openingMarker.length() > 0) {
            val priority = taskListFormatOptions.formatTaskItemPriorities.get(openingMarker.charAt(0))
            @SuppressWarnings(Array("org.wartremover.warts.Null"))
            val hasPriority = priority != null // @nowarn - Java interop: Map.get returns null
            if (hasPriority) priority
            else taskListFormatOptions.formatDefaultTaskItemPriority
          } else {
            taskListFormatOptions.formatDefaultTaskItemPriority
          }
        }
      case _ => Int.MinValue
    }

  def itemPriority(node: Node): Int = {
    var priority = Int.MinValue

    node match {
      case tli: TaskListItem if !tli.isItemDoneMarker =>
        priority = Math.max(priority, taskItemPriority(node))
      case _ => // no-op
    }

    var item = node.firstChild
    while (item.isDefined) {
      item.get match {
        case tli: TaskListItem if !tli.isItemDoneMarker =>
          priority = Math.max(priority, taskItemPriority(item.get))
        case _ => // no-op
      }
      if (item.get.isInstanceOf[Block] && !item.get.isInstanceOf[Paragraph]) {
        priority = Math.max(priority, itemPriority(item.get))
      }
      item = item.get.next
    }

    priority
  }
}

object TaskListNodeFormatter {

  def hasIncompleteDescendants(node: Node): Boolean = boundary {
    var item = node.firstChild
    while (item.isDefined) {
      item.get match {
        case tli: TaskListItem if !tli.isItemDoneMarker => break(true)
        case _ => // continue
      }
      if (item.get.isInstanceOf[Block] && !item.get.isInstanceOf[Paragraph] && hasIncompleteDescendants(item.get)) {
        break(true)
      }
      item = item.get.next
    }
    false
  }
}
