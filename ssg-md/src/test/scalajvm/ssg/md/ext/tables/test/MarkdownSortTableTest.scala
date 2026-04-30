/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../MarkdownSortTableTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.formatter.MarkdownWriter
import ssg.md.html.HtmlWriter
import ssg.md.util.ast.TextContainer
import ssg.md.util.format.{ ColumnSort, MarkdownTable, TrackedOffset }
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }

import scala.language.implicitConversions

final class MarkdownSortTableTest extends MarkdownTableTestBase {

  test("test_basic1A") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, false, false)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        ""
    )
  }

  test("test_basic1ALinkText") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, false, false)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|          Header1           | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------------------------|----------|----------|----------|----------|----------|\n" +
        "| column11                   | column28 | column31 | 11       | 028      | 31       |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13                   | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14                   | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15                   | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17                   | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18                   | column21 | column35 | 018      | 21       | 043      |\n" +
        ""
    )
  }

  test("test_basic1ALinkPageRef") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 |     11 |      028  |       31 |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, false, false)), TextContainer.F_LINK_PAGE_REF, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|          Header1           | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------------------------|----------|----------|----------|----------|----------|\n" +
        "| column11                   | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column13                   | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14                   | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15                   | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column17                   | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18                   | column21 | column35 | 018      | 21       | 043      |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        ""
    )
  }

  test("test_basic1ALinkAnchor") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, false, false)), TextContainer.F_LINK_ANCHOR, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|          Header1           | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------------------------|----------|----------|----------|----------|----------|\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column11                   | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column13                   | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14                   | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15                   | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column17                   | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18                   | column21 | column35 | 018      | 21       | 043      |\n" +
        ""
    )
  }

  test("test_basic1ALinkUrl") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, false, false)), TextContainer.F_LINK_URL, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|          Header1           | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------------------------|----------|----------|----------|----------|----------|\n" +
        "| column11                   | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column13                   | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14                   | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15                   | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column17                   | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18                   | column21 | column35 | 018      | 21       | 043      |\n" +
        "| [column12](url12#anchor12) | column27 | column32 | 12       | column27 | 32       |\n" +
        "| [column16](url16#anchor16) | column23 | column37 | column16 | 0x17     | column37 |\n" +
        ""
    )
  }

  test("test_basic1D") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, true, false, false)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        ""
    )
  }

  test("test_basic2A") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(1, false, false, false)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        ""
    )
  }

  test("test_basic2D") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(1, true, false, false)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        ""
    )
  }

  test("test_basicN1AF") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(3, false, true, false)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        ""
    )
  }

  test("test_basicN1AFSuffix") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 11NonNum |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, true, false)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| 11        |\n" +
        "| 11Another |\n" +
        "| 11NonNum  |\n" +
        "| 12        |\n" +
        "| 16        |\n" +
        "| column15  |\n" +
        "| column17  |\n" +
        "| column18  |\n" +
        ""
    )
  }

  test("test_basicN1DFSuffix") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 11NonNum |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, true, true, false)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| 16        |\n" +
        "| 12        |\n" +
        "| 11        |\n" +
        "| 11NonNum  |\n" +
        "| 11Another |\n" +
        "| column18  |\n" +
        "| column17  |\n" +
        "| column15  |\n" +
        ""
    )
  }

  test("test_basicN1ALSuffix") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 11NonNum |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, true, true)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| column15  |\n" +
        "| column17  |\n" +
        "| column18  |\n" +
        "| 11Another |\n" +
        "| 11NonNum  |\n" +
        "| 11        |\n" +
        "| 12        |\n" +
        "| 16        |\n" +
        ""
    )
  }

  test("test_basicN1DLSuffix") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 11NonNum |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, true, true, true)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| column18  |\n" +
        "| column17  |\n" +
        "| column15  |\n" +
        "| 16        |\n" +
        "| 12        |\n" +
        "| 11NonNum  |\n" +
        "| 11Another |\n" +
        "| 11        |\n" +
        ""
    )
  }

  test("test_basicN1AFSuffixNoSort") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11NonNum |\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, true, false)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_NO_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| 11NonNum  |\n" +
        "| 11        |\n" +
        "| 11Another |\n" +
        "| 12        |\n" +
        "| 16        |\n" +
        "| column15  |\n" +
        "| column17  |\n" +
        "| column18  |\n" +
        ""
    )
  }

  test("test_basicN1DFSuffixNoSort") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11NonNum |\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, true, true, false)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_NO_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| 16        |\n" +
        "| 12        |\n" +
        "| 11NonNum  |\n" +
        "| 11        |\n" +
        "| 11Another |\n" +
        "| column18  |\n" +
        "| column17  |\n" +
        "| column15  |\n" +
        ""
    )
  }

  test("test_basicN1ALSuffixNoSort") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11NonNum |\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, false, true, true)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_NO_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| column15  |\n" +
        "| column17  |\n" +
        "| column18  |\n" +
        "| 11NonNum  |\n" +
        "| 11        |\n" +
        "| 11Another |\n" +
        "| 12        |\n" +
        "| 16        |\n" +
        ""
    )
  }

  test("test_basicN1DLSuffixNoSort") {
    val table = getTable(
      "" +
        "| Header1  |\n" +
        "|----------|\n" +
        "| 11NonNum |\n" +
        "| 11 |\n" +
        "| 11Another |\n" +
        "| 12 |\n" +
        "| column15 |\n" +
        "| 16 |\n" +
        "| column17 |\n" +
        "| column18 |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(0, true, true, true)), 0, Nullable(MarkdownTable.ALL_SUFFIXES_NO_SORT))
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "|  Header1  |\n" +
        "|-----------|\n" +
        "| column18  |\n" +
        "| column17  |\n" +
        "| column15  |\n" +
        "| 16        |\n" +
        "| 12        |\n" +
        "| 11NonNum  |\n" +
        "| 11        |\n" +
        "| 11Another |\n" +
        ""
    )
  }

  test("test_basicN1AL") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(3, false, true, true)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        ""
    )
  }

  test("test_basicN1DF") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(3, true, true, false)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        ""
    )
  }

  test("test_basicN1DL") {
    val table = getTable(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(3, true, true, true)), 0, Nullable.empty)
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        ""
    )
  }

  test("test_basicSpanA") {
    val table = getTable(
      "" +
        "| Header1 | Header2 | Header3 |\n" +
        "|---------|---------|---------|\n" +
        "| span13  | span24  | span36  |\n" +
        "| span11                    |||\n" +
        "| span13  | span24  | span35  |\n" +
        "| span12  | span22           ||\n" +
        "| span11  | value2  |         |\n" +
        "| span13  | span23  | span34  |\n" +
        "| span11  | value1  |         |\n" +
        "| span13  | span23  | span33  |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(
      Array(
        ColumnSort.columnSort(0, false, false, false),
        ColumnSort.columnSort(1, false, false, false),
        ColumnSort.columnSort(2, false, false, false)
      ),
      0,
      Nullable.empty
    )
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1 | Header2 | Header3 |\n" +
        "|---------|---------|---------|\n" +
        "| span11                    |||\n" +
        "| span11  | value1  |         |\n" +
        "| span11  | value2  |         |\n" +
        "| span12  | span22           ||\n" +
        "| span13  | span23  | span33  |\n" +
        "| span13  | span23  | span34  |\n" +
        "| span13  | span24  | span35  |\n" +
        "| span13  | span24  | span36  |\n" +
        ""
    )
  }

  test("test_basicSpanD") {
    val table = getTable(
      "" +
        "| Header1 | Header2 | Header3 |\n" +
        "|---------|---------|---------|\n" +
        "| span13  | span24  | span36  |\n" +
        "| span11                    |||\n" +
        "| span13  | span24  | span35  |\n" +
        "| span12  | span22           ||\n" +
        "| span11  | value2  |         |\n" +
        "| span13  | span23  | span34  |\n" +
        "| span11  | value1  |         |\n" +
        "| span13  | span23  | span33  |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(
      Array(
        ColumnSort.columnSort(0, true, false, false),
        ColumnSort.columnSort(1, true, false, false),
        ColumnSort.columnSort(2, true, false, false)
      ),
      0,
      Nullable.empty
    )
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1 | Header2 | Header3 |\n" +
        "|---------|---------|---------|\n" +
        "| span13  | span24  | span36  |\n" +
        "| span13  | span24  | span35  |\n" +
        "| span13  | span23  | span34  |\n" +
        "| span13  | span23  | span33  |\n" +
        "| span12  | span22           ||\n" +
        "| span11  | value2  |         |\n" +
        "| span11  | value1  |         |\n" +
        "| span11                    |||\n" +
        ""
    )
  }

  test("test_basicSpan1A2D3A") {
    val table = getTable(
      "" +
        "| Header1 | Header2 | Header3 |\n" +
        "|---------|---------|---------|\n" +
        "| span13  | span24  | span36  |\n" +
        "| span11                    |||\n" +
        "| span13  | span24  | span35  |\n" +
        "| span12  | span22           ||\n" +
        "| span11  | value2  |         |\n" +
        "| span13  | span23  | span34  |\n" +
        "| span11  | value1  |         |\n" +
        "| span13  | span23  | span33  |\n" +
        "",
      formatOptionsAsIs("", null)
    )

    val out    = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(
      Array(
        ColumnSort.columnSort(0, false, false, false),
        ColumnSort.columnSort(1, true, false, false),
        ColumnSort.columnSort(2, false, false, false)
      ),
      0,
      Nullable.empty
    )
    sorted.appendTable(out)

    assertEquals(
      out.toString,
      "" +
        "| Header1 | Header2 | Header3 |\n" +
        "|---------|---------|---------|\n" +
        "| span11  | value2  |         |\n" +
        "| span11  | value1  |         |\n" +
        "| span11                    |||\n" +
        "| span12  | span22           ||\n" +
        "| span13  | span24  | span35  |\n" +
        "| span13  | span24  | span36  |\n" +
        "| span13  | span23  | span33  |\n" +
        "| span13  | span23  | span34  |\n" +
        ""
    )
  }

  test("test_trackingOffset1") {
    val markdown = "" +
      "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
      "|----------|----------|----------|----------|----------|----------|\n" +
      "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
      "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
      "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
      "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
      "| column15 | column24 | column38 | 017^      | 0b11000  | 038      |\n" +
      "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
      "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
      "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
      ""

    val pos = markdown.indexOf("^")
    val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
    val source = BasedSequence.of(charSequence)
    val table  = getTable(source, formatOptionsAsIs("", null))
    assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
    val out    = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
    val sorted = table.sorted(Array(ColumnSort.columnSort(1, false, false, false)), 0, Nullable.empty)
    sorted.appendTable(out)
    val sortedTable = out.toString(0, 0)
    val offset      = sorted.getTrackedOffsetIndex(pos)

    assertEquals(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column15 | column24 | column38 | 017      | 0b11000  | 038      |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "",
      sortedTable
    )
    assertEquals(
      "" +
        "| Header1  | Header2  | Header3  | Numeric1 | Numeric2 | Numeric3 |\n" +
        "|----------|----------|----------|----------|----------|----------|\n" +
        "| column18 | column21 | column35 | 018      | 21       | 043      |\n" +
        "| column17 | column22 | column36 | column17 | 22       | column36 |\n" +
        "| column16 | column23 | column37 | column16 | 0x17     | column37 |\n" +
        "| column15 | column24 | column38 | 017^      | 0b11000  | 038      |\n" +
        "| column14 | column25 | column34 | 0b1110   | 031      | 0x100010 |\n" +
        "| column13 | column26 | column33 | 0xD      | column26 | 0x21     |\n" +
        "| column12 | column27 | column32 | 12       | column27 | 32       |\n" +
        "| column11 | column28 | column31 | 11       | 028      | 31       |\n" +
        "",
      sortedTable.substring(0, offset) + "^" + sortedTable.substring(offset)
    )
  }
}
