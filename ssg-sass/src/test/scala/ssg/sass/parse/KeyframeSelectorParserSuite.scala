/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package parse

import ssg.sass.SassFormatException

final class KeyframeSelectorParserSuite extends munit.FunSuite {

  test("parses single percentage") {
    assertEquals(KeyframeSelectorParser.parseList("0%"), List("0%"))
    assertEquals(KeyframeSelectorParser.parseList("50%"), List("50%"))
    assertEquals(KeyframeSelectorParser.parseList("100%"), List("100%"))
  }

  test("normalizes from -> 0%") {
    assertEquals(KeyframeSelectorParser.parseList("from"), List("0%"))
    assertEquals(KeyframeSelectorParser.parseList("FROM"), List("0%"))
  }

  test("normalizes to -> 100%") {
    assertEquals(KeyframeSelectorParser.parseList("to"), List("100%"))
    assertEquals(KeyframeSelectorParser.parseList("To"), List("100%"))
  }

  test("parses comma-separated selectors") {
    assertEquals(KeyframeSelectorParser.parseList("0%, 100%"), List("0%", "100%"))
    assertEquals(KeyframeSelectorParser.parseList("from, to"), List("0%", "100%"))
  }

  test("parses fractional percentage") {
    assertEquals(KeyframeSelectorParser.parseList("12.5%"), List("12.5%"))
  }

  test("rejects out-of-range percentage") {
    intercept[SassFormatException](KeyframeSelectorParser.parseList("101%"))
    intercept[SassFormatException](KeyframeSelectorParser.parseList("150%"))
  }

  test("rejects garbage") {
    intercept[SassFormatException](KeyframeSelectorParser.parseList("foo"))
    intercept[SassFormatException](KeyframeSelectorParser.parseList("50"))
  }
}
