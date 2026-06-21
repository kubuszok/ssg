/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Reproduces ISS-1185: ClassDb.setLink must force the link target to '_top'
 * when the active security level is 'sandbox'.
 *
 * Upstream mermaid (diagrams/class/classDb.ts:276-281) does:
 *   if (config.securityLevel === 'sandbox') {
 *     theClass.linkTarget = '_top';
 *   } else if (typeof target === 'string') {
 *     theClass.linkTarget = sanitizeText(target);
 *   } else {
 *     theClass.linkTarget = '_blank';
 *   }
 *
 * The `else '_blank'` (non-string target) is handled in SSG at the parser
 * (ClassParser.scala:466 passes "_blank" when LINK_TARGET is omitted), so the
 * only branch missing in ClassDb.setLink was `sandbox → '_top'`. The active
 * level is threaded into the db via ClassDiagram.render (db.securityLevel =
 * config.securityLevel), mirroring FlowchartDiagram.scala:81.
 */
package ssg
package mermaid
package diagrams
package class_

import lowlevel.Nullable

import munit.FunSuite

final class ClassSandboxLinkTargetIss1185Suite extends FunSuite {

  // RED (must FAIL without the Part-1 securityLevel field — it won't even compile):
  // under sandbox, setLink forces the link target to '_top' regardless of the
  // requested target (classDb.ts:276-278).
  test("ISS-1185: setLink forces _top under sandbox security level") {
    val db = new ClassDb
    db.securityLevel = "sandbox"
    db.addClass("A")
    db.setLink("A", "https://example.com", "_blank")
    assertEquals(db.getClass("A").linkTarget, Nullable("_top"))
  }

  // GUARD (passes on current strict default): when not sandboxed, the requested
  // target is sanitized and stored verbatim (classDb.ts:279-280).
  test("Iss1185: setLink sanitizes the given target under strict (default)") {
    val db2 = new ClassDb
    db2.addClass("A")
    db2.setLink("A", "https://example.com", "_self")
    assertEquals(db2.getClass("A").linkTarget, Nullable("_self"))
  }

  // THREADING: ClassDiagram wires config.securityLevel into the db before parse,
  // so a click-link parsed under sandbox lands as '_top'. Here we drive the
  // parser path directly with a db whose securityLevel was pre-set to "sandbox"
  // (mirrors how ClassDiagram.render threads it — ClassDiagram.scala:75).
  test("Iss1185: parsed click-link resolves to _top under sandbox") {
    val db = new ClassDb
    db.securityLevel = "sandbox"
    ClassParser.parse(
      "classDiagram\nclass Class1\nclick Class1 href \"google.com\" \"A tooltip\" _self",
      db
    )
    assertEquals(db.getClass("Class1").linkTarget, Nullable("_top"))
  }
}
