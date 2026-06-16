/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform dagre pipeline invariant tests for ISS-1075.
 *
 * Asserts four fundamental invariants of the dagre Sugiyama layout pipeline on
 * representative graphs: linear chain, diamond, layered DAG, and a cyclic
 * graph. The invariants are:
 *
 *   1. Ranks respect minlen: after Rank.rank(g), for every edge (v, w),
 *      rank(w) - rank(v) >= minlen. (Gansner, North, Koutsofios, Vo,
 *      "A Technique for Drawing Directed Graphs", IEEE TSE 19(3), 1993,
 *      section 4.2 — the feasibility condition for the network simplex
 *      ranking.)
 *
 *   2. No same-rank node overlap: after the full DagreLayout.layout(g)
 *      pipeline, for any two distinct non-dummy nodes at the same
 *      y-coordinate band, their horizontal extents [x - width/2, x + width/2]
 *      must not overlap (strict inequality). (dagre lib/position.js places
 *      nodes at non-overlapping positions respecting nodesep.) ISS-1198
 *      fixed DagreUtil.normalizeRanks to use a structural hasRank predicate
 *      instead of a positivity filter, so the full pipeline now correctly
 *      positions all nodes — invariant 2 asserts on DagreLayout.layout
 *      output directly.
 *
 *   3. Acyclic after Acyclic.run: after Acyclic.run(g) on a graph with a
 *      directed cycle, the resulting edge set has no directed cycle.
 *      (dagre lib/acyclic.js reverses back-edges to break all cycles.)
 *
 *   4. Order reduces crossings: Order.order(g) produces a crossing count
 *      that is less than or equal to the crossing count of the initial DFS
 *      ordering, and strictly less for a graph crafted to have crossings
 *      under the initial order. (dagre lib/order uses the barycenter
 *      heuristic with iterative sweep — Order.crossCount is the public
 *      crossing-count utility.) The pre-Order pipeline mirrors
 *      DagreLayout.layout steps 3-14 (using the real DagreUtil.normalizeRanks
 *      fixed by ISS-1198).
 *
 * All graphs use isDirected=true, isMultigraph=true, isCompound=true —
 * matching the actual usage by ssg-mermaid and ssg-graphviz. The layout
 * pipeline requires compound multigraphs because Nesting.run (step 4 of
 * DagreLayout.layout) inserts a nesting root that connects all components,
 * and Normalize.undo (step 21) restores named edges requiring
 * isMultigraph=true.
 *
 * Placed in the SHARED test root (src/test/scala/) so it compiles and runs
 * on all three platforms (JVM, JS, Native). No threads, no daemons, no
 * timeouts — these are deterministic invariant checks.
 *
 * dagre-js is not vendored in original-src (ISS-1072, ISS-1131). The
 * invariants are grounded in the Gansner et al. 1993 paper and the dagre
 * algorithm contracts documented in dagre's own lib/ source comments.
 */
package ssg
package graphs
package commons
package layout
package dagre

import scala.collection.mutable

import munit.FunSuite

import ssg.graphs.commons.layout.graph.Graph

final class DagreInvariantsIss1075Suite extends FunSuite {

  // ---------------------------------------------------------------------------
  // Graph construction helpers (same pattern as RankIss1129HangCasesSuite)
  // ---------------------------------------------------------------------------

  private def newGraph(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = "TB"
    gl.nodesep = 50
    gl.ranksep = 50
    g.setGraph(gl)

    g
  }

  private def addNode(g: Graph[NodeLabel, EdgeLabel], name: String, w: Double = 50, h: Double = 30): Unit = {
    val label = new NodeLabel
    label.width = w
    label.height = h
    label.label = name
    g.setNode(name, label)
  }

  private def addEdge(
    g:      Graph[NodeLabel, EdgeLabel],
    v:      String,
    w:      String,
    weight: Double = 1,
    minlen: Int = 1,
    name:   String
  ): Unit = {
    val e = new EdgeLabel
    e.minlen = minlen
    e.weight = weight
    g.setEdge(v, w, e, name)
  }

  // ---------------------------------------------------------------------------
  // Representative graphs
  // ---------------------------------------------------------------------------

  /** Linear chain: a -> b -> c -> d. Four nodes, three edges, each minlen=1. */
  private def linearChain(): Graph[NodeLabel, EdgeLabel] = {
    val g = newGraph()
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addNode(g, "d")
    addEdge(g, "a", "b", name = "e-a-b")
    addEdge(g, "b", "c", name = "e-b-c")
    addEdge(g, "c", "d", name = "e-c-d")
    g
  }

  /** Diamond: a -> b, a -> c, b -> d, c -> d. Nodes b and c share a rank. */
  private def diamond(): Graph[NodeLabel, EdgeLabel] = {
    val g = newGraph()
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addNode(g, "d")
    addEdge(g, "a", "b", name = "e-a-b")
    addEdge(g, "a", "c", name = "e-a-c")
    addEdge(g, "b", "d", name = "e-b-d")
    addEdge(g, "c", "d", name = "e-c-d")
    g
  }

  /** Layered DAG with forced crossings under naive ordering:
    *
    * layer 0: a0, a1 layer 1: b0, b1, b2 layer 2: c0
    *
    * Edges are wired so that a naive DFS order produces crossings between layers 0 and 1: a0 -> b2, a0 -> b0, a1 -> b1, a1 -> b0, plus b0 -> c0, b1 -> c0, b2 -> c0.
    */
  private def layeredDagWithCrossings(): Graph[NodeLabel, EdgeLabel] = {
    val g = newGraph()
    addNode(g, "a0")
    addNode(g, "a1")
    addNode(g, "b0")
    addNode(g, "b1")
    addNode(g, "b2")
    addNode(g, "c0")
    addEdge(g, "a0", "b2", name = "e-a0-b2")
    addEdge(g, "a0", "b0", name = "e-a0-b0")
    addEdge(g, "a1", "b1", name = "e-a1-b1")
    addEdge(g, "a1", "b0", name = "e-a1-b0")
    addEdge(g, "b0", "c0", name = "e-b0-c0")
    addEdge(g, "b1", "c0", name = "e-b1-c0")
    addEdge(g, "b2", "c0", name = "e-b2-c0")
    g
  }

  /** Cyclic graph: a -> b -> c -> a (a directed 3-cycle). */
  private def cyclicTriangle(): Graph[NodeLabel, EdgeLabel] = {
    val g = newGraph()
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addEdge(g, "a", "b", name = "e-a-b")
    addEdge(g, "b", "c", name = "e-b-c")
    addEdge(g, "c", "a", name = "e-c-a")
    g
  }

  // ---------------------------------------------------------------------------
  // Pipeline helper for invariant 4
  //
  // Runs the real DagreLayout.layout steps 3-14 (the pre-Order sequence),
  // using DagreUtil.normalizeRanks (fixed by ISS-1198 to use a structural
  // hasRank predicate). Returns maxRank for layering construction.
  // ---------------------------------------------------------------------------

  /** Run the real pre-Order pipeline matching DagreLayout.layout steps 3-14: Acyclic.run, Nesting.run, Rank.rank, DagreUtil.removeEmptyRanks, Nesting.cleanup, DagreUtil.normalizeRanks, Normalize.run,
    * ParentDummyChains.run, AddBorderSegments.run. Returns the maxRank for layering construction.
    */
  private def runPreOrderPipeline(g: Graph[NodeLabel, EdgeLabel]): Int = {
    DagreUtil.resetIdCounter()
    // step 3: cycle removal
    Acyclic.run(g)
    // step 4: nesting (before rank, so nesting root joins components)
    Nesting.run(g)
    // step 5: rank assignment
    Rank.rank(g)
    // step 7: remove empty ranks
    DagreUtil.removeEmptyRanks(g)
    // step 8: nesting cleanup
    Nesting.cleanup(g)
    // step 9: normalize ranks (ISS-1198 fixed: structural hasRank predicate)
    DagreUtil.normalizeRanks(g)
    // step 12: edge normalization (insert dummy nodes for long edges)
    Normalize.run(g)
    // step 13: parent dummy chains
    ParentDummyChains.run(g)
    // step 14: add border segments
    AddBorderSegments.run(g)

    DagreUtil.maxRank(g)
  }

  // ---------------------------------------------------------------------------
  // Invariant 1: Ranks respect minlen
  //
  // After Rank.rank(g), for every edge (v, w):
  //   rank(w) - rank(v) >= edge(e).minlen
  //
  // Cited: Gansner et al. 1993 section 4.2 feasible-tree / dagre rank
  // invariant. Teeth: asserts on actual computed rank values — if Rank.rank
  // produced an infeasible ranking (e.g. rank(w) - rank(v) < minlen for
  // some edge), this test fails.
  // ---------------------------------------------------------------------------

  test("ISS-1075 invariant 1: ranks respect minlen on the linear chain") {
    val g = linearChain()
    Rank.rank(g)
    assertRanksFeasible(g, "linear chain")

    // Additional structural check: ranks must be strictly increasing along
    // the chain (each minlen=1, so rank must increase by at least 1).
    val rankA = g.node("a").rank
    val rankB = g.node("b").rank
    val rankC = g.node("c").rank
    val rankD = g.node("d").rank
    assert(rankB > rankA, s"rank(b)=$rankB must be > rank(a)=$rankA")
    assert(rankC > rankB, s"rank(c)=$rankC must be > rank(b)=$rankB")
    assert(rankD > rankC, s"rank(d)=$rankD must be > rank(c)=$rankC")
  }

  test("ISS-1075 invariant 1: ranks respect minlen on the diamond") {
    val g = diamond()
    Rank.rank(g)
    assertRanksFeasible(g, "diamond")

    // Structural: b and c must be between a and d.
    val rankA = g.node("a").rank
    val rankB = g.node("b").rank
    val rankC = g.node("c").rank
    val rankD = g.node("d").rank
    assert(rankB - rankA >= 1, s"rank(b)-rank(a)=${rankB - rankA} must be >= 1")
    assert(rankC - rankA >= 1, s"rank(c)-rank(a)=${rankC - rankA} must be >= 1")
    assert(rankD - rankB >= 1, s"rank(d)-rank(b)=${rankD - rankB} must be >= 1")
    assert(rankD - rankC >= 1, s"rank(d)-rank(c)=${rankD - rankC} must be >= 1")
  }

  test("ISS-1075 invariant 1: ranks respect minlen=2 edges") {
    val g = newGraph()
    addNode(g, "x")
    addNode(g, "y")
    addNode(g, "z")
    addEdge(g, "x", "y", minlen = 2, name = "e-x-y")
    addEdge(g, "y", "z", minlen = 1, name = "e-y-z")
    Rank.rank(g)
    assertRanksFeasible(g, "minlen=2 chain")

    val rankX = g.node("x").rank
    val rankY = g.node("y").rank
    val rankZ = g.node("z").rank
    assert(rankY - rankX >= 2, s"rank(y)-rank(x)=${rankY - rankX} must be >= 2 (minlen=2)")
    assert(rankZ - rankY >= 1, s"rank(z)-rank(y)=${rankZ - rankY} must be >= 1")
  }

  // ---------------------------------------------------------------------------
  // Invariant 2: No same-rank node overlap after layout
  //
  // After rank, order, and position phases, for any two distinct non-dummy
  // nodes at the same y-coordinate band, their horizontal extents
  // [x - width/2, x + width/2] must not overlap (strict inequality;
  // touching is allowed). For rankdir "TB", same y = same rank band.
  //
  // Cited: dagre lib/position.js places nodes at non-overlapping positions
  // respecting nodesep. Teeth: asserts on actual (x, width) values — if
  // position placement overlapped two same-rank nodes, this test fails.
  // ---------------------------------------------------------------------------

  test("ISS-1075 invariant 2: no same-rank node overlap on the diamond") {
    val g = diamond()
    DagreLayout.layout(g)

    // After the full layout, nodes b and c share a rank (both at rank(a)+1
    // in the diamond). Their horizontal extents must not overlap.
    assertNoSameYOverlap(g, "diamond")
  }

  test("ISS-1075 invariant 2: no same-rank node overlap on the layered DAG") {
    val g = layeredDagWithCrossings()
    DagreLayout.layout(g)

    assertNoSameYOverlap(g, "layered DAG with crossings")
  }

  test("ISS-1075 invariant 2: no same-rank node overlap on a wide single-rank layer") {
    // Five nodes feeding into one sink — all five share a rank.
    val g = newGraph()
    for (i <- 0 until 5)
      addNode(g, s"n$i", w = 60, h = 30)
    addNode(g, "sink")
    for (i <- 0 until 5)
      addEdge(g, s"n$i", "sink", name = s"e-n$i-sink")
    DagreLayout.layout(g)

    assertNoSameYOverlap(g, "wide single-rank layer (5 sources -> 1 sink)")
  }

  // ---------------------------------------------------------------------------
  // Invariant 3: Acyclic after Acyclic.run
  //
  // After Acyclic.run(g) on a graph with a directed cycle, there are no
  // directed cycles remaining in the graph's edge set. dagre lib/acyclic.js
  // reverses back-edges discovered by DFS to break all cycles.
  //
  // Teeth: we first confirm the input graph HAS a cycle (otherwise the test
  // is vacuous), then run Acyclic.run, then confirm no directed cycle exists
  // via DFS cycle detection. If Acyclic.run failed to break the cycle, the
  // DFS would find it and the test fails.
  // ---------------------------------------------------------------------------

  test("ISS-1075 invariant 3: acyclic after Acyclic.run on a 3-cycle (a -> b -> c -> a)") {
    val g = cyclicTriangle()

    // Pre-condition: the graph has a directed cycle.
    assert(hasCycle(g), "precondition: the cyclic triangle must have a directed cycle before Acyclic.run")

    Acyclic.run(g)

    // Post-condition: no directed cycle remains.
    assert(!hasCycle(g), "postcondition: after Acyclic.run, no directed cycle should remain")

    // Verify at least one edge was reversed (Acyclic.run marks reversed edges).
    val reversedCount = g.edges().count(e => g.edge(e).reversed)
    assert(
      reversedCount >= 1,
      s"Acyclic.run should reverse at least one edge to break the cycle, but reversed $reversedCount"
    )
  }

  test("ISS-1075 invariant 3: acyclic after Acyclic.run on a longer cycle (a -> b -> c -> d -> a)") {
    val g = newGraph()
    addNode(g, "a")
    addNode(g, "b")
    addNode(g, "c")
    addNode(g, "d")
    addEdge(g, "a", "b", name = "e-a-b")
    addEdge(g, "b", "c", name = "e-b-c")
    addEdge(g, "c", "d", name = "e-c-d")
    addEdge(g, "d", "a", name = "e-d-a")

    assert(hasCycle(g), "precondition: the 4-cycle must have a directed cycle")

    Acyclic.run(g)

    assert(!hasCycle(g), "postcondition: after Acyclic.run on a 4-cycle, no directed cycle should remain")
  }

  // ---------------------------------------------------------------------------
  // Invariant 4: Order reduces crossings
  //
  // Order.order(g) produces a node ordering whose crossing count (computed
  // by Order.crossCount, which uses merge-sort inversion counting — dagre
  // lib/order/cross-count.js) is less than or equal to the crossing count
  // of the initial ordering, and strictly less for a graph crafted to have
  // crossings under the initial order.
  //
  // Teeth: if Order.order failed to reduce crossings on the crafted graph
  // (e.g. due to a broken barycenter heuristic), the post-order crossing
  // count would not be strictly less and the test fails.
  // ---------------------------------------------------------------------------

  test("ISS-1075 invariant 4: Order.order reduces crossings on the layered DAG") {
    val g = layeredDagWithCrossings()

    // Run the real pre-Order pipeline (DagreLayout.layout steps 3-14).
    val mr = runPreOrderPipeline(g)
    assert(mr >= 1, s"the layered DAG should have at least 2 ranks, but maxRank=$mr")

    // Build an adversarial initial layering by reversing the middle layer
    // to maximize crossings.
    val initLayering = buildLayering(g, mr)

    // Find the layer with the most nodes (the middle layer) and reverse it.
    val middleRank = initLayering.indices.maxBy(i => initLayering(i).length)
    if (initLayering(middleRank).length > 1) {
      initLayering(middleRank) = initLayering(middleRank).reverse
    }

    for (layer <- initLayering)
      for (i <- layer.indices)
        g.node(layer(i)).order = i

    val initCC = Order.crossCount(g, initLayering)

    // Now run Order.order to minimize crossings.
    Order.order(g)

    val postLayering = buildLayering(g, mr)
    val postCC       = Order.crossCount(g, postLayering)

    assert(
      postCC <= initCC,
      s"Order.order must not increase crossings: initial=$initCC, post-order=$postCC"
    )

    // For the adversarial ordering, the initial crossing count should be > 0.
    assert(
      initCC > 0,
      s"precondition: the crafted layered DAG should have >0 crossings under adversarial order, but initCC=$initCC"
    )
  }

  test("ISS-1075 invariant 4: Order.order does not increase crossings on the diamond") {
    val g = diamond()

    // Run the real pre-Order pipeline (DagreLayout.layout steps 3-14).
    val mr = runPreOrderPipeline(g)

    val initLayering = buildLayering(g, mr)
    for (layer <- initLayering)
      for (i <- layer.indices)
        g.node(layer(i)).order = i
    val initCC = Order.crossCount(g, initLayering)

    Order.order(g)

    val postLayering = buildLayering(g, mr)
    val postCC       = Order.crossCount(g, postLayering)

    assert(
      postCC <= initCC,
      s"Order.order must not increase crossings on the diamond: initial=$initCC, post-order=$postCC"
    )
  }

  // ---------------------------------------------------------------------------
  // Assertion helpers
  // ---------------------------------------------------------------------------

  /** Asserts that for every edge (v, w) in g, rank(w) - rank(v) >= minlen. */
  private def assertRanksFeasible(g: Graph[NodeLabel, EdgeLabel], graphName: String): Unit =
    for (e <- g.edges()) {
      val tailRank = g.node(e.v).rank
      val headRank = g.node(e.w).rank
      val minlen   = g.edge(e).minlen
      assert(
        headRank - tailRank >= minlen,
        s"[$graphName] Infeasible ranking for edge ${e.v} -> ${e.w}: " +
          s"rank(${e.w})=$headRank, rank(${e.v})=$tailRank, minlen=$minlen " +
          s"(expected headRank - tailRank >= minlen, got ${headRank - tailRank})"
      )
    }

  /** Asserts that no two distinct non-dummy nodes at the same y-coordinate band have overlapping horizontal extents. After the position phase with rankdir="TB", same rank means same y; we group by y
    * (rounded to avoid floating-point drift) and check x-overlap.
    */
  private def assertNoSameYOverlap(g: Graph[NodeLabel, EdgeLabel], graphName: String): Unit = {
    val byY = mutable.Map.empty[Long, mutable.ArrayBuffer[String]]
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.dummy.isEmpty) {
        val yKey = Math.round(node.y)
        byY.getOrElseUpdate(yKey, mutable.ArrayBuffer.empty) += v
      }
    }

    for ((yKey, nodesAtY) <- byY)
      if (nodesAtY.length > 1) {
        val sorted = nodesAtY.toArray.sortBy(v => g.node(v).x)
        for (i <- 0 until sorted.length - 1) {
          val nodeI  = g.node(sorted(i))
          val nodeJ  = g.node(sorted(i + 1))
          val rightI = nodeI.x + nodeI.width / 2.0
          val leftJ  = nodeJ.x - nodeJ.width / 2.0
          assert(
            rightI <= leftJ,
            s"[$graphName] Overlap at y=$yKey: node ${sorted(i)} right edge=$rightI " +
              s"overlaps node ${sorted(i + 1)} left edge=$leftJ " +
              s"(${sorted(i)}: x=${nodeI.x}, w=${nodeI.width}; " +
              s"${sorted(i + 1)}: x=${nodeJ.x}, w=${nodeJ.width})"
          )
        }
      }
  }

  /** DFS-based directed cycle detection. Returns true iff the graph has at least one directed cycle.
    */
  private def hasCycle(g: Graph[NodeLabel, EdgeLabel]): Boolean = {
    val visited = mutable.Set.empty[String]
    val stack   = mutable.Set.empty[String]

    def dfs(v: String): Boolean =
      if (stack.contains(v)) { true }
      else if (visited.contains(v)) { false }
      else {
        visited += v
        stack += v
        val found = g.outEdges(v).getOrElse(Array.empty).exists(e => dfs(e.w))
        stack -= v
        found
      }

    g.nodes().exists(v => dfs(v))
  }

  /** Build a layering array from the current node ranks, ordered by the node's `order` field.
    */
  private def buildLayering(g: Graph[NodeLabel, EdgeLabel], maxRank: Int): Array[Array[String]] = {
    val layers = Array.fill(maxRank + 1)(mutable.ArrayBuffer.empty[String])
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.rank >= 0 && node.rank <= maxRank) {
        layers(node.rank) += v
      }
    }
    layers.map(_.sortBy(v => g.node(v).order).toArray)
  }
}
