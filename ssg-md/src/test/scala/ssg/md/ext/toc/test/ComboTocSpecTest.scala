/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/.../ComboTocSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package test

import ssg.md.Nullable
import ssg.md.ext.toc.TocExtension
import ssg.md.ext.toc.internal.TocOptions
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboTocSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboTocSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboTocSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboTocSpecTest.OPTIONS_MAP
}

object ComboTocSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/toc/test/ext_toc_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboTocSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singletonList(TocExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("text-only", new MutableDataSet().set(TocExtension.IS_TEXT_ONLY, true).toImmutable)
    map.put("formatted", new MutableDataSet().set(TocExtension.IS_TEXT_ONLY, false).toImmutable)
    map.put("hierarchy", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.HIERARCHY).toImmutable)
    map.put("flat", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.FLAT).toImmutable)
    map.put("flat-reversed", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.FLAT_REVERSED).toImmutable)
    map.put("sorted", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.SORTED).toImmutable)
    map.put("sorted-reversed", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.SORTED_REVERSED).toImmutable)
    map.put("numbered", new MutableDataSet().set(TocExtension.IS_NUMBERED, true).toImmutable)
    map.put("levels-2", new MutableDataSet().set(TocExtension.LEVELS, Integer.valueOf(1 << 2)).toImmutable)
    map.put("title", new MutableDataSet().set(TocExtension.TITLE, "Table of Contents").toImmutable)
    map.put("div-class", new MutableDataSet().set(TocExtension.DIV_CLASS, "content-class").toImmutable)
    map.put("list-class", new MutableDataSet().set(TocExtension.LIST_CLASS, "list-class").toImmutable)
    map.put("not-case-sensitive", new MutableDataSet().set(TocExtension.CASE_SENSITIVE_TOC_TAG, false).toImmutable)
    map
  }
}
