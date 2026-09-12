/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package format
package test

import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.SequenceBuilder
import ssg.md.util.sequence.mappers.SpecialLeadInCharsHandler

import java.util.Collections

import scala.language.implicitConversions

final class MarkdownParagraphSuite extends munit.FunSuite {

  test("test_wrapIndentedLines") {
    val input = BasedSequence.of(
      "Add: configuration for repeated prefixes in items, which would `be #2` copied when adding/splitting an item. In other words they\n" +
        "    would be treated equivalent to task item marker prefix. That way\n" +
        "    standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes would be automatically copied."
    )

    val expected = "Add: configuration for repeated prefixes in items, which would `be\n" +
      "#2` copied when adding/splitting an item. In other words they\n" +
      "would be treated equivalent to task item marker prefix. That way\n" +
      "standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\n" +
      "would be automatically copied."

    val formatter = new MarkdownParagraph(input, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 66
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true

    val actual = formatter.wrapTextNotTracked()
    assertEquals(actual.toString, expected)
  }

  test("test_wrapIndentedLines2") {
    val input = BasedSequence.of(
      "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only task\n" +
        "    item prefix or adding to only list items. Test is done."
    )

    val expected = "" +
      "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\n" +
      "task item prefix or adding to only list items. Test is done." +
      ""

    val formatter = new MarkdownParagraph(input, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 90
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true

    val actual = formatter.wrapTextNotTracked()
    assertEquals(actual.toString, expected)
  }

  test("test_leadInEscaper") {
    val input = BasedSequence.of(
      "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only #\n" +
        "    item prefix or adding to only list items. Test is done."
    )

    val expected = "" +
      "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\n" +
      "\\# item prefix or adding to only list items. Test is done." +
      ""

    val basedInput = BasedSequence.of(input)
    val formatter  = new MarkdownParagraph(basedInput, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 90
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true

    formatter.leadInHandlers = Collections.singletonList(SpecialLeadInCharsHandler.create('#'))

    val actual = formatter.wrapTextNotTracked()
    assertEquals(actual.toString, expected)

    val builder = SequenceBuilder.emptyBuilder(basedInput)
    actual.addSegments(builder.segmentBuilder)
    assertEquals(
      builder.segmentBuilder.toStringWithRanges(),
      "" +
        "\u27e6Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\u27e7\n" +
        "\\\u27e6#\u27e7\u27e6 \u27e7\u27e6item prefix or adding to only list items. Test is done.\u27e7" +
        ""
    )

    val sequence = actual.toSpc().trimEnd(CharPredicate.WHITESPACE).appendEOL()

    val builder2 = SequenceBuilder.emptyBuilder(basedInput)
    sequence.addSegments(builder2.segmentBuilder)
    assertEquals(
      builder2.segmentBuilder.toStringWithRanges(),
      "" +
        "\u27e6Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\u27e7\n" +
        "\\\u27e6#\u27e7\u27e6 \u27e7\u27e6item prefix or adding to only list items. Test is done.\u27e7\n" +
        "\u27e6\u27e7" +
        ""
    )
  }

  test("test_leadInUnEscaper") {
    val input = BasedSequence.of(
      "" +
        "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\n" +
        "      \\# item prefix or adding to only list items. Test is done." +
        ""
    )

    val expected = "" +
      "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only #\n" +
      "item prefix or adding to only list items. Test is done." +
      ""

    val basedInput = BasedSequence.of(input)
    val formatter  = new MarkdownParagraph(basedInput, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 92
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true

    formatter.leadInHandlers = Collections.singletonList(SpecialLeadInCharsHandler.create('#'))

    val actual = formatter.wrapTextNotTracked()
    assertEquals(actual.toString, expected)

    val builder = SequenceBuilder.emptyBuilder(basedInput)
    actual.addSegments(builder.segmentBuilder)
    assertEquals(
      builder.segmentBuilder.toStringWithRanges(),
      "" +
        "\u27e6Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\u27e7\u27e6 \u27e7\u27e6#\u27e7\n" +
        "\u27e6item prefix or adding to only list items. Test is done.\u27e7" +
        ""
    )

    val sequence = actual.toSpc().trimEnd(CharPredicate.WHITESPACE).appendEOL()

    val builder2 = SequenceBuilder.emptyBuilder(basedInput)
    sequence.addSegments(builder2.segmentBuilder)
    assertEquals(
      builder2.segmentBuilder.toStringWithRanges(),
      "" +
        "\u27e6Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\u27e7\u27e6 \u27e7\u27e6#\u27e7\n" +
        "\u27e6item prefix or adding to only list items. Test is done.\u27e7\n" +
        "\u27e6\u27e7" +
        ""
    )
  }
}
