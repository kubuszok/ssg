/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package parse

import ssg.sass.SassFormatException

final class AtRootQueryParserSuite extends munit.FunSuite {

  test("parses (with: media)") {
    val q = AtRootQueryParser.parseQuery("(with: media)")
    assertEquals(q.include, true)
    assertEquals(q.names, Set("media"))
    assertEquals(q.excludesName("media"), false)
    assertEquals(q.excludesName("supports"), true)
  }

  test("parses (without: rule)") {
    val q = AtRootQueryParser.parseQuery("(without: rule)")
    assertEquals(q.include, false)
    assertEquals(q.names, Set("rule"))
    assertEquals(q.excludesStyleRules, true)
    assertEquals(q.excludesName("media"), false)
  }

  test("parses (with: media supports)") {
    val q = AtRootQueryParser.parseQuery("(with: media supports)")
    assertEquals(q.names, Set("media", "supports"))
    assertEquals(q.include, true)
    assertEquals(q.excludesName("media"), false)
    assertEquals(q.excludesName("supports"), false)
    assertEquals(q.excludesStyleRules, true)
  }

  test("parses (without: all)") {
    val q = AtRootQueryParser.parseQuery("(without: all)")
    assertEquals(q.names, Set("all"))
    assertEquals(q.include, false)
    assertEquals(q.excludesName("media"), true)
    assertEquals(q.excludesName("supports"), true)
    assertEquals(q.excludesStyleRules, true)
  }

  test("rejects garbage") {
    intercept[SassFormatException](AtRootQueryParser.parseQuery("garbage"))
    intercept[SassFormatException](AtRootQueryParser.parseQuery("(foo: bar)"))
  }

  test("tryParseQuery returns None on failure") {
    assertEquals(AtRootQueryParser.tryParseQuery("garbage"), None)
  }
}
