/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Rank assignment for the Sugiyama algorithm.
 * Implements longest-path ranking and the network simplex algorithm.
 *
 * Original source: dagre (dagrejs/dagre) lib/rank
 * Original author: Chris Pettitt
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 *
 * Migration notes:
 *   - calcCutValue implements the full incremental network-simplex cut-value
 *     computation of Gansner, North, Koutsofios, Vo, "A Technique for Drawing
 *     Directed Graphs", IEEE TSE 19(3), 1993, §4.2, matching dagre-js
 *     lib/rank/network-simplex.js `calcCutValue`. The accumulator is seeded
 *     with the tree edge's own weight in the simplified directed graph, then
 *     for each other incident graph edge the sign of its weight is
 *     orientation-aware — pointsToHead = (isOutEdge == childIsTail) — and when
 *     the neighbour edge (child, other) is itself a tree edge its already
 *     computed cut value folds in with the opposite sign (the incremental
 *     adjacent-tree-edge term). Computing cut values in postorder guarantees
 *     those adjacent tree edges' values already exist. Without the
 *     orientation-aware signs and the incremental term, networkSimplex never
 *     terminated on graphs such as the diamond-with-cross-edge or the
 *     start -> s1 -> end chain with parallel s1 -> end edges produced by state
 *     diagrams (ISS-1129). dagre-js is not vendored in original-src (ISS-1072);
 *     this fix follows the published algorithm and reconstructed dagre
 *     semantics, so a source-level `enforce compare` against dagre-js remains
 *     pending ISS-1072.
 *   - enterEdge and updateRanks are ported from dagre-js
 *     lib/rank/network-simplex.js (`enterEdge`/`isDescendant`/`updateRanks`).
 *     enterEdge normalizes the leaving (undirected, lexicographically stored)
 *     tree edge to the directed graph's orientation before computing `flip`,
 *     filters candidates by dagre's exact predicate
 *     `flip === isDescendant(edge.v) && flip !== isDescendant(edge.w)`
 *     (which excludes the leaving edge itself — without it the slack-0
 *     self-swap loops forever on the Gansner et al. 1993 §4.2 worked example,
 *     ISS-1129/ISS-1131), and throws on an empty candidate set rather than
 *     picking an arbitrary edge. updateRanks recomputes ranks by a preorder
 *     walk from the tree root, deriving each node's rank from its tree-parent
 *     and the graph orientation of the connecting edge. dagre-js is not
 *     vendored (ISS-1072), so these methods follow the published §4.2 algorithm
 *     and reconstructed dagre semantics; a source-level `enforce compare`
 *     against dagre-js is pending ISS-1072.
 */
package ssg
package graphs
package commons
package layout
package dagre

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable
import ssg.graphs.commons.layout.graph.{ EdgeObj, Graph, GraphAlgorithms }

object Rank {

  def rank(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val ranker = g.graph[GraphLabel]().ranker
    ranker match {
      case "network-simplex" => networkSimplexRank(g)
      case "tight-tree"      => tightTreeRank(g)
      case "longest-path"    => longestPath(g)
      case _                 => networkSimplexRank(g)
    }
  }

  def longestPath(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val visited = mutable.Set.empty[String]

    def dfs(v: String): Int = boundary {
      if (visited.contains(v)) {
        break(g.node(v).rank)
      }
      visited += v
      val outEdges = g.outEdges(v).getOrElse(Array.empty)
      var minRank  = Int.MaxValue
      for (e <- outEdges) {
        val targetRank = dfs(e.w)
        val minlen     = g.edge(e).minlen
        if (targetRank - minlen < minRank) {
          minRank = targetRank - minlen
        }
      }
      if (minRank == Int.MaxValue) { minRank = 0 }
      g.node(v).rank = minRank
      minRank
    }

    for (v <- g.sources())
      dfs(v)
  }

  private def tightTreeRank(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    longestPath(g)
    networkSimplex(g)
  }

  private def networkSimplexRank(g: Graph[NodeLabel, EdgeLabel]): Unit =
    networkSimplex(g)

  // ---- Network Simplex Algorithm ----

  def networkSimplex(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val simplified = DagreUtil.simplify(g)
    longestPath(simplified)
    val tree = feasibleTree(simplified)
    initLowLimValues(tree)
    initCutValues(tree, simplified)

    var edge = leaveEdge(tree)
    while (edge.isDefined) {
      val enter = enterEdge(tree, simplified, edge.get)
      exchangeEdges(tree, simplified, edge.get, enter)
      edge = leaveEdge(tree)
    }

    // Copy ranks back to original graph
    for (v <- simplified.nodes())
      g.node(v).rank = simplified.node(v).rank
  }

  private def feasibleTree(g: Graph[NodeLabel, EdgeLabel]): Graph[NodeLabel, EdgeLabel] = {
    val tree = new Graph[NodeLabel, EdgeLabel](isDirected = false)

    val start     = g.nodes()(0)
    val nodeCount = g.nodeCount

    tree.setNode(start, g.node(start))

    var edge = tightEdge(tree, g)
    while (tree.nodeCount < nodeCount) {
      edge match {
        case Some(e) =>
          val edgeLabel = g.edge(e)
          val vInTree   = tree.hasNode(e.v)
          val newNode   = if (vInTree) e.w else e.v
          tree.setNode(newNode, g.node(newNode))
          tree.setEdge(e.v, e.w, edgeLabel)
          val nl = new EdgeLabel
          tree.setEdge(e.v, e.w, nl)
          tighten(tree, g, newNode)
        case None =>
          // No tight edge found — shift ranks
          val delta = slack(g, tree)
          for (v <- g.nodes())
            if (!tree.hasNode(v)) {
              g.node(v).rank = g.node(v).rank - delta
            }
      }
      edge = tightEdge(tree, g)
    }

    tree
  }

  private def tightEdge(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel]
  ): Option[EdgeObj] = boundary {
    for (v <- tree.nodes())
      for (e <- g.nodeEdges(v).getOrElse(Array.empty)) {
        val other = if (e.v == v) e.w else e.v
        if (!tree.hasNode(other) && edgeSlack(g, e) == 0) {
          break(Some(e))
        }
      }
    None
  }

  private def tighten(
    tree:    Graph[NodeLabel, EdgeLabel],
    g:       Graph[NodeLabel, EdgeLabel],
    newNode: String
  ): Unit =
    for (e <- g.nodeEdges(newNode).getOrElse(Array.empty)) {
      val other = if (e.v == newNode) e.w else e.v
      if (!tree.hasNode(other) && edgeSlack(g, e) == 0) {
        tree.setNode(other, g.node(other))
        tree.setEdge(e.v, e.w, new EdgeLabel)
        tighten(tree, g, other)
      }
    }

  private def edgeSlack(g: Graph[NodeLabel, EdgeLabel], e: EdgeObj): Int =
    g.node(e.w).rank - g.node(e.v).rank - g.edge(e).minlen

  private def slack(g: Graph[NodeLabel, EdgeLabel], tree: Graph[NodeLabel, EdgeLabel]): Int = {
    var min = Int.MaxValue
    for (e <- g.edges()) {
      val s       = edgeSlack(g, e)
      val vInTree = tree.hasNode(e.v)
      val wInTree = tree.hasNode(e.w)
      if (vInTree != wInTree && s < min) {
        min = s
      }
    }
    min
  }

  // DFS numbering for cut value computation
  private def initLowLimValues(tree: Graph[NodeLabel, EdgeLabel], root: String = ""): Unit = {
    val r = if (root.isEmpty) tree.nodes()(0) else root
    dfsAssignLowLim(tree, mutable.Set.empty, 1, r)
  }

  private def dfsAssignLowLim(
    tree:       Graph[NodeLabel, EdgeLabel],
    visited:    mutable.Set[String],
    nextLim:    Int,
    v:          String,
    parentNode: Nullable[String] = Nullable.Null
  ): Int = {
    visited += v
    val low = nextLim
    var lim = nextLim

    for (w <- tree.neighbors(v).getOrElse(Array.empty))
      if (!visited.contains(w)) {
        lim = dfsAssignLowLim(tree, visited, lim, w, Nullable(v))
      }

    val node = tree.node(v)
    node.low = low
    node.lim = lim
    node.parent = parentNode
    lim + 1
  }

  private def initCutValues(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel]
  ): Unit = {
    val vs = GraphAlgorithms.postorder(tree, Array(tree.nodes()(0)))
    // Skip the root (last in postorder is the root when processed this way)
    for (v <- vs)
      calcCutValue(tree, g, v)
  }

  private def calcCutValue(
    tree:  Graph[NodeLabel, EdgeLabel],
    g:     Graph[NodeLabel, EdgeLabel],
    child: String
  ): Unit = {
    val parentOpt = tree.node(child).parent

    if (parentOpt.isEmpty) { () }
    else {

      val parent = parentOpt.get

      // Network-simplex cut value, full incremental formulation (Gansner,
      // North, Koutsofios, Vo, "A Technique for Drawing Directed Graphs", IEEE
      // TSE 19(3), 1993, §4.2); a direct port of dagre-js
      // lib/rank/network-simplex.js `calcCutValue`. The cut value of the tree
      // edge (child, parent) is the signed sum of the weights of every graph
      // edge crossing the cut induced by removing that tree edge — counting an
      // edge positively when it points from the tail component to the head
      // component, negatively otherwise. dagre computes this incrementally in
      // postorder so that the already-computed cut values of adjacent tree
      // edges deeper in the subtree fold in directly.
      //
      // `childIsTail` records which orientation the tree edge has in the
      // directed graph `g`: true when the tree edge runs child ->
      // parent, false when it runs parent -> child. The accumulator is seeded
      // with that tree edge's own weight, then for each other graph edge `e`
      // incident to `child`:
      //   - `isOutEdge` is whether `e` leaves `child` (e.v == child);
      //   - `pointsToHead = (isOutEdge == childIsTail)` is whether `e` points
      //     from the tail component into the head component of the cut, which
      //     determines the sign of its weight;
      //   - when (child, other) is itself a tree edge, its already-computed cut
      //     value participates with the opposite sign, because that adjacent
      //     tree edge's cut already aggregates the crossing edges of the deeper
      //     subtree (this incremental term is what makes a chain such as
      //     start -> s1 -> end with parallel s1 -> end edges converge instead
      //     of looping forever in leaveEdge/enterEdge/exchangeEdges — ISS-1129).
      //
      // The directed graph `g` here is DagreUtil.simplify(g) — a non-multigraph
      // with parallel edges pre-summed — so g.edge(child, parent) /
      // g.edge(parent, child) resolves the unique tree edge and the iteration
      // already sees summed weights. dagre-js is not vendored (ISS-1072), so
      // this follows the published algorithm; a source-level `enforce compare`
      // against dagre-js is tracked by ISS-1072.
      val childIsTail = g.hasEdge(child, parent)
      var cutValue    =
        if (childIsTail) g.edge(child, parent).weight.toInt
        else g.edge(parent, child).weight.toInt
      for (e <- g.nodeEdges(child).getOrElse(Array.empty)) {
        val isOutEdge = e.v == child
        val other     = if (isOutEdge) e.w else e.v

        if (other != parent) {
          val pointsToHead = isOutEdge == childIsTail
          val otherWeight  = g.edge(e).weight.toInt

          cutValue += (if (pointsToHead) otherWeight else -otherWeight)
          // If (child, other) is itself a tree edge, fold in its already
          // computed cut value (postorder guarantees it exists).
          tree.edgeOpt(child, other).foreach { otherTreeEdge =>
            val otherCutValue = otherTreeEdge.cutvalue
            cutValue += (if (pointsToHead) -otherCutValue else otherCutValue)
          }
        }
      }

      // Store cut value on the tree edge between child and parent
      val treeEdge =
        if (tree.hasEdge(child, parent)) tree.edgeOpt(child, parent)
        else tree.edgeOpt(parent, child)
      treeEdge.foreach(_.cutvalue = cutValue)
    } // end if parentOpt.nonEmpty
  }

  private def leaveEdge(tree: Graph[NodeLabel, EdgeLabel]): Option[EdgeObj] = boundary {
    for (e <- tree.edges()) {
      val label = tree.edge(e)
      if (label.cutvalue < 0) {
        break(Some(e))
      }
    }
    None
  }

  // isDescendant: a node is a descendant of `rootLabel`'s subtree iff its `lim`
  // falls within the half-open low..lim interval assigned to that root by the
  // postorder DFS numbering. Direct port of dagre-js network-simplex.js
  // `isDescendant`: `rootLabel.low <= vLabel.lim && vLabel.lim <= rootLabel.lim`.
  private def isDescendant(vLabel: NodeLabel, rootLabel: NodeLabel): Boolean =
    rootLabel.low <= vLabel.lim && vLabel.lim <= rootLabel.lim

  // enterEdge — port of dagre-js network-simplex.js `enterEdge`. Given the
  // leaving tree edge, find the minimum-slack non-tree graph edge that crosses
  // the cut in the orientation that re-tightens the tree. dagre-js is not
  // vendored (ISS-1072); this follows the Gansner, North, Koutsofios, Vo
  // "A Technique for Drawing Directed Graphs" (IEEE TSE 19(3), 1993), §4.2
  // network-simplex enter-edge selection and reconstructed dagre semantics; a
  // source-level `enforce compare` against dagre-js is tracked by ISS-1072
  // (vendoring prerequisite).
  private def enterEdge(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel],
    edge: EdgeObj
  ): EdgeObj = {
    var v = edge.v
    var w = edge.w

    // For the rest of this function we assume that v is the tail and w is the
    // head, so if we don't have this edge in the graph we should flip it to
    // match the correct orientation. The undirected tree stores tree edges
    // lexicographically (EdgeObj.edgeArgsToId), so the leaving edge's stored
    // v/w may be in the wrong orientation relative to the directed graph `g`;
    // normalize to `g`'s orientation before computing `flip` (defect 1).
    if (!g.hasEdge(v, w)) {
      v = edge.w
      w = edge.v
    }

    val vLabel = tree.node(v)
    val wLabel = tree.node(w)

    // If the root is in the tail of the edge then we need to flip the logic
    // that checks for the head and tail nodes in the candidates filter below.
    var tailLabel = vLabel
    var flip      = false
    if (vLabel.lim > wLabel.lim) {
      tailLabel = wLabel
      flip = true
    }

    var bestEdge: Option[EdgeObj] = None
    var bestSlack = Int.MaxValue

    for (e <- g.edges())
      // Candidate filter (defect 2): exactly dagre-js's
      //   flip === isDescendant(g.node(e.v), tailLabel) &&
      //   flip !== isDescendant(g.node(e.w), tailLabel)
      // This excludes the leaving edge itself (whose endpoints are both inside
      // or both outside the tail subtree in the orientation dagre selects),
      // preventing the slack-0 self-swap loop.
      if (
        flip == isDescendant(tree.node(e.v), tailLabel) &&
        flip != isDescendant(tree.node(e.w), tailLabel)
      ) {
        val s = edgeSlack(g, e)
        if (s < bestSlack) {
          bestSlack = s
          bestEdge = Some(e)
        }
      }

    // dagre reduces over a NON-EMPTY candidate list (util.minBy). An empty
    // candidate set is a network-simplex invariant violation — never fall back
    // to an arbitrary graph edge (defect 3): fail loudly instead.
    bestEdge.getOrElse(
      throw new IllegalStateException(
        s"enterEdge: no candidate edge crosses the cut induced by leaving edge ${edge.v} -> ${edge.w}; " +
          "the network-simplex spanning tree is not tight (algorithm invariant violated)"
      )
    )
  }

  private def exchangeEdges(
    tree:     Graph[NodeLabel, EdgeLabel],
    g:        Graph[NodeLabel, EdgeLabel],
    leaving:  EdgeObj,
    entering: EdgeObj
  ): Unit = {
    tree.removeEdge(leaving)
    tree.setEdge(entering.v, entering.w, new EdgeLabel)

    initLowLimValues(tree)
    initCutValues(tree, g)

    // Update ranks: shift subtree ranks to satisfy new tree edge
    updateRanks(tree, g)
  }

  // updateRanks — port of dagre-js network-simplex.js `updateRanks`. After an
  // edge exchange the tree is re-rooted and re-numbered (initLowLimValues), so
  // ranks must be recomputed by a preorder walk from the tree root: each node's
  // rank is its tree-parent's rank adjusted by the GRAPH orientation of the
  // connecting edge. If the tree edge points parent -> child in `g`
  // (g.hasEdge(parent, child)) then rank(child) = rank(parent) + minlen; if it
  // points child -> parent in `g` then rank(child) = rank(parent) - minlen.
  // The root keeps its existing rank (the walk skips it). This replaces the
  // earlier g-orientation neighbor walk that reset the root to 0 (defect 4).
  // dagre-js is not vendored, so a source-level `enforce compare` against
  // dagre-js is tracked by ISS-1072 (vendoring prerequisite).
  private def updateRanks(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel]
  ): Unit = {
    val root = tree.nodes().find(v => tree.node(v).parent.isEmpty).getOrElse(tree.nodes()(0))
    val vs   = GraphAlgorithms.preorder(tree, Array(root))

    // vs.slice(1): skip the root, whose rank is the anchor for the walk.
    for (v <- vs.drop(1)) {
      val parent = tree.node(v).parent.get

      // The tree edge (v, parent) exists in `g` in exactly one orientation.
      // flipped = the graph edge runs parent -> v (so v is the head, +minlen).
      // Otherwise the graph edge runs v -> parent (v is the tail, -minlen).
      val (minlen, flipped) =
        if (g.hasEdge(v, parent)) (g.edge(v, parent).minlen, false)
        else (g.edge(parent, v).minlen, true)

      g.node(v).rank = g.node(parent).rank + (if (flipped) minlen else -minlen)
    }
  }
}
