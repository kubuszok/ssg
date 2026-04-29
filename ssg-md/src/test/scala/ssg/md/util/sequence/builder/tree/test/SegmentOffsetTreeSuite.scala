/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package tree
package test

import ssg.md.Nullable
import ssg.md.util.misc.Utils.escapeJavaString
import ssg.md.util.sequence.{ BasedSequence, PositionAnchor, SegmentedSequenceFull }
import ssg.md.util.sequence.builder.ISegmentBuilder.{ F_INCLUDE_ANCHORS, F_TRACK_FIRST256 }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class SegmentOffsetTreeSuite extends munit.FunSuite {

  private def assertCharAt(sequence: BasedSequence, segments: SegmentBuilderBase[?], segTree: SegmentTree): Unit = {
    val sequenceFull  = SegmentedSequenceFull.create(sequence, segments)
    val segOffsetTree = segTree.getSegmentOffsetTree(sequence)

    // System.out.println(segments.toStringWithRangesVisibleWhitespace(sequence))
    // System.out.println(segTree.toString(sequence))
    // System.out.println(segOffsetTree.toString(sequence))

    val iMax = sequenceFull.length()
    var seg: Nullable[Segment] = Nullable.empty[Segment]
    var i = 0
    while (i < iMax) {
      val offset = sequenceFull.getIndexOffset(i)

      if (offset >= 0) {
        if (seg.isEmpty || seg.get.offsetNotInSegment(offset)) {
          seg = segOffsetTree.findSegmentByOffset(offset, sequence, seg)
          assert(
            seg.isDefined && (!seg.get.offsetNotInSegment(offset) || seg.get.pos == segOffsetTree.size - 1),
            s"offset: $offset seg: ${seg.getOrElse(null)}"
          )
//                    System.out.println("i=" + i + " pos=" + seg.get.pos + ", segOff=" + seg.get)
        }

        val actual = offset - seg.get.getStartOffset + seg.get.startIndex
        assertEquals(actual, i, s"i=$i offset=$offset seg=${seg.get} segStartIndex=${seg.get.startIndex}")
      }
      i += 1
    }

    seg = Nullable.empty[Segment]
    i = iMax - 1
    while (i >= 0) {
      val offset = sequenceFull.getIndexOffset(i)

      if (offset >= 0) {
        if (seg.isEmpty || seg.get.offsetNotInSegment(offset)) {
          seg = segOffsetTree.findSegmentByOffset(offset, sequence, seg)
          assert(
            seg.isDefined && (!seg.get.offsetNotInSegment(offset) || seg.get.pos == segOffsetTree.size - 1),
            s"offset: $offset seg: ${seg.getOrElse(null)}"
          )
//                    System.out.println("i=" + i + " pos=" + seg.get.pos + ", segOff=" + seg.get)
        }

        val actual = offset - seg.get.getStartOffset + seg.get.startIndex
        assertEquals(actual, i, s"i=$i offset=$offset seg=${seg.get} segStartIndex=${seg.get.startIndex}")
      }
      i -= 1
    }
  }

  test("test_build1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 0)
    segments.append(2, 5)
    segments.append(6, 9)
    segments.append(10, 10)
    assertEquals(
      segments.toStringPrep,
      "BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=6, sz=4, na=2: [0), [2, 5), [6, 9), [10) }"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(segTree.toString(sequence), "SegmentTree{aggr: {[3, 1:, 0:], [6, 4:] }, seg: { 0:[0), 1:[2, 5), 4:[6, 9), 7:[10) } }")
    assertCharAt(sequence, segments, segTree)
  }

  test("test_build2") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 0)
    segments.append(2, 5)
    segments.append("abcd")
    segments.append(6, 9)
    segments.append(10, 10)
    assertEquals(
      segments.toStringPrep,
      "BasedSegmentBuilder{[0, 10), s=0:0, u=1:4, t=1:4, l=10, sz=5, na=3: [0), [2, 5), a:'abcd', [6, 9), [10) }"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[3, 1:, 0:], [7, 4:], [10, 9:] }, seg: { 0:[0), 1:[2, 5), 4:a:'abcd', 9:[6, 9), 12:[10) } }"
    )
    assertCharAt(sequence, segments, segTree)
  }

  test("test_build1SegmentTree") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 0)
    segments.append(2, 5)
    segments.append(6, 9)
    segments.append(10, 10)
    assertEquals(
      segments.segmentBuilder.toStringPrep,
      "BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=6, sz=4, na=2: [0), [2, 5), [6, 9), [10) }"
    )

    val segTree = segments.toSequence.getSegmentTree
    assertEquals(segTree.toString(sequence), "SegmentTree{aggr: {[3, 1:, 0:], [6, 4:] }, seg: { 0:[0), 1:[2, 5), 4:[6, 9), 7:[10) } }")
    assertCharAt(sequence, segments.segmentBuilder, segTree)
  }

  test("test_build2SegmentTree") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = SequenceBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 0)
    segments.append(2, 5)
    segments.append("abcd")
    segments.append(6, 9)
    segments.append(10, 10)
    assertEquals(
      segments.segmentBuilder.toStringPrep,
      "BasedSegmentBuilder{[0, 10), s=0:0, u=1:4, t=1:4, l=10, sz=5, na=3: [0), [2, 5), a:'abcd', [6, 9), [10) }"
    )

    val segTree = segments.toSequence.getSegmentTree
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[3, 1:, 0:], [7, 4:], [10, 9:] }, seg: { 0:[0), 1:[2, 5), 4:a:'abcd', 9:[6, 9), 12:[10) } }"
    )
    assertCharAt(sequence, segments.segmentBuilder, segTree)
  }

  // ************************************************************************
  // NOTE: Segment building directly from SegmentTree data
  // ************************************************************************

  test("test_buildSegments1") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    val lines = sequence.splitListEOL(false)

    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) segments.append("    ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }

    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[2, 0:], [10, 1:], [11, 4:], [13, 7:], [21, 8:], [23, 11:], [25, 14:], [34, 15:] }, seg: { 0:a:2x' ', 1:[0, 8), 4:[9, 10), 7:a:2x' ', 8:[10, 18), 11:[19, 21), 14:a:2x' ', 15:[21, 30) } }"
    )

    val segments2 = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    segTree.addSegments(segments2, 0, segments.length, segments.startOffsetIfNeeded, segments.endOffsetIfNeeded, 0, segTree.size)

    assertEquals(
      escapeJavaString(segments2.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    )
    assertEquals(segments.toString(sequence).length, segments2.length)
    assertEquals(
      segments2.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )
    assertCharAt(sequence, segments, segTree)
  }

  test("test_buildSegments2") {
    val input = "" +
      "  line 1 \n" +
      "  line 2 \n" +
      "\n" +
      "  line 3\n" +
      ""
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    val lines = sequence.splitListEOL(false)

    lines.asScala.foreach { line =>
      val trim = line.trim()
      if (!trim.isEmpty) segments.append("    ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }

    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[2, 0:], [10, 1:], [11, 4:], [13, 7:], [21, 8:], [23, 11:], [25, 14:], [34, 15:] }, seg: { 0:a:2x' ', 1:[0, 8), 4:[9, 10), 7:a:2x' ', 8:[10, 18), 11:[19, 21), 14:a:2x' ', 15:[21, 30) } }"
    )

    val segments3 = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    val sequence1 = SegmentedSequenceFull.create(sequence, segments)
    val sequence2 = sequence1.subSequence(10, segments.length - 10)
    assertEquals(sequence2.toVisibleWhitespaceString(), "\\n    line 2\\n\\n ")
    sequence2.addSegments(segments3)
    assertEquals(
      escapeJavaString(segments3.toStringPrep),
      "BasedSegmentBuilder{[9, 21), s=2:3, u=2:3, t=2:3, l=14, sz=6, na=5: [9, 10), a:2x' ', [10, 18), [19, 21), a:' ', [21) }"
    )
    assertEquals(
      segments3.toStringWithRangesVisibleWhitespace(input),
      "\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7 \u27e6\u27e7"
    )

    val segments2 = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    val treeRange = segTree.getSegmentRange(10, segments.length - 10, 0, segTree.size, sequence, Nullable.empty[Segment])
    segTree.addSegments(segments2, treeRange)

    assertEquals(
      escapeJavaString(segments2.toStringPrep),
      "BasedSegmentBuilder{[9, 21), s=2:3, u=2:3, t=2:3, l=14, sz=6, na=5: [9, 10), a:2x' ', [10, 18), [19, 21), a:' ', [21) }"
    )
    assertEquals(segments2.toString(sequence).length, segments2.length)
    assertEquals(
      segments2.toStringWithRangesVisibleWhitespace(input),
      "\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7 \u27e6\u27e7"
    )
    assertCharAt(sequence, segments, segTree)
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
    val segments  = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)

    val lines = sequence.splitListEOL(false)
    lines.asScala.foreach { line =>
      val trim = line.trim()
//            if (!trim.isEmpty()) segments.append("  ")
      segments.append(trim.getSourceRange)
      segments.append("\n")
    }
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 23), s=0:0, u=0:0, t=0:0, l=22, sz=2, na=2: [0, 13), [14, 23) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(input), "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7")

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(segTree.toString(sequence), "SegmentTree{aggr: {[13, 0:], [22, 3:] }, seg: { 0:[0, 13), 3:[14, 23) } }")

    val segments3 = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    val sequence1 = SegmentedSequenceFull.create(sequence, segments)
    val sequence2 = sequence1.subSequence(5, segments.length - 5)
    assertEquals(sequence2.toVisibleWhitespaceString(), "1\\nline 2\\n\\nli")
    sequence2.addSegments(segments3)
    assertEquals(
      escapeJavaString(segments3.toStringPrep),
      "BasedSegmentBuilder{[5, 18), s=0:0, u=0:0, t=0:0, l=12, sz=2, na=2: [5, 13), [14, 18) }"
    )
    assertEquals(segments3.toStringWithRangesVisibleWhitespace(input), "\u27e61\\nline 2\u27e7\u27e6\\n\\nli\u27e7")

    val segments2 = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    val treeRange = segTree.getSegmentRange(5, segments.length - 5, 0, segTree.size, sequence, Nullable.empty[Segment])
    segTree.addSegments(segments2, treeRange)

    assertEquals(
      escapeJavaString(segments2.toStringPrep),
      "BasedSegmentBuilder{[5, 18), s=0:0, u=0:0, t=0:0, l=12, sz=2, na=2: [5, 13), [14, 18) }"
    )
    assertEquals(segments2.toString(sequence).length, segments2.length)
    assertEquals(segments2.toStringWithRangesVisibleWhitespace(input), "\u27e61\\nline 2\u27e7\u27e6\\n\\nli\u27e7")
    assertCharAt(sequence, segments, segTree)
  }
}
