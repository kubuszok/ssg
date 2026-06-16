/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package flowchart

import munit.FunSuite
import ssg.mermaid.MermaidConfig

/** Differential test for ISS-1062: the edge-limit check in FlowchartDb.addEdge
  * must consult MermaidConfig.maxEdges instead of the hardcoded 500.
  *
  * Faithful to: flowDb.ts:148
  *   `if (edges.length < (config.maxEdges ?? 500)) { ... } else { throw ... }`
  *
  * The test is differential: it proves the limit is LIVE by toggling the config
  * value and observing different behavior.
  */
final class FlowchartMaxEdgesIss1062Suite extends FunSuite {

  /** A simple flowchart with 3 edges: A-->B, B-->C, C-->D. */
  private val threeEdgeChart: String =
    """graph TD
      |    A-->B
      |    B-->C
      |    C-->D""".stripMargin

  // -- LOW limit: config.maxEdges = 2, chart has 3 edges => must throw -------

  test("Iss1062: low maxEdges config causes edge-limit exception during render") {
    val lowConfig = MermaidConfig(maxEdges = 2)
    val ex = intercept[IllegalStateException] {
      FlowchartDiagram.render(threeEdgeChart, lowConfig)
    }
    // flowDb.ts:153 — the message includes "Edge limit exceeded"
    assert(
      ex.getMessage.contains("Edge limit exceeded"),
      s"expected 'Edge limit exceeded' in message, got: ${ex.getMessage}"
    )
    // The limit reported must be 2 (from config), NOT 500 (hardcoded)
    assert(
      ex.getMessage.contains("the limit is 2"),
      s"expected 'the limit is 2' in message, got: ${ex.getMessage}"
    )
  }

  // -- HIGH limit: config.maxEdges = 1000, same chart => must succeed --------

  test("Iss1062: high maxEdges config allows many edges without exception") {
    val highConfig = MermaidConfig(maxEdges = 1000)
    // Must not throw — 3 edges is well under 1000
    val svg = FlowchartDiagram.render(threeEdgeChart, highConfig)
    assert(svg.nonEmpty, "expected non-empty SVG output")
  }

  // -- DEFAULT: config.maxEdges stays 500, small chart => no throw -----------

  test("Iss1062: default maxEdges (500) allows a small flowchart") {
    // Default MermaidConfig has maxEdges = 500; 3 edges is well under
    val svg = FlowchartDiagram.render(threeEdgeChart)
    assert(svg.nonEmpty, "expected non-empty SVG output with default config")
  }

  // -- BOUNDARY: flowDb.ts:148 — `edges.length < config.maxEdges` accepts while
  // count < limit, so 3 edges with limit 3 succeeds (last add at count 2 < 3).
  // With limit 3, a 4th edge (count 3 >= 3) would throw.

  test("Iss1062: boundary — maxEdges equal to edge count succeeds (< semantics)") {
    val boundaryConfig = MermaidConfig(maxEdges = 3)
    // 3 edges with limit 3: edges are added at count 0, 1, 2 — all < 3
    val svg = FlowchartDiagram.render(threeEdgeChart, boundaryConfig)
    assert(svg.nonEmpty, "expected non-empty SVG when edge count equals limit")
  }

  test("Iss1062: boundary — one more edge than maxEdges throws") {
    // 4-edge chart: A-->B, B-->C, C-->D, D-->E
    val fourEdgeChart =
      """graph TD
        |    A-->B
        |    B-->C
        |    C-->D
        |    D-->E""".stripMargin
    val limitThreeConfig = MermaidConfig(maxEdges = 3)
    val ex = intercept[IllegalStateException] {
      FlowchartDiagram.render(fourEdgeChart, limitThreeConfig)
    }
    assert(
      ex.getMessage.contains("Edge limit exceeded"),
      s"expected 'Edge limit exceeded' in message, got: ${ex.getMessage}"
    )
    // The limit reported must be 3 (from config)
    assert(
      ex.getMessage.contains("the limit is 3"),
      s"expected 'the limit is 3' in message, got: ${ex.getMessage}"
    )
  }

  // -- DIRECT DB: verify the Db-level wiring independent of render pipeline --

  test("Iss1062: FlowchartDb.maxEdges is settable and honored by addEdge") {
    val db = new FlowchartDb
    db.maxEdges = 2
    db.addEdge("A", "B")
    db.addEdge("B", "C")
    // Third edge should trigger the limit (2 edges present, limit is 2)
    val ex = intercept[IllegalStateException] {
      db.addEdge("C", "D")
    }
    assert(
      ex.getMessage.contains("the limit is 2"),
      s"expected 'the limit is 2' in message, got: ${ex.getMessage}"
    )
  }
}
