/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package test

import ssg.md.util.misc.Utils.escapeJavaString
import ssg.md.util.sequence.{ BasedSequence, PositionAnchor, Range }
import ssg.md.util.sequence.builder.ISegmentBuilder.{ F_INCLUDE_ANCHORS, F_TRACK_FIRST256 }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final class BasedSegmentBuilderSuite extends munit.FunSuite {

  test("test_basicBuildEmpty") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    assertEquals(escapeJavaString(segments.toStringPrep), "BasedSegmentBuilder{NULL, s=0:0, u=0:0, t=0:0, l=0, sz=0, na=0 }")
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "")
    assertEquals(segments.toString(sequence), "")
  }

  test("test_basicEmptyDefaults") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 0)
    segments.append(sequence.length(), sequence.length())
    assertEquals(segments.length, 0)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=0, sz=2, na=0: [0), [10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_basicEmptyNoAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256)
    segments.append(0, 0)
    segments.append(sequence.length(), sequence.length())
    assertEquals(segments.length, 0)
    assertEquals(escapeJavaString(segments.toStringPrep), "BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=0, sz=0, na=0 }")
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_basicEmptyAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS)
    segments.append(0, 0)
    segments.append(sequence.length(), sequence.length())
    assertEquals(segments.length, 0)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=0, sz=2, na=0: [0), [10) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_basicPrefixDefault") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append("  ")
    segments.append(0, 4)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 4), s=1:2, u=1:2, t=1:2, l=6, sz=2, na=2: a:2x' ', [0, 4) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "  \u27e60123\u27e7")
    assertEquals(segments.toString(sequence), "  0123")
  }

  test("test_appendRange1") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 4)
    assertEquals(escapeJavaString(segments.toStringPrep), "BasedSegmentBuilder{[0, 4), s=0:0, u=0:0, t=0:0, l=4, sz=1, na=1: [0, 4) }")
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 4))
  }

  test("test_appendRangeNonOverlapping") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 4)
    segments.append(6, 7)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=5, sz=2, na=2: [0, 4), [6, 7) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123\u27e7\u27e66\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 4) + input.substring(6, 7))
  }

  test("test_appendRangeOverlapping") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 5)
    segments.append(3, 7)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=1:2, t=1:2, l=9, sz=3, na=3: [0, 5), a:'34', [5, 7) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e734\u27e656\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 5) + input.substring(3, 7))
  }

  test("test_appendRangeOverlappingOverString") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 5)
    segments.append("abc")
    segments.append(3, 7)
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=1:5, t=1:5, l=12, sz=3, na=3: [0, 5), a:'abc34', [5, 7) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e7abc34\u27e656\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 5) + "abc" + input.substring(3, 7))
  }

  test("test_appendRangeStrings") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 5)
    segments.append("abc")
    segments.append("def")
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 5), s=0:0, u=1:6, t=1:6, l=11, sz=3, na=2: [0, 5), a:'abcdef', [5) }"
    )
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e7abcdef\u27e6\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 5) + "abcdef")
  }

  test("test_appendRangeTouching") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
    segments.append(0, 5)
    segments.append(5, 7)
    assertEquals(escapeJavaString(segments.toStringPrep), "BasedSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=7, sz=1, na=1: [0, 7) }")
    assertEquals(segments.toString(sequence).length, segments.length)
    assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123456\u27e7")
    assertEquals(segments.toString(sequence), input.substring(0, 7))
  }

  // handleOverlap tests with anchors
  test("test_handleOverlapDefaultChop1") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append("-"); segments.append(4, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:2, t=1:2, l=8, sz=3, na=3: [2, 5), a:'-4', [5, 8) }"
    );
    assertEquals(segments.toString(sequence), "234-4567"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop2") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append("-"); segments.append(1, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:5, t=1:5, l=11, sz=3, na=3: [2, 5), a:'-1234', [5, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop3") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append("-"); segments.append(3, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:3, t=1:3, l=6, sz=3, na=2: [2, 5), a:'-34', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop4") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append("-"); segments.append(2, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:3, t=1:3, l=6, sz=3, na=2: [2, 5), a:'-23', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop5") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append("-"); segments.append(2, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:4, t=1:4, l=7, sz=3, na=2: [2, 5), a:'-234', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop6") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append("-"); segments.append(3, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:2, t=1:2, l=5, sz=3, na=2: [2, 5), a:'-3', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultFromBefore") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 1);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:1, t=1:1, l=5, sz=3, na=2: [2, 6), a:'0', [6) }"
    );
    assertEquals(segments.toStringChars(), "23450"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBefore0") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 2);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:2, t=1:2, l=6, sz=3, na=2: [2, 6), a:'01', [6) }"
    );
    assertEquals(segments.toStringChars(), "234501"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeIn") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 3);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:3, t=1:3, l=7, sz=3, na=2: [2, 6), a:'012', [6) }"
    );
    assertEquals(segments.toStringChars(), "2345012"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInLess1") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:5, t=1:5, l=9, sz=3, na=2: [2, 6), a:'01234', [6) }"
    );
    assertEquals(segments.toStringChars(), "234501234"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInLess0") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 6);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:6, t=1:6, l=10, sz=3, na=2: [2, 6), a:'012345', [6) }"
    );
    assertEquals(segments.toStringChars(), "2345012345"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInOver1") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 7);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 7), s=0:0, u=1:6, t=1:6, l=11, sz=3, na=3: [2, 6), a:'012345', [6, 7) }"
    );
    assertEquals(segments.toStringChars(), "23450123456"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInOver2") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(0, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:6, t=1:6, l=12, sz=3, na=3: [2, 6), a:'012345', [6, 8) }"
    );
    assertEquals(segments.toStringChars(), "234501234567"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultIn0By1") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(2, 3);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:1, t=1:1, l=5, sz=3, na=2: [2, 6), a:'2', [6) }"
    );
    assertEquals(segments.toStringChars(), "23452"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultIn0By2") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 6);
    segments.append(2, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:2, t=1:2, l=6, sz=3, na=2: [2, 6), a:'23', [6) }"
    );
    assertEquals(segments.toStringChars(), "234523"); assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapLoop") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    var s        = 0
    while (s < input.length) {
      var e = s
      while (e < input.length) {
        val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256)
        segments.append(2, 6)
        segments.append(s, e)
        val expected = input.substring(2, 6) + input.substring(s, e)
        assertEquals(segments.toStringChars(), expected, s"$s,$e")
        assertEquals(segments.length, expected.length)
        e += 1
      }
      s += 1
    }
  }

  // handleOverlapDefaultMerge tests with anchors
  test("test_handleOverlapDefaultMerge1") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append(4, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:1, t=1:1, l=7, sz=3, na=3: [2, 5), a:'4', [5, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge2") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append(1, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:4, t=1:4, l=10, sz=3, na=3: [2, 5), a:'1234', [5, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge3") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append(3, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:2, t=1:2, l=5, sz=3, na=2: [2, 5), a:'34', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge4") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append(2, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:2, t=1:2, l=5, sz=3, na=2: [2, 5), a:'23', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge5") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append(2, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:3, t=1:3, l=6, sz=3, na=2: [2, 5), a:'234', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge6") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(2, 5);
    segments.append(3, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:1, t=1:1, l=4, sz=3, na=2: [2, 5), a:'3', [5) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  // No Anchors variants
  test("test_appendRange1NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(0, 4);
    assertEquals(escapeJavaString(segments.toStringPrep), "BasedSegmentBuilder{[0, 4), s=0:0, u=0:0, t=0:0, l=4, sz=1, na=1: [0, 4) }");
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123\u27e7");
    assertEquals(segments.toString(sequence), input.substring(0, 4))
  }
  test("test_appendRangeNonOverlappingNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(0, 4); segments.append(6, 7);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=5, sz=2, na=2: [0, 4), [6, 7) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123\u27e7\u27e66\u27e7");
    assertEquals(segments.toString(sequence), input.substring(0, 4) + input.substring(6, 7))
  }
  test("test_appendRangeOverlappingNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(0, 5); segments.append(3, 7);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=1:2, t=1:2, l=9, sz=3, na=3: [0, 5), a:'34', [5, 7) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e734\u27e656\u27e7");
    assertEquals(segments.toString(sequence), input.substring(0, 5) + input.substring(3, 7))
  }
  test("test_appendRangeOverlappingOverStringNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(0, 5); segments.append("abc");
    segments.append(3, 7);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=1:5, t=1:5, l=12, sz=3, na=3: [0, 5), a:'abc34', [5, 7) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e7abc34\u27e656\u27e7");
    assertEquals(segments.toString(sequence), input.substring(0, 5) + "abc" + input.substring(3, 7))
  }
  test("test_appendRangeStringsNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(0, 5); segments.append("abc");
    segments.append("def");
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 5), s=0:0, u=1:6, t=1:6, l=11, sz=2, na=2: [0, 5), a:'abcdef' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e601234\u27e7abcdef");
    assertEquals(segments.toString(sequence), input.substring(0, 5) + "abcdef")
  }
  test("test_appendRangeTouchingNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(0, 5); segments.append(5, 7);
    assertEquals(escapeJavaString(segments.toStringPrep), "BasedSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=7, sz=1, na=1: [0, 7) }");
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(sequence), "\u27e60123456\u27e7");
    assertEquals(segments.toString(sequence), input.substring(0, 7))
  }

  // handleOverlap NoAnchors tests
  test("test_handleOverlapDefaultChop1NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append("-");
    segments.append(4, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:2, t=1:2, l=8, sz=3, na=3: [2, 5), a:'-4', [5, 8) }"
    );
    assertEquals(segments.toString(sequence), "234-4567"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop2NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append("-");
    segments.append(1, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:5, t=1:5, l=11, sz=3, na=3: [2, 5), a:'-1234', [5, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop3NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append("-");
    segments.append(3, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:3, t=1:3, l=6, sz=2, na=2: [2, 5), a:'-34' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop4NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append("-");
    segments.append(2, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:3, t=1:3, l=6, sz=2, na=2: [2, 5), a:'-23' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop5NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append("-");
    segments.append(2, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:4, t=1:4, l=7, sz=2, na=2: [2, 5), a:'-234' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultChop6NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append("-");
    segments.append(3, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:2, t=1:2, l=5, sz=2, na=2: [2, 5), a:'-3' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapDefaultFromBeforeNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 1);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:1, t=1:1, l=5, sz=2, na=2: [2, 6), a:'0' }"
    );
    assertEquals(segments.toStringChars(), "23450"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBefore0NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 2);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:2, t=1:2, l=6, sz=2, na=2: [2, 6), a:'01' }"
    );
    assertEquals(segments.toStringChars(), "234501"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 3);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:3, t=1:3, l=7, sz=2, na=2: [2, 6), a:'012' }"
    );
    assertEquals(segments.toStringChars(), "2345012"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInLess1NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:5, t=1:5, l=9, sz=2, na=2: [2, 6), a:'01234' }"
    );
    assertEquals(segments.toStringChars(), "234501234"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInLess0NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 6);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:6, t=1:6, l=10, sz=2, na=2: [2, 6), a:'012345' }"
    );
    assertEquals(segments.toStringChars(), "2345012345"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInOver1NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 7);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 7), s=0:0, u=1:6, t=1:6, l=11, sz=3, na=3: [2, 6), a:'012345', [6, 7) }"
    );
    assertEquals(segments.toStringChars(), "23450123456"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultFromBeforeInOver2NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(0, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:6, t=1:6, l=12, sz=3, na=3: [2, 6), a:'012345', [6, 8) }"
    );
    assertEquals(segments.toStringChars(), "234501234567"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultIn0By1NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(2, 3);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:1, t=1:1, l=5, sz=2, na=2: [2, 6), a:'2' }"
    );
    assertEquals(segments.toStringChars(), "23452"); assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultIn0By2NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 6); segments.append(2, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 6), s=0:0, u=1:2, t=1:2, l=6, sz=2, na=2: [2, 6), a:'23' }"
    );
    assertEquals(segments.toStringChars(), "234523"); assertEquals(segments.toString(sequence).length, segments.length)
  }

  test("test_handleOverlapLoopNoAnchors") {
    val input    = "0123456789"
    val sequence = BasedSequence.of(input)
    var s        = 0
    while (s < input.length) {
      var e = s
      while (e < input.length) {
        val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256)
        segments.append(2, 6)
        segments.append(s, e)
        val expected = input.substring(2, 6) + input.substring(s, e)
        assertEquals(segments.toStringChars(), expected, s"$s,$e")
        assertEquals(segments.length, expected.length)
        e += 1
      }
      s += 1
    }
  }

  // handleOverlapDefaultMerge NoAnchors tests
  test("test_handleOverlapDefaultMerge1NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append(4, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:1, t=1:1, l=7, sz=3, na=3: [2, 5), a:'4', [5, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge2NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append(1, 8);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 8), s=0:0, u=1:4, t=1:4, l=10, sz=3, na=3: [2, 5), a:'1234', [5, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge3NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append(3, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:2, t=1:2, l=5, sz=2, na=2: [2, 5), a:'34' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge4NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append(2, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:2, t=1:2, l=5, sz=2, na=2: [2, 5), a:'23' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge5NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append(2, 5);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:3, t=1:3, l=6, sz=2, na=2: [2, 5), a:'234' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_handleOverlapDefaultMerge6NoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(2, 5); segments.append(3, 4);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[2, 5), s=0:0, u=1:1, t=1:1, l=4, sz=2, na=2: [2, 5), a:'3' }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  // Optimizer tests
  test("test_optimizerExtendPrev1") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("345"); segments.append(6, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=10, sz=1, na=1: [0, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizerExtendPrev2") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34 "); segments.append(6, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=1:1, u=1:1, t=1:1, l=10, sz=3, na=3: [0, 5), a:' ', [6, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizerExtendPrevNext") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34 5"); segments.append(6, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 5), a:' ', [5, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizerExtendPrevNextCollapse") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34 56"); segments.append(7, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 5), a:' ', [5, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizerExtendNext") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append(" 3456"); segments.append(7, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 3), a:' ', [3, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizerExtendNext1") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append(" 345"); segments.append(6, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 3), a:' ', [3, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizerIndent1") {
    val input    = "0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append(" 345"); segments.append(6, 10);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 10), s=1:1, u=1:1, t=1:1, l=11, sz=3, na=3: [0, 3), a:' ', [3, 10) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersIndent1None") {
    val input    = "  0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append("    "); segments.append(2, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=2, na=2: a:2x' ', [0, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersSpacesNone") {
    val input    = "01234  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("    "); segments.append(7, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersSpacesLeft") {
    val input    = "01234  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("    "); segments.append(7, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 5), a:2x' ', [5, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersSpacesRight") {
    val input    = "01234  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("    "); segments.append(7, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersIndent1Left") {
    val input    = "  0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append("    "); segments.append(2, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=2, na=2: a:2x' ', [0, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersIndent1Right") {
    val input    = "  0123456789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append("    "); segments.append(2, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=2, na=2: a:2x' ', [0, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL1None") {
    val input    = "01234\n  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n    "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL1Left") {
    val input    = "01234\n  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n    "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL1Right") {
    val input    = "01234\n  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n    "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL2None") {
    val input    = "01234\n\n 56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n\n   "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL2Left") {
    val input    = "01234\n\n 56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n\n   "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL2Right") {
    val input    = "01234\n\n 56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n\n   "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 7), a:2x' ', [7, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL3None") {
    val input    = "01234\n  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34\n    "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL3Left") {
    val input    = "01234\n  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34\n    "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL3LeftNonAscii") {
    val input    = "01234\n\u2026\u202656789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.PREVIOUS);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34\n\u2026\u2026\u2026\u2026");
    segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=0:0, u=0:0, t=1:2, l=14, sz=3, na=3: [0, 6), 2x'\u2026', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizersEOL3Right") {
    val input    = "01234\n  56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 3); segments.append("34\n    "); segments.append(8, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=14, sz=3, na=3: [0, 6), a:2x' ', [6, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizers1") {
    val input    = "01234 \n56789"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n  "); segments.append(7, 12);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 12), s=1:2, u=1:2, t=1:2, l=13, sz=4, na=4: [0, 5), [6, 7), a:2x' ', [7, 12) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizers2") {
    val input    = "01234 \n"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n");
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 7), s=0:0, u=0:0, t=0:0, l=6, sz=2, na=2: [0, 5), [6, 7) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizers2a") {
    val input    = "01234  \n"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append(" \n");
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 8), s=0:0, u=0:0, t=0:0, l=7, sz=2, na=2: [0, 6), [7, 8) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizers3") {
    val input    = "012340123401234"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("01234");
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=10, sz=1, na=1: [0, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }
  test("test_optimizers4") {
    val input    = "0123  \n  5678"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.NEXT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(0, 5); segments.append("\n"); segments.append(8, 13);
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 13), s=0:0, u=0:0, t=0:0, l=11, sz=3, na=3: [0, 5), [6, 7), [8, 13) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length)
  }

  // Compound tests - no anchors, no optimizer
  test("test_optimizersCompoundNoAnchors1") {
    val input = "  line 1 \n  line 2 \n\n  line 3\n"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256);
    sequence.splitListEOL(false).asScala.foreach { line =>
      val trim = line.trim(); if (!trim.isEmpty) segments.append("    "); segments.append(trim.getSourceRange); segments.append("\n")
    };
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length);
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    );
    assertEquals(segments.toString(sequence), "    line 1\n    line 2\n\n    line 3\n")
  }
  test("test_optimizersCompoundNoAnchors2") {
    val input = "  line 1 \n  line 2 \n\n  line 3\n"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256);
    sequence.splitListEOL(false).asScala.foreach { line =>
      val trim = line.trim(); if (!trim.isEmpty) segments.append("  "); segments.append(trim.getSourceRange); segments.append("\n")
    };
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=0:0, u=0:0, t=0:0, l=28, sz=3, na=3: [0, 8), [9, 18), [19, 30) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length);
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7"
    );
    assertEquals(segments.toString(sequence), "  line 1\n  line 2\n\n  line 3\n")
  }
  test("test_optimizersCompoundNoAnchors3") {
    val input = "line 1\nline 2 \n\nline 3\n"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256);
    sequence.splitListEOL(false).asScala.foreach { line =>
      val trim = line.trim(); segments.append(trim.getSourceRange); segments.append("\n")
    };
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 23), s=0:0, u=0:0, t=0:0, l=22, sz=2, na=2: [0, 13), [14, 23) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(input), "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7");
    assertEquals(segments.toString(sequence), "line 1\nline 2\n\nline 3\n")
  }

  // Compound tests - with anchors, no optimizer
  test("test_optimizersCompoundAnchors1") {
    val input = "  line 1 \n  line 2 \n\n  line 3\n"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS);
    sequence.splitListEOL(false).asScala.foreach { line =>
      val trim = line.trim(); if (!trim.isEmpty) segments.append("    "); segments.append(trim.getSourceRange); segments.append("\n")
    };
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=3:6, u=3:6, t=3:6, l=34, sz=8, na=8: a:2x' ', [0, 8), [9, 10), a:2x' ', [10, 18), [19, 21), a:2x' ', [21, 30) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length);
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "  \u27e6  line 1\u27e7\u27e6\\n\u27e7  \u27e6  line 2\u27e7\u27e6\\n\\n\u27e7  \u27e6  line 3\\n\u27e7"
    );
    assertEquals(segments.toString(sequence), "    line 1\n    line 2\n\n    line 3\n")
  }
  test("test_optimizersCompoundAnchors2") {
    val input = "  line 1 \n  line 2 \n\n  line 3\n"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS);
    sequence.splitListEOL(false).asScala.foreach { line =>
      val trim = line.trim(); if (!trim.isEmpty) segments.append("  "); segments.append(trim.getSourceRange); segments.append("\n")
    };
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 30), s=0:0, u=0:0, t=0:0, l=28, sz=3, na=3: [0, 8), [9, 18), [19, 30) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length);
    assertEquals(
      segments.toStringWithRangesVisibleWhitespace(input),
      "\u27e6  line 1\u27e7\u27e6\\n  line 2\u27e7\u27e6\\n\\n  line 3\\n\u27e7"
    );
    assertEquals(segments.toString(sequence), "  line 1\n  line 2\n\n  line 3\n")
  }
  test("test_optimizersCompound3Anchors") {
    val input    = "line 1\nline 2 \n\nline 3\n"; val sequence = BasedSequence.of(input); val optimizer = new CharRecoveryOptimizer(PositionAnchor.CURRENT);
    val segments = BasedSegmentBuilder.emptyBuilder(sequence, optimizer, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS);
    sequence.splitListEOL(false).asScala.foreach { line =>
      val trim = line.trim(); segments.append(trim.getSourceRange); segments.append("\n")
    };
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      "BasedSegmentBuilder{[0, 23), s=0:0, u=0:0, t=0:0, l=22, sz=2, na=2: [0, 13), [14, 23) }"
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toStringWithRangesVisibleWhitespace(input), "\u27e6line 1\\nline 2\u27e7\u27e6\\n\\nline 3\\n\u27e7");
    assertEquals(segments.toString(sequence), "line 1\nline 2\n\nline 3\n")
  }

  // CAUTION: BasedSegmentBuilder Unique Test, Not in Segment Builder Tests
  test("test_extractRangesDefault") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(Range.of(0, 0));
    segments.append(Range.of(0, 1)); segments.append(Range.of(3, 6)); segments.append(Range.of(8, 10));
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=6, sz=3, na=3: [0, 1), [3, 6), [8, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toString(sequence), "034589")
  }
  test("test_extractRangesAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS); segments.append(Range.of(0, 0));
    segments.append(Range.of(0, 1)); segments.append(Range.of(3, 6)); segments.append(Range.of(8, 10));
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=6, sz=3, na=3: [0, 1), [3, 6), [8, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toString(sequence), "034589")
  }
  test("test_extractRangesNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(Range.of(0, 0));
    segments.append(Range.of(0, 1)); segments.append(Range.of(3, 6)); segments.append(Range.of(8, 10));
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=0:0, t=0:0, l=6, sz=3, na=3: [0, 1), [3, 6), [8, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toString(sequence), "034589")
  }
  test("test_replacePrefixDefault") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_INCLUDE_ANCHORS | F_TRACK_FIRST256); segments.append(Range.of(0, 0));
    segments.append("^"); segments.append(Range.of(1, 10));
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=1:1, t=1:1, l=10, sz=3, na=2: [0), a:'^', [1, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toString(sequence), "^123456789")
  }
  test("test_replacePrefixAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256 | F_INCLUDE_ANCHORS); segments.append(Range.of(0, 0));
    segments.append("^"); segments.append(Range.of(1, 10));
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=1:1, t=1:1, l=10, sz=3, na=2: [0), a:'^', [1, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toString(sequence), "^123456789")
  }
  test("test_replacePrefixNoAnchors") {
    val input = "0123456789"; val sequence = BasedSequence.of(input); val segments = BasedSegmentBuilder.emptyBuilder(sequence, F_TRACK_FIRST256); segments.append(Range.of(0, 0));
    segments.append("^"); segments.append(Range.of(1, 10));
    assertEquals(
      escapeJavaString(segments.toStringPrep),
      escapeJavaString("BasedSegmentBuilder{[0, 10), s=0:0, u=1:1, t=1:1, l=10, sz=2, na=2: a:'^', [1, 10) }")
    );
    assertEquals(segments.toString(sequence).length, segments.length); assertEquals(segments.toString(sequence), "^123456789")
  }
}
