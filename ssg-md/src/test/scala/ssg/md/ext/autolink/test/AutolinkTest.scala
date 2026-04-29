/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-autolink/.../AutolinkTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package autolink
package test

import ssg.md.ext.autolink.AutolinkExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.TestUtils
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class AutolinkTest extends munit.FunSuite {
  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(TestUtils.NO_FILE_EOL, false)
    .set(Parser.EXTENSIONS, Collections.singleton(AutolinkExtension.create()))
    .toImmutable

  private val PARSER:   Parser       = Parser.builder(OPTIONS).build()
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build()

  private def assertRendering(input: String, expectedHtml: String): Unit = {
    val document   = PARSER.parse(input)
    val actualHtml = RENDERER.render(document)
    assertEquals(actualHtml, expectedHtml)
  }

  test("oneTextNode") {
    assertRendering(
      "foo http://one.org/ bar http://two.org/",
      "<p>foo <a href=\"http://one.org/\">http://one.org/</a> bar <a href=\"http://two.org/\">http://two.org/</a></p>\n"
    )
  }

  test("textNodeAndOthers") {
    assertRendering(
      "foo http://one.org/ bar `code` baz http://two.org/",
      "<p>foo <a href=\"http://one.org/\">http://one.org/</a> bar <code>code</code> baz <a href=\"http://two.org/\">http://two.org/</a></p>\n"
    )
  }

  test("tricky") {
    assertRendering(
      "http://example.com/one. Example 2 (see http://example.com/two). Example 3: http://example.com/foo_(bar)",
      "<p><a href=\"http://example.com/one\">http://example.com/one</a>. " +
        "Example 2 (see <a href=\"http://example.com/two\">http://example.com/two</a>). " +
        "Example 3: <a href=\"http://example.com/foo_(bar)\">http://example.com/foo_(bar)</a></p>\n"
    )
  }

  test("emailUsesMailto") {
    assertRendering(
      "foo@example.com",
      "<p><a href=\"mailto:foo@example.com\">foo@example.com</a></p>\n"
    )
  }

  test("emailWithTldNotLinked") {
    assertRendering("foo@com", "<p>foo@com</p>\n")
  }

  test("dontLinkTextWithinLinks") {
    assertRendering("<http://example.com>", "<p><a href=\"http://example.com\">http://example.com</a></p>\n")
  }
}
