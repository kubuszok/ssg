/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package tree
package test

import ssg.md.util.format.{ CharWidthProvider, MarkdownParagraph }
import ssg.md.util.misc.Pair
import ssg.md.util.sequence.BasedSequence

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class BasedOffsetTrackerSuite extends munit.FunSuite {

  private def getInput(input: String): Pair[String, Int] = {
    val pos = input.indexOf("\u299a")
    if (pos >= 0) {
      Pair.of(input.substring(0, pos) + input.substring(pos + 1), pos)
    } else {
      Pair.of(input, 0)
    }
  }

  private def getResult(actual: String, result: OffsetInfo): String =
    if (result.isEndOffset == (result.startIndex == result.endIndex)) {
      val index = result.startIndex
      actual.substring(0, index) + "\u299a" + actual.substring(index)
    } else {
      actual.substring(0, result.startIndex) + "\u27e6" + actual.substring(result.startIndex, result.endIndex) + "\u27e7" + actual.substring(result.endIndex)
    }

  private def wrapText(input: String, margin: Int): BasedSequence = {
    val info     = getInput(input)
    val sequence = BasedSequence.of(info.first.get)

    val formatter = new MarkdownParagraph(sequence, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = margin
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true

    val offset   = info.second.get
    val builder1 = sequence.getBuilder[SequenceBuilder]
    if (offset >= 0 && offset < sequence.length()) {
      sequence.subSequence(0, offset).addSegments(builder1.segmentBuilder)
      builder1.append("\u299a")
      sequence.subSequence(offset).addSegments(builder1.segmentBuilder)
    } else {
      sequence.addSegments(builder1.segmentBuilder)
    }
    // System.out.println(builder1.toStringWithRanges)

    formatter.wrapTextNotTracked()
  }

  private def wrapText(input: String, isEndOffset: Boolean, margin: Int): String = {
    val actual    = wrapText(input, margin)
    val info1     = getInput(input)
    val sequence1 = BasedSequence.of(info1.first.get)
    resolveOffset(sequence1, actual, info1.second.get, isEndOffset)
  }

  private def wrapText(input: String, isEndOffset: Boolean, margin: Int, postProcessor: BasedSequence => BasedSequence): String = {
    val actual    = postProcessor(wrapText(input, margin))
    val info1     = getInput(input)
    val sequence1 = BasedSequence.of(info1.first.get)
    resolveOffset(sequence1, actual, info1.second.get, isEndOffset)
  }

  private def resolveOffset(sequence: BasedSequence, actual: BasedSequence, offset: Int, isEndOffset: Boolean): String = {
    val builder = sequence.getBuilder[SequenceBuilder]
    actual.addSegments(builder.segmentBuilder)
    // System.out.println(builder.toStringWithRanges)

    val tracker = BasedOffsetTracker.create(actual)
    val result  = tracker.getOffsetInfo(offset, isEndOffset)
    result.toString + "\n-----------------------------------------------------\n" +
      builder.toStringWithRanges.replace("\\n", "\n") + "\n-----------------------------------------------------\n" +
      getResult(actual.toString, result)
  }

  test("test_getOffsetIndex1") {
    val input = "" +
      "Add: configuration for repeated prefixes in items, which would `be #2` copied when \u299aadding/splitting an item. In other words they\n" +
      "    would be treated equivalent to task item marker prefix. That way\n" +
      "    standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes would be automatically copied." +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[83, 84), i=[83, 84) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6Add: configuration for repeated prefixes in items, which would `be\u27e7\n" +
      "\u27e6#2` copied when adding/splitting an item. In other words they\n" +
      "\u27e7\u27e6would be treated equivalent to task item marker prefix. That way\n" +
      "\u27e7\u27e6standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\u27e7\n" +
      "\u27e6would be automatically copied.\u27e7\n" +
      "-----------------------------------------------------\n" +
      "Add: configuration for repeated prefixes in items, which would `be\n" +
      "#2` copied when \u299aadding/splitting an item. In other words they\n" +
      "would be treated equivalent to task item marker prefix. That way\n" +
      "standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\n" +
      "would be automatically copied." +
      ""

    assertEquals(wrapText(input, false, 66), expected)
  }

  test("test_getOffsetIndex1End") {
    val input = "" +
      "Add: configuration for repeated prefixes in items, which would `be #2` copied when \u299aadding/splitting an item. In other words they\n" +
      "    would be treated equivalent to task item marker prefix. That way\n" +
      "    standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes would be automatically copied." +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[83), i=[83, 83) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6Add: configuration for repeated prefixes in items, which would `be\u27e7\n" +
      "\u27e6#2` copied when adding/splitting an item. In other words they\n" +
      "\u27e7\u27e6would be treated equivalent to task item marker prefix. That way\n" +
      "\u27e7\u27e6standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\u27e7\n" +
      "\u27e6would be automatically copied.\u27e7\n" +
      "-----------------------------------------------------\n" +
      "Add: configuration for repeated prefixes in items, which would `be\n" +
      "#2` copied when \u299aadding/splitting an item. In other words they\n" +
      "would be treated equivalent to task item marker prefix. That way\n" +
      "standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\n" +
      "would be automatically copied." +
      ""

    assertEquals(wrapText(input, true, 66), expected)
  }

  test("test_getOffsetIndex2") {
    val input = "" +
      "Add: configuration for repeated prefixes in items, which would `be #2` copied when \u299a adding/splitting an item. In other words they\n" +
      "    would be treated equivalent to task item marker prefix. That way\n" +
      "    standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes would be automatically copied." +
      ""

    val expected = "" +
      "OffsetInfo{ p=2, o=[83), i=[83, 83) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6Add: configuration for repeated prefixes in items, which would `be\u27e7\n" +
      "\u27e6#2` copied when \u27e7\u27e6adding/splitting an item. In other words they\n" +
      "\u27e7\u27e6would be treated equivalent to task item marker prefix. That way\n" +
      "\u27e7\u27e6standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\u27e7\n" +
      "\u27e6would be automatically copied.\u27e7\n" +
      "-----------------------------------------------------\n" +
      "Add: configuration for repeated prefixes in items, which would `be\n" +
      "#2` copied when \u299aadding/splitting an item. In other words they\n" +
      "would be treated equivalent to task item marker prefix. That way\n" +
      "standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\n" +
      "would be automatically copied." +
      ""

    assertEquals(wrapText(input, false, 66), expected)
  }

  test("test_getOffsetIndex2End") {
    val input = "" +
      "Add: configuration for repeated prefixes in items, which would `be #2` copied when \u299a adding/splitting an item. In other words they\n" +
      "    would be treated equivalent to task item marker prefix. That way\n" +
      "    standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes would be automatically copied." +
      ""

    val expected = "" +
      "OffsetInfo{ p=2, o=[83), i=[83, 83) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6Add: configuration for repeated prefixes in items, which would `be\u27e7\n" +
      "\u27e6#2` copied when \u27e7\u27e6adding/splitting an item. In other words they\n" +
      "\u27e7\u27e6would be treated equivalent to task item marker prefix. That way\n" +
      "\u27e7\u27e6standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\u27e7\n" +
      "\u27e6would be automatically copied.\u27e7\n" +
      "-----------------------------------------------------\n" +
      "Add: configuration for repeated prefixes in items, which would `be\n" +
      "#2` copied when \u299aadding/splitting an item. In other words they\n" +
      "would be treated equivalent to task item marker prefix. That way\n" +
      "standard: `Add: `, `Fix: `, `Break: ` and `Deprecate: ` prefixes\n" +
      "would be automatically copied." +
      ""

    assertEquals(wrapText(input, true, 66), expected)
  }

  test("test_getOffsetIndexTypedText") {
    val input = "" +
      "text should wrap onto the next t\u299a\n" +
      "line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[32), i=[32, 33) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6text should wrap onto the next t\u27e7 \u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "text should wrap onto the next t\u27e6 \u27e7line at right margin of 30" +
      ""

    assertEquals(wrapText(input, false, 66), expected)
  }

  test("test_getOffsetIndexTypedTextEnd") {
    val input = "" +
      "text should wrap onto the next t\u299a\n" +
      "line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[32), i=[32, 33) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6text should wrap onto the next t\u27e7 \u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "text should wrap onto the next t\u27e6 \u27e7line at right margin of 30" +
      ""

    assertEquals(wrapText(input, true, 66), expected)
  }

  test("test_getOffsetIndexTypedText2") {
    val input = "" +
      "text should wrap onto the next t\u299a\n" +
      "line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=2, o=[32), i=[32, 33) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6text should wrap onto the next\u27e7\n" +
      "\u27e6t\u27e7 \u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "text should wrap onto the next\n" +
      "t\u27e6 \u27e7line at right margin of 30" +
      ""

    assertEquals(wrapText(input, false, 30), expected)
  }

  test("test_getOffsetIndexTypedText2End") {
    val input = "" +
      "text should wrap onto the next t\u299a\n" +
      "line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=2, o=[32), i=[32, 33) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6text should wrap onto the next\u27e7\n" +
      "\u27e6t\u27e7 \u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "text should wrap onto the next\n" +
      "t\u27e6 \u27e7line at right margin of 30" +
      ""

    assertEquals(wrapText(input, true, 30), expected)
  }

  test("test_getOffsetIndexTypedText3") {
    val input = "" +
      "text should wrap onto the next\n" +
      "     \u299a line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[36), i=[31, 31) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6text should wrap onto the next\n" +
      "\u27e7\u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "text should wrap onto the next\n" +
      "\u299aline at right margin of 30" +
      ""

    assertEquals(wrapText(input, false, 30), expected)
  }

  test("test_getOffsetIndexTypedText3End") {
    val input = "" +
      "text should wrap onto the next\n" +
      "     \u299a line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[36), i=[31, 31) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6text should wrap onto the next\n" +
      "\u27e7\u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "text should wrap onto the next\n" +
      "\u299aline at right margin of 30" +
      ""

    assertEquals(wrapText(input, true, 30), expected)
  }

  test("test_getOffsetIndexTypedText4") {
    val input = "" +
      "text should wrap onto the next\n" +
      "     \u299a line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[36, 37), i=[38, 39) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6\u27e7    \u27e6text should wrap onto the next\n" +
      "  \u27e7\u27e6  line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "    text should wrap onto the next\n" +
      "   \u299a line at right margin of 30" +
      ""

    assertEquals(
      wrapText(
        input,
        false,
        30,
        { wrapped =>
          val indented = wrapped.getBuilder[SequenceBuilder]
          wrapped.splitListEOL(true).asScala.foreach { line =>
            indented.append("    ")
            indented.append(line)
          }
          indented.toSequence
        }
      ),
      expected
    )
  }

  test("test_getOffsetIndexTypedText4End") {
    val input = "" +
      "text should wrap onto the next\n" +
      "     \u299a line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[36), i=[38, 38) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6\u27e7    \u27e6text should wrap onto the next\n" +
      "  \u27e7\u27e6  line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "    text should wrap onto the next\n" +
      "   \u299a line at right margin of 30" +
      ""

    assertEquals(
      wrapText(
        input,
        true,
        30,
        { wrapped =>
          val indented = wrapped.getBuilder[SequenceBuilder]
          wrapped.splitListEOL(true).asScala.foreach { line =>
            indented.append("    ")
            indented.append(line)
          }
          indented.toSequence
        }
      ),
      expected
    )
  }

  test("test_getOffsetIndexTypedText5") {
    val input = "" +
      "text should wrap onto the next\u299a line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[30), i=[34, 35) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6\u27e7    \u27e6text should wrap onto the next\u27e7\n" +
      "\u27e6\u27e7    \u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "    text should wrap onto the next\u27e6\n" +
      "\u27e7    line at right margin of 30" +
      ""

    assertEquals(
      wrapText(
        input,
        false,
        30,
        { wrapped =>
          val indented = wrapped.getBuilder[SequenceBuilder]
          wrapped.splitListEOL(true).asScala.foreach { line =>
            indented.append("    ")
            indented.append(line)
          }
          indented.toSequence
        }
      ),
      expected
    )
  }

  test("test_getOffsetIndexTypedText5End") {
    val input = "" +
      "text should wrap onto the next\u299a line at right margin of 30" +
      ""

    val expected = "" +
      "OffsetInfo{ p=1, o=[30), i=[34, 35) }\n" +
      "-----------------------------------------------------\n" +
      "\u27e6\u27e7    \u27e6text should wrap onto the next\u27e7\n" +
      "\u27e6\u27e7    \u27e6line at right margin of 30\u27e7\n" +
      "-----------------------------------------------------\n" +
      "    text should wrap onto the next\u27e6\n" +
      "\u27e7    line at right margin of 30" +
      ""

    assertEquals(
      wrapText(
        input,
        true,
        30,
        { wrapped =>
          val indented = wrapped.getBuilder[SequenceBuilder]
          wrapped.splitListEOL(true).asScala.foreach { line =>
            indented.append("    ")
            indented.append(line)
          }
          indented.toSequence
        }
      ),
      expected
    )
  }
}
