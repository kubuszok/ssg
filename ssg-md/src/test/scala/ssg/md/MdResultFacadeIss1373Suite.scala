/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md

import ssg.commons.Severity
import ssg.md.ext.gfm.strikethrough.{ StrikethroughExtension, SubscriptExtension }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.misc.Extension

/** Differential tests for the ISS-1373 error-contract facade `Markdown.renderResult` / `Markdown.parseResult` (docs/architecture/error-contracts.md §2.1).
  *
  * ssg-md is CommonMark-lenient — malformed markdown never fails, and there is NO error collection (the review's "collects errors" phrasing does not match the code). The one failure the doc's §2.1
  * adapter catches at the facade is `IllegalArgumentException` (code `"invalid-input"`); ssg-md has no `SourcePosition` row in the §1.3 table, so its failure diagnostic carries `position = None`.
  * Everything else (renderer bugs, JS resource loading) keeps propagating unchanged (rule 3), and the lenient never-fails parse semantics stay intact.
  *
  * The failure input reaches the caught `IllegalArgumentException` through the public `DataHolder` overload: two extensions that both register a delimiter processor on `'~'` —
  * `StrikethroughExtension` and `SubscriptExtension` (StrikethroughExtension.scala:37 / SubscriptExtension.scala:37 both call `customDelimiterProcessor`) — collide in
  * `InlineParserImpl.calculateDelimiterProcessors` / `addDelimiterProcessorForChar` (InlineParserImpl.scala:1729-1741), which throws `IllegalArgumentException("Delimiter processor conflict ...")`
  * while the `Parser` builds (Parser.scala:77-78). flexmark ships `StrikethroughSubscriptExtension` precisely to avoid this, so enabling both separate extensions at once is the documented invalid
  * configuration.
  *
  * These tests assert structure/values (severity, component, code, position, cause) rather than non-emptiness, plus legacy parity: the `*Result` facade produces the same bytes/Document as the
  * throwing entry point on the happy path, and the legacy entry point genuinely throws the same `IllegalArgumentException` the facade catches.
  */
final class MdResultFacadeIss1373Suite extends munit.FunSuite {

  private val sampleMarkdown: String =
    "# Title\n\nSome **bold** and a list:\n\n- a\n- b\n"

  // Markdown with a soft line break inside a paragraph (single newline, no blank line).
  private val softBreakMarkdown: String =
    "first line\nsecond line\n"

  // Two extensions that both claim the '~' delimiter char; enabling both is the invalid configuration
  // that makes the Parser throw IllegalArgumentException as it builds (see suite doc).
  private val conflictingOptions: DataHolder = {
    val extensions = new java.util.ArrayList[Extension]()
    extensions.add(StrikethroughExtension.create())
    extensions.add(SubscriptExtension.create())
    new MutableDataSet().set(Parser.EXTENSIONS, extensions)
  }

  test("ISS-1373: renderResult(markdown) is a clean success carrying the same bytes as render") {
    val result = Markdown.renderResult(sampleMarkdown)
    assert(result.isSuccess, s"expected clean success, got $result")
    assert(!result.isDegraded)
    assert(!result.isFailure)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value, Some(Markdown.render(sampleMarkdown)))
  }

  test("ISS-1373: renderResult(markdown, options) is a clean success and honors the options") {
    val options = new MutableDataSet().set(HtmlRenderer.SOFT_BREAK, "<br />\n")
    val result  = Markdown.renderResult(softBreakMarkdown, options)
    assert(result.isSuccess, s"expected clean success, got $result")
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value, Some(Markdown.render(softBreakMarkdown, options)))
    // Prove the option flows through: the default render differs.
    assert(result.value.exists(_.contains("<br />")), s"expected <br /> in output, got ${result.value}")
    assert(!Markdown.render(softBreakMarkdown).contains("<br />"), "default render must not contain <br />")
  }

  test("ISS-1373: parseResult(markdown) is a clean success whose Document renders like render") {
    val result = Markdown.parseResult(sampleMarkdown)
    assert(result.isSuccess, s"expected clean success, got $result")
    assertEquals(result.diagnostics, Vector.empty)
    val document = result.value.getOrElse(fail("expected a Document value"))
    val rendered = HtmlRenderer.builder().build().render(document)
    assertEquals(rendered, Markdown.render(sampleMarkdown))
  }

  test("ISS-1373: parseResult(markdown, options) is a clean success consistent with render(md, options)") {
    val options = new MutableDataSet().set(HtmlRenderer.SOFT_BREAK, "<br />\n")
    val result  = Markdown.parseResult(softBreakMarkdown, options)
    assert(result.isSuccess, s"expected clean success, got $result")
    val document = result.value.getOrElse(fail("expected a Document value"))
    val rendered = HtmlRenderer.builder(Nullable(options)).build().render(document)
    assertEquals(rendered, Markdown.render(softBreakMarkdown, options))
  }

  test("ISS-1373: renderResult(md, options) maps a thrown IllegalArgumentException to an Error failure diagnostic") {
    // Sanity: the legacy throwing entry point genuinely raises the IllegalArgumentException the facade catches.
    val thrown = intercept[IllegalArgumentException](Markdown.render("~~x~~", conflictingOptions))
    assert(thrown.getMessage.contains("Delimiter processor conflict"), s"unexpected message: ${thrown.getMessage}")

    val result = Markdown.renderResult("~~x~~", conflictingOptions)
    assert(result.isFailure, s"expected failure (value absent), got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val diagnostic = result.diagnostics.head
    assertEquals(diagnostic.severity, Severity.Error)
    assertEquals(diagnostic.component, "ssg-md")
    assertEquals(diagnostic.code, Some("invalid-input"))
    assertEquals(diagnostic.position, None)
    assert(diagnostic.message.contains("Delimiter processor conflict"), s"message: ${diagnostic.message}")
    assert(diagnostic.cause.exists(_.isInstanceOf[IllegalArgumentException]), s"cause: ${diagnostic.cause}")
  }

  test("ISS-1373: parseResult(md, options) maps a thrown IllegalArgumentException to an Error failure diagnostic") {
    // Sanity: the legacy throwing entry point genuinely raises the IllegalArgumentException the facade catches.
    intercept[IllegalArgumentException](Markdown.parse("~~x~~", conflictingOptions))

    val result = Markdown.parseResult("~~x~~", conflictingOptions)
    assert(result.isFailure, s"expected failure (value absent), got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val diagnostic = result.diagnostics.head
    assertEquals(diagnostic.severity, Severity.Error)
    assertEquals(diagnostic.component, "ssg-md")
    assertEquals(diagnostic.code, Some("invalid-input"))
    assertEquals(diagnostic.position, None)
    assert(diagnostic.cause.exists(_.isInstanceOf[IllegalArgumentException]), s"cause: ${diagnostic.cause}")
  }
}
