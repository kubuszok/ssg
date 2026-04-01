/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TablesExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables

import ssg.md.ext.tables.internal.{TableNodeFormatter, TableNodeRenderer, TableParagraphPreProcessor}
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder, NullableDataKey}
import ssg.md.util.format.{CharWidthProvider, TableFormatOptions, TableManipulator}
import ssg.md.util.format.options.{DiscretionaryText, TableCaptionHandling}

/**
 * Extension for GFM tables using "|" pipes (GitHub Flavored Markdown).
 *
 * Create it with [[TablesExtension.create]] and then configure it on the builders.
 *
 * The parsed tables are turned into [[TableBlock]] blocks.
 */
class TablesExtension private ()
    extends Parser.ParserExtension,
    HtmlRenderer.HtmlRendererExtension,
    Formatter.FormatterExtension {

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new TableNodeFormatter.Factory())
  }

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.paragraphPreProcessorFactory(TableParagraphPreProcessor.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new TableNodeRenderer.Factory())
    } else if (htmlRendererBuilder.isRendererType("JIRA")) {
      // Skipping TableJiraRenderer per conversion rules
    }
  }
}

object TablesExtension {

  val TRIM_CELL_WHITESPACE: DataKey[Boolean] = new DataKey[Boolean]("TRIM_CELL_WHITESPACE", true)
  val MIN_SEPARATOR_DASHES: DataKey[Int] = new DataKey[Int]("MIN_SEPARATOR_DASHES", 3)
  val MAX_HEADER_ROWS: DataKey[Int] = new DataKey[Int]("MAX_HEADER_ROWS", Int.MaxValue)
  val MIN_HEADER_ROWS: DataKey[Int] = new DataKey[Int]("MIN_HEADER_ROWS", 0)
  val APPEND_MISSING_COLUMNS: DataKey[Boolean] = new DataKey[Boolean]("APPEND_MISSING_COLUMNS", false)
  val DISCARD_EXTRA_COLUMNS: DataKey[Boolean] = new DataKey[Boolean]("DISCARD_EXTRA_COLUMNS", false)
  val COLUMN_SPANS: DataKey[Boolean] = new DataKey[Boolean]("COLUMN_SPANS", true)
  val HEADER_SEPARATOR_COLUMN_MATCH: DataKey[Boolean] = new DataKey[Boolean]("HEADER_SEPARATOR_COLUMN_MATCH", false)
  val CLASS_NAME: DataKey[String] = new DataKey[String]("CLASS_NAME", "")
  val WITH_CAPTION: DataKey[Boolean] = new DataKey[Boolean]("WITH_CAPTION", true)

  // format options copy from TableFormatOptions
  val FORMAT_TABLE_TRIM_CELL_WHITESPACE: DataKey[Boolean] = TableFormatOptions.FORMAT_TABLE_TRIM_CELL_WHITESPACE
  val FORMAT_TABLE_LEAD_TRAIL_PIPES: DataKey[Boolean] = TableFormatOptions.FORMAT_TABLE_LEAD_TRAIL_PIPES
  val FORMAT_TABLE_SPACE_AROUND_PIPES: DataKey[Boolean] = TableFormatOptions.FORMAT_TABLE_SPACE_AROUND_PIPES
  val FORMAT_TABLE_ADJUST_COLUMN_WIDTH: DataKey[Boolean] = TableFormatOptions.FORMAT_TABLE_ADJUST_COLUMN_WIDTH
  val FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT: DataKey[Boolean] = TableFormatOptions.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT
  val FORMAT_TABLE_FILL_MISSING_COLUMNS: DataKey[Boolean] = TableFormatOptions.FORMAT_TABLE_FILL_MISSING_COLUMNS

  // QUERY: is this still needed???
  val FORMAT_TABLE_FILL_MISSING_MIN_COLUMN: NullableDataKey[Integer] = TableFormatOptions.FORMAT_TABLE_FILL_MISSING_MIN_COLUMN

  val FORMAT_TABLE_LEFT_ALIGN_MARKER: DataKey[DiscretionaryText] = TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER
  val FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH: DataKey[Int] = TableFormatOptions.FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH
  val FORMAT_TABLE_MIN_SEPARATOR_DASHES: DataKey[Int] = TableFormatOptions.FORMAT_TABLE_MIN_SEPARATOR_DASHES
  val FORMAT_CHAR_WIDTH_PROVIDER: DataKey[CharWidthProvider] = TableFormatOptions.FORMAT_CHAR_WIDTH_PROVIDER
  val FORMAT_TABLE_MANIPULATOR: DataKey[TableManipulator] = TableFormatOptions.FORMAT_TABLE_MANIPULATOR
  val FORMAT_TABLE_CAPTION: DataKey[TableCaptionHandling] = TableFormatOptions.FORMAT_TABLE_CAPTION
  val FORMAT_TABLE_CAPTION_SPACES: DataKey[DiscretionaryText] = TableFormatOptions.FORMAT_TABLE_CAPTION_SPACES
  val FORMAT_TABLE_INDENT_PREFIX: DataKey[String] = TableFormatOptions.FORMAT_TABLE_INDENT_PREFIX

  def create(): TablesExtension = new TablesExtension()
}
