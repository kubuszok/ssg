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
import ssg.md.util.sequence.{ BasedSequence, SegmentedSequenceFull }
import ssg.md.util.sequence.builder.ISegmentBuilder.{ F_INCLUDE_ANCHORS, F_TRACK_FIRST256 }

import java.util.Arrays

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class SegmentTreeSuite extends munit.FunSuite {

  private def loop(start: Int, end: Int, span: Int, param: Int, consumer: (Int, Int) => Unit): Unit = {
    val iMaxStart = start + span
    val iMinEnd   = end - span

    if (iMaxStart >= iMinEnd) {
      var i = start
      while (i < end) {
        consumer(param, i)
        i += 1
      }
    } else {
      var i = start
      while (i < iMaxStart) {
        consumer(param, i)
        i += 1
      }

      i = iMinEnd
      while (i < end) {
        consumer(param, i)
        i += 1
      }
    }
  }

  private def loopSizes(consumer: (Int, Int) => Unit): Unit = {
    loop(0, 16, 8, 0, consumer)
    loop(16, 256, 8, 1, consumer)
    loop(256, 65536, 8, 2, consumer)
    loop(65536, 65536 * 256, 8, 3, consumer)
    loop(65536 * 256, SegmentTree.MAX_VALUE, 8, 4, consumer)
  }

  private def loopSizesShort(consumer: (Int, Int) => Unit): Unit = {
    loop(0, 16, 8, 0, consumer)
    loop(16, 256, 8, 1, consumer)
    loop(256, 65536, 8, 2, consumer)
    loop(65536, 65536 * 256, 8, 3, consumer)
    loop(65536 * 256, SegmentTree.MAX_VALUE, 8, 4, consumer)
  }

  private def loopEnd(startOffset: Int, consumer: (Int, Int) => Unit): Unit = {
    loop(startOffset + 0, startOffset + 16, 8, 0, consumer)
    loop(startOffset + 16, startOffset + 256, 8, 1, consumer)
    loop(startOffset + 256, startOffset + 65536, 8, 2, consumer)
    loop(startOffset + 65536, startOffset + 65536 * 256, 8, 3, consumer)
    loop(startOffset + 65536 * 256, SegmentTree.MAX_VALUE, 8, 4, consumer)
  }

  @scala.annotation.nowarn("msg=unused private member")
  private def loopEndShort(startOffset: Int, consumer: (Int, Int) => Unit): Unit = {
    loop(startOffset + 0, startOffset + 16, 8, 0, consumer)
    loop(startOffset + 16, startOffset + 256, 8, 1, consumer)
    loop(startOffset + 256, startOffset + 65536, 8, 2, consumer)
    loop(startOffset + 65536, startOffset + 65536 * 8, 8, 3, consumer)
  }

  private def assertCharAt(sequence: BasedSequence, segments: PlainSegmentBuilder, segTree: SegmentTree): Unit = {
    val sequenceFull = SegmentedSequenceFull.create(sequence, segments)
    val iMax         = sequenceFull.length()
    // System.out.println(segTree.toString(sequence))
    var seg: Nullable[Segment] = Nullable.empty[Segment]

    var i = 0
    while (i < iMax) {
      if (seg.isEmpty || seg.get.notInSegment(i)) {
        seg = segTree.findSegment(i, sequence, seg)
        assert(seg.isDefined)
        // System.out.println("i: " + i + " pos: " + seg.get.pos + ", seg: " + seg.get)
      }

      val expected = Character.toString(sequenceFull.charAt(i))
      val actual   = Character.toString(seg.get.charAt(i))
      assertEquals(actual, expected, s"i: $i")
      i += 1
    }

    seg = Nullable.empty[Segment]
    i = iMax - 1
    while (i >= 0) {
      if (seg.isEmpty || seg.get.notInSegment(i)) {
        seg = segTree.findSegment(i, sequence, seg)
        assert(seg.isDefined, s"i: $i")
        // System.out.println("i: " + i + " pos: " + seg.get.pos + ", seg: " + seg.get)
      }

      val expected = Character.toString(sequenceFull.charAt(i))
      val actual   = Character.toString(seg.get.charAt(i))
      assertEquals(actual, expected, s"i: $i")
      i -= 1
    }
  }

  test("test_flags") {
    assertEquals(SegmentTree.F_ANCHOR_FLAGS, 0xe0000000)
  }

  test("test_anchorOffset") {
    assertEquals(SegmentTree.getAnchorOffset(0x00000000), 0)
    assertEquals(SegmentTree.getAnchorOffset(0x20000000), 1)
    assertEquals(SegmentTree.getAnchorOffset(0x40000000), 2)
    assertEquals(SegmentTree.getAnchorOffset(0x60000000), 3)
    assertEquals(SegmentTree.getAnchorOffset(0x80000000), 4)
    assertEquals(SegmentTree.getAnchorOffset(0xa0000000), 5)
    assertEquals(SegmentTree.getAnchorOffset(0xc0000000), 6)
    assertEquals(SegmentTree.getAnchorOffset(0xe0000000), 7)
  }

  test("test_aggrLength") {
    loopSizes { (b, i) =>
      val aggrSegData1 = Array(i, -1, -2, -4)
      val aggrSegData2 = Array(-1, -2, i, -3)
      assertEquals(SegmentTree.aggrLength(0, aggrSegData1), i.toInt, s"i: $i")
      assertEquals(SegmentTree.aggrLength(1, aggrSegData2), i.toInt, s"i: $i")
    }
  }

  test("test_byteOffset") {
    loopSizesShort { (b, i) =>
      var j = 0
      while (j < 8) {
        val aggrSegData1 = Array(-1, i | (j << 29), -2, -3)
        val aggrSegData2 = Array(-1, -2, -3, i | (j << 29))
        assertEquals(SegmentTree.byteOffset(0, aggrSegData1), i.toInt, s"i: $i j: $j")
        assertEquals(SegmentTree.byteOffset(1, aggrSegData2), i.toInt, s"i: $i j: $j")
        j += 1
      }
    }
  }

  test("test_hasPreviousAnchor") {
    loopSizesShort { (b, i) =>
      var j = 0
      while (j < 8) {
        val aggrSegData1 = Array(-1, i | (j << 29), -2, -3)
        val aggrSegData2 = Array(-1, -2, -3, i | (j << 29))
        assertEquals(SegmentTree.hasPreviousAnchor(0, aggrSegData1), j > 0, s"i: $i j: $j")
        assertEquals(SegmentTree.hasPreviousAnchor(1, aggrSegData2), j > 0, s"i: $i j: $j")
        j += 1
      }
    }
  }

  test("test_previousAnchorOffset") {
    loopSizesShort { (b, i) =>
      var j = 0
      while (j < 8) {
        val aggrSegData1 = Array(-1, i | (j << 29), -2, -3)
        val aggrSegData2 = Array(-1, -2, -3, i | (j << 29))
        assertEquals(SegmentTree.previousAnchorOffset(0, aggrSegData1), i - j, s"i: $i j: $j")
        assertEquals(SegmentTree.previousAnchorOffset(1, aggrSegData2), i - j, s"i: $i j: $j")
        j += 1
      }
    }
  }

  test("test_findSegSegIndex1") {
    loopSizes { (b, i) =>
      if (i > 0) {
        val aggrSegData1 = Array(i, -1, -2, -3)
        val aggrSegData2 = Array(-1, -2, i, -3)

        loopEnd(
          i,
          { (bj, j) =>
            assertEquals(
              SegmentTree.findSegmentPos(j, aggrSegData1, 0, 1),
              if (j >= i) Nullable.empty[SegmentTreePos] else Nullable(SegmentTreePos(0, 0, 0)),
              s"i: $i j: $j"
            )
            assertEquals(
              SegmentTree.findSegmentPos(j, aggrSegData2, 1, 1),
              if (j >= i) Nullable.empty[SegmentTreePos] else Nullable(SegmentTreePos(1, 0, 0)),
              s"i: $i j: $j"
            )
          }
        )
      }
    }
  }

  test("test_findSegSegIndex2") {
    loopSizesShort { (b, i) =>
      if (i > 0) {
        val aggrSegData1 = Array(i, -1, i + 1000, -3, -4, -5)
        val aggrSegData2 = Array(-1, -2, i, -3, i + 1000, -4)

        loopEnd(
          i,
          { (bj, j) =>
            assertEquals(
              SegmentTree.findSegmentPos(j, aggrSegData1, 0, 2),
              if (j >= i + 1000) Nullable.empty[SegmentTreePos] else Nullable(SegmentTreePos(if (j >= i) 1 else 0, if (j >= i) i else 0, 0)),
              s"i: $i j: $j"
            )
            assertEquals(
              SegmentTree.findSegmentPos(j, aggrSegData2, 1, 3),
              if (j >= i + 1000) Nullable.empty[SegmentTreePos] else Nullable(SegmentTreePos(if (j >= i) 2 else 1, if (j >= i) i else 0, 0)),
              s"i: $i j: $j"
            )
          }
        )
      }
    }
  }

  test("test_findSegSegIndexN") {
    loopSizesShort { (b, i) =>
      if (i > 0) {
        var k = 2
        while (k < 16) {
          val aggrSegData1 = new Array[Int](k * 2 + 4)
          val aggrSegData2 = new Array[Int](k * 2 + 4)

          Arrays.fill(aggrSegData1, -1)
          Arrays.fill(aggrSegData2, -1)

          var l = 0
          while (l < k) {
            aggrSegData1(l * 2) = i + l * 1000
            aggrSegData2(l * 2 + 2) = i + l * 1000
            l += 1
          }

          val finalK = k
          loopEnd(
            i,
            { (bj, j) =>
              val segment    = Array(0)
              val startIndex = Array(0)

              l = 0
              var done = false
              while (l < finalK && !done) {
                segment(0) = l
                startIndex(0) = i + Math.max(0, l - 1) * 1000
                if (j >= startIndex(0) && j < i + l * 1000) done = true
                else {
                  segment(0) = l + 1
                  l += 1
                }
              }

              assertEquals(
                SegmentTree.findSegmentPos(j, aggrSegData1, 0, finalK),
                if (segment(0) >= finalK) Nullable.empty[SegmentTreePos] else Nullable(SegmentTreePos(segment(0), startIndex(0), 0)),
                s"k: $finalK i: $i j: $j s:${segment(0)}"
              )
              assertEquals(
                SegmentTree.findSegmentPos(j, aggrSegData2, 1, finalK + 1),
                if (segment(0) >= finalK) Nullable.empty[SegmentTreePos] else Nullable(SegmentTreePos(segment(0) + 1, startIndex(0), 0)),
                s"k: $finalK i: $i j: $j s:${segment(0)}"
              )
            }
          )
          k += 1
        }
      }
    }
  }

  test("test_getPrevAnchor") {
    // TEST: need test for this
  }

  test("test_build1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 0)
    segments.append(2, 5)
    segments.append(6, 9)
    segments.append(10, 10)
    assertEquals(
      segments.toStringPrep,
      "PlainSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=6, sz=4, na=2: [0), [2, 5), [6, 9), [10) }"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(segTree.toString(sequence), "SegmentTree{aggr: {[3, 1:, 0:], [6, 4:] }, seg: { 0:[0), 1:[2, 5), 4:[6, 9), 7:[10) } }")
    assertCharAt(sequence, segments, segTree)
  }

  test("test_build2") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append(0, 0)
    segments.append(2, 5)
    segments.append("abcd")
    segments.append(6, 9)
    segments.append(10, 10)
    assertEquals(
      segments.toStringPrep,
      "PlainSegmentBuilder{[0, 10), s=0:0, u=1:4, t=1:4, l=10, sz=5, na=3: [0), [2, 5), a:'abcd', [6, 9), [10) }"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[3, 1:, 0:], [7, 4:], [10, 9:] }, seg: { 0:[0), 1:[2, 5), 4:a:'abcd', 9:[6, 9), 12:[10) } }"
    )
    assertCharAt(sequence, segments, segTree)
  }

  test("test_buildSubSequence") {
    val input = "0123456789"
    // val expected = "> 0123456789\n"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append("> ")
    segments.append(0, 10)
    segments.append("\n")
    assertEquals(
      segments.toStringPrep,
      "PlainSegmentBuilder{[0, 10), s=0:1, u=2:3, t=2:3, l=13, sz=4, na=3: a:'> ', [0, 10), a:'\\n', [10) }"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[2, 0:], [12, 3:], [13, 6:] }, seg: { 0:a:'> ', 3:[0, 10), 6:a:'\\n', 7:[10) } }"
    )
    assertCharAt(sequence, segments, segTree)

    val segRange = segTree.getSegmentRange(0, 12, 0, segTree.size, sequence, Nullable.empty[Segment])
    assertEquals(
      segRange.toString,
      "SegmentTreeRange{startIndex=0, endIndex=12, startOffset=0, endOffset=10, startPos=0, endPos=2, length=12}"
    )

    val builder = sequence.getBuilder[SequenceBuilder]
    segTree.addSegments(
      builder.segmentBuilder,
      segRange.startIndex,
      segRange.startIndex + segRange.length,
      segRange.startOffset,
      segRange.endOffset,
      segRange.startPos,
      segRange.endPos
    )
    assertEquals(builder.toStringWithRanges(true), "\u27e6\u27e7> \u27e60123456789\u27e7")
  }

  test("test_buildSubSequence2") {
    val input = "0123456789"
    // val expected = "> 0123456789\n"
    val sequence = BasedSequence.of(input)
    val segments = PlainSegmentBuilder.emptyBuilder(F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

    segments.append("> ")
    segments.append(0, 10)
    segments.append("\n")
    assertEquals(
      segments.toStringPrep,
      "PlainSegmentBuilder{[0, 10), s=0:1, u=2:3, t=2:3, l=13, sz=4, na=3: a:'> ', [0, 10), a:'\\n', [10) }"
    )

    val segTree = SegmentTree.build(segments.getSegments, segments.getText)
    assertEquals(
      segTree.toString(sequence),
      "SegmentTree{aggr: {[2, 0:], [12, 3:], [13, 6:] }, seg: { 0:a:'> ', 3:[0, 10), 6:a:'\\n', 7:[10) } }"
    )
    assertCharAt(sequence, segments, segTree)

    val segRange = segTree.getSegmentRange(0, 13, 0, segTree.size, sequence, Nullable.empty[Segment])
    assertEquals(
      segRange.toString,
      "SegmentTreeRange{startIndex=0, endIndex=13, startOffset=0, endOffset=0, startPos=0, endPos=3, length=13}"
    )

    val builder = sequence.getBuilder[SequenceBuilder]
    segTree.addSegments(
      builder.segmentBuilder,
      segRange.startIndex,
      segRange.startIndex + segRange.length,
      segRange.startOffset,
      segRange.endOffset,
      segRange.startPos,
      segRange.endPos
    )
    assertEquals(builder.toStringWithRanges(true), "\u27e6\u27e7> \u27e60123456789\u27e7\\n\u27e6\u27e7")
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
  }
}
