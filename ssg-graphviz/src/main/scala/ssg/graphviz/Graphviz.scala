/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main entry point for the Graphviz DOT renderer.
 */
package ssg
package graphviz

import ssg.commons.{ DiagResult, Diagnostic, Severity, SourcePosition }
import ssg.graphviz.parse.{ DotGraph, DotParseException, DotParser, DotScanner }
import ssg.graphviz.render.DotRenderer

object Graphviz {

  /** Parses a DOT language string into an AST. */
  def parse(input: String): DotGraph = {
    val tokens = DotScanner(input).scan()
    DotParser(tokens).parse()
  }

  /** Parses and renders a DOT language string to an SVG string. */
  def render(input: String, config: GraphvizConfig = GraphvizConfig()): String = {
    val ast = parse(input)
    DotRenderer.render(ast, config)
  }

  /** Additive `DiagResult` envelope over [[parse]] (docs/architecture/error-contracts.md §2.8).
    *
    * Catches ONLY the module-native [[DotParseException]] (a lexical/syntactic failure — never a blanket catch, §1.2 rule 3; genuine bugs keep propagating) and maps it to a [[DiagResult.failure]]:
    * one `Severity.Error` diagnostic, component `"ssg-graphviz"`, code `"parse-error"`, the native exception preserved as cause (§1.2 rule 5), and the §1.3 graphviz-row position (`line`/`col` are
    * both 1-based `Token` fields, copied verbatim with no `+1`). A valid graph is a clean success carrying the AST.
    */
  def parseResult(input: String): DiagResult[DotGraph] =
    try DiagResult.success(parse(input))
    catch {
      case e: DotParseException => DiagResult.failure(parseErrorDiagnostic(e))
    }

  /** Additive `DiagResult` envelope over [[render]] (docs/architecture/error-contracts.md §2.8).
    *
    * Same specific [[DotParseException]] catch and mapping as [[parseResult]] — parse/lex failures short-circuit before rendering, so the SVG is absent (a failure); a valid graph is a clean success
    * whose SVG byte-equals [[render]]. ssg-graphviz renders no substitute artifact on failure, so there is no degraded state.
    */
  def renderResult(input: String, config: GraphvizConfig = GraphvizConfig()): DiagResult[String] =
    try DiagResult.success(render(input, config))
    catch {
      case e: DotParseException => DiagResult.failure(parseErrorDiagnostic(e))
    }

  private def parseErrorDiagnostic(e: DotParseException): Diagnostic =
    Diagnostic.fromThrowable(
      Severity.Error,
      "ssg-graphviz",
      e,
      position = Some(SourcePosition.lineColumn(e.line, e.col)),
      code = Some("parse-error")
    )
}
