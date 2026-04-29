/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package parse

import ssg.sass.SassFormatException

final class MediaQueryParserSuite extends munit.FunSuite {

  test("parses bare type query") {
    val q = MediaQueryParser.parseList("screen")
    assertEquals(q.length, 1)
    assertEquals(q.head.type_, Some("screen"))
    assertEquals(q.head.modifier, None)
    assertEquals(q.head.conditions, Nil)
  }

  test("parses 'all' and 'print' types") {
    assertEquals(MediaQueryParser.parseList("all").head.type_, Some("all"))
    assertEquals(MediaQueryParser.parseList("print").head.type_, Some("print"))
  }

  test("parses condition-only query") {
    val q = MediaQueryParser.parseList("(max-width: 600px)")
    assertEquals(q.length, 1)
    assertEquals(q.head.type_, None)
    assertEquals(q.head.conditions, List("(max-width: 600px)"))
  }

  test("parses min-width feature query") {
    val q = MediaQueryParser.parseList("(min-width: 768px)")
    assertEquals(q.head.conditions, List("(min-width: 768px)"))
  }

  test("parses type and condition") {
    val q = MediaQueryParser.parseList("screen and (max-width: 600px)")
    assertEquals(q.head.type_, Some("screen"))
    assertEquals(q.head.conditions, List("(max-width: 600px)"))
  }

  test("parses compound conditions") {
    val q = MediaQueryParser.parseList("(orientation: landscape) and (color)")
    assertEquals(q.head.type_, None)
    assertEquals(q.head.conditions, List("(orientation: landscape)", "(color)"))
  }

  test("parses comma-separated query list") {
    val q = MediaQueryParser.parseList("screen, print")
    assertEquals(q.length, 2)
    assertEquals(q(0).type_, Some("screen"))
    assertEquals(q(1).type_, Some("print"))
  }

  test("parses 'not' modifier") {
    val q = MediaQueryParser.parseList("not screen")
    assertEquals(q.head.modifier, Some("not"))
    assertEquals(q.head.type_, Some("screen"))
  }

  test("parses 'only' modifier with conditions") {
    val q = MediaQueryParser.parseList("only screen and (color)")
    assertEquals(q.head.modifier, Some("only"))
    assertEquals(q.head.type_, Some("screen"))
    assertEquals(q.head.conditions, List("(color)"))
  }

  test("rejects garbage") {
    intercept[SassFormatException](MediaQueryParser.parseList("@@@"))
  }

  test("tryParseList returns None on failure") {
    assertEquals(MediaQueryParser.tryParseList("@@@"), None)
  }
}
