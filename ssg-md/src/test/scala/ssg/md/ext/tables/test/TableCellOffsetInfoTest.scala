/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../TableCellOffsetInfoTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.util.format.TableFormatOptions
import ssg.md.util.format.options.DiscretionaryText
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }

import scala.language.implicitConversions

final class TableCellOffsetInfoTest extends MarkdownTableTestBase {


      test("test_nextOffsetStop1") {
        val markdown = "" +
                "|            Default             | Lefts                          |    Right | Centered in a column^ |   |\n" +
                "|            Default             | Lefts                          |    Right |        Center        |   |\n" +
                "|--------------------------------|:-------------------------------|---------:|:--------------------:|---|\n" +
                "| item 1                         | item 1                         |   125.30 |          a           |   |\n" +
                "| item 2                         | item 2                         | 1,234.00 |          bc          |   |\n" +
                "| item 3 much longer description | item 3 much longer description |    10.50 |         def          |   |\n" +
                "| item 4 short                   | item 4 short                   |    34.10 |          h           |   |\n" +
                "[ cap**tion** ]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null).toMutable.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, DiscretionaryText.AS_IS))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        var offsetInfo = table.getCellOffsetInfo(pos)

        assertEquals("" +
                "|            Default             | Lefts                          |    Right | Centered in a column^ |   |\n" +
                "|            Default             | Lefts                          |    Right |        Center        |   |\n" +
                "|--------------------------------|:-------------------------------|---------:|:--------------------:|---|\n" +
                "| item 1                         | item 1                         |   125.30 |          a           |   |\n" +
                "| item 2                         | item 2                         | 1,234.00 |          bc          |   |\n" +
                "| item 3 much longer description | item 3 much longer description |    10.50 |         def          |   |\n" +
                "| item 4 short                   | item 4 short                   |    34.10 |          h           |   |\n" +
                "[ cap**tion** ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "|            Default             | Lefts                          |    Right | Centered in a column | ^  |\n" +
                "|            Default             | Lefts                          |    Right |        Center        |   |\n" +
                "|--------------------------------|:-------------------------------|---------:|:--------------------:|---|\n" +
                "| item 1                         | item 1                         |   125.30 |          a           |   |\n" +
                "| item 2                         | item 2                         | 1,234.00 |          bc          |   |\n" +
                "| item 3 much longer description | item 3 much longer description |    10.50 |         def          |   |\n" +
                "| item 4 short                   | item 4 short                   |    34.10 |          h           |   |\n" +
                "[ cap**tion** ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))
    }

      test("test_nextOffsetStop") {
        val markdown = "" +
                "^| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        var offsetInfo = table.getCellOffsetInfo(pos)

        assertEquals("" +
                "^| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features^                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic^ | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced^ |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced | ^   |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|^:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|^:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:^|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|^:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:^|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|^:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---^|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4^                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X^   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X^     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     | ^   |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub.^ |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X^   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X^     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     | ^   |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting^                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X^   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X^     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     | ^   |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing^ ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.nextOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n^" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))
    }

      test("test_previousOffsetStop") {
        val markdown = "" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n^" +
                ""

        val pos = markdown.indexOf("^")
        val charSequence: CharSequence = markdown.substring(0, pos) + markdown.substring(pos + 1)
        val source = BasedSequence.of(charSequence)
        val table = getTable(source, formatOptions("", null))
        val out = new HtmlWriter(0, LineAppendable.F_FORMAT_ALL)
        table.appendTable(out)
        val formattedTable = out.toString(0, 0)
        var offsetInfo = table.getCellOffsetInfo(pos)

        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n^" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing^ ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     | ^   |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X^     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X^   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting^                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     | ^   |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X^     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X^   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub.^ |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     | ^   |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X^     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X^   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4^                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---^|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|^:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:^|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|^:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:^|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|^:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------^|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced |    |\n" +
                "|^:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced | ^   |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic | Enhanced^ |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features                                                                        | Basic^ | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "| Features^                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))

        offsetInfo = offsetInfo.previousOffsetStop(Nullable.empty)
        assertEquals("" +
                "^| Features                                                                        | Basic | Enhanced |    |\n" +
                "|:--------------------------------------------------------------------------------|:-----:|:--------:|:---|\n" +
                "| Works with builds 143.2730 or newer, product version IDEA 14.1.4                |   X   |    X     |    |\n" +
                "| Preview Tab so you can see what the rendered markdown will look like on GitHub. |   X   |    X     |    |\n" +
                "| Syntax highlighting                                                             |   X   |    X     |    |\n" +
                "[ testing ]\n" +
                "", formattedTable.substring(0, offsetInfo.offset) + "^" + formattedTable.substring(offsetInfo.offset))
    }}
