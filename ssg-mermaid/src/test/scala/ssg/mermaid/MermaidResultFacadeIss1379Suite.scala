/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid

import ssg.commons.{ Severity, SourcePosition }
import ssg.mermaid.parse.ParseException

/** Differential tests for the ISS-1379 error-contract facade `Mermaid.renderResult` (docs/architecture/error-contracts.md §2.7).
  *
  * `renderResult` is the additive `DiagResult` envelope over the throwing `Mermaid.render`: it shares the same effective-config computation and replicates the dispatch `try`, then wraps the outcome
  * per §2.7 instead of the legacy throw-or-error-diagram contract (which ISS-1068 test-locks and this facade must NOT change). The three failure/degradation paths the §2.7 "Adapter" bullet
  * enumerates:
  *
  *   - a caught `ParseException` with `suppressErrorRendering = true` → `DiagResult.failure` (no substitute output requested) carrying one `Severity.Error` diagnostic (component `"ssg-mermaid"`, code
  *     `"parse-error"`, the native exception as cause, position from the §1.3 mermaid row);
  *   - a caught `ParseException` WITHOUT suppression → `DiagResult.degraded` whose value is `ErrorDiagram.renderError(e.getMessage, effectiveConfig)` — byte-equal to `render`'s error SVG (the adapter
  *     invariant) — plus the same `"parse-error"` diagnostic;
  *   - an undetected `DiagramType.Unknown` → `DiagResult.degraded` whose value is the unknown-type error SVG plus a `Severity.Error` diagnostic coded `"unknown-diagram-type"` (a direct
  *     `Diagnostic.error`, so no native cause and no position).
  *
  * The position assertions pin the §1.3 mermaid row's no-`+1` trap with LITERAL expected values: the native `ParseException` line/col are BOTH already 1-based (ParserBase.scala:29-33), so the mapping
  * copies them verbatim (`line = e.line`, `column = e.col`). Each parse-error test also intercepts the legacy `render` (under `suppressErrorRendering`) for the SAME input and asserts the raw
  * line/col, so the verbatim mapping is demonstrated against the source-of-truth rather than a hand-copied constant. The malformed input is the ISS-1068 suite's fixture (SequenceParser raises at line
  * 2, col 22).
  */
final class MermaidResultFacadeIss1379Suite extends munit.FunSuite {

  // DETECTED as a sequence diagram (so dispatch reaches SequenceParser) but the
  // second line matches no jison production, so SequenceParser raises a
  // ParseException at line 2, col 22 (SequenceParser.scala:231). Same fixture
  // the ISS-1068 suite locks the legacy `render` contract with.
  private val malformedSequence = "sequenceDiagram\n  notakeyword foo bar"

  // Matches no diagram keyword — DetectType.detect returns DiagramType.Unknown.
  private val unknownText = "this is not a mermaid diagram definition"

  // A well-formed sequence diagram (happy-path control).
  private val validSequence = "sequenceDiagram\n  Alice->>Bob: Hi"

  test(
    "ISS-1379: renderResult on a parse failure with suppressErrorRendering is an Error failure coded parse-error carrying the mermaid line/col position"
  ) {
    val cfg = MermaidConfig(suppressErrorRendering = true)
    // Source of truth: the legacy entry point rethrows the raw ParseException.
    val legacy = intercept[ParseException](Mermaid.render(malformedSequence, cfg))
    assertEquals(legacy.line, 2, "raw ParseException line is 1-based")
    assertEquals(legacy.col, 22, "raw ParseException col is 1-based")

    val result = Mermaid.renderResult(malformedSequence, cfg)
    assert(result.isFailure, s"suppressErrorRendering means no substitute output — a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-mermaid")
    assertEquals(d.code, Some("parse-error"))
    assert(d.message.contains("Unrecognized sequence statement"), s"message: ${d.message}")
    // The native exception rides along as the cause (§1.2 rule 5).
    assert(d.cause.exists(_.isInstanceOf[ParseException]), s"cause: ${d.cause}")
    // §1.3 mermaid row: line = e.line, column = e.col — NO +1 on either field.
    assertEquals(d.position, Some(SourcePosition.lineColumn(2, 22)))
    assertEquals(d.position, Some(SourcePosition.lineColumn(legacy.line, legacy.col)))
  }

  test(
    "ISS-1379: renderResult on a parse failure without suppression is a degraded result whose SVG byte-equals render's error diagram"
  ) {
    val result = Mermaid.renderResult(malformedSequence)
    assert(result.isDegraded, s"a substitute error diagram was produced — degraded, got $result")
    assert(!result.isSuccess)
    assert(result.hasErrors)
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-mermaid")
    assertEquals(d.code, Some("parse-error"))
    assert(d.message.contains("Unrecognized sequence statement"), s"message: ${d.message}")
    assert(d.cause.exists(_.isInstanceOf[ParseException]), s"cause: ${d.cause}")
    assertEquals(d.position, Some(SourcePosition.lineColumn(2, 22)))
    // Byte-equality with render's error SVG is the adapter invariant (§2.7).
    assertEquals(result.value, Some(Mermaid.render(malformedSequence)))
  }

  test("ISS-1379: renderResult on an unknown diagram type is a degraded result coded unknown-diagram-type") {
    val result = Mermaid.renderResult(unknownText)
    assert(result.isDegraded, s"the unknown-type error diagram is a substitute — degraded, got $result")
    assert(result.hasErrors)
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-mermaid")
    assertEquals(d.code, Some("unknown-diagram-type"))
    assert(d.message.contains("No diagram type detected"), s"message: ${d.message}")
    // The unknown-type diagnostic is a direct Diagnostic.error (§2.7) — there is
    // no ParseException behind it, so it carries neither cause nor position.
    assertEquals(d.cause, None)
    assertEquals(d.position, None)
    // Byte-equality with render's unknown-type error SVG.
    assertEquals(result.value, Some(Mermaid.render(unknownText)))
  }

  test("ISS-1379: renderResult on a valid diagram is a clean success byte-equal to render") {
    val result = Mermaid.renderResult(validSequence)
    assert(result.isSuccess, s"a valid diagram is a clean success, got ${result.diagnostics}")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value, Some(Mermaid.render(validSequence)))
  }
}
