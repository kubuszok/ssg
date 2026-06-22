/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1071: dead-code removal — the duplicate `if (firstLine.startsWith("sequencediagram"))`
 * check after the combined `sequencediagram || sequence-diagram` block was unreachable.
 *
 * ISS-1252: faithfulness correction — removed the `sequence-diagram` hyphen alias that was
 * an SSG-only addition. Upstream sequenceDetector.ts:10 uses only `/^\s*sequenceDiagram/`
 * (no hyphen form). The hyphen-alias assertion now expects Unknown (not Sequence).
 *
 * Upstream reference: mermaid/packages/mermaid/src/diagrams/sequence/sequenceDetector.ts:10
 *   detector regex: /^\s*sequenceDiagram/
 */
package ssg
package mermaid

import munit.FunSuite

final class DetectTypeSequenceIss1071Suite extends FunSuite {

  test("canonical sequenceDiagram keyword detects as Sequence") {
    val result = DetectType.detect("sequenceDiagram\n  Alice->>Bob: Hi")
    assertEquals(result, DiagramType.Sequence)
  }

  // ISS-1252: upstream sequenceDetector.ts:10 matches only /^\s*sequenceDiagram/ — no hyphen form.
  // The `sequence-diagram` alias was an unfaithful SSG addition; it must NOT detect as Sequence.
  test("sequence-diagram hyphen form does NOT detect as Sequence (ISS-1252)") {
    val result = DetectType.detect("sequence-diagram\n  Alice->>Bob: Hi")
    assertEquals(result, DiagramType.Unknown)
  }

  test("sequenceDiagram with leading whitespace detects as Sequence") {
    val result = DetectType.detect("  sequenceDiagram\n  Alice->>Bob: Hi")
    assertEquals(result, DiagramType.Sequence)
  }

  test("sequenceDiagram with front matter still detects as Sequence") {
    val input =
      """---
        |title: Test
        |---
        |sequenceDiagram
        |  Alice->>Bob: Hi""".stripMargin
    val result = DetectType.detect(input)
    assertEquals(result, DiagramType.Sequence)
  }
}
