/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This is the main entry point for KaTeX. Here, we expose functions for
 * rendering expressions to markup strings.
 *
 * We also expose the ParseError class to check if errors thrown from KaTeX are
 * errors in the expression, or errors in internal handling.
 *
 * Original source: katex katex.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: katex default export -> KaTeX object
 *   Convention: DOM-dependent render() omitted (server-side only)
 *   Idiom: TypeScript __VERSION__ -> ssg.katex.UpstreamVersion
 */
package ssg
package katex

import lowlevel.Nullable
import ssg.commons.{ DiagResult, Diagnostic, Severity, SourcePosition }
import ssg.katex.build.{ BuildCommon, BuildTree }
import ssg.katex.data.Macros
import ssg.katex.environments.Environments
import ssg.katex.functions.{ FunctionDef, Functions }
import ssg.katex.parse.{ AnyParseNode, ParseTree }
import ssg.katex.tree.{ DomSpan, SymbolNode }

/** Main KaTeX API — Scala 3 port.
  *
  * Unlike the browser-targeted original, this port is server-side only:
  *   - `render()` (DOM node insertion) is omitted because there is no DOM.
  *   - `renderToString()` is the primary API for SSG usage.
  *   - `generateParseTree()` is available for inspection.
  *   - `renderToDomTree()` and `renderToHTMLTree()` produce in-memory trees that can be serialized to markup via `.toMarkup()`.
  *
  * For typical SSG usage, prefer the [[KaTeXOptions]]-based overload of `renderToString` — it provides a user-friendly, type-safe options API with named defaults (e.g. `displayMode`, `errorColor`,
  * `throwOnError`).
  */
object KaTeX {

  /** Ensure all functions, environments, and macros are registered. */
  private def ensureRegistered(): Unit = {
    Functions.registerAll()
    Environments.registerAll()
    Macros.registerAll()
  }

  /** Current KaTeX version. */
  val version: String = UpstreamVersion

  /** Parse and build an expression, and return the markup for that.
    *
    * This overload accepts the internal [[Settings]] type used by the parser and builder. For typical SSG usage, prefer the [[KaTeXOptions]]-based overload which provides user-friendly, type-safe
    * named defaults.
    */
  def renderToString(
    expression: String,
    options:    Settings = new Settings()
  ): String = {
    ensureRegistered()
    val markup = renderToDomTree(expression, options).toMarkup()
    markup
  }

  /** Parse and build an expression, and return the markup for that.
    *
    * This is the recommended entry point for typical SSG usage. [[KaTeXOptions]] provides a user-friendly, type-safe, discoverable options API with named defaults (e.g. `displayMode`, `errorColor`,
    * `throwOnError`), and is the preferred way to configure rendering.
    *
    * {{{
    * val html = KaTeX.renderToString("x^2", KaTeXOptions(displayMode = true))
    * }}}
    *
    * @param expression
    *   The LaTeX expression to render
    * @param options
    *   Rendering options — see [[KaTeXOptions]] for available fields
    * @return
    *   HTML markup string
    */
  def renderToString(expression: String, options: KaTeXOptions): String =
    renderToString(expression, options.toSettings)

  /** Parse and build an expression, returning a diagnostics envelope (ISS-1378).
    *
    * Additive facade over [[renderToString(expression:String,options:ssg\.katex\.Settings)*]] per docs/architecture/error-contracts.md section 2.6, wiring the throwing entry point into the shared
    * [[ssg.commons.DiagResult]] envelope. It lives in-module so it can reach the private [[renderError]] path directly and reproduce the SAME in-band `katex-error` span the legacy path emits, without
    * re-rendering by re-throwing.
    *
    * The render pipeline (`ParseTree.parseTree` then `BuildTree.buildTree`, exactly as [[renderToDomTree]]'s `try` body) runs inside a `try` that catches ONLY the module-native [[ParseError]]
    * (section 1.2 rule 3 — genuine bugs outside the KaTeX parse contract keep propagating):
    *
    *   - a clean render is a `DiagResult.success` carrying byte-for-byte the same markup [[renderToString(expression:String,options:ssg\.katex\.Settings)*]] produces;
    *   - a caught `ParseError` with `options.throwOnError == true` becomes a failure carrying one `Severity.Error` [[ssg.commons.Diagnostic]] (component `"ssg-katex"`, code `"parse-error"`, position
    *     from [[positionOf]], the native `ParseError` preserved as `cause`);
    *   - a caught `ParseError` with `options.throwOnError == false` becomes a DEGRADED result: the value is the [[renderError]] `katex-error` span markup (byte-identical to what the legacy entry
    *     point renders in-band), carried alongside the same diagnostic (section 1.1 — substitute output WITH an Error diagnostic).
    *
    * `Settings.throwOnError` semantics and the error-HTML markup are untouched — callers get the same bytes the legacy entry point produces AND a machine-readable diagnostic.
    *
    * @param expression
    *   The LaTeX expression to render
    * @param options
    *   Rendering options — the internal [[Settings]] type
    * @return
    *   a success carrying the HTML markup, a degraded result carrying the in-band error span, or a failure carrying one `"parse-error"` diagnostic
    */
  def renderToStringResult(
    expression: String,
    options:    Settings = new Settings()
  ): DiagResult[String] = {
    ensureRegistered()
    try {
      val tree   = ParseTree.parseTree(expression, options)
      val markup = BuildTree.buildTree(tree, expression, options).toMarkup()
      DiagResult.success(markup)
    } catch {
      case error: ParseError =>
        val diagnostic = Diagnostic.fromThrowable(Severity.Error, "ssg-katex", error, position = positionOf(error), code = Some("parse-error"))
        if (options.throwOnError) {
          DiagResult.failure(diagnostic)
        } else {
          // Same in-band katex-error span the legacy renderToDomTree path emits;
          // renderError access is in-module, so we do not re-render by re-throwing.
          DiagResult.degraded(renderError(error, expression, options).toMarkup(), diagnostic)
        }
    }
  }

  /** Parse and build an expression, returning a diagnostics envelope (ISS-1378).
    *
    * The [[KaTeXOptions]] overload of [[renderToStringResult(expression:String,options:ssg\.katex\.Settings)*]]: bridges through [[KaTeXOptions.toSettings]] exactly as the throwing
    * [[renderToString(expression:String,options:ssg\.katex\.KaTeXOptions)*]] overload does, so a clean render is byte-identical.
    *
    * @param expression
    *   The LaTeX expression to render
    * @param options
    *   Rendering options — see [[KaTeXOptions]] for available fields
    * @return
    *   a success carrying the HTML markup, a degraded result carrying the in-band error span, or a failure carrying one `"parse-error"` diagnostic
    */
  def renderToStringResult(expression: String, options: KaTeXOptions): DiagResult[String] =
    renderToStringResult(expression, options.toSettings)

  /** Maps a [[ParseError]]'s native 0-based char offset into a [[ssg.commons.SourcePosition]] per the section 1.3 katex row: `offset = e.position`, `endOffset = e.position + e.length` when BOTH the
    * `position` and `length` `Nullable`s are present; line/column never exist for KaTeX, so they stay `None`, and an absent position (e.g. a `ParseError` thrown with no token) maps to `None`.
    */
  private def positionOf(error: ParseError): Option[SourcePosition] =
    (error.position.toOption, error.length.toOption) match {
      case (Some(position), Some(length)) => Some(SourcePosition.offsetRange(position, position + length))
      case _                              => None
    }

  /** Parse an expression and return the parse tree.
    */
  def generateParseTree(
    expression: String,
    options:    Settings = new Settings()
  ): Array[AnyParseNode] = {
    ensureRegistered()
    ParseTree.parseTree(expression, options)
  }

  /** If the given error is a KaTeX ParseError and options.throwOnError is false, renders the invalid LaTeX as a span with hover title giving the KaTeX error message. Otherwise, simply throws the
    * error.
    */
  private def renderError(
    error:      Throwable,
    expression: String,
    options:    Settings
  ): DomSpan = {
    if (options.throwOnError || !error.isInstanceOf[ParseError]) {
      throw error
    }
    import scala.collection.mutable.ArrayBuffer
    val node = BuildCommon.makeSpan(ArrayBuffer("katex-error"), ArrayBuffer(new SymbolNode(expression)))
    node.setAttribute("title", error.toString)
    node.setAttribute("style", s"color:${options.errorColor}")
    node
  }

  /** Generates and returns the katex build tree. This is used for advanced use cases (like rendering to custom output).
    */
  def renderToDomTree(
    expression: String,
    options:    Settings = new Settings()
  ): DomSpan = {
    ensureRegistered()
    try {
      val tree = ParseTree.parseTree(expression, options)
      BuildTree.buildTree(tree, expression, options)
    } catch {
      case error: Throwable =>
        renderError(error, expression, options)
    }
  }

  /** Generates and returns the katex build tree, with just HTML (no MathML). This is used for advanced use cases (like rendering to custom output).
    */
  def renderToHTMLTree(
    expression: String,
    options:    Settings = new Settings()
  ): DomSpan = {
    ensureRegistered()
    try {
      val tree = ParseTree.parseTree(expression, options)
      BuildTree.buildHTMLTree(tree, expression, options)
    } catch {
      case error: Throwable =>
        renderError(error, expression, options)
    }
  }

  // --- Aliases matching the original JS API ---

  /** Parses the given LaTeX into KaTeX's internal parse tree structure, without rendering to HTML or MathML.
    *
    * NOTE: This method is not currently recommended for public use. The internal tree representation is unstable and is very likely to change. Use at your own risk.
    */
  def __parse(
    expression: String,
    options:    Settings = new Settings()
  ): Array[AnyParseNode] = generateParseTree(expression, options)

  /** Renders the given LaTeX into an HTML+MathML internal DOM tree representation, without flattening that representation to a string.
    *
    * NOTE: This method is not currently recommended for public use. The internal tree representation is unstable and is very likely to change. Use at your own risk.
    */
  def __renderToDomTree(
    expression: String,
    options:    Settings = new Settings()
  ): DomSpan = renderToDomTree(expression, options)

  /** Renders the given LaTeX into an HTML internal DOM tree representation, without MathML and without flattening that representation to a string.
    *
    * NOTE: This method is not currently recommended for public use. The internal tree representation is unstable and is very likely to change. Use at your own risk.
    */
  def __renderToHTMLTree(
    expression: String,
    options:    Settings = new Settings()
  ): DomSpan = renderToHTMLTree(expression, options)

  /** Extends internal font metrics object with a new object each key in the new object represents a font name.
    */
  def __setFontMetrics(fontName: String, metrics: Map[Int, Array[Double]]): Unit =
    ssg.katex.data.FontMetrics.setFontMetrics(fontName, metrics)

  /** Adds a new symbol to builtin symbols table.
    */
  def __defineSymbol(
    mode:              String,
    font:              String,
    group:             String,
    replace:           Nullable[String],
    name:              String,
    acceptUnicodeChar: Boolean = false
  ): Unit =
    ssg.katex.data.Symbols.defineSymbol(mode, font, group, replace, name, acceptUnicodeChar)

  /** Adds a new function to builtin function list, which directly produce parse tree elements and have their own html/mathml builders.
    */
  def __defineFunction(spec: ssg.katex.functions.FunctionDefSpec): Unit =
    FunctionDef.defineFunction(spec)

  /** Adds a new macro to builtin macro list.
    */
  def __defineMacro(name: String, body: MacroDefinition): Unit =
    MacroDef.defineMacro(name, body)
}
