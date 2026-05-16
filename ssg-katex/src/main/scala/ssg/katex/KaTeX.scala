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
 *   Idiom: TypeScript __VERSION__ -> ssg.katex.Version
 */
package ssg
package katex

import lowlevel.Nullable
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
  */
object KaTeX {

  /** Ensure all functions, environments, and macros are registered. */
  private def ensureRegistered(): Unit = {
    Functions.registerAll()
    Environments.registerAll()
    Macros.registerAll()
  }

  /** Current KaTeX version. */
  val version: String = Version

  /** Parse and build an expression, and return the markup for that.
    */
  def renderToString(
    expression: String,
    options:    Settings = new Settings()
  ): String = {
    ensureRegistered()
    val markup = renderToDomTree(expression, options).toMarkup()
    markup
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
