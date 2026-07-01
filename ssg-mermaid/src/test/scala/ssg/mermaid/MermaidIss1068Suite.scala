/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid

import ssg.mermaid.parse.ParseException

import munit.FunSuite

/** ISS-1068: unified `Mermaid.render` failure contract.
  *
  * Upstream `mermaidAPI.ts:393-401` renders the ERROR diagram (the "bomb" graphic) on any parse failure — `catch (error) { … diag = Diagram.fromText('error'); parseEncounteredException = error }` —
  * only rethrowing when `config.suppressErrorRendering` is set. SSG previously had an INCONSISTENT contract: parse failures propagated the `ParseException`, an unknown type produced an in-band HTML
  * comment, and the error diagram was reachable ONLY via the literal `error` keyword. This suite proves the unified contract:
  *   - a malformed (but detected) diagram → error-diagram HTML, not a thrown exception, not an in-band comment;
  *   - `suppressErrorRendering` → the `ParseException` is rethrown;
  *   - a valid diagram still renders normally (regression guard);
  *   - an unknown/undetected diagram type flows to the error diagram too (no in-band comment).
  *
  * The malformed input relies on the sequence parser raising a `ParseException` on an unrecognized statement (SequenceParser.scala:227-231, ISS-1067), reached through the full `Mermaid.render`
  * dispatch (SequenceDiagram.render → SequenceParser.parse).
  */
final class MermaidIss1068Suite extends FunSuite {

  // An input that is DETECTED as a sequence diagram (so dispatch reaches the
  // sequence renderer) but whose second line matches no jison production, so
  // SequenceParser raises a ParseException mid-parse.
  private val malformedSequence = "sequenceDiagram\n  notakeyword foo bar"

  // A well-formed sequence diagram (regression control).
  private val validSequence = "sequenceDiagram\n  Alice->>Bob: Hi"

  // Text that matches no diagram keyword at all — DetectType.detect returns
  // DiagramType.Unknown (mirrors upstream's UnknownDiagramError path).
  private val unknownText = "this is not a mermaid diagram definition"

  test("Iss1068: malformed diagram renders the error diagram, not a thrown exception") {
    val svg = Mermaid.render(malformedSequence)
    // Must be the error-diagram SVG (ErrorRenderer signature: the `errorText`
    // class label + the `!` icon circle), NOT a propagated exception.
    assert(svg.contains("<svg"), s"expected SVG output, got: $svg")
    assert(svg.contains("errorText"), s"expected error-diagram signature (errorText), got: $svg")
    assert(svg.contains("<circle"), s"expected error-icon circle, got: $svg")
    // The parse message must be surfaced (renderError carries e.getMessage).
    assert(
      svg.contains("Unrecognized sequence statement"),
      s"expected the parse error message to be surfaced, got: $svg"
    )
    // And it must NOT be the old in-band HTML comment.
    assert(!svg.contains("<!--"), s"expected no in-band comment, got: $svg")
  }

  test("Iss1068: suppressErrorRendering rethrows the ParseException instead of rendering the error diagram") {
    val ex = intercept[ParseException] {
      Mermaid.render(malformedSequence, MermaidConfig(suppressErrorRendering = true))
    }
    assert(
      ex.getMessage.contains("Unrecognized sequence statement"),
      s"expected the original parse exception to propagate, got: ${ex.getMessage}"
    )
  }

  test("Iss1068: valid diagram still renders normally (no error diagram)") {
    val svg = Mermaid.render(validSequence)
    assert(svg.contains("<svg"), s"expected SVG output, got: $svg")
    // A real sequence render carries no error-diagram signature.
    assert(!svg.contains("errorText"), s"valid diagram must not render the error diagram, got: $svg")
  }

  test("Iss1068: unknown diagram type flows to the error diagram, not an in-band comment") {
    val svg = Mermaid.render(unknownText)
    assert(svg.contains("<svg"), s"expected SVG output, got: $svg")
    assert(svg.contains("errorText"), s"expected error-diagram signature (errorText), got: $svg")
    assert(
      svg.contains("No diagram type detected"),
      s"expected the unknown-type message to be surfaced, got: $svg"
    )
    assert(!svg.contains("<!--"), s"expected no in-band comment, got: $svg")
  }

  test("Iss1068: unknown diagram type with suppressErrorRendering rethrows a ParseException") {
    val ex = intercept[ParseException] {
      Mermaid.render(unknownText, MermaidConfig(suppressErrorRendering = true))
    }
    assert(
      ex.getMessage.contains("No diagram type detected"),
      s"expected the unknown-type exception to propagate, got: ${ex.getMessage}"
    )
  }
}
