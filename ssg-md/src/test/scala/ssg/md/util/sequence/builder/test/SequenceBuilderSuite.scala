/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package test

import ssg.md.util.sequence.{ BasedOptionsHolder, BasedOptionsSequence, BasedSequence, PositionAnchor, PrefixedSubSequence, Range }
import ssg.md.util.sequence.builder.ISegmentBuilder.{ F_INCLUDE_ANCHORS, F_TRACK_FIRST256 }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class SequenceBuilderSuite extends munit.FunSuite {

  test("test_appendRangeDefault") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence)

    builder.append(Range.of(0, 0))
    builder.append(Range.of(0, 4))
    builder.append(Range.of(10, 10))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendRangeAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    builder.append(Range.of(0, 0))
    builder.append(Range.of(0, 4))
    builder.append(Range.of(10, 10))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendRangeNoAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence, F_TRACK_FIRST256)

    builder.append(Range.of(0, 0))
    builder.append(Range.of(0, 4))
    builder.append(Range.of(10, 10))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendSubSequenceDefault") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence)

    builder.append(sequence.subSequence(0, 0))
    builder.append(sequence.subSequence(0, 4))
    builder.append(sequence.subSequence(10, 10))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendSubSequenceAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    builder.append(sequence.subSequence(0, 0))
    builder.append(sequence.subSequence(0, 4))
    builder.append(sequence.subSequence(10, 10))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendSubSequenceNoAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence, F_TRACK_FIRST256)

    builder.append(sequence.subSequence(0, 0))
    builder.append(sequence.subSequence(0, 4))
    builder.append(sequence.subSequence(10, 10))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendRange1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val builder  = SequenceBuilder.emptyBuilder(sequence)

    builder.append(Range.of(0, 4))

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123")
  }

  test("test_appendRangeNonOverlapping") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val builder = SequenceBuilder.emptyBuilder(sequence)

    builder.append(0, 4)
    builder.append(6, 7)

    assertEquals(builder.toStringWithRanges, "\u27e60123\u27e7\u27e66\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01236")
  }

  test("test_appendRangeTouching") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val builder = SequenceBuilder.emptyBuilder(sequence)

    builder.append(Range.of(0, 5))
    builder.append(Range.of(5, 7))
    assertEquals(builder.toStringWithRanges, "\u27e60123456\u27e7")
    assertEquals(builder.toSequence.toString, "0123456")
  }

  test("test_appendRangeOverlapping") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val builder = SequenceBuilder.emptyBuilder(sequence)

    builder.append(0, 5)
    builder.append(3, 7)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e734\u27e656\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "012343456")
  }

  test("test_appendRangeOverlappingOverString") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val builder = SequenceBuilder.emptyBuilder(sequence)

    builder.append(0, 5)
    builder.append("abc")
    builder.append(3, 7)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7abc34\u27e656\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234abc3456")
  }

  test("test_appendRangeStrings") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val builder = SequenceBuilder.emptyBuilder(sequence)

    builder.append(0, 5)
    builder.append("abc")
    builder.append("def")
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7abcdef\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234abcdef")
  }

  /*
     Optimization tests, optimizer for backward compatibility
   */

  test("test_optimizerExtendPrev1") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("345")
    builder.append(6, 10)
    assertEquals(builder.toStringWithRanges, "\u27e60123456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123456789")
  }

  test("test_optimizerExtendPrev2") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("34 ")
    builder.append(6, 10)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7 \u27e66789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234 6789")
  }

  test("test_optimizerExtendPrevNext") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("34 5")
    builder.append(6, 10)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7 \u27e656789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234 56789")
  }

  test("test_optimizerExtendPrevNextCollapse") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("34 56")
    builder.append(7, 10)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7 \u27e656789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234 56789")
  }

  test("test_optimizerExtendNext") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append(" 3456")
    builder.append(7, 10)
    assertEquals(builder.toStringWithRanges, "\u27e6012\u27e7 \u27e63456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "012 3456789")
  }

  test("test_optimizerExtendNext1") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append(" 345")
    builder.append(6, 10)
    assertEquals(builder.toStringWithRanges, "\u27e6012\u27e7 \u27e63456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "012 3456789")
  }

  test("test_optimizerIndent1") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append(" 345")
    builder.append(6, 10)
    assertEquals(builder.toStringWithRanges, "\u27e6012\u27e7 \u27e63456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "012 3456789")
  }

  /*
   * Optimizer tests to ensure all optimizations are handled properly
   */

  test("test_optimizersIndent1None") {
    val input     = "  0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append("    ")
    builder.append(2, 12)
    assertEquals(builder.toStringWithRanges, "  \u27e6  0123456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "    0123456789")
  }

  test("test_optimizersSpacesNone") {
    val input     = "01234  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("    ")
    builder.append(7, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234 \u27e7  \u27e6 56789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234    56789")
  }

  test("test_optimizersSpacesLeft") {
    val input     = "01234  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("    ")
    builder.append(7, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7  \u27e6  56789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234    56789")
  }

  test("test_optimizersSpacesRight") {
    val input     = "01234  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("    ")
    builder.append(7, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234  \u27e7  \u27e656789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234    56789")
  }

  test("test_optimizersIndent1Left") {
    val input     = "  0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append("    ")
    builder.append(2, 12)
    assertEquals(builder.toStringWithRanges, "  \u27e6  0123456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "    0123456789")
  }

  test("test_optimizersIndent1Right") {
    val input     = "  0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append("    ")
    builder.append(2, 12)
    assertEquals(builder.toStringWithRanges, "  \u27e6  0123456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "    0123456789")
  }

  test("test_optimizersEOL1None") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n    ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\u27e7  \u27e6  5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n    5678")
  }

  test("test_optimizersEOL1Left") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n    ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\u27e7  \u27e6  5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n    5678")
  }

  test("test_optimizersEOL1Right") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n    ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\u27e7  \u27e6  5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n    5678")
  }

  test("test_optimizersEOL2None") {
    val input     = "01234\n\n 56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n\n   ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\\n\u27e7  \u27e6 5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n\\n   5678")
  }

  test("test_optimizersEOL2Left") {
    val input     = "01234\n\n 56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n\n   ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\\n\u27e7  \u27e6 5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n\\n   5678")
  }

  test("test_optimizersEOL2Right") {
    val input     = "01234\n\n 56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n\n   ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\\n\u27e7  \u27e6 5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n\\n   5678")
  }

  test("test_optimizersEOL3None") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("34\n    ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\u27e7  \u27e6  5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n    5678")
  }

  test("test_optimizersEOL3Left") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("34\n    ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\u27e7  \u27e6  5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n    5678")
  }

  test("test_optimizersEOL3Right") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 3)
    builder.append("34\n    ")
    builder.append(8, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\\n\u27e7  \u27e6  5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n    5678")
  }

  test("test_optimizers1") {
    val input     = "01234 \n56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n  ")
    builder.append(7, 12)
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7\u27e6\\n\u27e7  \u27e656789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n  56789")
  }

  test("test_optimizers2") {
    val input     = "01234 \n"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n")
    assertEquals(builder.toStringWithRanges, "\u27e601234\u27e7\u27e6\\n\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234\\n")
  }

  test("test_optimizers2a") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = "01234  \n"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append(" \n")
    assertEquals(builder.toStringWithRanges, "\u27e601234 \u27e7\u27e6\\n\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "01234 \\n")
  }

  test("test_optimizers3") {
    val input     = "012340123401234"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("01234")
    assertEquals(builder.toStringWithRanges, "\u27e60123401234\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123401234")
  }

  test("test_optimizers4") {
    val input     = "0123  \n  5678"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 5)
    builder.append("\n")
    builder.append(8, 13)
    assertEquals(builder.toStringWithRanges, "\u27e60123 \u27e7\u27e6\\n\u27e7\u27e6 5678\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "0123 \\n 5678")
  }

  test("test_optimizersCompoundDefault1") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) builder.append("    ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }

    assertEquals(
      builder.toStringWithRanges,
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )
    assertEquals(builder.toString,
                 "" +
                   "    line 1\n" +
                   "    line 2\n" +
                   "\n" +
                   "    line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundDefault2") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) builder.append("  ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }
    assertEquals(builder.toStringWithRanges, "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7")
    assertEquals(builder.toString,
                 "" +
                   "  line 1\n" +
                   "  line 2\n" +
                   "\n" +
                   "  line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundDefault3") {
    val input = "" +
      "line 1\n" +
      "line 2 \n" +
      "\n" +
      "line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
//            if (!trim.isEmpty()) segments.append("  ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }
    assertEquals(builder.toStringWithRanges, "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7")
    assertEquals(builder.toString,
                 "" +
                   "line 1\n" +
                   "line 2\n" +
                   "\n" +
                   "line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundNoAnchors1") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, F_TRACK_FIRST256, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) builder.append("    ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }

    assertEquals(
      builder.toStringWithRanges,
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )
    assertEquals(builder.toString,
                 "" +
                   "    line 1\n" +
                   "    line 2\n" +
                   "\n" +
                   "    line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundNoAnchors2") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, F_TRACK_FIRST256, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) builder.append("  ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }
    assertEquals(builder.toStringWithRanges, "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7")
    assertEquals(builder.toString,
                 "" +
                   "  line 1\n" +
                   "  line 2\n" +
                   "\n" +
                   "  line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundNoAnchors3") {
    val input = "" +
      "line 1\n" +
      "line 2 \n" +
      "\n" +
      "line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, F_TRACK_FIRST256, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
//            if (!trim.isEmpty()) segments.append("  ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }
    assertEquals(builder.toStringWithRanges, "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7")
    assertEquals(builder.toString,
                 "" +
                   "line 1\n" +
                   "line 2\n" +
                   "\n" +
                   "line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundAnchors1") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) builder.append("    ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }

    assertEquals(
      builder.toStringWithRanges,
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )
    assertEquals(builder.toString,
                 "" +
                   "    line 1\n" +
                   "    line 2\n" +
                   "\n" +
                   "    line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundAnchors2") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) builder.append("  ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }
    assertEquals(builder.toStringWithRanges, "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7")
    assertEquals(builder.toString,
                 "" +
                   "  line 1\n" +
                   "  line 2\n" +
                   "\n" +
                   "  line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompoundAnchors3") {
    val input = "" +
      "line 1\n" +
      "line 2 \n" +
      "\n" +
      "line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256, optimizer)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
//            if (!trim.isEmpty()) segments.append("  ")
      builder.append(trim.getSourceRange)
      builder.append("\n")
    }
    assertEquals(builder.toStringWithRanges, "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7")
    assertEquals(builder.toString,
                 "" +
                   "line 1\n" +
                   "line 2\n" +
                   "\n" +
                   "line 3\n" +
                   ""
    )
  }

  test("test_addSuffix2") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = "0123456789"
    val base      = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val sequence  = base.subSequence(1, 9)

    val builder = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(sequence.subSequence(0, 8))
    builder.append(">")
    assertEquals(builder.toStringWithRanges, "\u27e612345678\u27e7>\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "12345678>")

    builder.append(">")
    assertEquals(builder.toStringWithRanges, "\u27e612345678\u27e7>\u27e6\u27e7>\u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "12345678>>")

    val replaced1 = base.suffixWith(">")
    assertEquals(replaced1.toString, "0123456789>")

    val replaced2 = replaced1.suffixWith(">")
    assertEquals(replaced2.toString, "0123456789>>")

    val replaced3 = base.suffixWith(">").suffixWith(">")
    assertEquals(replaced3.toString, "0123456789>>")

    val builder2 = SequenceBuilder.emptyBuilder(replaced1, optimizer)
    builder2.append(replaced1)
    assertEquals(builder2.toStringWithRanges, "\u27e60123456789\u27e7>\u27e6\u27e7")
    builder2.append(">")
    assertEquals(builder2.toStringWithRanges, "\u27e60123456789\u27e7>\u27e6\u27e7>\u27e6\u27e7")
    assertEquals(builder2.toSequence.toVisibleWhitespaceString(), "0123456789>>")
    assertEquals(builder2.toSequence.getSourceRange, Range.of(0, 10))
  }

  test("test_replaced") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)

    val builder = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 0)
    builder.append("^")
    builder.append(1, 10)
    assertEquals(builder.toStringWithRanges, "\u27e6\u27e7^\u27e6123456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "^123456789")
    val sequence1 = builder.toSequence
    assertEquals(sequence1.getSourceRange, Range.of(0, 10))

    val replaced = sequence.replace(0, 1, "^")

    assertEquals(replaced.toString, "^123456789")
    assertEquals(replaced.getSourceRange, Range.of(0, 10))
  }

  test("test_replaced2") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = "01234567890123456789"
    val sequence  = BasedSequence.of(BasedOptionsSequence.of(input, BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES))
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 0)
    builder.append("abcd")
    builder.append(3, 10)
    builder.append("abcd")
    builder.append(13, 20)
    assertEquals(builder.toStringWithRanges, "\u27e6\u27e7abcd\u27e63456789\u27e7abcd\u27e63456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "abcd3456789abcd3456789")
    val sequence1 = builder.toSequence
    assertEquals(sequence1.getSourceRange, Range.of(0, 20))

    val replaced = sequence.replace("012", "abcd")
    assertEquals(replaced.toString, "abcd3456789abcd3456789")
    assertEquals(replaced.getSourceRange, Range.of(3, 20))

    val builder2 = SequenceBuilder.emptyBuilder(replaced, optimizer)
    builder2.append("  ")
    builder2.append(replaced.subSequence(0, 11))
    builder2.append("\n  ")
    builder2.append(replaced.subSequence(11, 22))
    builder2.append("\n")
    assertEquals(
      builder2.toStringWithRanges,
      "  \u27e6\u27e7abcd\u27e63456789\u27e7\\n  \u27e6\u27e7abcd\u27e63456789\u27e7\\n\u27e6\u27e7"
    )
    assertEquals(builder2.toSequence.toVisibleWhitespaceString(), "  abcd3456789\\n  abcd3456789\\n")
    val sequence2 = builder2.toSequence
    assertEquals(sequence2.getSourceRange, Range.of(3, 20))
  }

  test("test_replaced3") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = " 0123456789\n 0123456789"
    val sequence  = BasedSequence.of(BasedOptionsSequence.of(input, BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES))
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    builder.append(0, 1)
    builder.append("abcd")
    builder.append(4, 11)
    builder.append("abcd")
    builder.append(16, 23)
    assertEquals(builder.toStringWithRanges, "\u27e6 \u27e7abcd\u27e63456789\u27e7abcd\u27e63456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), " abcd3456789abcd3456789")
    val sequence1 = builder.toSequence
    assertEquals(sequence1.getSourceRange, Range.of(0, 23))

    val replaced = sequence.replace("012", "abcd")
    assertEquals(replaced.toString, " abcd3456789\n abcd3456789")
    assertEquals(replaced.getSourceRange, Range.of(0, 23))

    val builder2 = SequenceBuilder.emptyBuilder(replaced, optimizer)
    builder2.append("  ")
    builder2.append(replaced.subSequence(1, 12))
    builder2.append("\n  ")
    builder2.append(replaced.subSequence(14, 25))
    builder2.append("\n")
    assertEquals(
      builder2.toStringWithRanges,
      "  \u27e6\u27e7abcd\u27e63456789\\n\u27e7  \u27e6\u27e7abcd\u27e63456789\u27e7\\n\u27e6\u27e7"
    )
    assertEquals(builder2.toSequence.toVisibleWhitespaceString(), "  abcd3456789\\n  abcd3456789\\n")
    val sequence2 = builder2.toSequence
    assertEquals(sequence2.getSourceRange, Range.of(4, 23))
  }

  test("test_replaced4") {
    // this one causes text to be replaced with recovered EOL in the code
    val input    = " 0123456789\n 0123456789"
    val sequence = BasedSequence.of(BasedOptionsSequence.of(input, BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES))
    val builder  = SequenceBuilder.emptyBuilder(sequence)

    builder.append(0, 1)
    builder.append("abcd")
    builder.append(4, 11)
    builder.append("abcd")
    builder.append(16, 23)
    assertEquals(builder.toStringWithRanges, "\u27e6 \u27e7abcd\u27e63456789\u27e7abcd\u27e63456789\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), " abcd3456789abcd3456789")
    val sequence1 = builder.toSequence
    assertEquals(sequence1.getSourceRange, Range.of(0, 23))

    val replaced = sequence.replace("012", "abcd")
    assertEquals(replaced.toString, " abcd3456789\n abcd3456789")
    assertEquals(replaced.getSourceRange, Range.of(0, 23))

    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val builder2  = SequenceBuilder.emptyBuilder(replaced, optimizer)
    builder2.append("  ")
    assertEquals(builder2.toStringWithRanges, "  ")
    builder2.append(replaced.subSequence(1, 12))
    assertEquals(builder2.toStringWithRanges, "  \u27e6\u27e7abcd\u27e63456789\u27e7")
    builder2.append("\n  ")
    assertEquals(builder2.toStringWithRanges, "  \u27e6\u27e7abcd\u27e63456789\\n\u27e7  ")

    val builder3 = SequenceBuilder.emptyBuilder(replaced, optimizer)
    builder3.append(replaced.subSequence(14, 25))
    assertEquals(builder3.toStringWithRanges, "\u27e6\u27e7abcd\u27e63456789\u27e7")

    builder2.append(replaced.subSequence(14, 25))
    assertEquals(builder2.toStringWithRanges, "  \u27e6\u27e7abcd\u27e63456789\\n \u27e7 \u27e6\u27e7abcd\u27e63456789\u27e7")

    builder2.append("\n")
    assertEquals(
      builder2.toStringWithRanges,
      "  \u27e6\u27e7abcd\u27e63456789\\n \u27e7 \u27e6\u27e7abcd\u27e63456789\u27e7\\n\u27e6\u27e7"
    )

    assertEquals(builder2.toSequence.toVisibleWhitespaceString(), "  abcd3456789\\n  abcd3456789\\n")
    val sequence2 = builder2.toSequence
    assertEquals(sequence2.getSourceRange, Range.of(4, 23))
  }

  test("test_appendPrefixed2") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = "0123456789"
    val sequence  = BasedSequence.of(BasedOptionsSequence.of(input, BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES))
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val builder   = SequenceBuilder.emptyBuilder(sequence, optimizer)

    val text = PrefixedSubSequence.repeatOf(" ", 5, sequence).append(PrefixedSubSequence.repeatOf(" ", 5, sequence.getEmptySuffix))
    builder.append(text)

    assertEquals(builder.toStringWithRanges, "\u27e6\u27e7     \u27e60123456789\u27e7     \u27e6\u27e7")
    assertEquals(builder.toSequence.toVisibleWhitespaceString(), "     0123456789     ")
  }
}
