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

  test("preserves from as-is") {
    assertEquals(KeyframeSelectorParser.parseList("from"), List("from"))
  }

  test("preserves to as-is") {
    assertEquals(KeyframeSelectorParser.parseList("to"), List("to"))
  }

  test("parses comma-separated selectors") {
    assertEquals(KeyframeSelectorParser.parseList("0%, 100%"), List("0%", "100%"))
    assertEquals(KeyframeSelectorParser.parseList("from, to"), List("from", "to"))
  }

  test("parses fractional percentage") {
    assertEquals(KeyframeSelectorParser.parseList("12.5%"), List("12.5%"))
  }

  test("parses percentage with + prefix") {
    assertEquals(KeyframeSelectorParser.parseList("+50%"), List("+50%"))
  }

  test("parses percentage with scientific notation") {
    assertEquals(KeyframeSelectorParser.parseList("1e2%"), List("1e2%"))
    assertEquals(KeyframeSelectorParser.parseList("5e-1%"), List("5e-1%"))
  }

  test("does not reject out-of-range percentage (dart-sass compat)") {
    // dart-sass does not validate 0-100 range at parse time
    assertEquals(KeyframeSelectorParser.parseList("101%"), List("101%"))
    assertEquals(KeyframeSelectorParser.parseList("150%"), List("150%"))
  }

  test("rejects garbage") {
    intercept[SassFormatException](KeyframeSelectorParser.parseList("foo"))
    intercept[SassFormatException](KeyframeSelectorParser.parseList("50"))
  }
}
