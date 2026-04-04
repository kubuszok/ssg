/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/.../ComboGfmTaskListSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package tasklist
package test

import ssg.md.Nullable
import ssg.md.ext.gfm.tasklist.TaskListExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboGfmTaskListSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboGfmTaskListSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboGfmTaskListSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboGfmTaskListSpecTest.OPTIONS_MAP
}

object ComboGfmTaskListSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gfm/tasklist/test/ext_gfm_tasklist_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGfmTaskListSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(TaskListExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-suffix-content", new MutableDataSet().set(Parser.LISTS_ITEM_CONTENT_AFTER_SUFFIX, true).toImmutable)
    map.put("marker-space", new MutableDataSet().set(Parser.LISTS_ITEM_MARKER_SPACE, true).toImmutable)
    map.put("src-pos-lines", new MutableDataSet().set(HtmlRenderer.SOURCE_POSITION_PARAGRAPH_LINES, true).toImmutable)
    map.put("item-class", new MutableDataSet().set(TaskListExtension.TIGHT_ITEM_CLASS, "").toImmutable)
    map.put("loose-class", new MutableDataSet().set(TaskListExtension.LOOSE_ITEM_CLASS, "").toImmutable)
    map.put("closed-item-class", new MutableDataSet().set(TaskListExtension.ITEM_DONE_CLASS, "closed-task").toImmutable)
    map.put("open-item-class", new MutableDataSet().set(TaskListExtension.ITEM_NOT_DONE_CLASS, "open-task").toImmutable)
    map.put("p-class", new MutableDataSet().set(TaskListExtension.PARAGRAPH_CLASS, "task-item").toImmutable)
    map.put("done", new MutableDataSet().set(TaskListExtension.ITEM_DONE_MARKER, "<span class=\"taskitem\">X</span>").toImmutable)
    map.put(
      "not-done",
      new MutableDataSet().set(TaskListExtension.ITEM_NOT_DONE_MARKER, "<span class=\"taskitem\">O</span>").toImmutable
    )
    map.put("no-ordered-items", new MutableDataSet().set(Parser.LISTS_NUMBERED_ITEM_MARKER_SUFFIXED, false).toImmutable)
    map.put(
      "kramdown",
      new MutableDataSet().setFrom(ParserEmulationProfile.KRAMDOWN).set(Parser.EXTENSIONS, Collections.singleton(TaskListExtension.create())).toImmutable
    )
    map.put(
      "markdown",
      new MutableDataSet().setFrom(ParserEmulationProfile.MARKDOWN).set(Parser.EXTENSIONS, Collections.singleton(TaskListExtension.create())).toImmutable
    )
    map
  }
}
