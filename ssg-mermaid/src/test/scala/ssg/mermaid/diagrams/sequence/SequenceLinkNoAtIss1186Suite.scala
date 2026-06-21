/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sequence

import munit.FunSuite

/** Differential suite for ISS-1186: `link <Actor>: <payload>` with NO `@`.
  *
  * Oracle: upstream `addALink` (original-src/mermaid/packages/mermaid/src/diagrams/sequence/sequenceDb.ts:388-399) is UNGUARDED. It computes `sep = sanitizedText.indexOf('@')` (so `sep == -1` when
  * there is no `@`), then `label = sanitizedText.slice(0, sep - 1).trim()` and `link = sanitizedText.slice(sep + 1).trim()`, and ALWAYS inserts `links[label] = link` via `insertLinks`. JS
  * `String.prototype.slice` treats negative indices as offsets from the end (clamped to [0, len]), so for a no-`@` payload upstream still inserts a (malformed) entry rather than nothing.
  *
  * SSG's `parseALinkMap` (SequenceParser.scala:584) instead guards the insert with `if (sep >= 1)`, so a no-`@` payload yields an EMPTY links map — a deviation this suite pins down.
  *
  * JS-slice arithmetic for payload `P = "abcdef"` (length 6, no `@`, no surrounding spaces, `sep = -1`):
  *   - label = P.slice(0, sep - 1) = P.slice(0, -2) = P.substring(0, 6 - 2) = P.substring(0, 4) = "abcd", trimmed → "abcd"
  *   - link = P.slice(sep + 1) = P.slice(0) = P (whole string) = "abcdef", trimmed → "abcdef"
  *   - upstream links map → { "abcd" -> "abcdef" }
  */
final class SequenceLinkNoAtIss1186Suite extends FunSuite {

  // RED: a `link` payload with NO `@`. Upstream's unguarded slice yields { "abcd" -> "abcdef" }
  // (see JS-slice arithmetic in the class doc). SSG's `if (sep >= 1)` guard yields an empty map.
  test("ISS-1186: link payload without @ inserts the upstream slice-derived entry") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "Alice->>Bob: hi\n" +
        "link Alice: abcdef"
    )
    val alice = db.actors("Alice")
    assertEquals(
      alice.links.get("abcd"),
      Some("abcdef"),
      s"upstream addALink inserts { abcd -> abcdef } for no-@ payload; SSG links were ${alice.links}"
    )
  }

  // GUARD (passes before and after the fix): a normal `label @ url` payload (WITH `@`) still
  // produces the correct entry. Mirrors the valid-path behavior asserted by
  // SequenceActorMetadataIss1067Suite (`link Bob: Dashboard @ https://dash.example`).
  test("ISS-1186 guard: link payload with @ still adds the correct label/url pair") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "Carol->>Dave: hi\n" +
        "link Carol: label @ https://example.com"
    )
    val carol = db.actors("Carol")
    assertEquals(carol.links.get("label"), Some("https://example.com"))
  }
}
