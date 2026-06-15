/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression test for ISS-1132: the orientation-normalization block in
 * enterEdge (Rank.scala:360-363):
 *
 *   if (!g.hasEdge(v, w)) { v = edge.w; w = edge.v }
 *
 * had ZERO coverage because the existing Gansner section 4.2 test
 * (RankIss1129GansnerPivotSuite) uses nodes named a..h, and the mandatory-
 * pivot tree edge (g, h) is stored lexicographically as (g, h) — the SAME
 * orientation as the directed graph edge g -> h — so the normalization is a
 * no-op there.
 *
 * This suite uses the SAME Gansner et al. 1993, section 4.2 graph, but with
 * node "h" renamed to "B". Because "B" < "g" lexicographically, the
 * undirected spanning tree stores the tree edge as (B, g) — OPPOSITE to the
 * directed graph edge g -> B. Without the normalization block, enterEdge
 * computes `flip` with the wrong orientation and the simplex loop hangs
 * forever (the auditor verified the mutant on 2026-06-10).
 *
 * The expected optimal ranking is identical to the original Gansner section
 * 4.2 result (relative to rank(a)): b=1, c=2, d=3, B=4, e=f=1, g=2. The
 * total weighted edge length drops from 12 (initial longest-path ranking) to
 * 10 (optimal) after the single mandatory pivot.
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 */
package ssg
package graphs
package commons
package layout
package dagre

import munit.FunSuite

import ssg.graphs.commons.layout.graph.Graph

final class RankIss1132Suite extends FunSuite {

  private val TimeoutMs = 15000L

  /** Builds the Gansner et al. 1993 section 4.2 example with node "h" renamed to "B", so the mandatory-pivot tree edge (g, B) is stored lexicographically as (B, g) — opposite to the directed graph
    * edge g -> B. This forces the enterEdge orientation-normalization block at Rank.scala:360-363 to actually execute.
    *
    * The graph has eight nodes a, b, c, d, e, f, g, B and nine edges: a -> b -> c -> d -> B, a -> e -> g, a -> f -> g, g -> B. All edges have minlen = 1, weight = 1.
    */
  private def buildGansnerRenamedExample(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = "TB"
    gl.nodesep = 50
    gl.ranksep = 50
    g.setGraph(gl)

    // Node "h" from the original Gansner example is renamed to "B".
    // "B" < "g" lexicographically, so the undirected tree stores the edge
    // as (B, g) rather than (g, B), exercising the normalization.
    for (name <- List("a", "b", "c", "d", "e", "f", "g", "B")) {
      val label = new NodeLabel
      label.width = 50
      label.height = 30
      label.label = name
      g.setNode(name, label)
    }

    // Same edge set as Gansner section 4.2, with h -> B.
    val edges = List(
      ("a", "b"),
      ("b", "c"),
      ("c", "d"),
      ("d", "B"), // was d -> h
      ("a", "e"),
      ("a", "f"),
      ("e", "g"),
      ("f", "g"),
      ("g", "B") // was g -> h — the mandatory-pivot tree edge
    )
    for (((v, w), i) <- edges.zipWithIndex) {
      val e = new EdgeLabel
      e.minlen = 1
      e.weight = 1
      g.setEdge(v, w, e, s"T-$v-$w-$i")
    }

    g
  }

  test("ISS-1132: enterEdge orientation normalization is exercised on the renamed-Gansner variant (h -> B)") {
    val g = buildGansnerRenamedExample()

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
        s"ISS-1132 reproduced: Rank.rank (network-simplex) did not terminate within ${TimeoutMs}ms " +
          "on the renamed-Gansner variant (h -> B). The mandatory-pivot tree edge (g, B) is stored " +
          "lexicographically as (B, g) — opposite to the directed graph edge g -> B — so enterEdge's " +
          "orientation normalization (Rank.scala:360-363) is required to compute flip correctly. " +
          "Without it, the simplex loop hangs forever."
      )
    }
    failure.foreach(t => fail(s"Rank.rank threw instead of completing: ${t}", t))

    // (a) Rank feasibility (Gansner et al. 1993, section 4.2): for every edge
    // (v, w), rank(w) - rank(v) must be at least minlen(v, w).
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

    // (b) Optimal ranking (Gansner et al. 1993, section 4.2): the single
    // mandatory pivot (leave tree edge (g, B), enter a -> e or a -> f) moves
    // e and f from rank(a)+2 to rank(a)+1 and g from rank(a)+3 to rank(a)+2,
    // reducing total weighted edge length from 12 to 10. The expected optimal
    // ranks (relative to rank(a)) are:
    //   b=1, c=2, d=3, B=4, e=f=1, g=2
    val base = g.node("a").rank
    def relRank(v: String):            Int  = g.node(v).rank - base
    def assertRank(v: String, r: Int): Unit =
      assertEquals(
        relRank(v),
        r,
        s"rank($v) - rank(a) should be $r in the optimal ranking of the Gansner et al. 1993 " +
          s"section 4.2 example (renamed variant: h -> B)"
      )

    assertRank("b", 1)
    assertRank("c", 2)
    assertRank("d", 3)
    assertRank("B", 4)
    assertRank("e", 1)
    assertRank("f", 1)
    assertRank("g", 2)
  }
}
