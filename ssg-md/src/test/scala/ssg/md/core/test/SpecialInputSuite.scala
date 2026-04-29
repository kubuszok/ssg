/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/SpecialInputTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.{ Strings, TestUtils }
import ssg.md.util.data.MutableDataSet

import scala.language.implicitConversions

final class SpecialInputSuite extends munit.FunSuite {

  private val options = new MutableDataSet().set(TestUtils.NO_FILE_EOL, false).toImmutable

  private def assertRendering(source: String, expectedHtml: String): Unit = {
    val parser   = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()
    val document = parser.parse(source)
    val html     = renderer.render(document)
    assertEquals(html, expectedHtml)
  }

  test("empty") {
    assertRendering("", "")
  }

  test("nullCharacterShouldBeReplaced") {
    assertRendering("foo\u0000bar", "<p>foo\uFFFDbar</p>\n")
  }

  test("nullCharacterEntityShouldBeReplaced") {
    assertRendering("foo&#0;bar", "<p>foo\uFFFDbar</p>\n")
  }

  test("crLfAsLineSeparatorShouldBeParsed") {
    assertRendering("foo\r\nbar", "<p>foo\nbar</p>\n")
  }

  test("crLfAtEndShouldBeParsed") {
    assertRendering("foo\r\n", "<p>foo</p>\n")
  }

  test("mixedLineSeparators") {
    assertRendering("- a\n- b\r- c\r\n- d", "<ul>\n<li>a</li>\n<li>b</li>\n<li>c</li>\n<li>d</li>\n</ul>\n")
    assertRendering("a\n\nb\r\rc\r\n\r\nd\n\re", "<p>a</p>\n<p>b</p>\n<p>c</p>\n<p>d</p>\n<p>e</p>\n")
  }

  test("surrogatePair") {
    assertRendering("surrogate pair: \uD834\uDD1E", "<p>surrogate pair: \uD834\uDD1E</p>\n")
  }

  test("surrogatePairInLinkDestination") {
    assertRendering("[title](\uD834\uDD1E)", "<p><a href=\"\uD834\uDD1E\">title</a></p>\n")
  }

  test("indentedCodeBlockWithMixedTabsAndSpaces") {
    assertRendering("    foo\n\tbar", "<pre><code>foo\nbar\n</code></pre>\n")
  }

  test("tightListInBlockQuote") {
    assertRendering("> *\n> * a", "<blockquote>\n<ul>\n<li></li>\n<li>a</li>\n</ul>\n</blockquote>\n")
  }

  test("looseListInBlockQuote") {
    // Second line in block quote is considered blank for purpose of loose list
    assertRendering("> *\n>\n> * a", "<blockquote>\n<ul>\n<li></li>\n<li>\n<p>a</p>\n</li>\n</ul>\n</blockquote>\n")
  }

  test("lineWithOnlySpacesAfterListBullet") {
    assertRendering("-  \n  \n  foo\n", "<ul>\n<li></li>\n</ul>\n<p>foo</p>\n")
  }

  test("listWithTwoSpacesForFirstBullet") {
    // We have two spaces after the bullet, but no content. With content, the next line would be required
    assertRendering("*  \n  foo\n", "<ul>\n<li>foo</li>\n</ul>\n")
  }

  test("orderedListMarkerOnly") {
    assertRendering("2.", "<ol start=\"2\">\n<li></li>\n</ol>\n")
  }

  test("columnIsInTabOnPreviousLine") {
    assertRendering(
      "- foo\n\n\tbar\n\n# baz\n",
      "<ul>\n<li>\n<p>foo</p>\n<p>bar</p>\n</li>\n</ul>\n<h1>baz</h1>\n"
    )
    assertRendering(
      "- foo\n\n\tbar\n# baz\n",
      "<ul>\n<li>\n<p>foo</p>\n<p>bar</p>\n</li>\n</ul>\n<h1>baz</h1>\n"
    )
  }

  test("linkLabelWithBracket") {
    assertRendering("[a[b]\n\n[a[b]: /", "<p>[a[b]</p>\n<p>[a[b]: /</p>\n")
    assertRendering("[a]b]\n\n[a]b]: /", "<p>[a]b]</p>\n<p>[a]b]: /</p>\n")
    assertRendering("[a[b]]\n\n[a[b]]: /", "<p>[a[b]]</p>\n<p>[a[b]]: /</p>\n")
  }

  test("linkLabelLength") {
    val label1 = Strings.repeat("a", 999)
    assertRendering("[foo][" + label1 + "]\n\n[" + label1 + "]: /", "<p><a href=\"/\">foo</a></p>\n")
    assertRendering(
      "[foo][x" + label1 + "]\n\n[x" + label1 + "]: /",
      "<p>[foo][x" + label1 + "]</p>\n<p>[x" + label1 + "]: /</p>\n"
    )
    assertRendering(
      "[foo][\n" + label1 + "]\n\n[\n" + label1 + "]: /",
      "<p>[foo][\n" + label1 + "]</p>\n<p>[\n" + label1 + "]: /</p>\n"
    )

    val label2 = Strings.repeat("a\n", 499)
    assertRendering("[foo][" + label2 + "]\n\n[" + label2 + "]: /", "<p><a href=\"/\">foo</a></p>\n")
    assertRendering(
      "[foo][12" + label2 + "]\n\n[12" + label2 + "]: /",
      "<p>[foo][12" + label2 + "]</p>\n<p>[12" + label2 + "]: /</p>\n"
    )
  }

  test("manyUnderscores") {
    assertRendering(Strings.repeat("_", 1000), "<hr />\n")
  }
}
