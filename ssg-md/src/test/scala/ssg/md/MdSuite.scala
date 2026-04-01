/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package md

final class MdSuite extends munit.FunSuite {

  test("ssg-md module loads") {
    assertEquals(Version, "0.1.0-SNAPSHOT")
  }

  test("parser can be created") {
    val parser = ssg.md.parser.Parser.builder().build()
    assert(parser != null)
  }

  test("parser can parse plain text") {
    val parser = ssg.md.parser.Parser.builder().build()
    val doc = parser.parse("hello")
    assert(doc != null)
    assert(doc.hasChildren)
  }

  test("renderer can be created") {
    val renderer = ssg.md.html.HtmlRenderer.builder().build()
    assert(renderer != null)
  }

  test("renderer can render plain text") {
    val parser = ssg.md.parser.Parser.builder().build()
    val renderer = ssg.md.html.HtmlRenderer.builder().build()
    val doc = parser.parse("hello")
    val html = renderer.render(doc)
    assertEquals(html, "<p>hello</p>\n")
  }

  test("parser can parse emphasis") {
    val parser = ssg.md.parser.Parser.builder().build()
    val renderer = ssg.md.html.HtmlRenderer.builder().build()
    val doc = parser.parse("hello **world**")
    val html = renderer.render(doc)
    assertEquals(html, "<p>hello <strong>world</strong></p>\n")
  }
}
