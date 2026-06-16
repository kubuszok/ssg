/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red repro for ISS-1198: DagreUtil.normalizeRanks (and removeEmptyRanks,
 * buildLayerMatrix) filter `rank >= 0`, so they never normalize the negative
 * ranks that Rank.longestPath assigns (sinks at 0, sources negative). Upstream
 * dagre util.js normalizeRanks mins over ALL nodes and shifts ALL of them.
 *
 * Because SSG's filter skips negative ranks, those nodes are also skipped by
 * Position (buildLayerMatrix uses the same `rank >= 0` filter), so they keep
 * the default center coordinates (x = width/2, y = height/2) and OVERLAP.
 *
 * Verified repro: DagreLayout.layout on a bare diamond (a -> b, a -> c,
 * b -> d, c -> d) leaves nodes a and b both at x = 25 -> overlap.
 *
 * This suite drives the REAL production entry point DagreLayout.layout and
 * asserts (1) every real node's rank is normalized to >= 0, and (2) no two
 * distinct real nodes sharing a y band overlap horizontally. It is RED until
 * normalizeRanks shifts negative ranks like upstream.
 *
 * Cross-platform (no threads): placed in the shared test root so it runs on
 * JVM, JS, and Native.
 */
package ssg
package graphs
package commons
package layout
package dagre

import munit.FunSuite

import ssg.graphs.commons.layout.graph.Graph

final class NormalizeRanksIss1198Suite extends FunSuite {

  private def newGraph(isCompound: Boolean): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = isCompound)

    val gl = new GraphLabel
    gl.rankdir = "TB"
    gl.nodesep = 50
    gl.ranksep = 50
    g.setGraph(gl)

    g
  }

  private def addNode(g: Graph[NodeLabel, EdgeLabel], name: String): Unit = {
    val label = new NodeLabel
    label.width = 50
    label.height = 30
    label.label = name
    g.setNode(name, label)
  }

  private def addEdge(g: Graph[NodeLabel, EdgeLabel], v: String, w: String, name: String): Unit = {
    val e = new EdgeLabel
    e.minlen = 1
    e.weight = 1
    g.setEdge(v, w, e, name)
  }

  /** Builds the verified diamond repro: a -> b, a -> c, b -> d, c -> d. */
  private def diamond(isCompound: Boolean): Graph[NodeLabel, EdgeLabel] = {
    val g = newGraph(isCompound)
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addNode(g, "d")
    addEdge(g, "a", "b", "T-a-b-0")
    addEdge(g, "a", "c", "T-a-c-1")
    addEdge(g, "b", "d", "T-b-d-2")
    addEdge(g, "c", "d", "T-c-d-3")
    g
  }

  /** Real (non-dummy) original nodes only — dummy nodes carry a non-null `dummy` tag. */
  private def realNodes(g: Graph[NodeLabel, EdgeLabel]): Array[String] =
    g.nodes().filter(v => g.node(v).dummy.isEmpty)

  private def assertNoOverlap(g: Graph[NodeLabel, EdgeLabel], desc: String): Unit = {
    val nodes = realNodes(g)

    // Symptom 1: every real node's rank must be normalized to >= 0.
    for (v <- nodes) {
      val r = g.node(v).rank
      assert(
        r >= 0,
        s"ISS-1198 reproduced [$desc]: node '$v' has non-normalized rank $r (expected >= 0); " +
          "normalizeRanks never shifted the negative ranks longestPath assigned"
      )
    }

    // Symptom 2: no two distinct real nodes sharing a y band overlap horizontally.
    // rankdir TB => same rank ~ same y. Compare every pair sharing a y band.
    for (i <- nodes.indices; j <- (i + 1) until nodes.length) {
      val a  = g.node(nodes(i))
      val b  = g.node(nodes(j))
      val dy = math.abs(a.y - b.y)
      // Same y band: within half the smaller height (i.e. boxes vertically overlap).
      val sameBand = dy < (math.min(a.height, b.height) / 2.0)
      if (sameBand) {
        val aLeft  = a.x - a.width / 2.0
        val aRight = a.x + a.width / 2.0
        val bLeft  = b.x - b.width / 2.0
        val bRight = b.x + b.width / 2.0
        // Overlap (touching allowed): intervals [aLeft, aRight] and [bLeft, bRight]
        // overlap strictly iff aLeft < bRight and bLeft < aRight.
        val overlap = aLeft < bRight && bLeft < aRight
        assert(
          !overlap,
          s"ISS-1198 reproduced [$desc]: nodes '${nodes(i)}' (x=${a.x}, y=${a.y}) and " +
            s"'${nodes(j)}' (x=${b.x}, y=${b.y}) overlap horizontally in the same y band " +
            s"[${aLeft},${aRight}] vs [${bLeft},${bRight}] — they were never positioned " +
            "(default x = width/2) because normalizeRanks left their ranks negative"
        )
      }
    }
  }

  test("ISS-1198: bare diamond lays out without same-rank overlap (non-compound)") {
    val g = diamond(isCompound = false)
    DagreLayout.layout(g)
    assertNoOverlap(g, "non-compound diamond a->b, a->c, b->d, c->d")
  }

  test("ISS-1198: bare diamond lays out without same-rank overlap (compound)") {
    val g = diamond(isCompound = true)
    DagreLayout.layout(g)
    assertNoOverlap(g, "compound diamond a->b, a->c, b->d, c->d")
  }
}
