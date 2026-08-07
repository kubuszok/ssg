/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Differential coverage for ISS-1230: a class with a click-link must have its
 * TITLE wrapped in a clickable `<a xlink:href=... target=...>` anchor by the
 * class-diagram renderer, mirroring svgDraw.js:176-183:
 *
 *   if (classDef.link) {
 *     title = g.append('svg:a')
 *              .attr('xlink:href', classDef.link)
 *              .attr('target', classDef.linkTarget)
 *              .append('text')...
 *   } else { title = g.append('text')... }
 *
 * (Only the TITLE is wrapped — NOT the whole node; v3-unified's whole-node wrap
 * is a different renderer SSG does not use here.) The link/linkTarget carried on
 * ClassNode (ClassDb.scala:280-281, set by setLink per ISS-1059/1185) was
 * previously dead render state — renderClassBox ignored it (0 anchor sites).
 *
 * Proof-of-red is stash-based: stashing the renderClassBox change makes these
 * assertions red (0 `<a>` emitted).
 */
package ssg
package mermaid
package diagrams
package class_

import munit.FunSuite
import ssg.mermaid.MermaidConfig

final class ClassLinkAnchorIss1230Suite extends FunSuite {

  /** Renders a class diagram whose class carries a click-link, via the full parse → render pipeline (ClassDiagram.render).
    */
  private def renderSource(source: String, config: MermaidConfig = MermaidConfig()): String =
    ClassDiagram.render(source, config)

  test("Iss1230: linked class title is wrapped in an anchor with xlink:href and target") {
    val svg = renderSource(
      "classDiagram\n" +
        "class Class1\n" +
        "click Class1 href \"https://example.com/page\" \"A tooltip\" _blank"
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element for the linked class, got:\n$svg")
    assert(
      svg.contains("xlink:href=\"https://example.com/page\""),
      s"expected xlink:href to the link URL, got:\n$svg"
    )
    assert(svg.contains("target=\"_blank\""), s"expected target=\"_blank\", got:\n$svg")
  }

  test("Iss1230: class without a link is NOT wrapped in an anchor") {
    val svg = renderSource(
      "classDiagram\n" +
        "class Plain"
    )
    assert(!svg.contains("<a"), s"plain (unlinked) class must not be wrapped in an anchor, got:\n$svg")
  }

  test("Iss1230: unsafe javascript: URL is sanitized to about:blank in the anchor") {
    val svg = renderSource(
      "classDiagram\n" +
        "class Class1\n" +
        "click Class1 href \"javascript:alert(1)\" \"tip\" _blank"
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element, got:\n$svg")
    assert(
      !svg.contains("javascript:alert(1)"),
      s"unsafe javascript: URL must not appear in output, got:\n$svg"
    )
    assert(
      svg.contains("xlink:href=\"about:blank\""),
      s"expected sanitized xlink:href=\"about:blank\", got:\n$svg"
    )
  }

  test("Iss1230: sandbox security level forces target=_top on the class anchor") {
    val svg = renderSource(
      "classDiagram\n" +
        "class Class1\n" +
        "click Class1 href \"https://example.com\" \"tip\" _self",
      MermaidConfig(securityLevel = "sandbox")
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element under sandbox, got:\n$svg")
    assert(svg.contains("target=\"_top\""), s"expected sandbox target=\"_top\", got:\n$svg")
  }
}
