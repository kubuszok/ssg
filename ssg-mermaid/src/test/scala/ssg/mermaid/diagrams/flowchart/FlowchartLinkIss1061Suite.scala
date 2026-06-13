/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package flowchart

import munit.FunSuite
import lowlevel.Nullable
import ssg.mermaid.MermaidConfig

/** Differential coverage for ISS-1061: clickable nodes must be wrapped in a sanitized `<a xlink:href=...>` anchor by the flowchart renderer, end-to-end from Mermaid source through the parser's
  * `clickStatement` rule.
  *
  * Faithful to:
  *   - diagrams/flowchart/parser/flow.jison:485-500 (clickStatement grammar)
  *   - rendering-util/rendering-elements/nodes.js:67-80 (anchor emission, target rules)
  *   - diagrams/flowchart/flowDb.ts:345-354 (setLink: sanitize URL, store target raw)
  *   - utils.ts:248 formatUrl → Utils.sanitizeUrl
  */
final class FlowchartLinkIss1061Suite extends FunSuite {

  private def renderWith(linkStr: String, target: String, securityLevel: String = "strict"): String = {
    val db = new FlowchartDb
    db.addNode("A", text = Nullable("Click me"), shape = Nullable("square"))
    db.setLink("A", linkStr, target)
    FlowchartRenderer.render(db, MermaidConfig(securityLevel = securityLevel))
  }

  /** Renders Mermaid source through the full parse → render pipeline. */
  private def renderSource(source: String, securityLevel: String = "strict"): String = {
    val db = FlowchartParser.parse(source)
    FlowchartRenderer.render(db, MermaidConfig(securityLevel = securityLevel))
  }

  test("Iss1061: linked node is wrapped in an anchor with xlink:href and target") {
    val svg = renderWith("https://example.com/page", "_blank")
    assert(svg.contains("<a"), s"expected an <a> anchor element, got:\n$svg")
    assert(
      svg.contains("xlink:href=\"https://example.com/page\""),
      s"expected xlink:href to the link URL, got:\n$svg"
    )
    assert(svg.contains("target=\"_blank\""), s"expected target=\"_blank\", got:\n$svg")
  }

  test("Iss1061: unsafe javascript: URL is sanitized to about:blank in the anchor") {
    val svg = renderWith("javascript:alert(1)", "_blank")
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

  test("Iss1061: sandbox security level forces target=_top") {
    val svg = renderWith("https://example.com/page", "_blank", securityLevel = "sandbox")
    assert(svg.contains("<a"), s"expected an <a> anchor element, got:\n$svg")
    assert(svg.contains("target=\"_top\""), s"expected sandbox target=\"_top\", got:\n$svg")
  }

  test("Iss1061: node without a link is not wrapped in an anchor") {
    val db = new FlowchartDb
    db.addNode("A", text = Nullable("Plain"), shape = Nullable("square"))
    val svg = FlowchartRenderer.render(db, MermaidConfig())
    assert(!svg.contains("<a"), s"plain node must not be wrapped in an anchor, got:\n$svg")
  }

  // --- End-to-end source path: parser must wire `click ... href` to setLink ---
  // flow.jison:490-493 (HREF forms) / :496-499 (bare-string forms).

  test("Iss1061: `click A href \"url\" target` from source renders an anchor (flow.jison:492)") {
    val svg = renderSource(
      "flowchart TD\n  A[Click me]\n  click A href \"https://example.com\" _blank"
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element from source, got:\n$svg")
    assert(
      svg.contains("xlink:href=\"https://example.com\""),
      s"expected xlink:href to the parsed link URL, got:\n$svg"
    )
    assert(svg.contains("target=\"_blank\""), s"expected parsed target=\"_blank\", got:\n$svg")
  }

  test("Iss1061: bare-string `click A \"url\"` from source renders an anchor (flow.jison:496)") {
    val svg = renderSource(
      "flowchart TD\n  A[Click me]\n  click A \"https://bare.example.com\""
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element from bare-string form, got:\n$svg")
    assert(
      svg.contains("xlink:href=\"https://bare.example.com\""),
      s"expected xlink:href to the bare-string link URL, got:\n$svg"
    )
  }

  test("Iss1061: `click A href \"url\" \"tooltip\"` parses link and tooltip (flow.jison:491)") {
    val db = FlowchartParser.parse(
      "flowchart TD\n  A[Click me]\n  click A href \"https://example.com\" \"Open the page\""
    )
    val node = db.nodes("A")
    assertEquals(node.link, Nullable("https://example.com"))
    assertEquals(db.getTooltip("A"), Nullable("Open the page"))
  }

  test("Iss1061: `click A href \"link\" \"tip\" target` parses all three (flow.jison:493)") {
    val db = FlowchartParser.parse(
      "flowchart TD\n  A[Click me]\n  click A href \"https://example.com\" \"Tip text\" _self"
    )
    val node = db.nodes("A")
    assertEquals(node.link, Nullable("https://example.com"))
    assertEquals(node.linkTarget, Nullable("_self"))
    assertEquals(db.getTooltip("A"), Nullable("Tip text"))
  }

  test("Iss1061: unsafe javascript: URL from source is sanitized to about:blank (flowDb.ts:349)") {
    val svg = renderSource(
      "flowchart TD\n  A[Click me]\n  click A href \"javascript:alert(1)\""
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element from source, got:\n$svg")
    assert(
      !svg.contains("javascript:alert(1)"),
      s"unsafe javascript: URL must not appear in output, got:\n$svg"
    )
    assert(
      svg.contains("xlink:href=\"about:blank\""),
      s"expected sanitized xlink:href=\"about:blank\", got:\n$svg"
    )
  }

  test("Iss1061: callback form `click A cb` marks node clickable but emits no anchor (flow.jison:494)") {
    val db = FlowchartParser.parse(
      "flowchart TD\n  A[Click me]\n  click A myCallback"
    )
    val node = db.nodes("A")
    assert(node.haveCallback, "callback form must mark the node as having a callback")
    assert(!node.link.isDefined, "callback form must not set a link")
    val svg = FlowchartRenderer.render(db, MermaidConfig())
    assert(!svg.contains("<a"), s"callback-only node must not be wrapped in an anchor, got:\n$svg")
  }
}
