/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md

import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataSet

final class MarkdownFacadeIss1105Suite extends munit.FunSuite {

  private val sampleMarkdown: String =
    "# Title\n\nSome **bold** and a list:\n\n- a\n- b\n"

  // Markdown with a soft line break inside a paragraph (single newline, no blank line).
  // The SOFT_BREAK option controls how this renders.
  private val softBreakMarkdown: String =
    "first line\nsecond line\n"

  test("render() equals the manual two-builder dance (default)") {
    val facadeHtml = Markdown.render(sampleMarkdown)

    val manualHtml = {
      val doc = Parser.builder().build().parse(sampleMarkdown)
      HtmlRenderer.builder().build().render(doc)
    }

    assertEquals(facadeHtml, manualHtml)
    assert(facadeHtml.nonEmpty, "rendered HTML must not be empty")
    assert(facadeHtml.contains("<h1>"), "expected <h1> tag in output")
    assert(facadeHtml.contains("<strong>"), "expected <strong> tag in output")
    assert(facadeHtml.contains("<li>"), "expected <li> tag in output")
  }

  test("render(md, options) equals the manual options dance and honors options") {
    val options = MutableDataSet()
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n")

    // Use softBreakMarkdown which contains a soft line break inside a paragraph
    val facadeHtml = Markdown.render(softBreakMarkdown, options)

    val manualHtml = {
      val doc = Parser.builder(options).build().parse(softBreakMarkdown)
      HtmlRenderer.builder(Nullable(options)).build().render(doc)
    }

    assertEquals(facadeHtml, manualHtml)

    // Prove the option is actually honored: default render must differ
    val defaultHtml = Markdown.render(softBreakMarkdown)
    assertNotEquals(
      facadeHtml,
      defaultHtml,
      "SOFT_BREAK option should change the rendered output"
    )
    assert(facadeHtml.contains("<br />"), "expected <br /> in soft-break-configured output")
  }

  test("parse() returns a usable Document consistent with render()") {
    val doc               = Markdown.parse(sampleMarkdown)
    val renderedViaDoc    = HtmlRenderer.builder().build().render(doc)
    val renderedViaFacade = Markdown.render(sampleMarkdown)

    assertEquals(renderedViaDoc, renderedViaFacade)
  }

  test("parse(md, options) returns a usable Document consistent with render(md, options)") {
    val options = MutableDataSet()
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n")

    val doc               = Markdown.parse(softBreakMarkdown, options)
    val renderedViaDoc    = HtmlRenderer.builder(Nullable(options)).build().render(doc)
    val renderedViaFacade = Markdown.render(softBreakMarkdown, options)

    assertEquals(renderedViaDoc, renderedViaFacade)
  }
}
