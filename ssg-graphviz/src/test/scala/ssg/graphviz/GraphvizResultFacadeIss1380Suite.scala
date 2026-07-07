/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package graphviz

import ssg.commons.{ Severity, SourcePosition }
import ssg.graphviz.parse.DotParseException

/** Differential tests for the ISS-1380 error-contract facades `Graphviz.parseResult` / `Graphviz.renderResult` (docs/architecture/error-contracts.md §2.8).
  *
  * ssg-graphviz is SSG-native (no port-fidelity constraint on its internals), so §2.8 is a TWO-STEP adapter:
  *
  *   1. a structured `DotParseException(message, line, col)` subclassing `IllegalArgumentException` is thrown at the 11 scanner/parser failure sites, carrying the 1-based `line`/`col` those sites
  *      already interpolate into their message TEXT (previously the position lived in the message only — §1.3 graphviz row: `position = None` "until a structured exception exists"). Being a subclass
  *      with the SAME message, existing `catch`/message-text tests (e.g. DotParserSuite) keep working — this suite pins that compatibility.
  *   2. `parseResult` / `renderResult` are the additive `DiagResult` envelopes over the throwing `parse` / `render`: they catch ONLY the module-native `DotParseException` (never a blanket catch —
  *      §1.2 rule 3; genuine bugs keep propagating), mapping it to a `DiagResult.failure` carrying one `Severity.Error` diagnostic (component `"ssg-graphviz"`, code `"parse-error"`, the native
  *      exception as cause per §1.2 rule 5, and `position = Some(SourcePosition.lineColumn(e.line, e.col))`).
  *
  * The position assertions pin the §1.3 graphviz row's mapping with LITERAL expected values: `Token` line/col are BOTH already 1-based (DotScanner.scala:28-29 init both to 1), so the mapping copies
  * them verbatim (`line = e.line`, `column = e.col`) — NO `+1` on either field. Each failure test also intercepts the legacy throwing entry point for the SAME input and asserts the raw `line`/`col`,
  * so the verbatim mapping is demonstrated against the source-of-truth exception rather than a hand-copied constant.
  *
  * ssg-graphviz never renders a substitute artifact on failure (no error-diagram fallback), so there is no degraded state here — every parse/lex failure is `DiagResult.failure`.
  */
final class GraphvizResultFacadeIss1380Suite extends munit.FunSuite {

  // A LEXICAL failure on line 2: the scanner hits '%' (not an id-start, digit or
  // operator) at line 2, col 5 and throws at DotScanner.scala:160-162. The
  // multi-line placement pins that line/col are read from the token, not
  // hard-coded to 1.
  //   line 1: "digraph {"
  //   line 2: "  a % b"  -> cols: ' '=1 ' '=2 'a'=3 ' '=4 '%'=5
  private val invalidLexical = "digraph {\n  a % b\n}"

  // A SYNTACTIC failure: the first token is '{', so `expectKeyword("graph")`
  // fails at DotParser.scala:281-283 on the '{' token at line 1, col 1.
  private val invalidSyntactic = "{ A -> B }"

  // A well-formed digraph (happy-path control).
  private val validGraph = "digraph { a -> b }"

  // The render happy-path control renders under LayoutEngine.Neato, matching
  // every render test in ComprehensiveRenderSuite (which uses Neato "to avoid
  // Dagre layout hangs"): the DEFAULT engine (LayoutEngine.Dot) routes through
  // DagreLayout, whose pre-existing nesting/multigraph limitation is P3 fidelity
  // work (bk.js positioning port) unrelated to this error-contract facade. The
  // facade is engine-agnostic — it wraps render's outcome, whatever the engine.
  private val validRenderConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  test(
    "ISS-1380: parse throws a structured DotParseException (an IllegalArgumentException) carrying the 1-based line/col of its message"
  ) {
    val ex = intercept[DotParseException](Graphviz.parse(invalidLexical))
    // Subclass compatibility: existing catch/tests keyed on IllegalArgumentException keep working (§2.8 must-not-change).
    assert(ex.isInstanceOf[IllegalArgumentException], "DotParseException must remain an IllegalArgumentException")
    // The line/col fields equal what the (verbatim, unchanged) message text embeds.
    assertEquals(ex.line, 2)
    assertEquals(ex.col, 5)
    assertEquals(ex.getMessage, "Unexpected character '%' at line 2, col 5")
  }

  test(
    "ISS-1380: parseResult on an invalid (lexical) DOT graph is an Error failure coded parse-error carrying the graphviz line/col position"
  ) {
    // Source of truth: the legacy entry point throws the raw structured exception.
    val legacy = intercept[DotParseException](Graphviz.parse(invalidLexical))
    assertEquals(legacy.line, 2, "raw DotParseException line is 1-based")
    assertEquals(legacy.col, 5, "raw DotParseException col is 1-based")

    val result = Graphviz.parseResult(invalidLexical)
    assert(result.isFailure, s"invalid DOT yields no AST — a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-graphviz")
    assertEquals(d.code, Some("parse-error"))
    assert(d.message.contains("Unexpected character"), s"message: ${d.message}")
    // The native exception rides along as the cause (§1.2 rule 5).
    assert(d.cause.exists(_.isInstanceOf[DotParseException]), s"cause: ${d.cause}")
    // §1.3 graphviz row: line = e.line, column = e.col — NO +1 on either field.
    assertEquals(d.position, Some(SourcePosition.lineColumn(2, 5)))
    assertEquals(d.position, Some(SourcePosition.lineColumn(legacy.line, legacy.col)))
  }

  test(
    "ISS-1380: parseResult surfaces a parser (not lexer) DotParseException as a parse-error failure at the offending token"
  ) {
    val legacy = intercept[DotParseException](Graphviz.parse(invalidSyntactic))
    assertEquals(legacy.line, 1)
    assertEquals(legacy.col, 1)

    val result = Graphviz.parseResult(invalidSyntactic)
    assert(result.isFailure, s"missing graph type yields no AST, got $result")
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-graphviz")
    assertEquals(d.code, Some("parse-error"))
    assert(d.message.contains("Expected 'graph'"), s"message: ${d.message}")
    assert(d.cause.exists(_.isInstanceOf[DotParseException]), s"cause: ${d.cause}")
    assertEquals(d.position, Some(SourcePosition.lineColumn(1, 1)))
  }

  test("ISS-1380: parseResult on a valid DOT graph is a clean success whose value equals parse") {
    val result = Graphviz.parseResult(validGraph)
    assert(result.isSuccess, s"a valid graph is a clean success, got ${result.diagnostics}")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value, Some(Graphviz.parse(validGraph)))
  }

  test(
    "ISS-1380: renderResult on an invalid DOT graph is an Error failure coded parse-error carrying the graphviz line/col position"
  ) {
    val legacy = intercept[DotParseException](Graphviz.render(invalidLexical))
    assertEquals(legacy.line, 2)
    assertEquals(legacy.col, 5)

    val result = Graphviz.renderResult(invalidLexical)
    assert(result.isFailure, s"invalid DOT yields no SVG — a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-graphviz")
    assertEquals(d.code, Some("parse-error"))
    assert(d.cause.exists(_.isInstanceOf[DotParseException]), s"cause: ${d.cause}")
    assertEquals(d.position, Some(SourcePosition.lineColumn(2, 5)))
    assertEquals(d.position, Some(SourcePosition.lineColumn(legacy.line, legacy.col)))
  }

  test("ISS-1380: renderResult on a valid DOT graph is a clean success whose SVG byte-equals render") {
    val result = Graphviz.renderResult(validGraph, validRenderConfig)
    assert(result.isSuccess, s"a valid graph is a clean success, got ${result.diagnostics}")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    // Byte-equality with the legacy throwing entry point is the adapter invariant (§2.8).
    assertEquals(result.value, Some(Graphviz.render(validGraph, validRenderConfig)))
  }
}
