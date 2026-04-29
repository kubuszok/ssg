/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../MarkdownTransposeTableTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.formatter.MarkdownWriter
import ssg.md.html.HtmlWriter
import ssg.md.util.format.TrackedOffset
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }

import scala.annotation.nowarn
import scala.language.implicitConversions

@nowarn("msg=unused private member")
final class MarkdownTransposeTableTest extends MarkdownTableTestBase {

  private val markdown1 = "" +
            "| First Header  |\n" +
            "| ------------- |\n" +
            "| Content Cell  |\n" +
            "\n" +
            ""
  private val markdown2 = "" +
            "| Left-aligned1 | Right-aligned1 |\n" +
            "| Left-aligned2 | Right-aligned2 |\n" +
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

  private val transposedNoCaption1 = "" +
            "| First Header | Content Cell |\n" +
            "|--------------|--------------|\n" +
            ""

  private val transposedNoCaption2 = "" +
            "| Left-aligned | Right-aligned1 |\n" +
            "| Left-aligned | Right-aligned2 |\n" +
            "|:-------------|---------------:|\n" +
            "| git status   |    git status1 |\n" +
            "| git diff     |      git diff2 |\n" +
            ""
  private val transposedNoCaption3 = "" +
            "| Left-aligned | Center-aligned | Right-aligned1 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned2 |\n" +
            "| Left-aligned | Center-aligned | Right-aligned3 |\n" +
            "|:-------------|:--------------:|---------------:|\n" +
            "| git status   |   git status   |    git status1 |\n" +
            "| git diff     |    git diff    |      git diff2 |\n" +
            "| git diff     |    git diff    |      git diff3 |\n" +
            ""

  private val transposed1 = transposedNoCaption1 +
            ""

  private val transposed2 = transposedNoCaption2 +
            "[ ]\n" +
            ""

  private val transposed3 = transposedNoCaption3 +
            "[Table Caption]\n" +
            ""

      test("test_basic1") {
        val tables = getTables("" +
                "| First Header  |\n" +
                "| ------------- |\n" +
                "| Content Cell  |\n" +
                "\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(1)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "| First Header | Content Cell |\n" +
                "|--------------|--------------|\n" +
                "")
    }

      test("test_basic2") {
        val tables = getTables("" +
                "| Left-aligned1 | Right-aligned1 |\n" +
                "| Left-aligned2 | Right-aligned2 |\n" +
                "| :---         |          ---: |\n" +
                "| git status   | git status1    |\n" +
                "| git diff     | git diff2      |\n" +
                "[  ]\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(1)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "| Left-aligned1  | Left-aligned2  | git status  | git diff  |\n" +
                "|----------------|----------------|-------------|-----------|\n" +
                "| Right-aligned1 | Right-aligned2 | git status1 | git diff2 |\n" +
                "[ ]\n" +
                "")
    }

      test("test_basic3") {
        val tables = getTables("| Left-aligned | Center-aligned | Right-aligned1 |\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| :---         |     :---:      |          ---: |\n" +
                "| git status11   | git status21     | git status31    |\n" +
                "| git diff12     | git diff22       | git diff32      |\n" +
                "| git diff13     | git diff23       | git diff33      |\n" +
                "[Table Caption]\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(0)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "| Left-aligned   | Left-aligned1   | Left-aligned2   | git status11 | git diff12 | git diff13 |\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "[Table Caption]\n" +
                "")
    }

      test("test_basic4") {
        val tables = getTables("" +
                "| Left-aligned | Center-aligned | Right-aligned1 |\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| :---         |     :---:      |          ---: |\n" +
                "| git status11   | git status21     | git status31    |\n" +
                "| git diff12     | git diff22       | git diff32      |\n" +
                "| git diff13     | git diff23       | git diff33      |\n" +
                "[Table Caption]\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(1)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "|  Left-aligned  |  Left-aligned1  |  Left-aligned2  | git status11 | git diff12 | git diff13 |\n" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "[Table Caption]\n" +
                "")
    }

      test("test_basic5") {
        val tables = getTables("| Left-aligned | Center-aligned | Right-aligned1 |\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| :---         |     :---:      |          ---: |\n" +
                "| git status11   | git status21     | git status31    |\n" +
                "| git diff12     | git diff22       | git diff32      |\n" +
                "| git diff13     | git diff23       | git diff33      |\n" +
                "[Table Caption]\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(2)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "|  Left-aligned  |  Left-aligned1  |  Left-aligned2  | git status11 | git diff12 | git diff13 |\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "[Table Caption]\n" +
                "")
    }

      test("test_basic6") {
        val tables = getTables("| Left-aligned | Center-aligned | Right-aligned1 |\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| :---         |     :---:      |          ---: |\n" +
                "| git status11   | git status21     | git status31    |\n" +
                "| git diff12     | git diff22       | git diff32      |\n" +
                "| git diff13     | git diff23       | git diff33      |\n" +
                "[Table Caption]\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(3)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "|  Left-aligned  |  Left-aligned1  |  Left-aligned2  | git status11 | git diff12 | git diff13 |\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "[Table Caption]\n" +
                "")
    }

      test("test_basic7") {
        val tables = getTables("| Left-aligned | Center-aligned | Right-aligned1 |\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| :---         |     :---:      |          ---: |\n" +
                "| git status11   | git status21     | git status31    |\n" +
                "| git diff12     | git diff22       | git diff32      |\n" +
                "| git diff13     | git diff23       | git diff33      |\n" +
                "[Table Caption]\n" +
                "")

        val out = new MarkdownWriter(LineAppendable.F_FORMAT_ALL)
        val transposed = tables(0).transposed(4)
        transposed.appendTable(out)

        assertEquals(out.toString, "" +
                "|  Left-aligned  |  Left-aligned1  |  Left-aligned2  | git status11 | git diff12 | git diff13 |\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "[Table Caption]\n" +
                "")
    }

      test("test_trackingOffset1") {
        val markdown = "" +
                "| Left-aligned   | Left-^aligned1   | Left-aligned2   | git status11 | git diff12 | git diff13 |\n" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "[Table Caption]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        val transposed = table.transposed(1)
        transposed.appendTable(out)
        val transposedTable = out.toString(0, 0)
        val offset = transposed.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Left-aligned  | Center-aligned  | Right-aligned1 |\n" +
                "|:--------------|:----------------|:---------------|\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| git status11  | git status21    | git status31   |\n" +
                "| git diff12    | git diff22      | git diff32     |\n" +
                "| git diff13    | git diff23      | git diff33     |\n" +
                "[ Table Caption ]\n" +
                "", transposedTable)
        assertEquals("" +
                "| Left-aligned  | Center-aligned  | Right-aligned1 |\n" +
                "|:--------------|:----------------|:---------------|\n" +
                "| Left-^aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| git status11  | git status21    | git status31   |\n" +
                "| git diff12    | git diff22      | git diff32     |\n" +
                "| git diff13    | git diff23      | git diff33     |\n" +
                "[ Table Caption ]\n" +
                "", transposedTable.substring(0, offset) + "^" + transposedTable.substring(offset))
    }

      test("test_trackingOffset2") {
        val markdown = "" +
                "| Left-aligned   | Left-aligned1   | Left-aligned2   | git status11 | git diff12 | git diff13 |\n" +
                "|----------------|-----------------|---------^--------|--------------|------------|------------|\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 | git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "[Table Caption]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        val transposed = table.transposed(1)
        transposed.appendTable(out)
        val transposedTable = out.toString(0, 0)
        val offset = transposed.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Left-aligned  | Center-aligned  | Right-aligned1 |\n" +
                "|:--------------|:----------------|:---------------|\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| git status11  | git status21    | git status31   |\n" +
                "| git diff12    | git diff22      | git diff32     |\n" +
                "| git diff13    | git diff23      | git diff33     |\n" +
                "[ Table Caption ]\n" +
                "", transposedTable)
        assertEquals("" +
                "| Left-aligned  | Center-aligned  | Right-aligned1 |\n" +
                "|:--------------|:----------------|:---------^------|\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| git status11  | git status21    | git status31   |\n" +
                "| git diff12    | git diff22      | git diff32     |\n" +
                "| git diff13    | git diff23      | git diff33     |\n" +
                "[ Table Caption ]\n" +
                "", transposedTable.substring(0, offset) + "^" + transposedTable.substring(offset))
    }

      test("test_trackingOffset3") {
        val markdown = "" +
                "| Left-aligned   | Left-aligned1   | Left-aligned2   | git status11 | git diff12 | git diff13 |\n" +
                "|----------------|-----------------|-----------------|--------------|------------|------------|\n" +
                "| Center-aligned | Center-aligned1 | Center-aligned2 | git status21 ^| git diff22 | git diff23 |\n" +
                "| Right-aligned1 | Right-aligned2  | Right-aligned3  | git status31 | git diff32 | git diff33 |\n" +
                "[Table Caption]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        assert(table.addTrackedOffset(TrackedOffset.track(pos, Nullable.empty[Character], false)))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        val transposed = table.transposed(1)
        transposed.appendTable(out)
        val transposedTable = out.toString(0, 0)
        val offset = transposed.getTrackedOffsetIndex(pos)

        assertEquals("" +
                "| Left-aligned  | Center-aligned  | Right-aligned1 |\n" +
                "|:--------------|:----------------|:---------------|\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| git status11  | git status21    | git status31   |\n" +
                "| git diff12    | git diff22      | git diff32     |\n" +
                "| git diff13    | git diff23      | git diff33     |\n" +
                "[ Table Caption ]\n" +
                "", transposedTable)
        assertEquals("" +
                "| Left-aligned  | Center-aligned  | Right-aligned1 |\n" +
                "|:--------------|:----------------|:---------------|\n" +
                "| Left-aligned1 | Center-aligned1 | Right-aligned2 |\n" +
                "| Left-aligned2 | Center-aligned2 | Right-aligned3 |\n" +
                "| git status11  | git status21 ^   | git status31   |\n" +
                "| git diff12    | git diff22      | git diff32     |\n" +
                "| git diff13    | git diff23      | git diff33     |\n" +
                "[ Table Caption ]\n" +
                "", transposedTable.substring(0, offset) + "^" + transposedTable.substring(offset))
    }}
