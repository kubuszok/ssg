/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/.../StrikethroughTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package gfm
package strikethrough
package test

import ssg.md.ext.gfm.strikethrough.{ Strikethrough, StrikethroughExtension }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.TestUtils
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class StrikethroughTest extends munit.FunSuite {
  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(TestUtils.NO_FILE_EOL, false)
    .set(Parser.EXTENSIONS, Collections.singleton(StrikethroughExtension.create()))
    .toImmutable

  private val PARSER:   Parser       = Parser.builder(OPTIONS).build()
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build()

  private def assertRendering(input: String, expectedHtml: String): Unit = {
    val document   = PARSER.parse(input)
    val actualHtml = RENDERER.render(document)
    assertEquals(actualHtml, expectedHtml)
  }

  test("oneTildeIsNotEnough") {
    assertRendering("~foo~", "<p>~foo~</p>\n")
  }

  test("twoTildesYay") {
    assertRendering("~~foo~~", "<p><del>foo</del></p>\n")
  }

  test("fourTildesNope") {
    assertRendering("foo ~~~~", "<p>foo ~~~~</p>\n")
  }

  test("unmatched") {
    assertRendering("~~foo", "<p>~~foo</p>\n")
    assertRendering("foo~~", "<p>foo~~</p>\n")
  }

  test("threeInnerThree") {
    assertRendering("~~~foo~~~", "<p>~<del>foo</del>~</p>\n")
  }

  test("twoInnerThree") {
    assertRendering("~~foo~~~", "<p><del>foo</del>~</p>\n")
  }

  test("tildesInside") {
    assertRendering("~~foo~bar~~", "<p><del>foo~bar</del></p>\n")
    assertRendering("~~foo~~bar~~", "<p><del>foo</del>bar~~</p>\n")
    assertRendering("~~foo~~~bar~~", "<p><del>foo</del>~bar~~</p>\n")
    assertRendering("~~foo~~~~bar~~", "<p><del>foo</del><del>bar</del></p>\n")
    assertRendering("~~foo~~~~~bar~~", "<p><del>foo</del>~<del>bar</del></p>\n")
    assertRendering("~~foo~~~~~~bar~~", "<p><del>foo</del>~~<del>bar</del></p>\n")
    assertRendering("~~foo~~~~~~~bar~~", "<p><del>foo</del>~~~<del>bar</del></p>\n")
  }

  test("strikethroughWholeParagraphWithOtherDelimiters") {
    assertRendering(
      "~~Paragraph with *emphasis* and __strong emphasis__~~",
      "<p><del>Paragraph with <em>emphasis</em> and <strong>strong emphasis</strong></del></p>\n"
    )
  }

  test("insideBlockQuote") {
    assertRendering("> strike ~~that~~", "<blockquote>\n<p>strike <del>that</del></p>\n</blockquote>\n")
  }

  test("delimited") {
    val document      = PARSER.parse("~~foo~~")
    val strikethrough = document.firstChild.get.firstChild.get.asInstanceOf[Strikethrough]
    assertEquals(strikethrough.openingMarker.toString, "~~")
    assertEquals(strikethrough.closingMarker.toString, "~~")
  }
}
