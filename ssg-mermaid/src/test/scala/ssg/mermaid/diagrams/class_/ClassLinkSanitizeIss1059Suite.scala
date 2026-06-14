/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Reproduces ISS-1059: ClassDb.setLink must sanitize the stored link URL.
 *
 * Upstream mermaid applies utils.formatUrl(linkStr, config) at the class-link
 * emission site (diagrams/class/classDb.ts:275 — `theClass.link =
 * utils.formatUrl(linkStr, config)`). The SSG equivalent is
 * Utils.sanitizeUrl (util/Utils.scala:54), which maps dangerous protocols
 * (javascript:, data:, vbscript:) and empty to "about:blank" and returns
 * safe/relative URLs unchanged (trimmed).
 *
 * The flowchart path already follows this precedent: FlowchartDb.scala:580
 * sanitizes the link (ISS-1061). The class path is the remaining gap —
 * ClassDb.setLink (ClassDb.scala:624) stores `theClass.link = Nullable(linkStr)`
 * RAW, so a `javascript:` URL leaks through verbatim.
 */
package ssg
package mermaid
package diagrams
package class_

import munit.FunSuite

final class ClassLinkSanitizeIss1059Suite extends FunSuite {

  // Control (passes today): a safe URL is preserved verbatim by setLink.
  test("Iss1059: setLink preserves a safe url") {
    val db = new ClassDb
    db.addClass("Class1")
    db.setLink("Class1", "https://example.com/page", "_blank")
    val stored = db.getClass("Class1").link.get
    assertEquals(stored, "https://example.com/page")
  }

  // RED (must FAIL on current code): a javascript: url must be sanitized to
  // "about:blank" per upstream classDb.ts:275 (utils.formatUrl) and SSG
  // Utils.sanitizeUrl. Current ClassDb.setLink stores it RAW, leaking
  // "javascript:alert(1)" instead of "about:blank".
  test("Iss1059: setLink sanitizes a javascript: url to about:blank") {
    val db = new ClassDb
    db.addClass("Class1")
    db.setLink("Class1", "javascript:alert(1)", "_blank")
    val stored = db.getClass("Class1").link.get
    assertEquals(
      stored,
      "about:blank",
      s"expected sanitized link (about:blank) per Utils.sanitizeUrl, but got raw leaked link: $stored"
    )
  }
}
