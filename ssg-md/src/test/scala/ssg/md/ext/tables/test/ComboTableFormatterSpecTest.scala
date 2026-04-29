/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../ComboTableFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.ext.tables.TablesExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.{ CharWidthProvider, TableFormatOptions }
import ssg.md.util.format.options.{ DiscretionaryText, TableCaptionHandling }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboTableFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboTableFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboTableFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboTableFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Diagnostic/3095 -", "GFM -", "Tables Extension -", "Tracked Offset -", "Width Provider -")
}

object ComboTableFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/tables/test/ext_tables_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboTableFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(TablesExtension.create()))
    .set(Parser.LISTS_AUTO_LOOSE, false)
    .toImmutable

  private val WIDTH_PROVIDER: CharWidthProvider = new CharWidthProvider {
    override def spaceWidth: Int = 8
    override def getCharWidth(c: Char): Int = if (c <= 255) 8 else 13
  }

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("gfm", new MutableDataSet()
      .set(TablesExtension.COLUMN_SPANS, false)
      .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
      .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
      .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
      .toImmutable
    )
    map.put("no-caption", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.REMOVE).toImmutable)
    map.put("no-alignment", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT, false).toImmutable)
    map.put("no-width", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, false).toImmutable)
    map.put("keep-whitespace", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_TRIM_CELL_WHITESPACE, false).toImmutable)
    map.put("lead-trail-pipes", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_LEAD_TRAIL_PIPES, false).toImmutable)
    map.put("space-around-pipe", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_SPACE_AROUND_PIPES, false).toImmutable)
    map.put("adjust-column-width", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, false).toImmutable)
    map.put("apply-column-alignment", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT, false).toImmutable)
    map.put("fill-missing-columns", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, true).toImmutable)
    map.put("left-align-marker-as-is", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS).toImmutable)
    map.put("left-align-marker-add", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.ADD).toImmutable)
    map.put("left-align-marker-remove", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.REMOVE).toImmutable)
    map.put("line-prefix", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_INDENT_PREFIX, ">   ").toImmutable)
    map.put("add-caption-spaces", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_CAPTION_SPACES, DiscretionaryText.ADD).toImmutable)
    map.put("remove-caption-spaces", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_CAPTION_SPACES, DiscretionaryText.REMOVE).toImmutable)
    map.put("add-caption", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.ADD).toImmutable)
    map.put("remove-empty-caption", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.REMOVE_EMPTY).toImmutable)
    map.put("remove-caption", new MutableDataSet().set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.REMOVE).toImmutable)
    map.put("markdown-navigator", new MutableDataSet()
      .set(TablesExtension.FORMAT_TABLE_INDENT_PREFIX, "")
      .set(TablesExtension.FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH, 3)
      .set(TablesExtension.FORMAT_TABLE_LEAD_TRAIL_PIPES, true)
      .set(TablesExtension.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, true)
      .set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, true)
      .set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.ADD)
      .set(TablesExtension.FORMAT_TABLE_CAPTION_SPACES, DiscretionaryText.AS_IS)
      .set(TablesExtension.FORMAT_TABLE_SPACE_AROUND_PIPES, true)
      .set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.AS_IS)
      .set(TablesExtension.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT, true)
      .set(TablesExtension.FORMAT_TABLE_MIN_SEPARATOR_DASHES, 3)
      .set(TablesExtension.FORMAT_TABLE_TRIM_CELL_WHITESPACE, false)
      .set(TablesExtension.FORMAT_CHAR_WIDTH_PROVIDER, new CharWidthProvider {
        override def spaceWidth: Int = 1
        override def getCharWidth(c: Char): Int = if (c == TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER_CHAR) 0 else 1
      })
      .toImmutable
    )
    map.put("width-provider", new MutableDataSet().set(TablesExtension.FORMAT_CHAR_WIDTH_PROVIDER, WIDTH_PROVIDER).toImmutable)
    map
  }
}
