/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../MarkdownTableTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.util.format.{ TableFormatOptions, TrackedOffset }
import ssg.md.util.format.options.DiscretionaryText
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }

import scala.language.implicitConversions

final class MarkdownTableTest extends MarkdownTableTestBase {


  private val markdown1 = "" +
            "| First Header  |\n" +
            "| ------------- |\n" +
            "| Content Cell  |\n" +
            "\n" +
            ""
  private val markdown2 = "" +
            "| Left-aligned | Right-aligned1 |\n" +
            "| Left-aligned | Right-aligned2 |\n" +
            "| :---         |          ---: |\n" +
            "| git status   | git status1    |\n" +
            "| git diff     | git diff2      |\n" +
            "[  ]\n" +
            ""
  private val markdown3 =
            "| Left-aligned | Center-aligned | Right-aligned1 |\n" +
                    "| Left-aligned | Center-aligned | Right-aligned2 |\n" +
                    "| Left-aligned | Center-aligned | Right-aligned3 |\n" +
                    "| :---         |     :---:      |          ---: |\n" +
                    "| git status   | git status     | git status1    |\n" +
                    "| git diff     | git diff       | git diff2      |\n" +
                    "| git diff     | git diff       | git diff3      |\n" +
                    "[Table Caption]\n" +
                    ""

  private val markdown4 = "" +
            "| Left-aligned1 |\n" +
            "| Left-aligned | Center-aligned2 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned3 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned | Right-aligned4 |\n" +
            "| :---         |     :---:      |          ---: |          ---: |\n" +
            "| git status1  |\n" +
            "| git diff     | git diff2      |\n" +
            "| git diff     | git diff       | git diff3      |\n" +
            "| git diff     | git diff       | git diff       | git diff4      |\n" +
            "[Table Caption]\n" +
            ""

  private val formattedNoCaption1 = "" +
            "| First Header |\n" +
            "|--------------|\n" +
            "| Content Cell |\n" +
            ""

  private val formattedNoCaption2 = "" +
            "| Left-aligned | Right-aligned1 |\n" +
            "| Left-aligned | Right-aligned2 |\n" +
            "|:-------------|---------------:|\n" +
            "| git status   |    git status1 |\n" +
            "| git diff     |      git diff2 |\n" +
            ""
  private val formattedNoCaption3 = "" +
            "| Left-aligned | Center-aligned | Right-aligned1 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned2 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned3 |\n" +
            "|:-------------|:--------------:|---------------:|\n" +
            "| git status   |   git status   |    git status1 |\n" +
            "| git diff     |    git diff    |      git diff2 |\n" +
            "| git diff     |    git diff    |      git diff3 |\n" +
            ""

  private val formatted1 = formattedNoCaption1 +
            ""

  private val formatted2 = formattedNoCaption2 +
            "[ ]\n" +
            ""

  private val formatted3 = formattedNoCaption3 +
            "[Table Caption]\n" +
            ""

      test("test_basic") {
        val tables = getTables(markdown1 + markdown2 + markdown3)
        val formattedTables = getFormattedTables(tables)
        val expected: Array[String] = Array(formatted1, formatted2, formatted3)

        for (i <- (tables.length - 1) to 0 by -1) {
            assertEquals(formattedTables(i), expected(i), "Table " + (i + 1))
        }
    }

      test("test_getCaption") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)
        assertEquals("", table1.getCaption.toString)
        assertEquals("  ", table2.getCaption.toString)
        assertEquals("Table Caption", table3.getCaption.toString)

        assertEquals("", table1.getCaptionCell.openMarker.toString)
        assertEquals("[", table2.getCaptionCell.openMarker.toString)
        assertEquals("[", table3.getCaptionCell.openMarker.toString)

        assertEquals("", table1.getCaptionCell.closeMarker.toString)
        assertEquals("]", table2.getCaptionCell.closeMarker.toString)
        assertEquals("]", table3.getCaptionCell.closeMarker.toString)
    }

      test("test_setCaption") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        table1.setCaption("Table 1")
        table2.setCaption("Table 2")
        table3.setCaption("Table 3")

        assertEquals("Table 1", table1.getCaption.toString)
        assertEquals("Table 2", table2.getCaption.toString)
        assertEquals("Table 3", table3.getCaption.toString)

        assertEquals(formattedNoCaption1 + "[Table 1]\n", getFormattedTable(table1).toString)
        assertEquals(formattedNoCaption2 + "[Table 2]\n", getFormattedTable(table2).toString)
        assertEquals(formattedNoCaption3 + "[Table 3]\n", getFormattedTable(table3).toString)
    }

      test("test_setCaptionWithMarkers") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        table1.setCaptionWithMarkers(null, "[", "Table 1", "]")
        table2.setCaptionWithMarkers(null, "[", "Table 2", "]")
        table3.setCaptionWithMarkers(null, "[", "Table 3", "]")

        assertEquals("Table 1", table1.getCaption.toString)
        assertEquals("Table 2", table2.getCaption.toString)
        assertEquals("Table 3", table3.getCaption.toString)

        assertEquals(formattedNoCaption1 + "[Table 1]\n", getFormattedTable(table1).toString)
        assertEquals(formattedNoCaption2 + "[Table 2]\n", getFormattedTable(table2).toString)
        assertEquals(formattedNoCaption3 + "[Table 3]\n", getFormattedTable(table3).toString)
    }

      test("test_getHeadingRowCount") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getHeadingRowCount)
        assertEquals(2, table2.getHeadingRowCount)
        assertEquals(3, table3.getHeadingRowCount)
    }

      test("test_getBodyRowCount") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getBodyRowCount)
        assertEquals(2, table2.getBodyRowCount)
        assertEquals(3, table3.getBodyRowCount)
    }

      test("test_getMaxHeadingColumns") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getMaxHeadingColumns)
        assertEquals(2, table2.getMaxHeadingColumns)
        assertEquals(3, table3.getMaxHeadingColumns)

        val table4 = getTable(markdown4)
        assertEquals(4, table4.getMaxHeadingColumns)
    }

      test("test_getMaxSeparatorColumns") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getMaxSeparatorColumns)
        assertEquals(2, table2.getMaxSeparatorColumns)
        assertEquals(3, table3.getMaxSeparatorColumns)

        val table4 = getTable(markdown4)
        assertEquals(4, table4.getMaxSeparatorColumns)
    }

      test("test_getMaxBodyColumns") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getMaxBodyColumns)
        assertEquals(2, table2.getMaxBodyColumns)
        assertEquals(3, table3.getMaxBodyColumns)

        val table4 = getTable(markdown4)
        assertEquals(4, table4.getMaxBodyColumns)
    }

      test("test_getMinColumns") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getMinColumns)
        assertEquals(2, table2.getMinColumns)
        assertEquals(3, table3.getMinColumns)

        val table4 = getTable(markdown4)
        assertEquals(1, table4.getMinColumns)
    }

      test("test_getMaxColumns") {
        val table1 = getTable(markdown1)
        val table2 = getTable(markdown2)
        val table3 = getTable(markdown3)

        assertEquals(1, table1.getMaxColumns)
        assertEquals(2, table2.getMaxColumns)
        assertEquals(3, table3.getMaxColumns)

        val table4 = getTable(markdown4)
        assertEquals(4, table4.getMaxColumns)
    }

      test("test_maxColumnsWithout") {
        val table4 = getTable(markdown4)
        // @formatter:off
        assertEquals(0, table4.getMaxColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(1, table4.getMaxColumnsWithoutRows(true,    1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(2, table4.getMaxColumnsWithoutRows(true, 0,    2, 3, 4, 5, 6, 7, 8))
        assertEquals(3, table4.getMaxColumnsWithoutRows(true, 0, 1,    3, 4, 5, 6, 7, 8))
        assertEquals(4, table4.getMaxColumnsWithoutRows(true, 0, 1, 2,    4, 5, 6, 7, 8))
        assertEquals(4, table4.getMaxColumnsWithoutRows(true, 0, 1, 2, 3,    5, 6, 7, 8))
        assertEquals(1, table4.getMaxColumnsWithoutRows(true, 0, 1, 2, 3, 4,    6, 7, 8))
        assertEquals(2, table4.getMaxColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5,    7, 8))
        assertEquals(3, table4.getMaxColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5, 6,    8))
        assertEquals(4, table4.getMaxColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5, 6, 7   ))

        assertEquals(0, table4.getMaxColumnsWithoutRows(false, 0, 1, 2, 3, 4, 5, 6, 7))
        assertEquals(1, table4.getMaxColumnsWithoutRows(false,    1, 2, 3, 4, 5, 6, 7))
        assertEquals(2, table4.getMaxColumnsWithoutRows(false, 0,    2, 3, 4, 5, 6, 7))
        assertEquals(3, table4.getMaxColumnsWithoutRows(false, 0, 1,    3, 4, 5, 6, 7))
        assertEquals(4, table4.getMaxColumnsWithoutRows(false, 0, 1, 2,    4, 5, 6, 7))
        assertEquals(1, table4.getMaxColumnsWithoutRows(false, 0, 1, 2, 3,    5, 6, 7))
        assertEquals(2, table4.getMaxColumnsWithoutRows(false, 0, 1, 2, 3, 4,    6, 7))
        assertEquals(3, table4.getMaxColumnsWithoutRows(false, 0, 1, 2, 3, 4, 5,    7))
        assertEquals(4, table4.getMaxColumnsWithoutRows(false, 0, 1, 2, 3, 4, 5, 6   ))
        // @formatter:on
    }

      test("test_minColumnsWithout") {
        val table4 = getTable(markdown4)
        // @formatter:off
        assertEquals(0, table4.getMinColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(1, table4.getMinColumnsWithoutRows(true,    1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(2, table4.getMinColumnsWithoutRows(true, 0,    2, 3, 4, 5, 6, 7, 8))
        assertEquals(3, table4.getMinColumnsWithoutRows(true, 0, 1,    3, 4, 5, 6, 7, 8))
        assertEquals(4, table4.getMinColumnsWithoutRows(true, 0, 1, 2,    4, 5, 6, 7, 8))
        assertEquals(4, table4.getMinColumnsWithoutRows(true, 0, 1, 2, 3,    5, 6, 7, 8))
        assertEquals(1, table4.getMinColumnsWithoutRows(true, 0, 1, 2, 3, 4,    6, 7, 8))
        assertEquals(2, table4.getMinColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5,    7, 8))
        assertEquals(3, table4.getMinColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5, 6,    8))
        assertEquals(4, table4.getMinColumnsWithoutRows(true, 0, 1, 2, 3, 4, 5, 6, 7   ))
        assertEquals(1, table4.getMinColumnsWithoutRows(true))

        assertEquals(0, table4.getMinColumnsWithoutRows(false, 0, 1, 2, 3, 4, 5, 6, 7))
        assertEquals(1, table4.getMinColumnsWithoutRows(false,    1, 2, 3, 4, 5, 6, 7))
        assertEquals(2, table4.getMinColumnsWithoutRows(false, 0,    2, 3, 4, 5, 6, 7))
        assertEquals(3, table4.getMinColumnsWithoutRows(false, 0, 1,    3, 4, 5, 6, 7))
        assertEquals(4, table4.getMinColumnsWithoutRows(false, 0, 1, 2,    4, 5, 6, 7))
        assertEquals(1, table4.getMinColumnsWithoutRows(false, 0, 1, 2, 3,    5, 6, 7))
        assertEquals(2, table4.getMinColumnsWithoutRows(false, 0, 1, 2, 3, 4,    6, 7))
        assertEquals(3, table4.getMinColumnsWithoutRows(false, 0, 1, 2, 3, 4, 5,    7))
        assertEquals(4, table4.getMinColumnsWithoutRows(false, 0, 1, 2, 3, 4, 5, 6   ))
        // @formatter:on
    }

  private val markdown6 = "" +
            "|  | Left-aligned1 |\n" +
            "|  | Left-aligned | Center-aligned2 |\n" +
            "|  | Left-aligned | Center-aligned | Right-aligned3 |\n" +
            "|  | Left-aligned | Center-aligned | Right-aligned | Right-aligned4 |\n" +
            "|  |  |  |  |  |\n" +
            "| --- | :---         |     :---:      |          ---: |          ---: |\n" +
            "|  | git status1  |\n" +
            "|  | git diff     | git diff2      |\n" +
            "|  | git diff     | git diff       | git diff3      |\n" +
            "|  | git diff     | git diff       | git diff       | git diff4      |\n" +
            "|  |  |  |  |  |\n" +
            "[Table Caption]\n" +
            ""

      test("test_isEmptyColumn") {
        val table6 = getTable(markdown6)
        for (i <- 0 until 10) {
            assertEquals(table6.isEmptyColumn(i), i == 0 || i > 4, "Column: " + i)
        }
    }

  private val markdown5 = "" +
            "| Left-aligned1 |\n" +
            "| Left-aligned | Center-aligned2 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned3 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned | Right-aligned4 |\n" +
            "|  |  |  |  |\n" +
            "| :---         |     :---:      |          ---: |          ---: |\n" +
            "| git status1  |\n" +
            "| git diff     | git diff2      |\n" +
            "| git diff     | git diff       | git diff3      |\n" +
            "| git diff     | git diff       | git diff       | git diff4      |\n" +
            "|  |  |  |  |\n" +
            "[Table Caption]\n" +
            ""

      test("test_isEmptyRow") {
        val table5 = getTable(markdown5)
        for (i <- 0 until 10) {
            assertEquals(table5.isAllRowsEmptyAt(i), i == 4 || i == 10, "Row with sep: " + i)
            assertEquals(table5.isContentRowsEmptyAt(i), i == 4 || i == 9, "Row without sep: " + i)
        }
    }

  private val markdown7 = "" +
            "| Header 1.1 | Header 1.2 | Header 1.3 | Header 1.4 | Header 1.5 |\n" +
            "| Header 2.1 | Header 2.2 | Header 2.3 | Header 2.4 | Header 2.5 |\n" +
            "|------------|------------|------------|------------|------------|\n" +
            "| Data 1.1   | Data 1.2   | Data 1.3   | Data 1.4   | Data 1.5   |\n" +
            "| Data 2.1               || Data 2.3   | Data 2.4   | Data 2.5   |\n" +
            "| Data 3.1                           ||| Data 3.4   | Data 3.5   |\n" +
            "| Data 4.1                                       |||| Data 4.5   |\n" +
            "| Data 5.1                                                   |||||\n" +
            "|                                                            |||||\n" +
            "| Data 6.1   | Data 6.2               || Data 6.4   | Data 6.5   |\n" +
            "| Data 7.1   | Data 7.2                           ||| Data 7.5   |\n" +
            "| Data 8.1   | Data 8.2                                       ||||\n" +
            "| Data 9.1   | Data 9.2   | Data 9.3               || Data 9.5   |\n" +
            "| Data 10.1  | Data 10.2  | Data 10.3                          |||\n" +
            "| Data 11.1  | Data 11.2  | Data 11.3  | Data 11.4              ||" +
            ""

      test("test_indexOf") {
        val table7 = getTable(markdown7)

        // Data 1.1 row
        assertIndexOf(0, 0, table7.body.rows.get(0).indexOf(0))
        assertIndexOf(1, 0, table7.body.rows.get(0).indexOf(1))
        assertIndexOf(2, 0, table7.body.rows.get(0).indexOf(2))
        assertIndexOf(3, 0, table7.body.rows.get(0).indexOf(3))
        assertIndexOf(4, 0, table7.body.rows.get(0).indexOf(4))

        // Data 2.1 row
        assertIndexOf(0, 0, table7.body.rows.get(1).indexOf(0))
        assertIndexOf(0, 1, table7.body.rows.get(1).indexOf(1))
        assertIndexOf(1, 0, table7.body.rows.get(1).indexOf(2))
        assertIndexOf(2, 0, table7.body.rows.get(1).indexOf(3))
        assertIndexOf(3, 0, table7.body.rows.get(1).indexOf(4))

        // Data 3.1 row
        assertIndexOf(0, 0, table7.body.rows.get(2).indexOf(0))
        assertIndexOf(0, 1, table7.body.rows.get(2).indexOf(1))
        assertIndexOf(0, 2, table7.body.rows.get(2).indexOf(2))
        assertIndexOf(1, 0, table7.body.rows.get(2).indexOf(3))
        assertIndexOf(2, 0, table7.body.rows.get(2).indexOf(4))

        // Data 4.1 row
        assertIndexOf(0, 0, table7.body.rows.get(3).indexOf(0))
        assertIndexOf(0, 1, table7.body.rows.get(3).indexOf(1))
        assertIndexOf(0, 2, table7.body.rows.get(3).indexOf(2))
        assertIndexOf(0, 3, table7.body.rows.get(3).indexOf(3))
        assertIndexOf(1, 0, table7.body.rows.get(3).indexOf(4))

        // Data 5.1 row
        assertIndexOf(0, 0, table7.body.rows.get(4).indexOf(0))
        assertIndexOf(0, 1, table7.body.rows.get(4).indexOf(1))
        assertIndexOf(0, 2, table7.body.rows.get(4).indexOf(2))
        assertIndexOf(0, 3, table7.body.rows.get(4).indexOf(3))
        assertIndexOf(0, 4, table7.body.rows.get(4).indexOf(4))

        // Empty Data row with span 5
        assertIndexOf(0, 0, table7.body.rows.get(5).indexOf(0))
        assertIndexOf(0, 1, table7.body.rows.get(5).indexOf(1))
        assertIndexOf(0, 2, table7.body.rows.get(5).indexOf(2))
        assertIndexOf(0, 3, table7.body.rows.get(5).indexOf(3))
        assertIndexOf(0, 4, table7.body.rows.get(5).indexOf(4))
    }

  private val markdown8 = "some text\n\n" +
            "| Header 1.1 | Header 1.2 | Header 1.3 | Header 1.4 | Header 1.5 |\n" +
            "| Header 2.1 | Header 2.2 | Header 2.3 | Header 2.4 | Header 2.5 |\n" +
            "|------------|------------|------------|------------|------------|\n" +
            "| Data 1.1   | Data 1.2   | Data 1.3   | Data 1.4   | Data 1.5   |\n" +
            "| Data 2.1               || Data 2.3   | Data 2.4   | Data 2.5   |\n" +
            "| Data 3.1                           ||| Data 3.4   | Data 3.5   |\n" +
            "| Data 4.1                                       |||| Data 4.5   |\n" +
            ""

      test("test_ExactColumn") {
        val table8 = getTable(markdown8)
        val offset = 11

        assertEquals(offset, table8.getTableStartOffset)

        assertCellInfo("", 0, 0, null, null, table8.getCellOffsetInfo(10))
        assertCellInfo("", 0, 0, null, null, table8.getCellOffsetInfo(11))
        assertCellInfo("", 0, 0, 0, 0, table8.getCellOffsetInfo(offset + 1))
        assertCellInfo("", 0, 0, 0, 1, table8.getCellOffsetInfo(offset + 2))

        // last cell
        assertCellInfo("", 0, 4, 4, 11, table8.getCellOffsetInfo(offset + 64))
        assertCellInfo("", 0, 4, 4, 12, table8.getCellOffsetInfo(offset + 65))
        assertCellInfo("", 0, 5, null, null, table8.getCellOffsetInfo(offset + 66))

        // line 2
        assertCellInfo("", 1, 0, null, null, table8.getCellOffsetInfo(offset + 67))
        assertCellInfo("", 1, 0, 0, 0, table8.getCellOffsetInfo(offset + 68))
    }

      test("test_trackingOffset") {
        val markdown = "" +
                "| Header 1.1 | Header 1.2 |\n" +
                "| Header 2.1 | Header 2.2 |\n" +
                "|------------|------------|\n" +
                "| Data 1.1 ^ Long | Data 1.2   |\n" +
                "| Data 2.1               ||\n" +
                "| Data 3.1                |\n" +
                "| Data 4.1                |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Header 1.1     | Header 1.2 |\n" +
                "| Header 2.1     | Header 2.2 |\n" +
                "|:---------------|:-----------|\n" +
                "| Data 1.1  Long | Data 1.2   |\n" +
                "| Data 2.1                   ||\n" +
                "| Data 3.1       |            |\n" +
                "| Data 4.1       |            |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Header 1.1     | Header 1.2 |\n" +
                "| Header 2.1     | Header 2.2 |\n" +
                "|:---------------|:-----------|\n" +
                "| Data 1.1 ^ Long | Data 1.2   |\n" +
                "| Data 2.1                   ||\n" +
                "| Data 3.1       |            |\n" +
                "| Data 4.1       |            |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 12, offset)
    }

      test("test_trackingOffset2") {
        val markdown = "" +
                "| Header 1.1 | Header 1.2 |\n" +
                "| Header 2.1 | Header 2.2 |\n" +
                "|------------|------------|\n" +
                "| Data 2.1               ||\n" +
                "| Data 3.1                |\n" +
                "| Data 1.1 ^ Long | Data 1.2   |\n" +
                "| Data 4.1                |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Header 1.1     | Header 1.2 |\n" +
                "| Header 2.1     | Header 2.2 |\n" +
                "|:---------------|:-----------|\n" +
                "| Data 2.1                   ||\n" +
                "| Data 3.1       |            |\n" +
                "| Data 1.1  Long | Data 1.2   |\n" +
                "| Data 4.1       |            |\n" +
                "" +
                "", formattedTable)
        assertEquals(pos + 20, offset)
        assertEquals("" +
                "| Header 1.1     | Header 1.2 |\n" +
                "| Header 2.1     | Header 2.2 |\n" +
                "|:---------------|:-----------|\n" +
                "| Data 2.1                   ||\n" +
                "| Data 3.1       |            |\n" +
                "| Data 1.1 ^ Long | Data 1.2   |\n" +
                "| Data 4.1       |            |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
    }

      test("test_trackingOffset3") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see whats^ the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("| Features                                                                         | Basic | Enhanced |    |\n" +
                "|:---------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                 |   X   |    X     |    |\n" +
                "| Preview Tab so you can see whats the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                              |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("| Features                                                                         | Basic | Enhanced |    |\n" +
                "|:---------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                 |   X   |    X     |    |\n" +
                "| Preview Tab so you can see whats^ the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                              |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 3, offset)
    }

      test("test_trackingOffset4") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------:^|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:-------------------------------------------------------------------------------:^|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_trackingOffset_BeforeCells") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "^|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "^|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_trackingOffset_AfterCells") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |^\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |^\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_trackingOffset_TypeAfterCells") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |d^\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    | d  |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    | d^  |\n" +
                "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_LeftAligned_TypedSpaceAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. ^ |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. ^|   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_BackspaceSpaceAfter") {
        val markdown = "" +
                "| Features                                                                           | Basic | Enhanced |    |\n" +
                "|:-----------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                   |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub.    ^|   X   |    X     |    |\n" +
                "| Syntax highlighting                                                                |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                           | Basic | Enhanced |    |\n" +
                "|:-----------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                   |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub.    |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                                |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                           | Basic | Enhanced |    |\n" +
                "|:-----------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                   |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub.    ^|   X   |    X     |    |\n" +
                "| Syntax highlighting                                                                |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_TypedSpaceAfterGrow") {
        val markdown = "" +
                "|                                     Features                                     | Basic | Enhanced |\n" +
                "|----------------------------------------------------------------------------------|:-----:|---------:|\n" +
                "| Works with builds 143.2730 or newer, product IDEA 14.1.4                         |   X   |        X |\n" +
                "| Preview Tab so you can see whats the rendered markdown will look like on GitHub. ^ |   X   |        X |\n" +
                "| Syntax highlighting                                                              |   X   |        X |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                         | Basic | Enhanced |\n" +
                "|:---------------------------------------------------------------------------------|:-----:|---------:|\n" +
                "| Works with builds 143.2730 or newer, product IDEA 14.1.4                         |   X   |        X |\n" +
                "| Preview Tab so you can see whats the rendered markdown will look like on GitHub. |   X   |        X |\n" +
                "| Syntax highlighting                                                              |   X   |        X |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                         | Basic | Enhanced |\n" +
                "|:---------------------------------------------------------------------------------|:-----:|---------:|\n" +
                "| Works with builds 143.2730 or newer, product IDEA 14.1.4                         |   X   |        X |\n" +
                "| Preview Tab so you can see whats the rendered markdown will look like on GitHub. ^|   X   |        X |\n" +
                "| Syntax highlighting                                                              |   X   |        X |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_TypedSpaceAfterFixed") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4 ^                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4 ^               |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_BackspaceAfterEmpty") {
        val markdown = "" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |^    |     |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |      |     |\n" +
                "", formattedTable)
        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd | ^     |     |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_LeftAligned_Backspace2AfterEmpty") {
        val markdown = "" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd | ^    |     |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], true)))

        //System.out.println("Table before: " + table.toString)

        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        //System.out.println("pos " + pos + " -> " + offset)
        //System.out.println("Table after: " + table.toString)

        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |      |     |\n" +
                "", formattedTable)
        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd | ^     |     |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_Backspace4AfterEmpty") {
        val markdown = "" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |    ^|     |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))

        //System.out.println("Table before: " + table.toString)

        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        //System.out.println("pos " + pos + " -> " + offset)
        //System.out.println("Table after: " + table.toString)

        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |      |     |\n" +
                "", formattedTable)
        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |    ^  |     |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_Type2AfterEmpty") {
        val markdown = "" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd |  d^   |     |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd | d    |     |\n" +
                "", formattedTable)
        assertEquals("" +
                "|  tex   | abcd | efg |\n" +
                "|--------|------|-----|\n" +
                "| dddd   | dddd | ddd |\n" +
                "| adfasd | d^    |     |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_RightAligned_TypedSpaceAfterGrow") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                | XXXXXXXXX ^ |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        |     Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|----------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                | XXXXXXXXX |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |         X |    X     |    |\n" +
                "| Syntax highlighting                                                             |         X |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        |     Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|----------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                | XXXXXXXXX ^|    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |         X |    X     |    |\n" +
                "| Syntax highlighting                                                             |         X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 8, offset)
    }

      test("test_LeftAligned_BackSpacesAfterFixed") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4   ^           |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4   ^             |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftAligned_TypedSpaceBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "|  ^Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| ^Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_LeftAligned_2SpacesBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| ^      Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| ^Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Centered_TypedSpaceAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X ^  |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                        "| Syntax highlighting                                                             |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X ^  |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Centered_Typed2SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X  ^  |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |  X    |    X     |    |\n" +
                        "| Syntax highlighting                                                             |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |  X  ^  |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_Centered_Typed3SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |  X    |    X     |    |\n" +
                        "| Syntax highlighting                                                             |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |  X   ^ |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_Centered_TypedExtraSpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. | X     ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. | X     |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. | X     ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Centered_TypedExtra2SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. | X      ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic  | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X    |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. | X      |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X    |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic  | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X    |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. | X      ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |   X    |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 3, offset)
    }

      test("test_Centered_TypedSpaceBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    ^X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                        "| Syntax highlighting                                                             |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   ^X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_Centered_Typed2SpacesBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    ^ X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                        "| Syntax highlighting                                                             |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   ^X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_Centered_Typed3SpacesBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   ^  X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                        "| Syntax highlighting                                                             |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |  ^ X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_RightAligned_TypedSpaceAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X ^ |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X |    X     |    |\n" +
                        "| Syntax highlighting                                                             |     X |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_RightAligned_BackspaceAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X |    X     |    |\n" +
                        "| Syntax highlighting                                                             |     X |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X^ |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_RightAligned_BackspaceAfter2Spaces") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X  ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    X  |    X     |    |\n" +
                        "| Syntax highlighting                                                             |     X |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    X ^ |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 2, offset)
    }

      test("test_RightAligned_Typed2SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X  ^ |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    X  |    X     |    |\n" +
                        "| Syntax highlighting                                                             |     X |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    X  ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_RightAligned_Typed3SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X   ^ |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "| Features                                                                        | Basic | Enhanced |    |\n" +
                        "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                        "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                        "| Syntax highlighting                                                             |     X |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   ^|    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 2, offset)
    }

      test("test_RightAligned_TypedSpaceBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |      ^X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     ^X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_RightAligned_Typed2SpacesBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     ^ X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     ^X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_RightAligned_Typed3SpacesBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     ^  X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |     X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|------:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |     X |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |    ^ X |    X     |    |\n" +
                "| Syntax highlighting                                                             |     X |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_Separator_LeftAlignedBackspaceFirstColon") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_Separator_TypedDashAfter2") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|-^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:-^-------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_Separator_TypedColonBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Separator_RightAlignedTypedColonBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:^-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                        "|                                    Features                                     | Basic | Enhanced |    |\n" +
                        "|:-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                        "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                        "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                        "|                               Syntax highlighting                               |   X   |    X     |    |\n",
                formattedTable)
        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:^-------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Separator_LeftAlignedBackspaceColonBefore") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:^--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_Separator_CenteredBackspaceFirstColon") {
        val markdown = "" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|^------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|                                                                        Features | Basic | Enhanced |    |\n" +
                "|--------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|                Works with builds 143.2730 or newer, product version IDEA 14.1.4 |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                                                             Syntax highlighting |   X   |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "|                                                                        Features | Basic | Enhanced |    |\n" +
                "|^--------------------------------------------------------------------------------:|:-----:|:--------:|:---|\n" +
                "|                Works with builds 143.2730 or newer, product version IDEA 14.1.4 |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                                                             Syntax highlighting |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Separator_CenteredBackspaceLastColon") {
        val markdown = "" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 2, offset)
    }

      test("test_Separator_CenteredBackspaceLastColonRemoveLeftColon") {
        val markdown = "" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.REMOVE))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |   |\n" +
                "|---------------------------------------------------------------------------------|:-----:|:--------:|---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |   |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |   |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |   |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "|                                    Features                                     | Basic | Enhanced |   |\n" +
                "|---------------------------------------------------------------------------------^|:-----:|:--------:|---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |   |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |   |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |   |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_Separator_CenteredBackspaceLastColon2") {
        val markdown = "" +
                "|                                    Features                                     | Basic | Enhanced |    |\n" +
                "|:------------------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "|        Works with builds 143.2730 or newer, product version IDEA 14.1.4         |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "|                               Syntax highlighting                               |   X   |    X     |    |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 10, offset)
    }

      test("test_Caption_TypedAfter") {
        val markdown = "" +
                "| Features                                          | Basic | Enhanced |    |\n" +
                "|:------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                 |   X   |    X     |    |\n" +
                "[testing^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing^ ]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 89, offset)
    }

      test("test_Caption_TypedSpaceAfterEmpty".tag(munit.Ignore)) {
        val markdown = "" +
                "| Features                                          | Basic | Enhanced |    |\n" +
                "|:------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                 |   X   |    X     |    |\n" +
                "[ ^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 88, offset)
    }

      test("test_Caption_Typed2SpaceAfterEmpty".tag(munit.Ignore)) {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[  ^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos - 1, offset)
    }

      test("test_Caption_TypedSpaceAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ^ ]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Caption_Typed2SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing  ^ ]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing  ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing  ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Caption_Typed3SpacesAfter") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing   ^ ]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing   ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing   ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Caption_Backspaces3After") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing  ^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing  ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing  ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Caption_Backspaces2After") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Caption_Backspaces1After") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing^ ]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_Caption_BackspaceAfter".tag(munit.Ignore)) {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[^]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ ]\n" +
                "", formattedTable)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ ^]\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 1, offset)
    }

      test("test_EmbeddedPipe") {
        val markdown = "" +
                "| c | d |\n" +
                "| --- | --- |\n" +
                "| ^*a | b* |\n" +
                "| `e | f` |\n" +
                "| [g | h](http://a.com) |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, false))
        table.fillMissingColumns()

        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| c                     | d  |\n" +
                "|:----------------------|:---|\n" +
                "| *a                    | b* |\n" +
                "| `e | f`               |    |\n" +
                "| [g | h](http://a.com) |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "| c                     | d  |\n" +
                "|:----------------------|:---|\n" +
                "| ^*a                    | b* |\n" +
                "| `e | f`               |    |\n" +
                "| [g | h](http://a.com) |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 38, offset)
    }

      test("test_EmbeddedPipe0") {
        val markdown = "" +
                "| c | d |\n" +
                "| --- | --- |\n" +
                "| ^*a | b* |\n" +
                "| `e | f` |\n" +
                "| [g | h](http://a.com) |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, false))
        table.fillMissingColumns(Nullable(Integer.valueOf(0)))

        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| c  | d                     |\n" +
                "|:---|:----------------------|\n" +
                "| *a | b*                    |\n" +
                "|    | `e | f`               |\n" +
                "|    | [g | h](http://a.com) |\n" +
                "", formattedTable)
        assertEquals("" +
                "| c  | d                     |\n" +
                "|:---|:----------------------|\n" +
                "| ^*a | b*                    |\n" +
                "|    | `e | f`               |\n" +
                "|    | [g | h](http://a.com) |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 38, offset)
    }

      test("test_EmbeddedPipe0Options") {
        val markdown = "" +
                "| c | d |\n" +
                "| --- | --- |\n" +
                "| ^*a | b* |\n" +
                "| `e | f` |\n" +
                "| [g | h](http://a.com) |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, true).set(TableFormatOptions.FORMAT_TABLE_FILL_MISSING_MIN_COLUMN, Nullable(Integer.valueOf(0))))
//        table.fillMissingColumns(Nullable(Integer.valueOf(0)))

        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| c  | d                     |\n" +
                "|:---|:----------------------|\n" +
                "| *a | b*                    |\n" +
                "|    | `e | f`               |\n" +
                "|    | [g | h](http://a.com) |\n" +
                "", formattedTable)
        assertEquals("" +
                "| c  | d                     |\n" +
                "|:---|:----------------------|\n" +
                "| ^*a | b*                    |\n" +
                "|    | `e | f`               |\n" +
                "|    | [g | h](http://a.com) |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 38, offset)
    }

      test("test_indentPrefix") {
        val markdown = "" +
                "| c | d |\n" +
                "| --- | --- |\n" +
                "| ^*a | b* |\n" +
                "| `e | f` |\n" +
                "| [g | h](http://a.com) |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_FILL_MISSING_COLUMNS, false).set(TablesExtension.FORMAT_TABLE_INDENT_PREFIX, "    "))
        table.fillMissingColumns()

        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable(Character.valueOf(' ')), true)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "    | c                     | d  |\n" +
                "    |:----------------------|:---|\n" +
                "    | *a                    | b* |\n" +
                "    | `e | f`               |    |\n" +
                "    | [g | h](http://a.com) |    |\n" +
                "", formattedTable)
        assertEquals("" +
                "    | c                     | d  |\n" +
                "    |:----------------------|:---|\n" +
                "    | ^*a                    | b* |\n" +
                "    | `e | f`               |    |\n" +
                "    | [g | h](http://a.com) |    |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos + 50, offset)
    }

      test("test_LeftCaretAfterNoSpace") {
        val markdown = "" +
                "|       names^       |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|       names       |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable)
        assertEquals("" +
                "|       names^       |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

      test("test_LeftCaretAfterSpace") {
        val markdown = "" +
                "|       names ^      |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|       names       |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable)
        assertEquals("" +
                "|       names ^      |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

    test("test_LeftCaretAfter2Spaces".tag(munit.Ignore)) {
        val markdown = "" +
                "|       names  ^     |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|       names       |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable)
        assertEquals("" +
                "|       names  ^     |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

    test("test_LeftCaretAfter3Spaces".tag(munit.Ignore)) {
        val markdown = "" +
                "|       names   ^    |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TablesExtension.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        val offset = table.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "|       names       |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable)
        assertEquals("" +
                "|       names   ^    |\n" +
                "|-------------------|\n" +
                "| Works with builds |\n" +
                "", formattedTable.substring(0, offset) + "^" + formattedTable.substring(offset))
        assertEquals(pos, offset)
    }

    // these are tested with manipulators
    // Original Java tests are intentionally empty — the methods are covered by
    // ComboTableManipulationSpecTest.  Smoke-tests here verify the API is callable.
    test("allRows") {
      val table = getTable(markdown1)
      val rows = table.getAllRows
      assert(rows != null, "getAllRows should return non-null list")
      assert(rows.size() > 0, "getAllRows should return at least one row")
    }

    test("allRowCount") {
      val table = getTable(markdown1)
      val count = table.getAllRowsCount
      assert(count > 0, s"getAllRowsCount should be positive, got $count")
    }

    test("forAllRows") {
      val table = getTable(markdown2)
      var count = 0
      table.forAllRows(new ssg.md.util.format.TableRowManipulator {
        override def apply(
            row: ssg.md.util.format.TableRow,
            allRowsIndex: Int,
            sectionRows: java.util.ArrayList[ssg.md.util.format.TableRow],
            sectionRowIndex: Int
        ): Int = {
          count += 1
          0
        }
      })
      assert(count > 0, "forAllRows should visit at least one row")
    }

    test("deleteRows") {
      val table = getTable(markdown2)
      val before = table.getAllRowsCount
      table.deleteRows(0, 1)
      val after = table.getAllRowsCount
      assert(after < before, s"deleteRows should reduce row count: before=$before, after=$after")
    }

    test("insertColumns") {
      val table = getTable(markdown1)
      val before = table.getMaxColumns
      table.insertColumns(0, 1)
      val after = table.getMaxColumns
      assert(after > before, s"insertColumns should increase column count: before=$before, after=$after")
    }

    test("deleteColumns") {
      val table = getTable(markdown2)
      val before = table.getMaxColumns
      table.deleteColumns(0, 1)
      val after = table.getMaxColumns
      assert(after < before, s"deleteColumns should reduce column count: before=$before, after=$after")
    }

    test("insertRows") {
      val table = getTable(markdown1)
      val before = table.getAllRowsCount
      table.insertRows(0, 1)
      val after = table.getAllRowsCount
      assert(after > before, s"insertRows should increase row count: before=$before, after=$after")
    }

    test("moveColumn") {
      val table = getTable(markdown2)
      val maxCols = table.getMaxColumns
      // moveColumn should not throw and should preserve column count
      table.moveColumn(0, maxCols - 1)
      assertEquals(table.getMaxColumns, maxCols, "moveColumn should preserve column count")
    }}
