/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sequence

import ssg.mermaid.parse.ParseException

import munit.FunSuite

/** Reproduces the THROW half of ISS-1067.
  *
  * Upstream mermaid's `sequenceDiagram.jison` grammar rejects any line that matches no production: the generated jison parser raises a parse error on unrecognized input. The SSG port instead falls
  * through `parseStatement` to `skipToNewline` (SequenceParser.scala:197-198), silently dropping the line with no diagnostic — so typos and unsupported statements vanish without any signal. This
  * differential suite proves the gap: valid input still parses, but a genuinely-invalid line (one upstream jison would also reject) is silently accepted today instead of raising a ParseException.
  */
final class SequenceUnknownStmtIss1067Suite extends FunSuite {

  // Control: a valid small diagram must continue to parse (no throw).
  test("Iss1067: valid minimal diagram parses") {
    val db = SequenceParser.parse("sequenceDiagram\n  Alice->>Bob: Hi")
    assert(db.actors.contains("Alice"), "Alice actor should exist")
    assert(db.actors.contains("Bob"), "Bob actor should exist")
  }

  // RED: a genuinely-invalid line (no recognized keyword, no signal arrow)
  // is also rejected by upstream jison, so SSG must raise a ParseException.
  // On current code this FAILS because the line is silently skipped — the
  // intercept observes no exception thrown.
  test("Iss1067: unrecognized statement raises ParseException") {
    intercept[ParseException] {
      SequenceParser.parse("sequenceDiagram\n  notakeyword foo bar")
    }
  }
}
