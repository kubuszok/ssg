/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native convenience facade for ssg-md.
 * This is NOT a port of a flexmark-java class — flexmark itself
 * has no single-call facade; users perform the same Parser + HtmlRenderer
 * two-builder dance.  This object wraps that dance into one-liners.
 */
package ssg
package md

import ssg.commons.{ DiagResult, Diagnostic, Severity }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder

/** Recommended one-liner entry point for ssg-md.
  *
  * Wraps the flexmark `Parser` + `HtmlRenderer` two-step into simple `parse` and `render` methods. A single `DataHolder` (e.g. a `MutableDataSet`) configures both the parse and render phases when
  * using the options-accepting overloads.
  *
  * {{{
  * // Quick render with defaults
  * val html = Markdown.render("# Hello\n\nworld")
  *
  * // Parse only (returns a Document you can inspect or render later)
  * val doc = Markdown.parse("# Hello")
  *
  * // With options (one DataHolder flows to both Parser and HtmlRenderer)
  * val options = MutableDataSet()
  * options.set(HtmlRenderer.SOFT_BREAK, "<br />\n")
  * val html = Markdown.render(source, options)
  * }}}
  */
object Markdown {

  /** Parse a markdown string into a [[Document]] using default settings.
    *
    * Equivalent to `Parser.builder().build().parse(markdown)`.
    *
    * @param markdown
    *   the markdown source text
    * @return
    *   the parsed document tree
    */
  def parse(markdown: String): Document =
    Parser.builder().build().parse(markdown)

  /** Parse a markdown string, returning a diagnostics envelope (ISS-1373).
    *
    * Additive facade over [[parse(markdown:String)*]] per docs/architecture/error-contracts.md section 2.1: calls the existing throwing [[parse(markdown:String)*]] inside a `try` and catches ONLY the
    * module-native `IllegalArgumentException` (Parser.scala:108's contiguity guard, the delimiter-processor conflict at InlineParserImpl.scala:1729-1741, and the other structural invariant checks) —
    * mapping it to a `Severity.Error` failure `Diagnostic` with component `"ssg-md"` and code `"invalid-input"`, the native exception preserved as its cause. ssg-md has no `SourcePosition` row in the
    * section 1.3 table, so `position` stays `None`. Everything else (renderer bugs, Scala.js resource-loading failures) keeps propagating unchanged (rule 3), and the lenient never-fails parse
    * semantics are untouched — a clean parse is a `DiagResult.success`.
    *
    * @param markdown
    *   the markdown source text
    * @return
    *   a success carrying the parsed document, or a failure carrying one `"invalid-input"` diagnostic
    */
  def parseResult(markdown: String): DiagResult[Document] =
    try
      DiagResult.success(parse(markdown))
    catch {
      case e: IllegalArgumentException =>
        DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-md", e, code = Some("invalid-input")))
    }

  /** Parse a markdown string into a [[Document]] using the given options.
    *
    * Equivalent to `Parser.builder(options).build().parse(markdown)`.
    *
    * @param markdown
    *   the markdown source text
    * @param options
    *   a `DataHolder` (e.g. `MutableDataSet`) configuring the parser
    * @return
    *   the parsed document tree
    */
  def parse(markdown: String, options: DataHolder): Document =
    Parser.builder(options).build().parse(markdown)

  /** Parse a markdown string with the given options, returning a diagnostics envelope (ISS-1373).
    *
    * The `DataHolder` overload of [[parseResult(markdown:String)*]]: same `try`/catch shape and `"invalid-input"` mapping over the throwing
    * [[parse(markdown:String,options:ssg\.md\.util\.data\.DataHolder)*]] (docs/architecture/error-contracts.md section 2.1). Options can reach the caught `IllegalArgumentException` — e.g. enabling
    * two extensions that both claim one delimiter char makes the `Parser` throw as it builds (InlineParserImpl.scala:1729-1741).
    *
    * @param markdown
    *   the markdown source text
    * @param options
    *   a `DataHolder` (e.g. `MutableDataSet`) configuring the parser
    * @return
    *   a success carrying the parsed document, or a failure carrying one `"invalid-input"` diagnostic
    */
  def parseResult(markdown: String, options: DataHolder): DiagResult[Document] =
    try
      DiagResult.success(parse(markdown, options))
    catch {
      case e: IllegalArgumentException =>
        DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-md", e, code = Some("invalid-input")))
    }

  /** Parse and render a markdown string to HTML using default settings.
    *
    * Equivalent to the flexmark two-builder dance:
    * {{{
    * val doc  = Parser.builder().build().parse(markdown)
    * val html = HtmlRenderer.builder().build().render(doc)
    * }}}
    *
    * @param markdown
    *   the markdown source text
    * @return
    *   the rendered HTML string
    */
  def render(markdown: String): String = {
    val doc = Parser.builder().build().parse(markdown)
    HtmlRenderer.builder().build().render(doc)
  }

  /** Parse and render a markdown string to HTML, returning a diagnostics envelope (ISS-1373).
    *
    * Additive facade over [[render(markdown:String)*]] per docs/architecture/error-contracts.md section 2.1: calls the existing throwing [[render(markdown:String)*]] inside a `try` and catches ONLY
    * the module-native `IllegalArgumentException`, mapping it to a `Severity.Error` failure `Diagnostic` (component `"ssg-md"`, code `"invalid-input"`, `position = None`, native exception as cause).
    * Everything else keeps propagating (rule 3); the lenient never-fails render semantics are untouched — a clean render is a `DiagResult.success` carrying the same HTML bytes as
    * [[render(markdown:String)*]].
    *
    * @param markdown
    *   the markdown source text
    * @return
    *   a success carrying the rendered HTML, or a failure carrying one `"invalid-input"` diagnostic
    */
  def renderResult(markdown: String): DiagResult[String] =
    try
      DiagResult.success(render(markdown))
    catch {
      case e: IllegalArgumentException =>
        DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-md", e, code = Some("invalid-input")))
    }

  /** Parse and render a markdown string to HTML using the given options.
    *
    * The same `options` are passed to both `Parser.builder(options)` and `HtmlRenderer.builder(Nullable(options))`, faithfully mirroring flexmark's model where one `DataHolder` configures both
    * phases.
    *
    * @param markdown
    *   the markdown source text
    * @param options
    *   a `DataHolder` (e.g. `MutableDataSet`) configuring both parsing and rendering
    * @return
    *   the rendered HTML string
    */
  def render(markdown: String, options: DataHolder): String = {
    val doc = Parser.builder(options).build().parse(markdown)
    HtmlRenderer.builder(Nullable(options)).build().render(doc)
  }

  /** Parse and render a markdown string to HTML with the given options, returning a diagnostics envelope (ISS-1373).
    *
    * The `DataHolder` overload of [[renderResult(markdown:String)*]]: same `try`/catch shape and `"invalid-input"` mapping over the throwing
    * [[render(markdown:String,options:ssg\.md\.util\.data\.DataHolder)*]] (docs/architecture/error-contracts.md section 2.1). The same `options` flow to both `Parser.builder(options)` and
    * `HtmlRenderer.builder(Nullable(options))`, so a clean render is byte-identical to [[render(markdown:String,options:ssg\.md\.util\.data\.DataHolder)*]].
    *
    * @param markdown
    *   the markdown source text
    * @param options
    *   a `DataHolder` (e.g. `MutableDataSet`) configuring both parsing and rendering
    * @return
    *   a success carrying the rendered HTML, or a failure carrying one `"invalid-input"` diagnostic
    */
  def renderResult(markdown: String, options: DataHolder): DiagResult[String] =
    try
      DiagResult.success(render(markdown, options))
    catch {
      case e: IllegalArgumentException =>
        DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-md", e, code = Some("invalid-input")))
    }
}
