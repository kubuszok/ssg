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
}
