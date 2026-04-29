/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util/src/test/java/com/vladsch/flexmark/util/sequence/LineAppendableImplTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package util
package sequence
package test

import ssg.md.test.util.ExceptionMatcher
import ssg.md.util.sequence.builder.SequenceBuilder
import ssg.md.util.sequence.mappers.SpaceMapper

import java.util.ArrayList
import scala.language.implicitConversions

@SuppressWarnings(Array("PointlessArithmeticExpression"))
final class LineAppendableImplSuite extends munit.FunSuite {

  // test 1
  test("test_emptyAppendableIterator") {
    val input    = "[simLink spaced](simLink.md)"
    val sequence = BasedSequence.of(input)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    val iter = fa.iterator()
    while (iter.hasNext) {
      iter.next()
      fail("Should not iterate over empty appendable")
    }
  }

  // test 2
  test("test_defaultAppendsAll1") {
    val fa = new LineAppendableImpl(0)

    fa.append("\n")
    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString, "\n")
  }

  // test 3
  test("test_defaultAppendsAll2") {
    val fa = new LineAppendableImpl(0)

    fa.append("    \n")
    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString, "    \n")
  }

  // test 4
  test("test_defaultAppendsOther03") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 0, 3, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString,
                 "line 1\n" +
                   "line 2\n" +
                   "line 3\n"
    )
  }

  // test 5
  test("test_defaultAppendsOther13") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 1, 3, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString,
                 "line 2\n" +
                   "line 3\n"
    )
  }

  // test 6
  test("test_defaultAppendsOther23") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 2, 3, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString, "line 3\n")
  }

  // test 7
  test("test_defaultAppendsOther33") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 3, 3, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString, "")
  }

  // test 8
  test("test_defaultAppendsOther01") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 0, 1, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString, "line 1\n")
  }

  // test 9
  test("test_defaultAppendsOther02") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 0, 2, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString,
                 "line 1\n" +
                   "line 2\n"
    )
  }

  // test 10
  test("test_defaultAppendsOther00") {
    val fa  = new LineAppendableImpl(0)
    val fa2 = new LineAppendableImpl(0)

    fa2.append("line 1\n")
    fa2.append("line 2\n")
    fa2.append("line 3\n")
    fa.append(fa2, 0, 0, true)

    val sequence1 = fa.toSequence()
    assertEquals(sequence1.toString, "")
  }

  // test 11
  test("test_appendCharSb") {
    var sb = new java.lang.StringBuilder()
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)

    fa.append(' ').appendTo(sb)
    assertEquals(sb.toString, "")

    sb = new java.lang.StringBuilder()
    fa.append('a').appendTo(sb)
    assertEquals(sb.toString, "a\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append('a').append('b').append('c').line().appendTo(sb)
    assertEquals(sb.toString, "abc\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append('a').append('b').append('\n').line().appendTo(sb)
    assertEquals(sb.toString, "ab\n")
  }

  // test 12
  test("test_toString") {
    val fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)

    fa.append(' ')
    assertEquals(fa.toString, "")

    fa.append('a')
    assertEquals(fa.toString, "a")

    fa.line()
    assertEquals(fa.toString, "a\n")
  }

  // test 13
  test("test_appendChar") {
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)

    fa.append(' ')
    assertEquals(fa.toString(0, 0), "")

    fa.append('a')
    assertEquals(fa.toString(0, 0), "a\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append('a').append('b').append('c').line()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append('a').append('b').append('\n').line()
    assertEquals(fa.toString(0, 0), "ab\n")
  }

  // test 14 — pre-existing bug: blankLine(1) after blankLine() accumulates extra blank lines
  test("test_leadingEOL".fail) {
    val sb = new java.lang.StringBuilder()
    val fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL & ~LineAppendable.F_TRIM_LEADING_EOL)

    fa.line().appendTo(sb)
    assertEquals(sb.toString, "")

    fa.blankLine().appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n")
    sb.delete(0, sb.length)

    fa.blankLine().appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n")
    sb.delete(0, sb.length)

    fa.blankLine(1).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n")
    sb.delete(0, sb.length)

    fa.blankLine(2).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n\n")
    sb.delete(0, sb.length)

    fa.blankLine(2).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n\n")
    sb.delete(0, sb.length)

    fa.blankLine(1).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n\n")
    sb.delete(0, sb.length)

    fa.blankLine().appendTo(sb, 2, 2)
    assertEquals(sb.toString, "\n\n")
    sb.delete(0, sb.length)
  }

  // test 15
  test("test_noLeadingEOL") {
    val sb = new java.lang.StringBuilder()
    val fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)

    fa.line().appendTo(sb)
    assertEquals(sb.toString, "")

    fa.blankLine().appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")

    fa.blankLine().appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")

    fa.blankLine(1).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")

    fa.blankLine(2).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")

    fa.blankLine(2).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")

    fa.blankLine(1).appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")

    fa.blankLine().appendTo(sb, 2, 2)
    assertEquals(sb.toString, "")
  }

  // test 16
  test("test_appendCharsSb") {
    var sb = new java.lang.StringBuilder()
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)

    fa.append(" ").appendTo(sb)
    assertEquals(sb.toString, "")

    fa.append("     ").appendTo(sb)
    assertEquals(sb.toString, "")

    fa.append("a").appendTo(sb)
    assertEquals(sb.toString, "a\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().appendTo(sb)
    assertEquals(sb.toString, "abc\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().line().appendTo(sb)
    assertEquals(sb.toString, "abc\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().blankLine().appendTo(sb)
    assertEquals(sb.toString, "abc\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().blankLine().appendTo(sb, 1, 1)
    assertEquals(sb.toString, "abc\n\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n").line().appendTo(sb)
    assertEquals(sb.toString, "ab\n")

    sb = new java.lang.StringBuilder()
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n    \t \n\t   \n\n").line().appendTo(sb)
    assertEquals(sb.toString, "ab\n")
  }

  // test 17
  test("test_appendChars") {
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)

    fa.append(" ")
    assertEquals(fa.toString(0, 0), "")

    fa.append("     ")
    assertEquals(fa.toString(0, 0), "")

    fa.append("a")
    assertEquals(fa.toString(0, 0), "a\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().line()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().blankLine()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").line().blankLine()
    assertEquals(fa.toString(1, 1), "abc\n\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n").line()
    assertEquals(fa.toString(0, 0), "ab\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n    \t \n\t   \n\n").line()
    assertEquals(fa.toString(0, 0), "ab\n")
  }

  // test 18
  test("test_lineIf") {
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").lineIf(false).append("c").line()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").lineIf(true).append("c").line()
    assertEquals(fa.toString(0, 0), "ab\nc\n")
  }

  // test 19
  test("test_BlankLine") {
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").blankLine().append("c").line()
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").blankLine().blankLine().append("c").line()
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n\n").blankLine().blankLine().append("c").line()
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n    \t \n\t   \n\n").blankLine().append("c")
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")
  }

  // test 20
  test("test_BlankLineIf") {
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").blankLineIf(false).append("c").line()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").blankLineIf(true).blankLine().append("c").line()
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")
    assertEquals(fa.toString(0, -1), "ab\nc")
  }

  // test 21
  test("test_BlankLines") {
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").blankLine(1).append("c").line()
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab").blankLine(1).blankLine(0).append("c").line()
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n\n").blankLine(1).blankLine(2).append("c").line()
    assertEquals(fa.toString(2, 0), "ab\n\n\nc\n")
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n    \t \n\t   \n\n").blankLine(1).append("c")
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n    \t \n\t   \n\n").blankLine(2).append("c")
    assertEquals(fa.toString(2, 0), "ab\n\n\nc\n")
    assertEquals(fa.toString(1, 0), "ab\n\nc\n")
    assertEquals(fa.toString(0, 0), "ab\nc\n")
  }

  // test 22
  test("test_noIndent") {
    val indent = ""
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.setIndentPrefix(indent)

    fa.indent().append(" ").unIndent()
    assertEquals(fa.toString(0, 0), "")

    fa.indent().append("     ").unIndent()
    assertEquals(fa.toString(0, 0), "")

    fa.indent().append("     ").indent().unIndent().unIndent()
    assertEquals(fa.toString(0, 0), "")

    fa.indent().append("a").unIndent()
    assertEquals(fa.toString(0, 0), "a\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("abc").indent().unIndent()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL)
    fa.append("ab\n    \t \n\t   \n\n").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\nc\n")
  }

  // test 23
  test("test_indent") {
    var indent = " "
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append(" ").unIndent()
    assertEquals(fa.toString(0, 0), "")

    fa.indent().append("     ").unIndent()
    assertEquals(fa.toString(0, 0), "")

    fa.indent().append("     ").indent().unIndent().unIndent()
    assertEquals(fa.toString(0, 0), "")

    fa.indent().append("a").unIndent()
    assertEquals(fa.toString(0, 0), " a\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("abc").indent().unIndent()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("ab").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\n c\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("ab\n    \t \n\t   \n\n").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\n c\n")

    indent = "  "
    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.indent().append("a").unIndent()
    assertEquals(fa.toString(0, 0), "  a\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("abc").indent().unIndent()
    assertEquals(fa.toString(0, 0), "abc\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("ab").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\n  c\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("ab\n    \t \n\t   \n\n").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\n  c\n")
  }

  // test 24
  test("test_openPreFormatted") {
    val indent = "  "
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append("<pre>").openPreFormatted(false).append("abc\ndef \n\n").append("hij\n").append("</pre>").closePreFormatted().unIndent()
    assertEquals(fa.toString(1, 0), "  <pre>abc\ndef \n\nhij\n</pre>\n")
    assertEquals(fa.toString(0, 0), "  <pre>abc\ndef \n\nhij\n</pre>\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append("<p>this is a paragraph ").openPreFormatted(false).append("<div style=''>    some text\n    some more text\n").closePreFormatted().append("</div>").unIndent()
    assertEquals(fa.toString(0, 0), "  <p>this is a paragraph <div style=''>    some text\n    some more text\n  </div>\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append("<p>this is a paragraph ").line().openPreFormatted(false).append("<div style=''>    some text\n    some more text\n").closePreFormatted().append("</div>").unIndent()
    assertEquals(fa.toString(0, 0), "  <p>this is a paragraph\n  <div style=''>    some text\n    some more text\n  </div>\n")

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent()
      .append("<p>this is a paragraph ")
      .indent()
      .openPreFormatted(false)
      .append("<div style=''>    some text\n    some more text\n")
      .closePreFormatted()
      .unIndent()
      .append("</div>")
      .unIndent()
    assertEquals(fa.toString(0, 0), "  <p>this is a paragraph\n    <div style=''>    some text\n    some more text\n  </div>\n")
  }

  // test 25
  test("test_lineCount") {
    val indent = "  "
    var fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append("<pre>").openPreFormatted(false).append("abc\ndef \n\n").append("hij\n").append("</pre>").closePreFormatted().unIndent()
    assertEquals(fa.toString(0, 0), "  <pre>abc\ndef \n\nhij\n</pre>\n")
    assertEquals(fa.toString(1, 0), "  <pre>abc\ndef \n\nhij\n</pre>\n")
    assertEquals(fa.getLineCountWithPending, 5)

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("ab").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\n  c\n")
    assertEquals(fa.getLineCountWithPending, 2)

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)
    fa.append("ab\n    \t \n\t   \n\n").indent().append("c").unIndent()
    assertEquals(fa.toString(0, 0), "ab\n  c\n")
    assertEquals(fa.getLineCountWithPending, 5)

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append("<p>this is a paragraph ").openPreFormatted(false).append("<div style=''>    some text\n    some more text\n").closePreFormatted().append("</div>").unIndent()
    // assertEquals("  <p>this is a paragraph <div style=''>    some text\n    some more text\n  </div>\n", fa.toString(0))
    assertEquals(fa.getLineCountWithPending, 3)

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent().append("<p>this is a paragraph ").line().openPreFormatted(false).append("<div style=''>    some text\n    some more text\n").closePreFormatted().append("</div>").unIndent()
    // assertEquals("  <p>this is a paragraph\n  <div style=''>    some text\n    some more text\n  </div>\n", fa.toString(0))
    assertEquals(fa.getLineCountWithPending, 4)

    fa = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL).setIndentPrefix(indent)

    fa.indent()
      .append("<p>this is a paragraph ")
      .indent()
      .openPreFormatted(false)
      .append("<div style=''>    some text\n    some more text\n")
      .closePreFormatted()
      .unIndent()
      .append("</div>")
      .unIndent()
    assertEquals(fa.toString(0, 0), "  <p>this is a paragraph\n    <div style=''>    some text\n    some more text\n  </div>\n")
    assertEquals(fa.getLineCountWithPending, 4)
  }

  // test 26
  test("test_leadingSpace") {
    val indent = ""
    val fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL & ~(LineAppendable.F_COLLAPSE_WHITESPACE | LineAppendable.F_TRIM_LEADING_WHITESPACE))
    fa.setIndentPrefix(indent)

    fa.append("  abc")
    assertEquals(fa.toString(0, 0), "  abc\n")

    fa.append("     def")
    assertEquals(fa.toString(0, 0), "  abc\n     def\n")

    fa.append("a")
    assertEquals(fa.toString(0, 0), "  abc\n     def\na\n")
  }

  // test 27
  test("test_leadingSpaceVaries") {
    val indent = ""
    val fa: LineAppendable = new LineAppendableImpl(LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    fa.setIndentPrefix(indent)

    val saved = fa.getOptions
    fa.setOptions(saved & ~(LineAppendable.F_COLLAPSE_WHITESPACE | LineAppendable.F_TRIM_LEADING_WHITESPACE))
    fa.append("  abc")
    fa.setOptions(saved)
    //        assertEquals("  abc\n", fa.toString(0))

    fa.append("     def\n")
    assertEquals(fa.toString(0, 0), "  abc def\n")

    fa.append("a")
    assertEquals(fa.toString(0, 0), "  abc def\na\n")
  }

  // test 28
  test("test_withBuilder") {
    val input = "" +
      "0:2343568\n" +
      "1:2343568\n" +
      "2:2343568\n" +
      "3:2343568\n" +
      "4:2343568\n" +
      ""
    val sequence = BasedSequence.of(input)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.append(sequence.subSequence(30, 39)).line()
    fa.append(sequence.subSequence(40, 49)).line()

    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assertEquals(fa.getLine(1).toString, sequence.subSequence(1 * 10, 1 * 10 + 10).toString, clue("Line: 1"))
    assertEquals(fa.getLine(2).toString, sequence.subSequence(2 * 10, 2 * 10 + 10).toString, clue("Line: 2"))
    assertEquals(fa.getLine(3).toString, sequence.subSequence(3 * 10, 3 * 10 + 10).toString, clue("Line: 3"))
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=0, tl=9, l=10, sumPl=0, sumTl=18, sumL=20, bp, '1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=0, tl=9, l=10, sumPl=0, sumTl=27, sumL=30, bp, '2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=0, sumTl=36, sumL=40, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=0, sumTl=45, sumL=50, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )
  }

  // test 29
  test("test_prefixAfterEol") {
    val input = "" +
      "0:2343568\n" +
      "1:2343568\n" +
      "2:2343568\n" +
      "3:2343568\n" +
      "4:2343568\n" +
      ""
    val sequence = BasedSequence.of(input)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.pushPrefix().addPrefix("  ", true)
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.popPrefix(true)
    fa.append(sequence.subSequence(30, 39)).line()
    fa.append(sequence.subSequence(40, 49)).line()

    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assertEquals(fa.getLine(1).toString, sequence.subSequence(1 * 10, 1 * 10 + 10).toString, clue("Line: 1"))
    assertEquals(fa.getLine(2).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(2 * 10, 2 * 10 + 10)).toString, clue("Line: 2"))
    assertEquals(fa.getLine(3).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(3 * 10, 3 * 10 + 10)).toString, clue("Line: 3"))
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=0, tl=9, l=10, sumPl=0, sumTl=18, sumL=20, bp, '1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=2, sumTl=27, sumL=32, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=2, tl=9, l=12, sumPl=4, sumTl=36, sumL=44, bp, '  3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=4, sumTl=45, sumL=54, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )

    assertEquals(fa.toString(2, 2, true),
                 "0:2343568\n" +
                   "1:2343568\n" +
                   "  2:2343568\n" +
                   "  3:2343568\n" +
                   "4:2343568\n"
    )

    assertEquals(fa.toString(2, 2, false),
                 "0:2343568\n" +
                   "1:2343568\n" +
                   "2:2343568\n" +
                   "3:2343568\n" +
                   "4:2343568\n"
    )
  }

  // test 30
  test("test_prefix") {
    val input = "" +
      "0:2343568\n" +
      "1:2343568\n" +
      "2:2343568\n" +
      "3:2343568\n" +
      "4:2343568\n" +
      ""
    val sequence = BasedSequence.of(input)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.pushPrefix().addPrefix("  ", false)
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.popPrefix(false)
    fa.append(sequence.subSequence(30, 39)).line()
    fa.append(sequence.subSequence(40, 49)).line()

    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assertEquals(fa.getLine(1).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(1 * 10, 1 * 10 + 10)).toString, clue("Line: 1"))
    assertEquals(fa.getLine(2).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(2 * 10, 2 * 10 + 10)).toString, clue("Line: 2"))
    assertEquals(fa.getLine(3).toString, sequence.subSequence(3 * 10, 3 * 10 + 10).toString, clue("Line: 3"))
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=34, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=4, sumTl=45, sumL=54, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )

    assertEquals(fa.toString(2, 2, true),
                 "0:2343568\n" +
                   "  1:2343568\n" +
                   "  2:2343568\n" +
                   "3:2343568\n" +
                   "4:2343568\n"
    )

    assertEquals(fa.toString(2, 2, false),
                 "0:2343568\n" +
                   "1:2343568\n" +
                   "2:2343568\n" +
                   "3:2343568\n" +
                   "4:2343568\n"
    )
  }

  // test 31
  test("test_setPrefixLength") {
    val input = "" +
      "0:2343568\n" +
      "1:2343568\n" +
      "2:2343568\n" +
      "3:2343568\n" +
      "4:2343568\n" +
      ""
    val sequence = BasedSequence.of(input)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.pushPrefix().addPrefix("  ", false)
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.popPrefix(false)
    fa.append(sequence.subSequence(30, 39)).line()
    fa.append(sequence.subSequence(40, 49)).line()

    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assertEquals(fa.getLine(1).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(1 * 10, 1 * 10 + 10)).toString, clue("Line: 1"))
    assertEquals(fa.getLine(2).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(2 * 10, 2 * 10 + 10)).toString, clue("Line: 2"))
    assertEquals(fa.getLine(3).toString, sequence.subSequence(3 * 10, 3 * 10 + 10).toString, clue("Line: 3"))
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=34, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=4, sumTl=45, sumL=54, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )

    fa.setPrefixLength(2, 4)

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=4, tl=7, l=12, sumPl=6, sumTl=25, sumL=34, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=6, sumTl=34, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=6, sumTl=43, sumL=54, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )

    assertEquals(fa.toString(2, 2, true),
                 "0:2343568\n" +
                   "  1:2343568\n" +
                   "  2:2343568\n" +
                   "3:2343568\n" +
                   "4:2343568\n"
    )

    assertEquals(fa.toString(2, 2, false),
                 "0:2343568\n" +
                   "1:2343568\n" +
                   "2343568\n" +
                   "3:2343568\n" +
                   "4:2343568\n"
    )
  }

  // test 32
  test("test_setLine") {
    val input    = "0:2343568\n1:2343568\n2:2343568\n3:2343568\n4:2343568\n"
    val sequence = BasedSequence.of(input)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.pushPrefix().addPrefix("  ", false)
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.popPrefix(false)
    fa.append(sequence.subSequence(30, 39)).line()
    fa.append(sequence.subSequence(40, 49)).line()

    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assertEquals(fa.getLine(1).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(1 * 10, 1 * 10 + 10)).toString, clue("Line: 1"))
    assertEquals(fa.getLine(2).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(2 * 10, 2 * 10 + 10)).toString, clue("Line: 2"))
    assertEquals(fa.getLine(3).toString, sequence.subSequence(3 * 10, 3 * 10 + 10).toString, clue("Line: 3"))
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=34, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=4, sumTl=45, sumL=54, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )

    fa.setLine(2, "", "0123456")
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=0, tl=7, l=8, sumPl=2, sumTl=25, sumL=30, bp, '0123456\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=2, sumTl=34, sumL=40, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=2, sumTl=43, sumL=50, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )

    assertEquals(fa.toString(2, 2, true), "0:2343568\n  1:2343568\n0123456\n3:2343568\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "0:2343568\n1:2343568\n0123456\n3:2343568\n4:2343568\n")

    fa.setLine(4, "  ", "4:01234")
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=0, tl=7, l=8, sumPl=2, sumTl=25, sumL=30, bp, '0123456\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=2, sumTl=34, sumL=40, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=2, tl=7, l=10, sumPl=4, sumTl=41, sumL=50, bp, '  4:01234\\n'}",
      clue("Line: 4")
    )

    assertEquals(fa.toString(2, 2, true), "0:2343568\n  1:2343568\n0123456\n3:2343568\n  4:01234\n")

    assertEquals(fa.toString(2, 2, false), "0:2343568\n1:2343568\n0123456\n3:2343568\n4:01234\n")
  }

  private def fiveLineInput: String = "0:2343568\n1:2343568\n2:2343568\n3:2343568\n4:2343568\n"

  private def setupPrefixed(sequence: BasedSequence, terminateLast: Boolean): LineAppendable = {
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    fa.append(sequence.subSequence(0, 9)).line()
    fa.pushPrefix().addPrefix("  ", false)
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.popPrefix(false)
    fa.append(sequence.subSequence(30, 39)).line()
    if (terminateLast) fa.append(sequence.subSequence(40, 49)).line()
    else fa.append(sequence.subSequence(40, 49))
    fa
  }

  private def assertPrefixedLines(fa: LineAppendable, sequence: BasedSequence): Unit = {
    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assertEquals(fa.getLine(1).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(1 * 10, 1 * 10 + 10)).toString, clue("Line: 1"))
    assertEquals(fa.getLine(2).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(2 * 10, 2 * 10 + 10)).toString, clue("Line: 2"))
    assertEquals(fa.getLine(3).toString, sequence.subSequence(3 * 10, 3 * 10 + 10).toString, clue("Line: 3"))
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))
  }

  private def assertPrefixedLineInfos(fa: LineAppendable): Unit = {
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=34, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=4, sumTl=45, sumL=54, bp, '4:2343568\\n'}",
      clue("Line: 4")
    )
  }

  // test 33
  test("test_setLinePending") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = setupPrefixed(sequence, terminateLast = false)
    assert(fa.getLineCountWithPending > fa.getLineCount)

    assertEquals(fa.getLine(0).toString, sequence.subSequence(0 * 10, 0 * 10 + 10).toString, clue("Line: 0"))
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(fa.getLine(1).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(1 * 10, 1 * 10 + 10)).toString, clue("Line: 1"))
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(fa.getLine(2).toString, PrefixedSubSequence.prefixOf("  ", sequence.subSequence(2 * 10, 2 * 10 + 10)).toString, clue("Line: 2"))
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(fa.getLine(3).toString, sequence.subSequence(3 * 10, 3 * 10 + 10).toString, clue("Line: 3"))
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(fa.getLine(4).toString, sequence.subSequence(4 * 10, 4 * 10 + 10).toString, clue("Line: 4"))
    assert(fa.getLineCountWithPending > fa.getLineCount)

    assertPrefixedLineInfos(fa)
    assert(fa.getLineCountWithPending > fa.getLineCount)

    fa.setLine(fa.getLineCount, "", "0123456")
    assert(!(fa.getLineCountWithPending > fa.getLineCount))

    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=34, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=7, l=8, sumPl=4, sumTl=43, sumL=52, bp, '0123456\\n'}",
      clue("Line: 4")
    )

    assertEquals(fa.toString(2, 2, true), "0:2343568\n  1:2343568\n  2:2343568\n3:2343568\n0123456\n")

    assertEquals(fa.toString(2, 2, false), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n0123456\n")
  }

  // test 34
  test("test_insertLine") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = setupPrefixed(sequence, terminateLast = true)

    assertPrefixedLines(fa, sequence)
    assertPrefixedLineInfos(fa)

    fa.insertLine(2, " ", "1.5:0123456")
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=1, tl=11, l=13, sumPl=3, sumTl=29, sumL=35, bp, ' 1.5:0123456\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=2, tl=9, l=12, sumPl=5, sumTl=38, sumL=47, bp, '  2:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=5, sumTl=47, sumL=57, bp, '3:2343568\\n'}",
      clue("Line: 4")
    )
    assertEquals(
      fa.getLineInfo(5).toString,
      "LineInfo{i=5, pl=0, tl=9, l=10, sumPl=5, sumTl=56, sumL=67, bp, '4:2343568\\n'}",
      clue("Line: 5")
    )

    assertEquals(fa.toString(2, 2, true), "0:2343568\n  1:2343568\n 1.5:0123456\n  2:2343568\n3:2343568\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "0:2343568\n1:2343568\n1.5:0123456\n2:2343568\n3:2343568\n4:2343568\n")

    fa.insertLine(5, "  ", "4.5:01234")
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=1, tl=11, l=13, sumPl=3, sumTl=29, sumL=35, bp, ' 1.5:0123456\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=2, tl=9, l=12, sumPl=5, sumTl=38, sumL=47, bp, '  2:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=5, sumTl=47, sumL=57, bp, '3:2343568\\n'}",
      clue("Line: 4")
    )
    assertEquals(
      fa.getLineInfo(5).toString,
      "LineInfo{i=5, pl=2, tl=9, l=12, sumPl=7, sumTl=56, sumL=69, bp, '  4.5:01234\\n'}",
      clue("Line: 5")
    )
    assertEquals(
      fa.getLineInfo(6).toString,
      "LineInfo{i=6, pl=0, tl=9, l=10, sumPl=7, sumTl=65, sumL=79, bp, '4:2343568\\n'}",
      clue("Line: 6")
    )

    assertEquals(fa.toString(2, 2, true), "0:2343568\n  1:2343568\n 1.5:0123456\n  2:2343568\n3:2343568\n  4.5:01234\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "0:2343568\n1:2343568\n1.5:0123456\n2:2343568\n3:2343568\n4.5:01234\n4:2343568\n")
  }

  // test 35
  test("test_insertLinePending") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = setupPrefixed(sequence, terminateLast = false)

    assertPrefixedLines(fa, sequence)
    assertPrefixedLineInfos(fa)

    fa.insertLine(fa.getLineCount, " ", "4.5:0123456")
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=10, bp, '0:2343568\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=22, bp, '  1:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=34, bp, '  2:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=44, bp, '3:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=1, tl=11, l=13, sumPl=5, sumTl=47, sumL=57, bp, ' 4.5:0123456\\n'}",
      clue("Line: 4")
    )
    assertEquals(
      fa.getLineInfo(5).toString,
      "LineInfo{i=5, pl=0, tl=9, l=10, sumPl=5, sumTl=56, sumL=67, bp, '4:2343568\\n'}",
      clue("Line: 5")
    )
    assert(fa.getLineCountWithPending > fa.getLineCount)

    assertEquals(fa.toString(2, 2, true), "0:2343568\n  1:2343568\n  2:2343568\n3:2343568\n 4.5:0123456\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n4.5:0123456\n4:2343568\n")
  }

  // test 36
  test("test_insertLineFirst") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = setupPrefixed(sequence, terminateLast = false)

    assertPrefixedLines(fa, sequence)
    assertPrefixedLineInfos(fa)

    fa.insertLine(0, " ", "0.5:0123456")
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(
      fa.getLineInfo(0).toString,
      "LineInfo{i=0, pl=1, tl=11, l=13, sumPl=1, sumTl=11, sumL=13, bp, ' 0.5:0123456\\n'}",
      clue("Line: 0")
    )
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=0, tl=9, l=10, sumPl=1, sumTl=20, sumL=23, bp, '0:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=3, sumTl=29, sumL=35, bp, '  1:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=2, tl=9, l=12, sumPl=5, sumTl=38, sumL=47, bp, '  2:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=5, sumTl=47, sumL=57, bp, '3:2343568\\n'}",
      clue("Line: 4")
    )
    assertEquals(
      fa.getLineInfo(5).toString,
      "LineInfo{i=5, pl=0, tl=9, l=10, sumPl=5, sumTl=56, sumL=67, bp, '4:2343568\\n'}",
      clue("Line: 5")
    )
    assert(fa.getLineCountWithPending > fa.getLineCount)

    assertEquals(fa.toString(2, 2, true), " 0.5:0123456\n0:2343568\n  1:2343568\n  2:2343568\n3:2343568\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "0.5:0123456\n0:2343568\n1:2343568\n2:2343568\n3:2343568\n4:2343568\n")
  }

  // test 37
  test("test_insertLineFirstBlank") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = setupPrefixed(sequence, terminateLast = false)

    assertPrefixedLines(fa, sequence)
    assertPrefixedLineInfos(fa)

    fa.insertLine(0, "", "")
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(fa.getLineInfo(0).toString, "LineInfo{i=0, pl=0, tl=0, l=1, sumPl=0, sumTl=0, sumL=1, bp bt, '\\n'}", clue("Line: 0"))
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=0, tl=9, l=10, sumPl=0, sumTl=9, sumL=11, bp, '0:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=2, sumTl=18, sumL=23, bp, '  1:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=2, tl=9, l=12, sumPl=4, sumTl=27, sumL=35, bp, '  2:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=4, sumTl=36, sumL=45, bp, '3:2343568\\n'}",
      clue("Line: 4")
    )
    assertEquals(
      fa.getLineInfo(5).toString,
      "LineInfo{i=5, pl=0, tl=9, l=10, sumPl=4, sumTl=45, sumL=55, bp, '4:2343568\\n'}",
      clue("Line: 5")
    )
    assert(fa.getLineCountWithPending > fa.getLineCount)

    assertEquals(fa.toString(2, 2, true), "\n0:2343568\n  1:2343568\n  2:2343568\n3:2343568\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "\n0:2343568\n1:2343568\n2:2343568\n3:2343568\n4:2343568\n")
  }

  // test 38
  test("test_insertLineFirstPrefixedBlank") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = setupPrefixed(sequence, terminateLast = false)

    assertPrefixedLines(fa, sequence)
    assertPrefixedLineInfos(fa)

    fa.insertLine(0, "> ", "")
    assert(fa.getLineCountWithPending > fa.getLineCount)
    assertEquals(fa.getLineInfo(0).toString, "LineInfo{i=0, pl=1, tl=0, l=2, sumPl=1, sumTl=0, sumL=2, bt, '>\\n'}", clue("Line: 0"))
    assertEquals(
      fa.getLineInfo(1).toString,
      "LineInfo{i=1, pl=0, tl=9, l=10, sumPl=1, sumTl=9, sumL=12, bp, '0:2343568\\n'}",
      clue("Line: 1")
    )
    assertEquals(
      fa.getLineInfo(2).toString,
      "LineInfo{i=2, pl=2, tl=9, l=12, sumPl=3, sumTl=18, sumL=24, bp, '  1:2343568\\n'}",
      clue("Line: 2")
    )
    assertEquals(
      fa.getLineInfo(3).toString,
      "LineInfo{i=3, pl=2, tl=9, l=12, sumPl=5, sumTl=27, sumL=36, bp, '  2:2343568\\n'}",
      clue("Line: 3")
    )
    assertEquals(
      fa.getLineInfo(4).toString,
      "LineInfo{i=4, pl=0, tl=9, l=10, sumPl=5, sumTl=36, sumL=46, bp, '3:2343568\\n'}",
      clue("Line: 4")
    )
    assertEquals(
      fa.getLineInfo(5).toString,
      "LineInfo{i=5, pl=0, tl=9, l=10, sumPl=5, sumTl=45, sumL=56, bp, '4:2343568\\n'}",
      clue("Line: 5")
    )
    assert(fa.getLineCountWithPending > fa.getLineCount)

    assertEquals(fa.toString(2, 2, true), ">\n0:2343568\n  1:2343568\n  2:2343568\n3:2343568\n4:2343568\n")

    assertEquals(fa.toString(2, 2, false), "\n0:2343568\n1:2343568\n2:2343568\n3:2343568\n4:2343568\n")
  }

  // test 39
  test("test_maxBlankLines") {
    val input    = fiveLineInput
    val sequence = BasedSequence.of(input)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.append(sequence.subSequence(10, 19)).blankLine()
    fa.append(sequence.subSequence(20, 29)).blankLine(2)
    fa.append(sequence.subSequence(30, 39)).blankLine(3)
    fa.blankLine()

    assertEquals(fa.toString(4, 4), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(3, 4), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(2, 4), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(1, 4), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(0, 4), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(4, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(4, 2), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n", clue("0"))
    assertEquals(fa.toString(4, 1), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n", clue("0"))
    assertEquals(fa.toString(4, 0), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n", clue("0"))
    assertEquals(fa.toString(4, -1), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568", clue("0"))
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(2, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(1, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(0, 3), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(-1, 3), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n\n\n", clue("0"))
    assertEquals(fa.toString(2, 2), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n", clue("0"))
    assertEquals(fa.toString(1, 2), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n\n\n", clue("0"))
    assertEquals(fa.toString(0, 2), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n\n", clue("0"))
    assertEquals(fa.toString(-1, 2), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n\n", clue("0"))
    assertEquals(fa.toString(2, 1), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n", clue("0"))
    assertEquals(fa.toString(2, 0), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n", clue("0"))
    assertEquals(fa.toString(2, -1), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568", clue("0"))
    assertEquals(fa.toString(1, 1), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n\n", clue("0"))
    assertEquals(fa.toString(0, 1), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n", clue("0"))
    assertEquals(fa.toString(-1, 1), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n\n", clue("0"))
    assertEquals(fa.toString(1, 0), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n", clue("0"))
    assertEquals(fa.toString(1, -1), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568", clue("0"))
    assertEquals(fa.toString(0, 0), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n", clue("0"))
    assertEquals(fa.toString(-1, 0), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n", clue("0"))
    assertEquals(fa.toString(0, -1), "0:2343568\n1:2343568\n2:2343568\n3:2343568", clue("0"))
    assertEquals(fa.toString(-1, -1), "0:2343568\n1:2343568\n2:2343568\n3:2343568", clue("0"))
  }

  // test 40
  test("test_normalize1") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.blankLine(0)
    fa.removeExtraBlankLines(-1, 0)

    assertEquals(fa.toString(0, 0), "0:2343568\n", clue("0"))
    assertEquals(fa.toString(1, 1), "0:2343568\n", clue("1"))
    assertEquals(fa.toString(2, 2), "0:2343568\n", clue("2"))

    fa.blankLine(0)
    fa.removeExtraBlankLines(-1, -1)

    assertEquals(fa.toString(0, 0), "0:2343568\n", clue("0"))
    assertEquals(fa.toString(1, 1), "0:2343568\n", clue("1"))
    assertEquals(fa.toString(2, 2), "0:2343568\n", clue("2"))
  }

  // test 41
  test("test_normalize2") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.append(sequence.subSequence(10, 19)).blankLine()
    fa.append(sequence.subSequence(20, 29)).blankLine(2)
    fa.append(sequence.subSequence(30, 39)).blankLine(3)

    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n\n", clue("0"))

    fa.removeExtraBlankLines(2, 2)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n\n", clue("0"))

    fa.removeExtraBlankLines(2, 1)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n\n3:2343568\n\n", clue("0"))

    fa.removeExtraBlankLines(1, 1)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n\n", clue("0"))

    fa.removeExtraBlankLines(1, 0)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n\n2:2343568\n\n3:2343568\n", clue("0"))

    fa.removeExtraBlankLines(0, 0)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n", clue("0"))

    fa.removeExtraBlankLines(0, -1)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n", clue("0"))

    fa.removeExtraBlankLines(-1, -1)
    assertEquals(fa.toString(3, 3), "0:2343568\n1:2343568\n2:2343568\n3:2343568\n", clue("0"))
  }

  // test 42
  test("test_maxTailBlankLinesBuilder") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    val builder = SequenceBuilder.emptyBuilder(sequence)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.blankLine(0)

    fa.appendToSilently(builder, -1, -1)
    assertEquals(builder.toString, "0:2343568", clue("-1"))
  }

  // test 43
  test("test_iterateMaxTailBlankLinesInfo") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()
    fa.append(sequence.subSequence(10, 19)).blankLine()
    fa.append(sequence.subSequence(20, 29)).blankLine(2)
    fa.append(sequence.subSequence(30, 39)).blankLine(3)

    {
      val i    = 2
      val out  = new java.lang.StringBuilder()
      val iter = fa.getLinesInfo(i).iterator()
      while (iter.hasNext) {
        val info = iter.next()
        out.append(info.lineSeq)
      }
      assertEquals(out.toString, fa.toString(4, i), clue("" + i))
    }

    var i = 5
    while ({ i -= 1; i >= 0 }) {
      val out  = new java.lang.StringBuilder()
      val iter = fa.getLinesInfo(i).iterator()
      while (iter.hasNext) {
        val info = iter.next()
        out.append(info.lineSeq)
      }
      assertEquals(out.toString, fa.toString(4, i), clue("" + i))
    }
  }

  // test 44
  test("test_iterateMaxTailBlankLinesLines") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("> ")
    fa.append(sequence.subSequence(0, 9)).line()
    fa.append(sequence.subSequence(10, 19)).blankLine()
    fa.append(sequence.subSequence(20, 29)).blankLine(2)
    fa.append(sequence.subSequence(30, 39)).blankLine(3)

    {
      val i    = 2
      val out  = new java.lang.StringBuilder()
      val iter = fa.getLines(i).iterator()
      while (iter.hasNext) {
        val lineSeq = iter.next()
        out.append(lineSeq)
      }
      assertEquals(out.toString, fa.toString(4, i), clue("" + i))
    }

    var i = 5
    while ({ i -= 1; i >= 0 }) {
      val out  = new java.lang.StringBuilder()
      val iter = fa.getLines(i).iterator()
      while (iter.hasNext) {
        val lineSeq = iter.next()
        out.append(lineSeq)
      }
      assertEquals(out.toString, fa.toString(4, i), clue("" + i))
    }
  }

  // test 45
  test("test_iterateMaxTailBlankLinesLinesNoEOL") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 10)).line()
    fa.append(sequence.subSequence(10, 19)).line()
    fa.append(sequence.subSequence(20, 29)).line()
    fa.append(sequence.subSequence(30, 39)).line()
    fa.append(sequence.subSequence(40, 49)).line()

    val lines = new ArrayList[BasedSequence]()
    val faLines: java.lang.Iterable[BasedSequence] = fa.getLines(-1, true)
    val iter = faLines.iterator()
    while (iter.hasNext) {
      val line = iter.next()
      lines.add(line)
    }

    assertEquals(lines.get(0).toString, "0:2343568\n", clue("0"))
    assertEquals(lines.get(1).toString, "1:2343568\n", clue("1"))
    assertEquals(lines.get(2).toString, "2:2343568\n", clue("2"))
    assertEquals(lines.get(3).toString, "3:2343568\n", clue("3"))
    assertEquals(lines.get(4).toString, "4:2343568", clue("4"))
  }

  // test 46
  test("test_iterateMaxTailBlankLinesLinesNoPrefix") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("> ")
    fa.append(sequence.subSequence(0, 9)).line()
    fa.append(sequence.subSequence(10, 19)).blankLine()
    fa.append(sequence.subSequence(20, 29)).blankLine(2)
    fa.append(sequence.subSequence(30, 39)).blankLine(3)

    {
      val i    = 2
      val out  = new java.lang.StringBuilder()
      val iter = fa.getLines(i, false).iterator()
      while (iter.hasNext) {
        val lineSeq = iter.next()
        out.append(lineSeq)
      }
      assertEquals(out.toString, fa.toString(4, i, false), clue("" + i))
    }

    var i = 5
    while ({ i -= 1; i >= 0 }) {
      val out  = new java.lang.StringBuilder()
      val iter = fa.getLines(i, false).iterator()
      while (iter.hasNext) {
        val lineSeq = iter.next()
        out.append(lineSeq)
      }
      assertEquals(out.toString, fa.toString(4, i, false), clue("" + i))
    }
  }

  // test 47
  test("test_appendToNoLine") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    assertEquals(fa.toString, "")
  }

  // test 48
  test("test_prefixAfterEOL") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("* ", false)
    fa.setPrefix("", true)
    assertEquals(fa.getBeforeEolPrefix.toString, "* ")
    fa.line()
    assertEquals(fa.getBeforeEolPrefix.toString, "* ")
    fa.blankLine()
    assertEquals(fa.getBeforeEolPrefix.toString, "* ")
    fa.append("abc")
    assertEquals(fa.getBeforeEolPrefix.toString, "* ")
    fa.line()
    assertEquals(fa.getBeforeEolPrefix.toString, "")
    assertEquals(fa.toString, "* abc\n")
  }

  // test 49
  test("test_getOffsetWithPending") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    val iMax = sequence.length()
    var i    = 0
    while (i < iMax) {
      fa.append(sequence.subSequence(i, i + 1))
      assertEquals(fa.offsetWithPending(), i + 1, clue("i: " + i))
      i += 1
    }
  }

  // test 50
  test("test_getOffset") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa: LineAppendable = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    val iMax    = sequence.length()
    var lastEol = 0
    var i       = 0
    while (i < iMax) {
      fa.append(sequence.subSequence(i, i + 1))
      if (sequence.charAt(i) == '\n') lastEol = i + 1
      assertEquals(fa.offset(), lastEol, clue("i: " + i))
      i += 1
    }
  }

  // test 51
  test("test_appendLineAppendable") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    val fa2      = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.append(sequence.subSequence(0, 9)).line()

    fa2.append(fa)
    assertEquals(fa2.toString(2, 2), "0:2343568\n", clue("0"))

    fa2.append(" ").line()

    fa2.pushPrefix().setPrefix("", false)
    fa2.blankLine().popPrefix(false)

    assertEquals(fa2.toString(2, 2), "0:2343568\n\n", clue("0"))

    fa2.blankLine()
    assertEquals(fa2.toString(2, 2), "0:2343568\n\n", clue("0"))
  }

  // test 52
  test("test_appendLineAppendablePrefixed") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    var fa2      = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("> ", false)
    fa.append(sequence.subSequence(0, 9)).line()

    fa2.append(fa)
    assertEquals(fa2.toString(2, 2), "> 0:2343568\n", clue("0"))

    fa.append(" ").line()
    fa.blankLine()
    assertEquals(fa.toString(2, 2), "> 0:2343568\n>\n", clue("0"))

    fa2 = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    fa2.setPrefix("  ", false)
    fa2.append(fa)

    assertEquals(fa2.toString(2, 2), "  > 0:2343568\n  >\n", clue("0"))

    fa2.blankLine()
    assertEquals(fa2.toString(2, 2), "  > 0:2343568\n  >\n", clue("0"))
  }

  // test 53
  test("test_appendLineAppendablePrefixedUnterminated") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    val fa2      = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("> ", false)
    fa.append(sequence.subSequence(0, 9))
    assertEquals(fa.toString, "> 0:2343568")

    fa2.setPrefix("  ", false)
    fa2.append(fa)
    assertEquals(fa2.toString, "  > 0:2343568")

    assertEquals(fa2.toString, "  > 0:2343568", clue("0"))

    fa2.append("|abc").line()
    assertEquals(fa2.toString, "  > 0:2343568|abc\n", clue("0"))
  }

  // test 54
  test("test_appendLineAppendablePrefixedUnterminated2") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), 0)

    fa.append("> ")
    assertEquals(fa.toString, "> ")

    fa.setPrefixLength(0, 2)
    assertEquals(fa.toString, "> \n")

    val info = fa.getLineInfo(0)
    assertEquals(info.length, 3)
    assertEquals(info.prefixLength, 2)
    assertEquals(info.textLength, 0)
  }

  // test 55
  test("test_appendLineAppendablePrefixedUnterminated3") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), 0)

    fa.append("> ")
    assertEquals(fa.toString, "> ")

    val ex = intercept[IllegalArgumentException] {
      fa.setPrefixLength(0, 3)
    }
    val matcher = ExceptionMatcher.`match`(classOf[IllegalArgumentException], "prefixLength 3 is out of valid range [0, 3) for the line")
    assert(matcher.matches(ex), s"Expected '${matcher.description}' but got: $ex")
  }

  // test 56
  test("test_appendLineAppendablePrefixedNoPrefixes") {
    val sequence = BasedSequence.of(fiveLineInput)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    var fa2      = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("> ", false)
    fa.append(sequence.subSequence(0, 9)).line()

    fa2.append(fa, false)
    assertEquals(fa2.toString(2, 2), "0:2343568\n", clue("0"))

    fa.append(" ").line()
    fa.blankLine()
    assertEquals(fa.toString(2, 2), "> 0:2343568\n>\n", clue("0"))

    fa2 = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)
    fa2.setPrefix("  ", false)
    fa2.append(fa, false)

    assertEquals(fa2.toString(2, 2), "  0:2343568\n\n", clue("0"))

    fa2.blankLine()
    assertEquals(fa2.toString(2, 2), "  0:2343568\n\n", clue("0"))
  }

  // test 57
  test("test_appendLineAppendableMapped") {
    val input    = "[simLink spaced](simLink.md)"
    val sequence = BasedSequence.of(input)
    val fa       = new LineAppendableImpl(SequenceBuilder.emptyBuilder(sequence), LineAppendable.F_FORMAT_ALL | LineAppendable.F_TRIM_LEADING_WHITESPACE)

    fa.setPrefix("> ", false)
    val mapped = sequence.toMapped(SpaceMapper.toNonBreakSpace)
    fa.append(mapped)
    val info        = fa.getLineInfo(0)
    val lineBuilder = sequence.getBuilder.asInstanceOf[SequenceBuilder].append(info.getLine)
    assertEquals(
      lineBuilder.toStringWithRanges(true),
      "\u27e6\u27e7> \u27e6[simLink\u27e7\u00a0\u27e6spaced](simLink.md)\u27e7\\n\u27e6\u27e7"
    )

    val lineBuilder2 = sequence.getBuilder.asInstanceOf[SequenceBuilder].append(info.getLineNoEOL)
    assertEquals(lineBuilder2.toStringWithRanges(true), "\u27e6\u27e7> \u27e6[simLink\u27e7\u00a0\u27e6spaced](simLink.md)\u27e7")

    val actual = BasedSequence.of(fa.toSequence(0, -1))
    assertEquals(actual.toString, "> [simLink\u00A0spaced](simLink.md)")
    val actualBuilder = sequence.getBuilder.asInstanceOf[SequenceBuilder].append(actual)
    assertEquals(actualBuilder.toStringWithRanges(true), "\u27e6\u27e7> \u27e6[simLink\u27e7\u00A0\u27e6spaced](simLink.md)\u27e7")

    val actualSpc = actual.toMapped(SpaceMapper.fromNonBreakSpace)
    assertEquals(actualSpc.toString, "> [simLink spaced](simLink.md)")
    val actualSpcBuilder = sequence.getBuilder.asInstanceOf[SequenceBuilder].append(actualSpc)
    assertEquals(actualSpcBuilder.toStringWithRanges(true), "\u27e6\u27e7> \u27e6[simLink spaced](simLink.md)\u27e7")
  }
}
