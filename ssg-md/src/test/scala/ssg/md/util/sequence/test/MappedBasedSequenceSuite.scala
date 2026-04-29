/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

import ssg.md.util.format.{ CharWidthProvider, MarkdownParagraph }
import ssg.md.util.sequence.builder.BasedSegmentBuilder
import ssg.md.util.sequence.mappers.{ ChangeCase, NullEncoder, SpaceMapper }

import scala.language.implicitConversions

final class MappedBasedSequenceSuite extends munit.FunSuite {

  test("nullEncoding") {
    val input        = "\u0000\n123456789\u0000\nabcdefghij\n\u0000"
    val encodedInput = "\uFFFD\n123456789\uFFFD\nabcdefghij\n\uFFFD"

    val sequence:       BasedSequence = BasedSequence.of(input)
    val mapEncoded:     BasedSequence = sequence.toMapped(NullEncoder.encodeNull)
    val mapDecoded:     BasedSequence = sequence.toMapped(NullEncoder.decodeNull)
    val encoded:        BasedSequence = BasedSequence.of(encodedInput)
    val encodedDecoded: BasedSequence = encoded.toMapped(NullEncoder.decodeNull)

    // sequences automatically encode nulls
    assertEquals(sequence.toString, encodedInput)

    assertEquals(mapEncoded.toString, encodedInput)
    assertEquals(mapDecoded.toString, input)
    assertEquals(encoded.toString, encodedInput)
    assertEquals(encodedDecoded.toString, input)
  }

  test("spaceMapping") {
    val input        = "\u0020\n123456789\u0020\nabcdefghij\n\u0020"
    val encodedInput = "\u00A0\n123456789\u00A0\nabcdefghij\n\u00A0"

    val sequence:       BasedSequence = BasedSequence.of(input)
    val mapEncoded:     BasedSequence = sequence.toMapped(SpaceMapper.toNonBreakSpace)
    val mapDecoded:     BasedSequence = sequence.toMapped(SpaceMapper.fromNonBreakSpace)
    val encoded:        BasedSequence = BasedSequence.of(encodedInput)
    val encodedDecoded: BasedSequence = encoded.toMapped(SpaceMapper.fromNonBreakSpace)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
    assertEquals(mapDecoded.toString, input)
    assertEquals(encoded.toString, encodedInput)
    assertEquals(encodedDecoded.toString, input)
  }

  test("toLowerCase") {
    val input        = "This Is Mixed\n"
    val encodedInput = "this is mixed\n"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toLowerCase)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("unmodifiedBaseSequence") {
    val input = "This Is Mixed\n"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toLowerCase)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.baseSubSequence(0).toString, input)
  }

  test("toUpperCase") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS IS MIXED\n"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toUpperCase)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("chain") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS\u00A0IS\u00A0MIXED\n"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toUpperCase.andThen(SpaceMapper.toNonBreakSpace))

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("chain2") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS\u00A0IS\u00A0MIXED\n"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toUpperCase).toMapped(SpaceMapper.toNonBreakSpace)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("segmented") {
    val input        = "This Is Mixed\n"
    val encodedInput = "<THIS IS MIXED\n"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toUpperCase).prefixWith("<")

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("segmented2") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS IS MIXED\n>"

    val sequence:   BasedSequence = BasedSequence.of(input)
    val mapEncoded: BasedSequence = sequence.toMapped(ChangeCase.toUpperCase).suffixWith(">")

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  // preserve segmented sequence segments
  test("segmented3") {
    val input    = "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only task\n    item prefix or adding to only list items. Test is done."
    val expected = "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\n" +
      "task item prefix or adding to only list items. Test is done."

    val sequence:   BasedSequence = BasedSequence.of(input)
    val wrapped:    BasedSequence = sequence.subSequence(0, 89).append(SequenceUtils.EOL).append(sequence.subSequence(90, 94)).append(SequenceUtils.SPACE).append(sequence.subSequence(99, 154))
    val mapEncoded: BasedSequence = wrapped.toMapped(SpaceMapper.fromNonBreakSpace)
    val eolAdded:   BasedSequence = mapEncoded.appendEOL()

    assertEquals(mapEncoded.toString, expected)
    assertEquals(eolAdded.toString, expected + "\n")
  }

  // preserve segmented sequence segments
  test("segmented4") {
    val input    = "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only task\n    item prefix or adding to only list items. Test is done."
    val expected = "Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\n" +
      "task item prefix or adding to only list items. Test is done."

    val sequence:  BasedSequence     = BasedSequence.of(input)
    val formatter: MarkdownParagraph = new MarkdownParagraph(sequence, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 90
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true
    val wrapped: BasedSequence = formatter.wrapTextNotTracked()
    assertEquals(wrapped.toString, expected)

    val builder = BasedSegmentBuilder.emptyBuilder(sequence)
    wrapped.addSegments(builder)
    assertEquals(
      builder.toStringWithRangesVisibleWhitespace(),
      "\u27E6Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\u27E7\\n\u27E6task\u27E7\u27E6 \u27E7\u27E6item prefix or adding to only list items. Test is done.\u27E7"
    )

    val mapEncoded: BasedSequence = wrapped.toMapped(SpaceMapper.fromNonBreakSpace)
    val eolAdded:   BasedSequence = mapEncoded.appendEOL()
    val builder2 = BasedSegmentBuilder.emptyBuilder(sequence)
    mapEncoded.addSegments(builder2)
    assertEquals(
      builder2.toStringWithRangesVisibleWhitespace(),
      "\u27E6Fix: mixed task and non-task items, toggle prefix adds it to all instead of removing only\u27E7\\n\u27E6task\u27E7\u27E6 \u27E7\u27E6item prefix or adding to only list items. Test is done.\u27E7"
    )

    assertEquals(mapEncoded.toString, expected)
    assertEquals(eolAdded.toString, expected + "\n")
  }

  // preserve segmented sequence segments
  test("segmentedNbsp") {
    val input    = "[simLink](simLink.md)"
    val expected = "[simLink](simLink.md)"

    val sequence:  BasedSequence     = BasedSequence.of(input).toMapped(SpaceMapper.toNonBreakSpace)
    val formatter: MarkdownParagraph = new MarkdownParagraph(sequence, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 90
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true
    val wrapped: BasedSequence = formatter.wrapTextNotTracked()
    assertEquals(wrapped.toString, expected)

    val builder = BasedSegmentBuilder.emptyBuilder(sequence)
    wrapped.addSegments(builder)
    assertEquals(builder.toStringWithRangesVisibleWhitespace(), "\u27E6[simLink](simLink.md)\u27E7")
  }

  test("segmentedNbsp2") {
    val input    = "[simLink spaced](simLink.md)"
    val expected = "[simLink spaced](simLink.md)"

    val sequence:  BasedSequence     = BasedSequence.of(input).toMapped(SpaceMapper.toNonBreakSpace)
    val formatter: MarkdownParagraph = new MarkdownParagraph(sequence, CharWidthProvider.NULL)
    formatter.setFirstIndent("")
    formatter.width = 90
    formatter.firstWidthOffset = 0
    formatter.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    formatter.keepHardLineBreaks = true
    val wrapped: BasedSequence = formatter.wrapTextNotTracked()
    assertEquals(wrapped.toString, expected.replace(" ", "\u00A0"))

    val builder = BasedSegmentBuilder.emptyBuilder(sequence)
    wrapped.toMapped(SpaceMapper.fromNonBreakSpace).addSegments(builder)
    assertEquals(builder.toStringWithRangesVisibleWhitespace(), "\u27E6[simLink spaced](simLink.md)\u27E7")
  }
}
