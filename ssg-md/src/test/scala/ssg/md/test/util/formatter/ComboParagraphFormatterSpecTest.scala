/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../ComboParagraphFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * This test uses MarkdownParagraph directly instead of the full Formatter pipeline.
 * It tests paragraph-level wrapping and tracked offsets. */
package ssg
package md
package test
package util
package formatter

import ssg.md.Nullable
import ssg.md.formatter.Formatter
import ssg.md.test.util.{ FormatterSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataSet, SharedDataKeys }
import ssg.md.util.format.{ CharWidthProvider, MarkdownParagraph, TrackedOffset }
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }
import ssg.md.util.sequence.builder.SequenceBuilder

import java.{ util => ju }
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class ComboParagraphFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboParagraphFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboParagraphFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboParagraphFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Wrap -")

  override protected def renderHtml(example: spec.SpecExample, options: DataHolder): String = {
    val noFileEol     = TestUtils.NO_FILE_EOL.get(options)
    val trimmedSource = if (noFileEol) TestUtils.trimTrailingEOL(example.source) else example.source
    val input = BasedSequence.of(trimmedSource)
    val out = new StringBuilder()

    val info = TestUtils.extractMarkup(input)
    val sequence = BasedSequence.of(info.first.get)

    val effectiveOptions = options

    val paragraph = new MarkdownParagraph(sequence, CharWidthProvider.NULL)
    paragraph.options = Nullable(effectiveOptions)

    val restoreTrackedSpaces = Formatter.RESTORE_TRACKED_SPACES.get(effectiveOptions)
    val rightMargin = Formatter.RIGHT_MARGIN.get(effectiveOptions)
    val prefix: CharSequence = Formatter.DOCUMENT_PREFIX.get(effectiveOptions)
    val firstIndent: CharSequence = Formatter.DOCUMENT_FIRST_PREFIX.get(effectiveOptions)

    if (restoreTrackedSpaces && (prefix.length > 0 || firstIndent.length > 0)) {
      paragraph.restoreTrackedSpaces = true
      paragraph.firstWidthOffset = firstIndent.length - prefix.length
      paragraph.width = rightMargin - prefix.length
    } else {
      paragraph.restoreTrackedSpaces = restoreTrackedSpaces
      paragraph.width = rightMargin
      paragraph.firstWidthOffset = ComboParagraphFormatterSpecTest.FIRST_WIDTH_DELTA.get(effectiveOptions)
      paragraph.setIndent(prefix)
      paragraph.setFirstIndent(firstIndent)
    }

    paragraph.keepSoftLineBreaks = false // cannot keep line breaks when formatting as you type
    paragraph.keepHardLineBreaks = true

    val offsets: Array[Int] = info.second.get

    for (offset <- offsets) {
      val c = FormatterSpecTestSuite.EDIT_OP_CHAR.get(effectiveOptions)
      val editOp = FormatterSpecTestSuite.EDIT_OP.get(effectiveOptions)

      val trackedOffset = TrackedOffset.track(offset, editOp != 0 && c == ' ', editOp > 0, editOp < 0)
      trackedOffset.spacesBefore = sequence.getBaseSequence.countTrailingSpaceTab(offset)
      trackedOffset.spacesAfter = sequence.getBaseSequence.countLeadingSpaceTab(offset)

      paragraph.addTrackedOffset(trackedOffset)
    }

    val actual = paragraph.wrapText()

    val builder: SequenceBuilder = sequence.getBuilder
    actual.addSegments(builder.segmentBuilder)

    val trackedOffsets = paragraph.getTrackedOffsets.asScala.toList
    val resultOffsets = new Array[Int](offsets.length)

    if (trackedOffsets.nonEmpty) {
      TestUtils.appendBanner(out, ComboParagraphFormatterSpecTest.BANNER_TRACKED_OFFSETS)
      var r = 0
      for (trackedOffset <- trackedOffsets) {
        val offset = trackedOffset.getIndex
        out.append("[").append(r).append("]: ").append(trackedOffset.toString).append("\n")
        resultOffsets(r) = offset
        r += 1
      }
    }

    TestUtils.appendBannerIfNeeded(out, ComboParagraphFormatterSpecTest.BANNER_WITH_RANGES)
    out.append(builder.toStringWithRanges(false).replace("\\n", "\n")).append(SequenceUtils.EOL)
    TestUtils.appendBannerIfNeeded(out, ComboParagraphFormatterSpecTest.BANNER_RESULT)
    out.append(TestUtils.insertCaretMarkup(actual, resultOffsets).toSequence)

    out.toString
  }
}

object ComboParagraphFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/test/util/formatter/core_paragraph_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboParagraphFormatterSpecTest], SPEC_RESOURCE)

  val FIRST_WIDTH_DELTA: DataKey[Int] = new DataKey[Int]("FIRST_WIDTH_DELTA", 0)

  val OPTIONS: DataHolder = new MutableDataSet()
    .set(SharedDataKeys.RUNNING_TESTS, false) // Set to true to get stdout printout of intermediate wrapping information
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put("first-width-delta", new MutableDataSet().set(TestUtils.CUSTOM_OPTION, ((option: String, params: String) => TestUtils.customIntOption(option, Nullable(params), (v: Int) => firstWidthDeltaOption(v))): java.util.function.BiFunction[String, String, DataHolder]).toImmutable)
    map
  }

  private def firstWidthDeltaOption(params: Int): DataHolder =
    new MutableDataSet().set(FIRST_WIDTH_DELTA, params).toImmutable

  val BANNER_TRACKED_OFFSETS: String = TestUtils.bannerText("Tracked Offsets")
  val BANNER_WITH_RANGES:    String = TestUtils.bannerText("Ranges")
  val BANNER_RESULT:         String = TestUtils.bannerText("Result")
}
