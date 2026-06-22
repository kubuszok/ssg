/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1071: dead-code removal — the duplicate `if (firstLine.startsWith("sequencediagram"))`
 * check after the combined `sequencediagram || sequence-diagram` block was unreachable.
 *
 * This suite proves that both the canonical keyword (`sequenceDiagram`) and the
 * hyphen alias (`sequence-diagram`) resolve to DiagramType.Sequence after the
 * dead line is removed — i.e. the first if-block covers both aliases.
 *
 * Upstream reference: mermaid/packages/mermaid/src/diagrams/sequence/sequenceDetector.ts:10
 *   detector regex: /^\s*sequenceDiagram/
 * The `sequence-diagram` alias is an SSG addition (not upstream).
 */
package ssg
package mermaid

import munit.FunSuite

final class DetectTypeSequenceIss1071Suite extends FunSuite {

  test("canonical sequenceDiagram keyword detects as Sequence") {
    val result = DetectType.detect("sequenceDiagram\n  Alice->>Bob: Hi")
    assertEquals(result, DiagramType.Sequence)
  }

  test("sequence-diagram hyphen alias detects as Sequence") {
    val result = DetectType.detect("sequence-diagram\n  Alice->>Bob: Hi")
    assertEquals(result, DiagramType.Sequence)
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
