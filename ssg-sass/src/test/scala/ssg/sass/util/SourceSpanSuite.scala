/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package util

import scala.language.implicitConversions

final class SourceSpanSuite extends munit.FunSuite {

  test("SourceFile tracks line numbers correctly") {
    val file = SourceFile("test.scss", "line1\nline2\nline3")
    assertEquals(file.getLine(0), 0)
    assertEquals(file.getLine(5), 0) // newline char itself is on line 0
    assertEquals(file.getLine(6), 1) // first char of line2
    assertEquals(file.getLine(12), 2) // first char of line3
  }

  test("SourceFile tracks columns correctly") {
    val file = SourceFile("test.scss", "abc\ndef")
    assertEquals(file.getColumn(0), 0) // 'a'
    assertEquals(file.getColumn(2), 2) // 'c'
    assertEquals(file.getColumn(4), 0) // 'd' on second line
    assertEquals(file.getColumn(6), 2) // 'f'
  }

  test("FileSpan text extraction") {
    val file = SourceFile("test.scss", "hello world")
    val span = file.span(6, 11)
    assertEquals(span.text, "world")
    assertEquals(span.length, 5)
  }

  test("FileSpan.trim removes whitespace") {
    val file    = SourceFile("test.scss", "  hello  ")
    val span    = file.span(0, 9)
    val trimmed = span.trim()
    assertEquals(trimmed.text, "hello")
    assertEquals(trimmed.start.offset, 2)
    assertEquals(trimmed.end.offset, 7)
  }

  test("FileSpan.subspan creates sub-range") {
    val file = SourceFile("test.scss", "hello world")
    val span = file.span(0, 11)
    val sub  = span.subspan(6, 11)
    assertEquals(sub.text, "world")
  }

  test("FileSpan.expand merges two spans") {
    val file   = SourceFile("test.scss", "hello world")
    val span1  = file.span(0, 5)
    val span2  = file.span(6, 11)
    val merged = span1.expand(span2)
    assertEquals(merged.text, "hello world")
  }

  test("FileSpan.between returns span between two spans") {
    val file    = SourceFile("test.scss", "hello world")
    val span1   = file.span(0, 5)
    val span2   = file.span(6, 11)
    val between = span1.between(span2)
    assertEquals(between.text, " ")
  }

  test("FileSpan.contains checks containment") {
    val file  = SourceFile("test.scss", "hello world")
    val outer = file.span(0, 11)
    val inner = file.span(3, 8)
    assert(outer.contains(inner))
    assert(!inner.contains(outer))
  }

  test("FileSpan.pointSpan creates zero-width span") {
    val file  = SourceFile("test.scss", "hello")
    val span  = file.span(2, 5)
    val point = span.pointSpan()
    assertEquals(point.length, 0)
    assertEquals(point.start.offset, 2)
  }

  test("FileSpan.synthetic creates span from string") {
    val span = FileSpan.synthetic("test")
    assertEquals(span.text, "test")
    assertEquals(span.length, 4)
  }

  test("FileLocation toString includes 1-based line/column") {
    val file = SourceFile("test.scss", "abc\ndef")
    val loc  = file.location(4) // 'd' on line 1, col 0
    assertEquals(loc.toString, "test.scss:2:1")
  }

  test("FileSpan.message formats error with context") {
    val file = SourceFile("test.scss", "abc\ndef")
    val span = file.span(4, 7) // "def"
    val msg  = span.message("test error")
    assert(msg.contains("test.scss:2:1"))
    assert(msg.contains("test error"))
  }

  test("SourceFile handles CR+LF line endings") {
    val file = SourceFile("test.scss", "abc\r\ndef")
    assertEquals(file.getLine(5), 1) // 'd' is on line 1
    assertEquals(file.getColumn(5), 0)
  }
}
