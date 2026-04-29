/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../MarkdownTableTestBase.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.ext.tables.{ TablesExtension, TableExtractingVisitor }
import ssg.md.formatter.MarkdownWriter
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.{ CharWidthProvider, ColumnSort, MarkdownTable, TableCellOffsetInfo, TableFormatOptions }
import ssg.md.util.format.options.{ DiscretionaryText, TableCaptionHandling }
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }

import java.util.Arrays
import scala.language.implicitConversions

abstract class MarkdownTableTestBase extends munit.FunSuite {

  protected def getTables(markdown: CharSequence): Array[MarkdownTable] =
    getTables(markdown, null)

  protected def getTables(markdown: CharSequence, options: DataHolder): Array[MarkdownTable] = {
    val useOptions: DataHolder = if (options == null) {
      new MutableDataSet()
        .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
        .toImmutable
    } else {
      new MutableDataSet(options)
        .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
        .toImmutable
    }

    val parser       = Parser.builder(useOptions).build()
    val document     = parser.parse(BasedSequence.of(markdown))
    val tableVisitor = new TableExtractingVisitor(useOptions)
    tableVisitor.getTables(document)
  }

  protected def getFormattedTable(table: MarkdownTable): String = {
    val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    table.appendTable(out)
    out.toString(0, 0)
  }

  protected def getFormattedTables(tables: Array[MarkdownTable]): Array[String] = {
    tables.map { table =>
      val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
      table.appendTable(out)
      out.toString(0, 0)
    }
  }

  protected def getTable(markdown: CharSequence): MarkdownTable =
    getTable(markdown, null)

  protected def getTransposedTable(table: MarkdownTable): String =
    getTransposedTable(table, 1)

  protected def getTransposedTable(table: MarkdownTable, columnHeaders: Int): String = {
    val out        = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val transposed = table.transposed(columnHeaders)
    transposed.appendTable(out)
    out.toString(0, 0)
  }

  @SuppressWarnings(Array("unused"))
  protected def getSortedTable(table: MarkdownTable, columnSorts: Array[ColumnSort]): MarkdownTable = {
    table.sorted(columnSorts, 0, Nullable.empty)
  }

  protected def getTable(markdown: CharSequence, options: DataHolder): MarkdownTable = {
    val table = getTables(markdown, options)(0)
    table.normalize()
    table
  }

  def formatOptions(tableIndentPrefix: CharSequence, options: DataHolder): MutableDataSet = {
    val useOptions = (if (options == null) new MutableDataSet() else new MutableDataSet(options))
      .set(TablesExtension.FORMAT_TABLE_INDENT_PREFIX, "")
      .set(TablesExtension.FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH, Integer.valueOf(3))
      .set(TablesExtension.FORMAT_TABLE_LEAD_TRAIL_PIPES, true)
      .set(TablesExtension.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, true)
      .set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, true)
      .set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.ADD)
      .set(TablesExtension.FORMAT_TABLE_CAPTION_SPACES, DiscretionaryText.ADD)
      .set(TablesExtension.FORMAT_TABLE_SPACE_AROUND_PIPES, true)
      .set(TablesExtension.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT, true)
      .set(TablesExtension.FORMAT_TABLE_MIN_SEPARATOR_DASHES, Integer.valueOf(3))
      .set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.AS_IS)
      .set(TablesExtension.FORMAT_TABLE_TRIM_CELL_WHITESPACE, true)
      .set(
        TablesExtension.FORMAT_CHAR_WIDTH_PROVIDER,
        new CharWidthProvider {
          override def spaceWidth: Int = 1
          override def getCharWidth(c: Char): Int = if (c == TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER_CHAR) 0 else 1
        }
      )
    useOptions
  }

  def formatOptionsAsIs(tableIndentPrefix: CharSequence, options: DataHolder): MutableDataSet = {
    val useOptions = (if (options == null) new MutableDataSet() else new MutableDataSet(options))
      .set(TablesExtension.FORMAT_TABLE_INDENT_PREFIX, "")
      .set(TablesExtension.FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH, Integer.valueOf(3))
      .set(TablesExtension.FORMAT_TABLE_LEAD_TRAIL_PIPES, true)
      .set(TablesExtension.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, true)
      .set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, true)
      .set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS)
      .set(TablesExtension.FORMAT_TABLE_CAPTION_SPACES, DiscretionaryText.AS_IS)
      .set(TablesExtension.FORMAT_TABLE_SPACE_AROUND_PIPES, true)
      .set(TablesExtension.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT, true)
      .set(TablesExtension.FORMAT_TABLE_MIN_SEPARATOR_DASHES, Integer.valueOf(3))
      .set(TablesExtension.FORMAT_TABLE_CAPTION, TableCaptionHandling.AS_IS)
      .set(TablesExtension.FORMAT_TABLE_TRIM_CELL_WHITESPACE, true)
      .set(
        TablesExtension.FORMAT_CHAR_WIDTH_PROVIDER,
        new CharWidthProvider {
          override def spaceWidth: Int = 1
          override def getCharWidth(c: Char): Int = if (c == TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER_CHAR) 0 else 1
        }
      )
    useOptions
  }

  protected def assertIndexOf(expIndex: Int, expSpanOffset: Int, index: MarkdownTable.IndexSpanOffset): Unit =
    assertEquals(index.toString, new MarkdownTable.IndexSpanOffset(expIndex, expSpanOffset).toString)

  protected def assertIndexOf(message: String, expIndex: Int, expSpanOffset: Int, index: MarkdownTable.IndexSpanOffset): Unit =
    assertEquals(index.toString, new MarkdownTable.IndexSpanOffset(expIndex, expSpanOffset).toString, message)

  protected def assertCellInfo(message: String, row: Int, column: Int, insideCol: Integer, insideOffset: Integer, info: TableCellOffsetInfo): Unit = {
    val insideColN: Nullable[Integer]    = if (insideCol == null) Nullable.empty else Nullable(insideCol)
    val insideOffsetN: Nullable[Integer] = if (insideOffset == null) Nullable.empty else Nullable(insideOffset)
    assertEquals(
      info.toString,
      new TableCellOffsetInfo(info.offset, info.table, info.section, Nullable.empty, Nullable.empty, row, column, insideColN, insideOffsetN).toString,
      message
    )
  }
}
