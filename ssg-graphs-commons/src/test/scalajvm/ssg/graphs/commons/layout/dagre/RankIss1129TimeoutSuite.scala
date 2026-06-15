/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1129: Rank.networkSimplex never terminates on a 3-node
 * DAG with start/end pseudo-nodes (start_0 -> s1 -> end_1), the graph shape
 * produced by ssg-mermaid's StateDb for `stateDiagram-v2; [*] --> s1; s1 --> [*]`.
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 *
 * Expected-value source: Gansner, North, Koutsofios, Vo — "A Technique for
 * Drawing Directed Graphs" (IEEE TSE 19(3), 1993), section 4: a ranking is
 * feasible iff for every edge (v, w), rank(w) - rank(v) >= minlen(v, w), and
 * the network simplex iteration terminates once no tree edge has a negative
 * cut value. dagre-js is not vendored in original-src (see ISS-1072), so the
 * paper is the authoritative reference for the expected behaviour.
 */
package ssg
package graphs
package commons
package layout
package dagre

import munit.FunSuite

import ssg.graphs.commons.layout.graph.Graph

final class RankIss1129TimeoutSuite extends FunSuite {

  private val TimeoutMs = 15000L

  /** Mirrors StateRenderer.buildDagreGraph for the StateDb produced by `stateDiagram-v2; [*] --> s1; s1 --> [*]`: three nodes (start_0, s1, end_1) and two unit-weight, minlen-1 edges.
    */
  private def buildStateDiagramShapedGraph(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = "TB"
    gl.nodesep = 50
    gl.ranksep = 50
    g.setGraph(gl)

    // start_0 / end_1 pseudo-states: 7px radius circles => 14x14 boxes
    val startLabel = new NodeLabel
    startLabel.width = 14
    startLabel.height = 14
    startLabel.label = "start_0"
    g.setNode("start_0", startLabel)

    val s1Label = new NodeLabel
    s1Label.width = 50
    s1Label.height = 30
    s1Label.label = "s1"
    g.setNode("s1", s1Label)

    val endLabel = new NodeLabel
    endLabel.width = 14
    endLabel.height = 14
    endLabel.label = "end_1"
    g.setNode("end_1", endLabel)

    val e0 = new EdgeLabel
    e0.minlen = 1
    e0.weight = 1
    g.setEdge("start_0", "s1", e0, "T-start_0-s1-0")

    val e1 = new EdgeLabel
    e1.minlen = 1
    e1.weight = 1
    g.setEdge("s1", "end_1", e1, "T-s1-end_1-1")

    g
  }

  test("ISS-1129: network-simplex ranking terminates and is feasible on start_0 -> s1 -> end_1") {
    val g = buildStateDiagramShapedGraph()

    @volatile var failure: Option[Throwable] = None

    val worker = new Thread(() =>
      try
        Rank.rank(g) // default ranker is "network-simplex"
      catch {
        case t: Throwable => failure = Some(t)
      }
    )
    worker.setDaemon(true)
    worker.start()
    worker.join(TimeoutMs)

    if (worker.isAlive) {
      // Leave the daemon thread to die with the JVM; do not block on it.
      fail(
        s"ISS-1129 reproduced: Rank.rank (network-simplex) did not terminate within ${TimeoutMs}ms " +
          "on the 3-node DAG start_0 -> s1 -> end_1 (state diagram `[*] --> s1; s1 --> [*]`). " +
          "networkSimplex loops forever in leaveEdge/enterEdge/exchangeEdges."
      )
    }
    failure.foreach(t => fail(s"Rank.rank threw instead of completing: ${t}", t))

    // Rank feasibility (Gansner et al. 1993, section 4): for every edge (v, w),
    // rank(w) - rank(v) must be at least minlen(v, w).
    for (e <- g.edges()) {
      val tailRank = g.node(e.v).rank
      val headRank = g.node(e.w).rank
      val minlen   = g.edge(e).minlen
      assert(
        headRank - tailRank >= minlen,
        s"Infeasible ranking for edge ${e.v} -> ${e.w}: rank(${e.w})=$headRank, " +
          s"rank(${e.v})=$tailRank, minlen=$minlen (expected headRank - tailRank >= minlen)"
      )
    }
  }
}
