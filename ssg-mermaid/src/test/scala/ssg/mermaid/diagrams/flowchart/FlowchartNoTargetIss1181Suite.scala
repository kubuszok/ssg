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

/** Differential coverage for ISS-1181: a flowchart click-link with NO target must leave `linkTarget` undefined and emit an anchor WITHOUT a `target` attribute (browser default `_self`), matching
  * upstream mermaid — not SSG's `_blank` default.
  *
  * Faithful to:
  *   - diagrams/flowchart/flowDb.ts:345-352 — `setLink(ids, linkStr, target)`: `target` has NO default; `vertex.linkTarget = target` is stored raw (undefined when not provided).
  *   - rendering-util/rendering-elements/nodes.js:69-74 — target is set only under `securityLevel==='sandbox'` (→`'_top'`) or when `node.linkTarget` is present; otherwise it stays undefined and no
  *     `target` attribute is emitted.
  *   - diagrams/flowchart/flowRenderer-v3-unified.ts:75-79 — same: a `target` attribute is set only for sandbox or when `vertex.linkTarget` is present.
  */
final class FlowchartNoTargetIss1181Suite extends FunSuite {

  /** Renders Mermaid source through the full parse → render pipeline. */
  private def renderSource(source: String, securityLevel: String = "strict"): String = {
    val db = FlowchartParser.parse(source)
    FlowchartRenderer.render(db, MermaidConfig(securityLevel = securityLevel))
  }

  // --- RED: no target supplied ---

  test("Iss1181: setLink without a target leaves linkTarget undefined (flowDb.ts:345-352)") {
    val db = new FlowchartDb
    db.addNode("A", text = Nullable("Click me"), shape = Nullable("square"))
    db.setLink("A", "https://example.com")
    assert(
      !db.nodes("A").linkTarget.isDefined,
      s"no-target setLink must leave linkTarget undefined, got: ${db.nodes("A").linkTarget}"
    )
  }

  test("Iss1181: no-target link renders an anchor without a target attribute (nodes.js:69-74)") {
    val svg = renderSource(
      "flowchart TD\n  A[Click me]\n  click A \"https://example.com\""
    )
    assert(svg.contains("<a"), s"expected an <a> anchor element, got:\n$svg")
    assert(
      svg.contains("xlink:href=\"https://example.com\""),
      s"expected xlink:href to the link URL, got:\n$svg"
    )
    assert(
      !svg.contains("target="),
      s"no-target link must not emit a target attribute, got:\n$svg"
    )
  }

  // --- GUARDS: explicit target and sandbox must still behave ---

  test("Iss1181: explicit target is preserved end-to-end (flowDb.ts:350)") {
    val db = FlowchartParser.parse(
      "flowchart TD\n  A[x]\n  click A href \"https://example.com\" _self"
    )
    assertEquals(db.nodes("A").linkTarget, Nullable("_self"))
    val svg = FlowchartRenderer.render(db, MermaidConfig(securityLevel = "strict"))
    assert(svg.contains("target=\"_self\""), s"expected target=\"_self\", got:\n$svg")
  }

  test("Iss1181: sandbox security level forces target=_top even with no link target (nodes.js:69-71)") {
    val db = new FlowchartDb
    db.addNode("A", text = Nullable("Click me"), shape = Nullable("square"))
    db.setLink("A", "https://example.com")
    val svg = FlowchartRenderer.render(db, MermaidConfig(securityLevel = "sandbox"))
    assert(svg.contains("target=\"_top\""), s"expected sandbox target=\"_top\", got:\n$svg")
  }
}
