/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression suite for ISS-1058: wire the documented MermaidConfig fields that
 * had no consumer into faithful render effects.
 *
 * Each case proves a field changes the rendered SVG / behavior versus its
 * default, with expected values taken from the upstream Mermaid sources:
 *
 *   - maxTextSize  — mermaidAPI.ts:319-322 (MAX_TEXTLENGTH_EXCEEDED_MSG, :30-32)
 *   - securityLevel — utils.ts formatUrl:248-260 + flowDb.ts:349 (setLink)
 *   - maxEdges     — flowDb.ts:148-155 (addSingleLink edge-limit throw)
 *   - wrap         — sequenceDiagram.ts:13-14 init({wrap}) -> db.setWrap;
 *                    sequenceDb.ts:245-248 autoWrap fallback
 *
 * Fields with no faithful pure-render consumer (startOnLoad, darkMode,
 * arrowMarkerAbsolute, logLevel, markdownAutoWrap) carry no case here — see the
 * MermaidConfig scaladoc for why each is an environment/bootstrap flag or
 * awaits a render path not yet ported. They are kept on the config for
 * full-fidelity schema parity.
 */
package ssg
package mermaid

import lowlevel.Nullable

import ssg.mermaid.diagrams.flowchart.FlowchartDiagram
import ssg.mermaid.diagrams.sequence.{ SequenceDb, SequenceParser }

import munit.FunSuite

final class ConfigFieldsIss1058Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // maxTextSize — mermaidAPI.ts:319-322
  //   if (text.length > (config?.maxTextSize ?? MAX_TEXTLENGTH)) {
  //     text = MAX_TEXTLENGTH_EXCEEDED_MSG;
  //   }
  //   MAX_TEXTLENGTH_EXCEEDED_MSG =
  //     'graph TB;a[Maximum text size in diagram exceeded];style a fill:#faa'
  // ──────────────────────────────────────────────────────────────────────────

  private val flowInput = "flowchart TD\n    AAAA-->BBBB-->CCCC"

  test("maxTextSize: over-limit input is replaced by the exceeded-message diagram; default is not") {
    // The guard (Mermaid.render, mermaidAPI.ts:319-322) swaps the whole input
    // for MAX_TEXTLENGTH_EXCEEDED_MSG before detection. The distinguishing
    // observable is that the AUTHOR's node text is gone — the original input was
    // never parsed.
    val truncated = Mermaid.render(flowInput, MermaidConfig(maxTextSize = 5))
    assert(truncated.contains("<svg"), s"expected an svg, got: ${truncated.take(200)}")
    assert(
      !truncated.contains("AAAA"),
      s"over-limit render must drop the original node text (input replaced), got: ${truncated.take(600)}"
    )

    // The replacement source is the upstream message flowchart verbatim.
    assertEquals(
      Mermaid.MaxTextLengthExceededMsg,
      "graph TB;a[Maximum text size in diagram exceeded];style a fill:#faa"
    )

    // Under the default limit (50000) the same input renders normally, keeping
    // the author's node text.
    val normal = Mermaid.render(flowInput, MermaidConfig())
    assert(
      normal.contains("AAAA"),
      s"under-limit render must keep the original node text, got: ${normal.take(600)}"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // securityLevel — utils.ts formatUrl:248-260: under any level other than
  // "loose" the URL is passed through sanitizeUrl; "loose" yields it verbatim.
  // A `javascript:` link is neutralised to about:blank under strict (default),
  // but passes through verbatim under loose.
  // ──────────────────────────────────────────────────────────────────────────

  private val linkInput =
    "flowchart TD\n    A-->B\n    click A \"javascript:alert(1)\""

  test("securityLevel: javascript link sanitized under strict, verbatim under loose") {
    val strict = FlowchartDiagram.render(linkInput, MermaidConfig(securityLevel = "strict"))
    assert(
      strict.contains("about:blank"),
      s"strict must neutralise the javascript: link to about:blank, got: ${strict.take(600)}"
    )
    assert(
      !strict.contains("javascript:alert(1)"),
      s"strict must not emit the raw javascript: URL, got: ${strict.take(600)}"
    )

    val loose = FlowchartDiagram.render(linkInput, MermaidConfig(securityLevel = "loose"))
    assert(
      loose.contains("javascript:alert(1)"),
      s"loose must pass the author URL through verbatim, got: ${loose.take(600)}"
    )
    assert(
      !loose.contains("about:blank"),
      s"loose must not sanitise to about:blank, got: ${loose.take(600)}"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // maxEdges — flowDb.ts:148-155
  //   if (edges.length < (config.maxEdges ?? 500)) { ... } else { throw ... }
  // The (maxEdges + 1)-th edge throws.
  // ──────────────────────────────────────────────────────────────────────────

  private val multiEdgeInput =
    "flowchart TD\n    A-->B\n    B-->C\n    C-->D"

  test("maxEdges: a graph exceeding maxEdges is rejected; default allows it") {
    // 3 edges; a limit of 2 rejects the 3rd.
    intercept[IllegalStateException] {
      FlowchartDiagram.render(multiEdgeInput, MermaidConfig(maxEdges = 2))
    }

    // The default limit (500) renders all 3 edges without throwing.
    val ok = FlowchartDiagram.render(multiEdgeInput, MermaidConfig())
    assert(ok.contains("<svg"), s"default maxEdges must render the graph, got: ${ok.take(200)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // wrap — sequenceDiagram.ts:13-14 init({wrap}) -> db.setWrap(wrap);
  //        sequenceDb.ts:245-248 autoWrap(): wrapEnabled ?? sequence?.wrap ?? false
  // Top-level config.wrap becomes the global auto-wrap default that every message
  // inherits when it carries no inline :wrap:/:nowrap: directive. We exercise the
  // exact glue SequenceDiagram.render performs (new db -> setWrap -> parse).
  // ──────────────────────────────────────────────────────────────────────────

  private val seqInput = "sequenceDiagram\n    Alice->>Bob: Hello"

  private def parseSeqWithWrap(config: MermaidConfig): SequenceDb = {
    // Mirrors SequenceDiagram.render's pre-parse glue.
    val db = new SequenceDb
    db.setWrap(Nullable(config.wrap))
    SequenceParser.parse(seqInput, db)
    db
  }

  test("wrap: config.wrap drives the message auto-wrap default") {
    val wrapped = parseSeqWithWrap(MermaidConfig(wrap = true))
    val plain   = parseSeqWithWrap(MermaidConfig(wrap = false))

    assert(wrapped.messages.nonEmpty, "expected at least one parsed message")
    assert(plain.messages.nonEmpty, "expected at least one parsed message")

    // sequenceDb.ts:148 — addMessage uses `message.wrap ?? autoWrap()`; with no
    // inline directive the message inherits the global default.
    assert(
      wrapped.messages.head.wrap,
      "with config.wrap=true the message must inherit wrap=true"
    )
    assert(
      !plain.messages.head.wrap,
      "with config.wrap=false the message must inherit wrap=false"
    )

    // autoWrap() itself must reflect the setWrap value (sequenceDb.ts:245-248).
    assert(wrapped.autoWrap, "autoWrap must be true when config.wrap=true")
    assert(!plain.autoWrap, "autoWrap must be false when config.wrap=false")
  }
}
