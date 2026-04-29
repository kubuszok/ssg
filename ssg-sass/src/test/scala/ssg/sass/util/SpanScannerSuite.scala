/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package util

import scala.language.implicitConversions

final class SpanScannerSuite extends munit.FunSuite {

  test("peekChar returns character at current position") {
    val scanner = SpanScanner("abc")
    assertEquals(scanner.peekChar(), 'a'.toInt)
    assertEquals(scanner.peekChar(1), 'b'.toInt)
    assertEquals(scanner.peekChar(2), 'c'.toInt)
    assertEquals(scanner.peekChar(3), -1)
  }

  test("readChar advances position") {
    val scanner = SpanScanner("ab")
    assertEquals(scanner.readChar(), 'a'.toInt)
    assertEquals(scanner.position, 1)
    assertEquals(scanner.readChar(), 'b'.toInt)
    assertEquals(scanner.position, 2)
    assertEquals(scanner.readChar(), -1)
  }

  test("scanChar succeeds on match and advances") {
    val scanner = SpanScanner("ab")
    assert(scanner.scanChar('a'.toInt))
    assertEquals(scanner.position, 1)
    assert(!scanner.scanChar('a'.toInt))
    assertEquals(scanner.position, 1)
    assert(scanner.scanChar('b'.toInt))
    assertEquals(scanner.position, 2)
  }

  test("expectChar throws on mismatch") {
    val scanner = SpanScanner("ab")
    scanner.expectChar('a'.toInt)
    assertEquals(scanner.position, 1)
    intercept[StringScannerException] {
      scanner.expectChar('c'.toInt)
    }
  }

  test("scan string matches prefix") {
    val scanner = SpanScanner("hello world")
    assert(scanner.scan("hello"))
    assertEquals(scanner.position, 5)
    assert(!scanner.scan("xyz"))
    assertEquals(scanner.position, 5)
  }

  test("scan regex matches and sets lastMatch") {
    val scanner = SpanScanner("123abc")
    assert(scanner.scan("\\d+".r))
    assertEquals(scanner.position, 3)
    assert(scanner.lastMatch.isDefined)
    assertEquals(scanner.lastMatch.get.matched, "123")
  }

  test("isDone is true at end") {
    val scanner = SpanScanner("a")
    assert(!scanner.isDone)
    scanner.readChar()
    assert(scanner.isDone)
  }

  test("rest returns remaining text") {
    val scanner = SpanScanner("hello")
    scanner.position = 3
    assertEquals(scanner.rest, "lo")
  }

  test("state save/restore works for backtracking") {
    val scanner = SpanScanner("abcdef")
    scanner.readChar() // 'a'
    scanner.readChar() // 'b'
    val saved = scanner.state
    scanner.readChar() // 'c'
    scanner.readChar() // 'd'
    assertEquals(scanner.position, 4)
    scanner.state = saved
    assertEquals(scanner.position, 2)
    assertEquals(scanner.peekChar(), 'c'.toInt)
  }

  test("spanFrom creates span with correct positions") {
    val scanner = SpanScanner("hello world", "test.scss")
    val start   = scanner.state
    scanner.scan("hello")
    val span = scanner.spanFrom(start)
    assertEquals(span.text, "hello")
    assertEquals(span.start.offset, 0)
    assertEquals(span.end.offset, 5)
    assertEquals(span.start.line, 0)
    assertEquals(span.start.column, 0)
  }

  test("line/column tracking across newlines") {
    val scanner = SpanScanner("ab\ncd\nef")
    scanner.readChar() // a (0,0)
    scanner.readChar() // b (0,1)
    scanner.readChar() // \n (0,2 -> advances to 1,0)
    val start = scanner.state
    assertEquals(start.line, 1)
    assertEquals(start.column, 0)
    scanner.readChar() // c (1,0)
    scanner.readChar() // d (1,1)
    scanner.readChar() // \n -> advances to 2,0
    val mid = scanner.state
    assertEquals(mid.line, 2)
    assertEquals(mid.column, 0)
  }

  test("emptySpan creates zero-length span") {
    val scanner = SpanScanner("abc")
    scanner.readChar()
    val span = scanner.emptySpan
    assertEquals(span.length, 0)
    assertEquals(span.start.offset, 1)
    assertEquals(span.end.offset, 1)
  }

  test("substring extracts text range") {
    val scanner = SpanScanner("hello world")
    scanner.position = 8
    assertEquals(scanner.substring(6), "wo")
    assertEquals(scanner.substring(0, 5), "hello")
  }

  test("error throws StringScannerException with span") {
    val scanner = SpanScanner("abc", "test.scss")
    scanner.readChar()
    val ex = intercept[StringScannerException] {
      scanner.error("test error", 1, 2)
    }
    assertEquals(ex.span.start.offset, 1)
    assertEquals(ex.span.length, 2)
  }

  test("expect string throws on mismatch") {
    val scanner = SpanScanner("abc")
    intercept[StringScannerException] {
      scanner.expect("xyz")
    }
  }
}
