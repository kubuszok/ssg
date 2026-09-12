/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package test

import ssg.md.util.misc.Utils.escapeJavaString
import ssg.md.util.sequence.{ BasedSequence, PositionAnchor }
import ssg.md.util.sequence.builder.ISegmentBuilder.{ F_INCLUDE_ANCHORS, F_TRACK_FIRST256 }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class PlainSegmentBuilderSuite extends munit.FunSuite {

  /** Test subclass that applies CharRecoveryOptimizer, matching the Java OptimizedSegmentBuilder2. */
  private class OptimizedSegmentBuilder2(base: CharSequence, optimizer: CharRecoveryOptimizer, options: Int) extends PlainSegmentBuilder(options) {

    def this(base: CharSequence, optimizer: CharRecoveryOptimizer) =
      this(base, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    override protected def optimizeText(parts: Array[Object]): Array[Object] =
      optimizer.apply(base, parts)
  }

  test("test_basicBuildEmpty") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    val expected = ""

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{NULL, s=0:0, u=0:0, t=0:0, l=0, sz=0, na=0 }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), expected)
    assertEquals(segments.toString(sequence), expected)
  }

  test("test_basicEmptyDefaults") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 0)
    segments.append(sequence.length(), sequence.length())

    assertEquals(segments.length, 0)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=0, sz=2, na=0: [0), [10) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_basicEmptyNoAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_TRACK_FIRST256)
    segments.append(0, 0)
    segments.append(sequence.length(), sequence.length())

    assertEquals(segments.length, 0)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=0, sz=0, na=0 }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_basicEmptyAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    segments.append(0, 0)
    segments.append(sequence.length(), sequence.length())

    assertEquals(segments.length, 0)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=0, sz=2, na=0: [0), [10) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_basicPrefix") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append("  ")
    segments.append(0, 4)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 4), s=1:2, u=1:2, t=1:2, l=6, sz=2, na=2: a:2x' ', [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_basicPrefix1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(' ')
    segments.append(' ')
    segments.append(0, 4)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 4), s=1:2, u=1:2, t=1:2, l=6, sz=2, na=2: a:2x' ', [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_basicPrefix2") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(' ', 2)
    segments.append(0, 4)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 4), s=1:2, u=1:2, t=1:2, l=6, sz=2, na=2: a:2x' ', [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_basicAnchorBeforeEnd") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    segments.append("  ")
    segments.append(0, 4)
    segments.appendAnchor(3)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 4), s=1:2, u=1:2, t=1:2, l=6, sz=2, na=2: a:2x' ', [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_basicAnchorAtEnd") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    segments.append("  ")
    segments.append(0, 4)
    segments.appendAnchor(4)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 4), s=1:2, u=1:2, t=1:2, l=6, sz=2, na=2: a:2x' ', [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_basicAnchorAfterEnd") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    segments.append("  ")
    segments.append(0, 4)
    segments.appendAnchor(5)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 5), s=1:2, u=1:2, t=1:2, l=6, sz=3, na=2: a:2x' ', [0, 4), [5) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7\u27e6\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_appendRange1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 4)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 4), s=0:0, u=0:0, t=0:0, l=4, sz=1, na=1: [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 4))
  }

  test("test_appendRangeNonOverlapping") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 4)
    segments.append(6, 7)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=5, sz=2, na=2: [0, 4), [6, 7) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123\u27e7\u27e66\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 4) + input.substring(6, 7))
  }

  test("test_appendRangeOverlapping") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 5)
    segments.append(3, 7)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=7, sz=1, na=1: [0, 7) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123456\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 7))
  }

  test("test_appendRangeOverlappingOverString") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 5)
    segments.append("abc")
    segments.append(3, 7)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 7), s=0:0, u=1:3, t=1:3, l=10, sz=3, na=3: [0, 5), a:'abc', [5, 7) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e7abc\u27e656\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 5) + "abc" + input.substring(5, 7))
  }

  test("test_appendRangeStrings") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 5)
    segments.append("abc")
    segments.append("def")

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 5), s=0:0, u=1:6, t=1:6, l=11, sz=3, na=2: [0, 5), a:'abcdef', [5) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e7abcdef\u27e6\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 5) + "abcdef")
  }

  test("test_appendRangeTouching") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)

    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 5)
    segments.append(5, 7)

    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=7, sz=1, na=1: [0, 7) }")
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123456\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 7))
  }

  test("test_handleOverlapDefaultChop1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append("-")
    segments.append(4, 8)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 8), s=0:0, u=1:1, t=1:1, l=7, sz=3, na=3: [2, 5), a:'-', [5, 8) }")
    assertEquals(segments.toString(sequence), "234-567")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultChop2") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append("-")
    segments.append(1, 8)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 8), s=0:0, u=1:1, t=1:1, l=7, sz=3, na=3: [2, 5), a:'-', [5, 8) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultChop3") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append("-")
    segments.append(3, 5)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=1:1, t=1:1, l=4, sz=3, na=2: [2, 5), a:'-', [5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultChop4") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append("-")
    segments.append(2, 4)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=1:1, t=1:1, l=4, sz=3, na=2: [2, 5), a:'-', [5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultChop5") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append("-")
    segments.append(2, 5)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=1:1, t=1:1, l=4, sz=3, na=2: [2, 5), a:'-', [5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultChop6") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append("-")
    segments.append(3, 4)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=1:1, t=1:1, l=4, sz=3, na=2: [2, 5), a:'-', [5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultMerge1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append(4, 8)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 8), s=0:0, u=0:0, t=0:0, l=6, sz=1, na=1: [2, 8) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultMerge2") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append(1, 8)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 8), s=0:0, u=0:0, t=0:0, l=6, sz=1, na=1: [2, 8) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultMerge3") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append(3, 5)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=0:0, t=0:0, l=3, sz=1, na=1: [2, 5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultMerge4") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append(2, 4)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=0:0, t=0:0, l=3, sz=1, na=1: [2, 5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultMerge5") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append(2, 5)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=0:0, t=0:0, l=3, sz=1, na=1: [2, 5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultMerge6") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(2, 5)
    segments.append(3, 4)
    assertEquals(segments.toStringPrep, "PlainSegmentBuilder{[2, 5), s=0:0, u=0:0, t=0:0, l=3, sz=1, na=1: [2, 5) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  /*
     Optimization tests, optimizer for backward compatibility
   */

  test("test_optimizerExtendPrev1") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("345")
    segments.append(6, 10)
    assertEquals(segments.toStringPrep, "OptimizedSegmentBuilder2{[0, 10), s=0:0, u=0:0, t=0:0, l=10, sz=1, na=1: [0, 10) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizerExtendPrev2") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34 ")
    segments.append(6, 10)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 10), s=1:1, u=1:1, t=1:1, l=10, sz=3, na=3: [0, 5), a:' ', [6, 10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizerExtendPrevNext") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34 5")
    segments.append(6, 10)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 5), a:' ', [5, 10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizerExtendPrevNextCollapse") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34 56")
    segments.append(7, 10)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 5), a:' ', [5, 10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizerExtendNext") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append(" 3456")
    segments.append(7, 10)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 3), a:' ', [3, 10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizerExtendNext1") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append(" 345")
    segments.append(6, 10)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 3), a:' ', [3, 10) }"
    )
  }

  test("test_optimizerIndent1") {
    val input     = "0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append(" 345")
    segments.append(6, 10)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 3), a:' ', [3, 10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  /*
   * Optimizer tests to ensure all optimizations are handled properly
   */

  test("test_optimizersIndent1None") {
    val input     = "  0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append("    ")
    segments.append(2, 12)
    assertEquals(segments.toStringPrep, "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=2, na=2: a:2x' ', [0, 12) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersSpacesNone") {
    val input     = "01234  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("    ")
    segments.append(7, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersSpacesLeft") {
    val input     = "01234  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("    ")
    segments.append(7, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 5), a:2x' ', [5, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersSpacesRight") {
    val input     = "01234  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("    ")
    segments.append(7, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersIndent1Left") {
    val input     = "  0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append("    ")
    segments.append(2, 12)
    assertEquals(segments.toStringPrep, "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=2, na=2: a:2x' ', [0, 12) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersIndent1Right") {
    val input     = "  0123456789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append("    ")
    segments.append(2, 12)
    assertEquals(segments.toStringPrep, "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=2, na=2: a:2x' ', [0, 12) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL1None") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n    ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL1Left") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n    ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL1Right") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n    ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL2None") {
    val input     = "01234\n\n 56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n\n   ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL2Left") {
    val input     = "01234\n\n 56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n\n   ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL2Right") {
    val input     = "01234\n\n 56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n\n   ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL3None") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34\n    ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL3Left") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34\n    ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL3LeftNonAscii") {
    val input     = "01234\n\u2026\u202656789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34\n\u2026\u2026\u2026\u2026")
    segments.append(8, 12)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 12), s=0:0, u=0:0, t=1:2, l=14, sz=3, na=3: [0, 6), 2x'\u2026', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizersEOL3Right") {
    val input     = "01234\n  56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 3)
    segments.append("34\n    ")
    segments.append(8, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizers1") {
    val input     = "01234 \n56789"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n  ")
    segments.append(7, 12)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 12), s=1:2, u=1:2, t=1:2, l=13, sz=4, na=4: [0, 5), [6, 7), a:2x' ', [7, 12) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizers2") {
    val input     = "01234 \n"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n")
    assertEquals(segments.toStringPrep, "OptimizedSegmentBuilder2{[0, 7), s=0:0, u=0:0, t=0:0, l=6, sz=2, na=2: [0, 5), [6, 7) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizers2a") {
    // this one causes text to be replaced with recovered EOL in the code
    val input     = "01234  \n"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append(" \n")
    assertEquals(segments.toStringPrep, "OptimizedSegmentBuilder2{[0, 8), s=0:0, u=0:0, t=0:0, l=7, sz=2, na=2: [0, 6), [7, 8) }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizers3") {
    val input     = "012340123401234"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("01234")
    assertEquals(
      segments.toStringPrep,
      escapeJavaString("OptimizedSegmentBuilder2{[0, 10), s=0:0, u=0:0, t=0:0, l=10, sz=1, na=1: [0, 10) }")
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_optimizers4") {
    val input     = "0123  \n  5678"
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer)

    segments.append(0, 5)
    segments.append("\n")
    segments.append(8, 13)
    assertEquals(
      segments.toStringPrep,
      "OptimizedSegmentBuilder2{[0, 13), s=0:0, u=0:0, t=0:0, l=11, sz=3, na=3: [0, 5), [6, 7), [8, 13) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
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
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer, F_TRACK_FIRST256)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) segments.append("    ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    )
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )

    assertEquals(segments.toString(sequence),
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
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer, F_TRACK_FIRST256)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) segments.append("  ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 30), s=0:0, u=0:0, t=0:0, l=28, sz=3, na=3: [0, 8), [9, 18), [19, 30) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7"
    )

    assertEquals(segments.toString(sequence),
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
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer, F_TRACK_FIRST256)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
//            if (!trim.isEmpty()) segments.append("  ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 23), s=0:0, u=0:0, t=0:0, l=22, sz=2, na=2: [0, 13), [14, 23) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(input), "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7")

    assertEquals(segments.toString(sequence),
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
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) segments.append("    ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    )
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )

    assertEquals(segments.toString(sequence),
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
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) segments.append("  ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 30), s=0:0, u=0:0, t=0:0, l=28, sz=3, na=3: [0, 8), [9, 18), [19, 30) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7"
    )

    assertEquals(segments.toString(sequence),
                 "" +
                   "  line 1\n" +
                   "  line 2\n" +
                   "\n" +
                   "  line 3\n" +
                   ""
    )
  }

  test("test_optimizersCompound3Anchors") {
    val input = "" +
      "line 1\n" +
      "line 2 \n" +
      "\n" +
      "line 3\n" +
      ""
    val sequence  = BasedSequence.of(input)
    val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT)
    val segments  = new OptimizedSegmentBuilder2(sequence, optimizer, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
//            if (!trim.isEmpty()) segments.append("  ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "OptimizedSegmentBuilder2{[0, 23), s=0:0, u=0:0, t=0:0, l=22, sz=2, na=2: [0, 13), [14, 23) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)

    assertEquals(segments.toStringWithRangesVisibleWhitespace(input), "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7")

    assertEquals(segments.toString(sequence),
                 "" +
                   "line 1\n" +
                   "line 2\n" +
                   "\n" +
                   "line 3\n" +
                   ""
    )
  }
}
