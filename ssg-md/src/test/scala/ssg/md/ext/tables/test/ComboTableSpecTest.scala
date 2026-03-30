/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../ComboTableSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.ext.tables.TablesExtension
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.parser.Parser
import ssg.md.test.util.{RendererSpecTestSuite, TestUtils}
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.util.{Arrays, Collections, HashMap}
import scala.language.implicitConversions

final class ComboTableSpecTest extends RendererSpecTestSuite {
  override def specResource: ResourceLocation = ComboTableSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboTableSpecTest.OPTIONS)
  override def optionsMap: java.util.Map[String, ? <: DataHolder] = ComboTableSpecTest.OPTIONS_MAP
}

object ComboTableSpecTest {
  val SPEC_RESOURCE: String = "/ssg/md/ext/tables/test/ext_tables_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboTableSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(TablesExtension.create()))
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("class-name", new MutableDataSet().set(TablesExtension.CLASS_NAME, "table-class").toImmutable)
    map.put("no-caption", new MutableDataSet().set(TablesExtension.WITH_CAPTION, false).toImmutable)
    map.put("gfm", new MutableDataSet()
      .set(TablesExtension.COLUMN_SPANS, false)
      .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
      .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
      .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
      .toImmutable)
    map.put("typographic", new MutableDataSet()
      .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), TypographicExtension.create()))
      .toImmutable)
    map.put("keep-whitespace", new MutableDataSet().set(TablesExtension.TRIM_CELL_WHITESPACE, false).toImmutable)
    map.put("min-dashes-2", new MutableDataSet().set(TablesExtension.MIN_SEPARATOR_DASHES, Integer.valueOf(2)).toImmutable)
    map.put("min-dashes-1", new MutableDataSet().set(TablesExtension.MIN_SEPARATOR_DASHES, Integer.valueOf(1)).toImmutable)
    map.put("strip-indent", new MutableDataSet().set(TestUtils.SOURCE_INDENT, "> > ").toImmutable)
    map.put("sub-parse", new MutableDataSet()
      .set(TestUtils.SOURCE_PREFIX, "Source Prefix\n")
      .set(TestUtils.SOURCE_SUFFIX, "Source Suffix\n")
      .toImmutable)
    map.put("sub-parse2", new MutableDataSet()
      .set(TestUtils.SOURCE_SUFFIX, "\n")
      .toImmutable)
    map
  }
}
