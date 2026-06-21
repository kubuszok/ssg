/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red-3 test for ISS-1129: after the fix2 round (357fbe17) calcCutValue is
 * correct, but the re-audit found that NO test in the corpus ever executes a
 * simplex pivot — every prior red graph's initial tight tree is already
 * optimal. The first graph that genuinely needs a pivot is the worked example
 * from Gansner, North, Koutsofios, Vo — "A Technique for Drawing Directed
 * Graphs" (IEEE TSE 19(3), 1993), section 4.2: eight nodes a..h with the nine
 * unit-weight, minlen-1 edges
 *
 *   a -> b -> c -> d -> h,   a -> e -> g,   a -> f -> g,   g -> h.
 *
 * The longest-path initial ranking (relative to rank(a)): b=1, c=2, d=3, h=4,
 * e=f=2, g=3. The initial tight tree is {a-b, b-c, c-d, d-h, g-h, e-g, f-g}
 * with the non-tree edges a -> e and a -> f at slack 1, and tree edge (g, h)
 * has cut value -1 (crossings: g -> h counts +1; a -> e and a -> f each count
 * -1). The simplex must therefore pivot exactly once — leave (g, h), enter
 * a -> e or a -> f — producing the paper's optimal ranking (relative to
 * rank(a)): b=1, c=2, d=3, h=4, e=f=1, g=2 (total weighted edge length drops
 * from 12 to 10). At 357fbe17 enterEdge admits the leaving edge itself as the
 * entering candidate (the auditor-verified self-swap), so the
 * leaveEdge/enterEdge/exchangeEdges loop re-exchanges (g, h) with itself
 * forever and Rank.rank never terminates.
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 *
 * dagre-js is not vendored in original-src (see ISS-1072, ISS-1131), so the
 * paper (section 4.2) and the ISS-1129 audit findings are the authoritative
 * references for the expected behaviour.
 */
package ssg
package graphs
package commons
package layout
package dagre

import munit.FunSuite

import ssg.graphs.commons.layout.graph.Graph

final class RankIss1129GansnerPivotSuite extends FunSuite {

  private val TimeoutMs = 15000L

  private def buildGansnerExample(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = "TB"
    gl.nodesep = 50
    gl.ranksep = 50
    g.setGraph(gl)

    for (name <- List("a", "b", "c", "d", "e", "f", "g", "h")) {
      val label = new NodeLabel
      label.width = 50
      label.height = 30
      label.label = name
      g.setNode(name, label)
    }

    val edges = List(
      ("a", "b"),
      ("b", "c"),
      ("c", "d"),
      ("d", "h"),
      ("a", "e"),
      ("a", "f"),
      ("e", "g"),
      ("f", "g"),
      ("g", "h")
    )
    for (((v, w), i) <- edges.zipWithIndex) {
      val e = new EdgeLabel
      e.minlen = 1
      e.weight = 1
      g.setEdge(v, w, e, s"T-$v-$w-$i")
    }

    g
  }

  test("ISS-1129 red-3: network-simplex pivots to the optimal ranking on the Gansner et al. 1993 section 4.2 example") {
    val g = buildGansnerExample()

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
          "on the Gansner et al. 1993 section 4.2 example (initial tight tree has cutvalue(g, h) = -1, " +
          "so a pivot is mandatory). enterEdge self-swaps the leaving edge (g, h) forever."
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

    // (b) The simplex must actually IMPROVE the initial ranking to the paper's
    // optimum (Gansner et al. 1993, section 4.2). The initial tight tree puts
    // e = f = rank(a) + 2 and g = rank(a) + 3 with cutvalue(g, h) = -1; the
    // single mandatory pivot (leave (g, h), enter a -> e or a -> f) moves e and
    // f to rank(a) + 1 and g to rank(a) + 2, reducing the total weighted edge
    // length from 12 to 10. Ranks are normalized relative to rank(a) so the
    // assertion is independent of the simplex's choice of root.
    val base = g.node("a").rank
    def relRank(v: String):            Int  = g.node(v).rank - base
    def assertRank(v: String, r: Int): Unit =
      assertEquals(
        relRank(v),
        r,
        s"rank($v) - rank(a) should be $r in the optimal ranking of the Gansner et al. 1993 " +
          s"section 4.2 example (feasible but unimproved rankings keep e, f, g one rank too low)"
      )

    assertRank("b", 1)
    assertRank("c", 2)
    assertRank("d", 3)
    assertRank("h", 4)
    assertRank("e", 1)
    assertRank("f", 1)
    assertRank("g", 2)
  }
}
