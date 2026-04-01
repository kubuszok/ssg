/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests for cross-platform regex compatibility.
 * Exercises edge cases of patterns that originally used JVM-only regex features:
 * lookaheads, \p{} Unicode categories, character class intersection (&&).
 */
package ssg
package md
package core
package test

import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser

import scala.language.implicitConversions

final class RegexCompatibilityTest extends munit.FunSuite {

  private def render(markdown: String): String = {
    val parser = Parser.builder().build()
    val renderer = HtmlRenderer.builder().build()
    val doc = parser.parse(markdown)
    renderer.render(doc)
  }

  // ---------------------------------------------------------------------------
  // 1. Lookahead patterns — list markers requiring space/tab after them
  // Tests LIST_ITEM_MARKER patterns which use (?=[ \t]) and (?= |\t|$)
  // ---------------------------------------------------------------------------

  test("bullet list: dash followed by space is a list item") {
    assertEquals(render("- item"), "<ul>\n<li>item</li>\n</ul>\n")
  }

  test("bullet list: dash without space is NOT a list item") {
    // "-item" should be a paragraph, not a list
    assertEquals(render("-item"), "<p>-item</p>\n")
  }

  test("bullet list: asterisk followed by space is a list item") {
    assertEquals(render("* item"), "<ul>\n<li>item</li>\n</ul>\n")
  }

  test("bullet list: plus followed by tab is a list item") {
    assertEquals(render("+\titem"), "<ul>\n<li>item</li>\n</ul>\n")
  }

  test("ordered list: digit-dot-space is a list item") {
    assertEquals(render("1. item"), "<ol>\n<li>item</li>\n</ol>\n")
  }

  test("ordered list: digit-dot without space is NOT a list item") {
    // "1.item" should be a paragraph, not a list
    assertEquals(render("1.item"), "<p>1.item</p>\n")
  }

  test("ordered list: multi-digit marker") {
    assertEquals(render("123. item"), "<ol start=\"123\">\n<li>item</li>\n</ol>\n")
  }

  test("bullet list: marker at end of line (no content)") {
    // A dash followed by end-of-line should still be a list marker
    assertEquals(render("-\n"), "<ul>\n<li></li>\n</ul>\n")
  }

  // ---------------------------------------------------------------------------
  // 2. Negative lookahead in link destinations — space followed by non-quote
  // Tests LINK_DESTINATION_ANGLES_SPC which uses (?![\"'])
  // ---------------------------------------------------------------------------

  test("angle bracket link with no spaces") {
    assertEquals(render("[link](<http://example.com>)"), "<p><a href=\"http://example.com\">link</a></p>\n")
  }

  test("angle bracket link with escaped chars") {
    assertEquals(render("[link](<url\\>escaped>)"), "<p><a href=\"url&gt;escaped\">link</a></p>\n")
  }

  // ---------------------------------------------------------------------------
  // 3. Fenced code — lookahead in opening/closing fence
  // Tests OPENING_FENCE (?!.*`) and CLOSING_FENCE (?=[ \t]*$)
  // ---------------------------------------------------------------------------

  test("fenced code: backtick fence opens and closes") {
    val md = "```\ncode\n```"
    assertEquals(render(md), "<pre><code>code\n</code></pre>\n")
  }

  test("fenced code: tilde fence opens and closes") {
    val md = "~~~\ncode\n~~~"
    assertEquals(render(md), "<pre><code>code\n</code></pre>\n")
  }

  test("fenced code: backtick in info string prevents opening") {
    // A line like ```not`code``` should not open a fenced code block because
    // the info string contains a backtick
    val md = "```not`code```\n\nparagraph"
    val html = render(md)
    // Should not produce a <pre><code> block — backtick in info string is illegal
    assert(!html.contains("<pre><code>"), s"Should not open fenced code with backtick in info: $html")
  }

  test("fenced code: closing fence must have only whitespace after") {
    // A closing fence with content after it should not close the block
    val md = "```\ncode\n``` not closing\nmore code\n```"
    assertEquals(render(md), "<pre><code>code\n``` not closing\nmore code\n</code></pre>\n")
  }

  test("fenced code: closing fence with trailing spaces") {
    val md = "```\ncode\n```   "
    assertEquals(render(md), "<pre><code>code\n</code></pre>\n")
  }

  // ---------------------------------------------------------------------------
  // 4. Unicode punctuation (\p{Pc} etc.) — emphasis around Unicode punctuation
  // Tests ST_PUNCTUATION and related patterns
  // ---------------------------------------------------------------------------

  test("emphasis: basic asterisk emphasis") {
    assertEquals(render("*hello*"), "<p><em>hello</em></p>\n")
  }

  test("emphasis: asterisk emphasis in parentheses") {
    // Parens are open/close punctuation (\p{Ps}/\p{Pe}), emphasis should work
    assertEquals(render("(*bold*)"), "<p>(<em>bold</em>)</p>\n")
  }

  test("emphasis: underscore emphasis") {
    assertEquals(render("_hello_"), "<p><em>hello</em></p>\n")
  }

  test("emphasis: underscore in middle of word should not emphasize") {
    // Underscore is \p{Pc} connector punctuation — flanking rules apply
    assertEquals(render("foo_bar_baz"), "<p>foo_bar_baz</p>\n")
  }

  test("emphasis: strong emphasis") {
    assertEquals(render("**hello**"), "<p><strong>hello</strong></p>\n")
  }

  // ---------------------------------------------------------------------------
  // 5. Unicode whitespace (\p{Zs}) — non-breaking space handling
  // Tests ST_UNICODE_WHITESPACE_CHAR which uses \p{Zs}
  // ---------------------------------------------------------------------------

  test("unicode whitespace: NBSP in text is preserved") {
    // NBSP (\u00A0) should appear in output — it is a space separator (\p{Zs})
    val md = "hello\u00A0world"
    val html = render(md)
    assert(html.contains("hello\u00A0world") || html.contains("hello&nbsp;world"),
      s"NBSP should be preserved in output: $html")
  }

  test("hard line break with trailing spaces") {
    // Two trailing spaces create a hard line break
    val md = "hello  \nworld"
    assertEquals(render(md), "<p>hello<br />\nworld</p>\n")
  }

  // ---------------------------------------------------------------------------
  // 6. Character class intersection — punctuation open vs close vs neither
  // Tests ST_PUNCTUATION_OPEN, ST_PUNCTUATION_CLOSE, ST_PUNCTUATION_ONLY
  // which use [...]&&[^...] Java regex syntax
  // ---------------------------------------------------------------------------

  test("emphasis after open paren") {
    assertEquals(render("(*text*)"), "<p>(<em>text</em>)</p>\n")
  }

  test("emphasis before close paren") {
    assertEquals(render("(*text*)"), "<p>(<em>text</em>)</p>\n")
  }

  test("emphasis after open bracket") {
    // Square brackets are open/close punctuation
    assertEquals(render("[*text*]"), "<p>[<em>text</em>]</p>\n")
  }

  test("emphasis around colon and semicolon") {
    // : and ; are \p{Po} (other punctuation)
    assertEquals(render("*a:b;c*"), "<p><em>a:b;c</em></p>\n")
  }

  test("strong emphasis within sentence") {
    assertEquals(render("a **b** c"), "<p>a <strong>b</strong> c</p>\n")
  }

  test("emphasis with exclamation and question marks") {
    // ! and ? are \p{Po} (other punctuation)
    assertEquals(render("*hello!*"), "<p><em>hello!</em></p>\n")
  }
}
