/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/.../ComboGfmTaskListFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package gfm
package tasklist
package test

import ssg.md.Nullable
import ssg.md.ext.gfm.tasklist.{ TaskListExtension, TaskListItemCase, TaskListItemPlacement }
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.options.ListBulletMarker

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboGfmTaskListFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboGfmTaskListFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboGfmTaskListFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboGfmTaskListFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Empty List Items -", "No Suffix Content -", "Prioritized -", "Task List Items -", "To Non-Task -")
}

object ComboGfmTaskListFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gfm/tasklist/test/ext_gfm_tasklist_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGfmTaskListFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(TaskListExtension.create()))
    .set(Parser.BLANK_LINES_IN_AST, true)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-suffix-content", new MutableDataSet().set(Parser.LISTS_ITEM_CONTENT_AFTER_SUFFIX, true).toImmutable)
    map.put("task-case-as-is", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_CASE, TaskListItemCase.AS_IS).toImmutable)
    map.put("task-case-lowercase", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_CASE, TaskListItemCase.LOWERCASE).toImmutable)
    map.put("task-case-uppercase", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_CASE, TaskListItemCase.UPPERCASE).toImmutable)
    map.put("task-placement-as-is", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.AS_IS).toImmutable)
    map.put("task-placement-incomplete-first", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.INCOMPLETE_FIRST).toImmutable)
    map.put("task-placement-incomplete-nested-first", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.INCOMPLETE_NESTED_FIRST).toImmutable)
    map.put("task-placement-complete-to-non-task", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.COMPLETE_TO_NON_TASK).toImmutable)
    map.put("task-placement-complete-nested-to-non-task", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.COMPLETE_NESTED_TO_NON_TASK).toImmutable)
    map.put("remove-empty-items", new MutableDataSet().set(Formatter.LIST_REMOVE_EMPTY_ITEMS, true).toImmutable)
    map.put("prioritized-tasks", new MutableDataSet()
      .set(TaskListExtension.FORMAT_PRIORITIZED_TASK_ITEMS, true)
      .set(Parser.LISTS_DELIMITER_MISMATCH_TO_NEW_LIST, false)
      .set(Parser.LISTS_AUTO_LOOSE, false)
      .toImmutable
    )
    map.put("ordered-task-item-priority-high", new MutableDataSet().set(TaskListExtension.FORMAT_ORDERED_TASK_ITEM_PRIORITY, 1).toImmutable)
    map.put("ordered-task-item-priority-normal", new MutableDataSet().set(TaskListExtension.FORMAT_ORDERED_TASK_ITEM_PRIORITY, 0).toImmutable)
    map.put("ordered-task-item-priority-low", new MutableDataSet().set(TaskListExtension.FORMAT_ORDERED_TASK_ITEM_PRIORITY, -1).toImmutable)
    map.put("list-bullet-any", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.ANY).toImmutable)
    map.put("list-bullet-dash", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.DASH).toImmutable)
    map.put("list-bullet-asterisk", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.ASTERISK).toImmutable)
    map.put("list-bullet-plus", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.PLUS).toImmutable)
    map
  }
}
