/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red-2 tests for ISS-1129: the first fix (6f8571e8) seeds calcCutValue with
 * the tree edge's own weight, which terminates the original 3-node linear
 * chain — but the ISS-1129 audit verified that Rank.networkSimplex STILL
 * hangs on graphs where the tree edge's weight alone is the wrong seed:
 *
 *   1. Unweighted diamond with a cross edge (a -> b -> d, a -> c -> d, c -> b):
 *      the audit measured cutvalue(c, b) computed as -1 where the true cut
 *      value is +3.
 *   2. Chain with parallel edges (start -> s1, then TWO parallel unit edges
 *      s1 -> end): DagreUtil.simplify (invoked inside networkSimplex) sums
 *      the parallel edges to a single weight-2 edge; the audit measured the
 *      computed cut value as 1 - 2 = -1 where the true cut value is +1.
 *   3. Weighted chain (a -> b weight 1, b -> c weight 5, c -> d weight 1):
 *      the audit measured the computed cut value as 1 - 5 = -4 where the
 *      true cut value is +5 (a chain tree edge's cut value equals its own
 *      weight).
 *
 * In each case a spurious negative cut value makes the
 * leaveEdge/enterEdge/exchangeEdges loop swap edges forever.
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 *
 * Expected-value source: Gansner, North, Koutsofios, Vo — "A Technique for
 * Drawing Directed Graphs" (IEEE TSE 19(3), 1993), section 4.2: the cut value
 * of a tree edge is the sum of the signed weights of ALL graph edges crossing
 * the cut induced by removing the tree edge, and a ranking is feasible iff for
 * every edge (v, w), rank(w) - rank(v) >= minlen(v, w); the network simplex
 * iteration terminates once no tree edge has a negative cut value. dagre-js is
 * not vendored in original-src (see ISS-1072, ISS-1131), so the paper and the
 * ISS-1129 audit findings are the authoritative references for the expected
 * behaviour.
 */
package ssg
package graphs
package commons
package layout
package dagre

import munit.FunSuite

import ssg.graphs.commons.layout.graph.Graph

final class RankIss1129HangCasesSuite extends FunSuite {

  private val TimeoutMs = 15000L

  private def newGraph(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

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

  private def addEdge(g: Graph[NodeLabel, EdgeLabel], v: String, w: String, weight: Double, name: String): Unit = {
    val e = new EdgeLabel
    e.minlen = 1
    e.weight = weight
    g.setEdge(v, w, e, name)
  }

  /** Runs Rank.rank (default ranker: network-simplex) on a daemon thread with a join timeout, fails with `hangMessage` if it does not terminate, then asserts rank feasibility (Gansner et al. 1993,
    * section 4.2): for every edge (v, w), rank(w) - rank(v) >= minlen(v, w).
    */
  private def assertRanksFeasibly(g: Graph[NodeLabel, EdgeLabel], hangMessage: String): Unit = {
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
      fail(s"ISS-1129 reproduced: Rank.rank (network-simplex) did not terminate within ${TimeoutMs}ms — $hangMessage")
    }
    failure.foreach(t => fail(s"Rank.rank threw instead of completing: ${t}", t))

    // Rank feasibility (Gansner et al. 1993, section 4.2): for every edge (v, w),
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

  test("ISS-1129 red-2 case 1: network-simplex terminates on the unweighted diamond with cross edge c -> b") {
    // a -> b -> d, a -> c -> d, plus the cross edge c -> b. Per the ISS-1129
    // audit, calcCutValue computes cutvalue(c, b) = -1 where the true cut
    // value (Gansner et al. 1993, section 4.2) is +3, so the pivot loop never
    // reaches the all-non-negative termination condition.
    val g = newGraph()
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addNode(g, "d")
    addEdge(g, "a", "b", weight = 1, name = "T-a-b-0")
    addEdge(g, "a", "c", weight = 1, name = "T-a-c-1")
    addEdge(g, "b", "d", weight = 1, name = "T-b-d-2")
    addEdge(g, "c", "d", weight = 1, name = "T-c-d-3")
    addEdge(g, "c", "b", weight = 1, name = "T-c-b-4")

    assertRanksFeasibly(
      g,
      "unweighted diamond a -> b -> d, a -> c -> d with cross edge c -> b " +
        "(audit: cutvalue(c, b) computed -1, true +3)"
    )
  }

  test("ISS-1129 red-2 case 2: network-simplex terminates on the chain with two parallel edges s1 -> end") {
    // start -> s1, then TWO parallel unit-weight edges s1 -> end.
    // DagreUtil.simplify (run inside networkSimplex) sums the parallel edges
    // into a single weight-2 edge. Per the ISS-1129 audit, the cut value is
    // then computed as 1 - 2 = -1 where the true cut value is +1.
    val g = newGraph()
    addNode(g, "start")
    addNode(g, "s1")
    addNode(g, "end")
    addEdge(g, "start", "s1", weight = 1, name = "T-start-s1-0")
    addEdge(g, "s1", "end", weight = 1, name = "T-s1-end-1")
    addEdge(g, "s1", "end", weight = 1, name = "T-s1-end-2")

    assertRanksFeasibly(
      g,
      "chain start -> s1 with two parallel edges s1 -> end (simplify sums them to weight 2; " +
        "audit: cut value computed 1 - 2 = -1, true +1)"
    )
  }

  test("ISS-1129 red-2 case 3: network-simplex terminates on the weighted chain a -> b -> c -> d (1, 5, 1)") {
    // a -> b weight 1, b -> c weight 5, c -> d weight 1. Per the ISS-1129
    // audit, the cut value is computed as 1 - 5 = -4 where the true cut value
    // is +5 (a chain tree edge's cut value equals its own weight).
    val g = newGraph()
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addNode(g, "d")
    addEdge(g, "a", "b", weight = 1, name = "T-a-b-0")
    addEdge(g, "b", "c", weight = 5, name = "T-b-c-1")
    addEdge(g, "c", "d", weight = 1, name = "T-c-d-2")

    assertRanksFeasibly(
      g,
      "weighted chain a -> b (weight 1), b -> c (weight 5), c -> d (weight 1) " +
        "(audit: cut value computed 1 - 5 = -4, true +5)"
    )
  }
}
