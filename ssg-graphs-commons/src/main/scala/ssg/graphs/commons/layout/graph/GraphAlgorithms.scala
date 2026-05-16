/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Ported from: dagre-d3-es/src/graphlib/alg/ (topsort, is-acyclic, dfs, preorder, postorder,
 *   components, dijkstra, dijkstra-all, tarjan, find-cycles, floyd-warshall, prim)
 * Original author: Chris Pettitt and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Separate JS files merged into a single GraphAlgorithms object
 *   Idiom: mutable collections for DFS visited sets; boundary/break for early exit
 *   Renames: topsort/isAcyclic/preorder/postorder/components/dijkstra/dijkstraAll/tarjan/findCycles/floydWarshall/prim
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package layout
package graph

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable

/** Graph algorithms operating on [[Graph]] instances.
  *
  * Provides topological sort, cycle detection, DFS traversal, shortest path (Dijkstra), all-pairs shortest path (Dijkstra-all, Floyd-Warshall), strongly connected components (Tarjan), cycle finding,
  * connected components, and minimum spanning tree (Prim).
  */
object GraphAlgorithms {

  /** Exception thrown when a cycle is detected during topological sort. */
  class CycleException extends RuntimeException("Graph has a cycle")

  /** Result entry for Dijkstra's algorithm. */
  final case class DijkstraEntry(var distance: Double, var predecessor: Nullable[String] = Nullable.Null)

  /** Result entry for Floyd-Warshall's algorithm. */
  final case class FloydWarshallEntry(var distance: Double, var predecessor: Nullable[String] = Nullable.Null)

  /** Result entry for Tarjan's algorithm. */
  final private case class TarjanEntry(var onStack: Boolean, var lowlink: Int, index: Int)

  // ── Topological sort ───────────────────────────────────────────────

  /** Performs a topological sort on the graph `g`. Returns an array of node IDs in topological order.
    *
    * @throws CycleException
    *   if the graph contains a cycle
    */
  def topsort[N, E](g: Graph[N, E]): Array[String] = {
    val visited = mutable.HashSet.empty[String]
    val stack   = mutable.HashSet.empty[String]
    val results = mutable.ArrayBuffer.empty[String]

    def visit(node: String): Unit = {
      if (stack.contains(node)) {
        throw new CycleException()
      }

      if (!visited.contains(node)) {
        stack += node
        visited += node
        g.predecessors(node).foreach(_.foreach(visit))
        stack -= node
        results += node
      }
    }

    g.sinks().foreach(visit)

    if (visited.size != g.nodeCount) {
      throw new CycleException()
    }

    results.toArray
  }

  // ── Acyclicity check ──────────────────────────────────────────────

  /** Returns true if the graph `g` has no cycles. */
  def isAcyclic[N, E](g: Graph[N, E]): Boolean =
    try {
      topsort(g)
      true
    } catch {
      case _: CycleException => false
    }

  // ── DFS ───────────────────────────────────────────────────────────

  /** A helper that performs a pre- or post-order traversal on the input graph and returns the nodes in the order they were visited. If the graph is undirected then this algorithm will navigate using
    * neighbors. If the graph is directed then this algorithm will navigate using successors.
    *
    * @param order
    *   must be "pre" or "post"
    */
  def dfs[N, E](g: Graph[N, E], vs: Array[String], order: String): Array[String] = {
    val navigation: String => Nullable[Array[String]] =
      if (g.isDirected) { (v: String) => g.successors(v) }
      else { (v: String) => g.neighbors(v) }

    val acc     = mutable.ArrayBuffer.empty[String]
    val visited = mutable.HashSet.empty[String]
    vs.foreach { v =>
      if (!g.hasNode(v)) {
        throw new IllegalArgumentException("Graph does not have node: " + v)
      }
      doDfs(g, v, order == "post", visited, navigation, acc)
    }
    acc.toArray
  }

  private def doDfs[N, E](
    g:          Graph[N, E],
    v:          String,
    postorder:  Boolean,
    visited:    mutable.HashSet[String],
    navigation: String => Nullable[Array[String]],
    acc:        mutable.ArrayBuffer[String]
  ): Unit =
    if (!visited.contains(v)) {
      visited += v

      if (!postorder) {
        acc += v
      }
      navigation(v).foreach(_.foreach(w => doDfs(g, w, postorder, visited, navigation, acc)))
      if (postorder) {
        acc += v
      }
    }

  // ── Preorder / Postorder ──────────────────────────────────────────

  /** Performs a pre-order DFS traversal starting from the given nodes. */
  def preorder[N, E](g: Graph[N, E], vs: Array[String]): Array[String] =
    dfs(g, vs, "pre")

  /** Performs a post-order DFS traversal starting from the given nodes. */
  def postorder[N, E](g: Graph[N, E], vs: Array[String]): Array[String] =
    dfs(g, vs, "post")

  // ── Connected components ──────────────────────────────────────────

  /** Returns the connected components of the graph as arrays of node IDs. */
  def components[N, E](g: Graph[N, E]): Array[Array[String]] = {
    val visited = mutable.HashSet.empty[String]
    val cmpts   = mutable.ArrayBuffer.empty[Array[String]]

    def innerDfs(v: String, cmpt: mutable.ArrayBuffer[String]): Unit =
      if (!visited.contains(v)) {
        visited += v
        cmpt += v
        g.successors(v).foreach(_.foreach(w => innerDfs(w, cmpt)))
        g.predecessors(v).foreach(_.foreach(w => innerDfs(w, cmpt)))
      }

    g.nodes().foreach { v =>
      val cmpt = mutable.ArrayBuffer.empty[String]
      innerDfs(v, cmpt)
      if (cmpt.nonEmpty) {
        cmpts += cmpt.toArray
      }
    }

    cmpts.toArray
  }

  // ── Dijkstra ──────────────────────────────────────────────────────

  /** Runs Dijkstra's shortest-path algorithm from `source`.
    *
    * @param weightFn
    *   edge weight function (default: constant 1)
    * @param edgeFn
    *   function that returns edges for a node (default: outEdges)
    */
  def dijkstra[N, E](
    g:        Graph[N, E],
    source:   String,
    weightFn: EdgeObj => Double = _ => 1.0,
    edgeFn:   (Graph[N, E], String) => Nullable[Array[EdgeObj]] = (g: Graph[N, E], v: String) => g.outEdges(v)
  ): mutable.HashMap[String, DijkstraEntry] =
    runDijkstra(g, source, weightFn, v => edgeFn(g, v))

  private def runDijkstra[N, E](
    g:        Graph[N, E],
    source:   String,
    weightFn: EdgeObj => Double,
    edgeFn:   String => Nullable[Array[EdgeObj]]
  ): mutable.HashMap[String, DijkstraEntry] = {
    val results = mutable.HashMap.empty[String, DijkstraEntry]
    val pq      = new PriorityQueue()

    g.nodes().foreach { v =>
      val distance = if (v == source) 0.0 else Double.PositiveInfinity
      results(v) = DijkstraEntry(distance)
      pq.add(v, distance)
    }

    boundary {
      while (pq.size > 0) {
        val v      = pq.removeMin()
        val vEntry = results(v)
        if (vEntry.distance == Double.PositiveInfinity) {
          break()
        }

        edgeFn(v).foreach { edgesArr =>
          edgesArr.foreach { edge =>
            val w        = if (edge.v != v) edge.v else edge.w
            val wEntry   = results(w)
            val weight   = weightFn(edge)
            val distance = vEntry.distance + weight

            if (weight < 0) {
              throw new IllegalArgumentException(
                "dijkstra does not allow negative edge weights. " +
                  "Bad edge: " + edge + " Weight: " + weight
              )
            }

            if (distance < wEntry.distance) {
              wEntry.distance = distance
              wEntry.predecessor = Nullable(v)
              pq.decrease(w, distance)
            }
          }
        }
      }
    }

    results
  }

  // ── Dijkstra All ──────────────────────────────────────────────────

  /** Runs Dijkstra's algorithm from every node in the graph.
    *
    * @return
    *   a map from source node to the Dijkstra result map
    */
  def dijkstraAll[N, E](
    g:        Graph[N, E],
    weightFn: EdgeObj => Double = _ => 1.0,
    edgeFn:   (Graph[N, E], String) => Nullable[Array[EdgeObj]] = (g: Graph[N, E], v: String) => g.outEdges(v)
  ): mutable.HashMap[String, mutable.HashMap[String, DijkstraEntry]] = {
    val result = mutable.HashMap.empty[String, mutable.HashMap[String, DijkstraEntry]]
    g.nodes().foreach { v =>
      result(v) = dijkstra(g, v, weightFn, edgeFn)
    }
    result
  }

  // ── Tarjan's SCC ──────────────────────────────────────────────────

  /** Finds the strongly connected components of a directed graph using Tarjan's algorithm. */
  def tarjan[N, E](g: Graph[N, E]): Array[Array[String]] = {
    var index   = 0
    val stack   = mutable.ArrayBuffer.empty[String]
    val visited = mutable.HashMap.empty[String, TarjanEntry]
    val results = mutable.ArrayBuffer.empty[Array[String]]

    def innerDfs(v: String): Unit = {
      val entry = TarjanEntry(onStack = true, lowlink = index, index = index)
      visited(v) = entry
      index += 1
      stack += v

      g.successors(v).foreach { succs =>
        succs.foreach { w =>
          if (!visited.contains(w)) {
            innerDfs(w)
            entry.lowlink = Math.min(entry.lowlink, visited(w).lowlink)
          } else if (visited(w).onStack) {
            entry.lowlink = Math.min(entry.lowlink, visited(w).index)
          }
        }
      }

      if (entry.lowlink == entry.index) {
        val cmpt = mutable.ArrayBuffer.empty[String]
        var w    = ""
        while ({
          w = stack.remove(stack.length - 1)
          visited(w).onStack = false
          cmpt += w
          v != w
        }) ()
        results += cmpt.toArray
      }
    }

    g.nodes().foreach { v =>
      if (!visited.contains(v)) {
        innerDfs(v)
      }
    }

    results.toArray
  }

  // ── Find cycles ───────────────────────────────────────────────────

  /** Finds all cycles in a directed graph. Returns the strongly connected components that contain cycles (size > 1, or size 1 with a self-edge).
    */
  def findCycles[N, E](g: Graph[N, E]): Array[Array[String]] =
    tarjan(g).filter { cmpt =>
      cmpt.length > 1 || (cmpt.length == 1 && g.hasEdge(cmpt(0), cmpt(0)))
    }

  // ── Floyd-Warshall ────────────────────────────────────────────────

  /** Runs the Floyd-Warshall all-pairs shortest path algorithm.
    *
    * @param weightFn
    *   edge weight function (default: constant 1)
    * @param edgeFn
    *   function that returns edges for a node (default: outEdges)
    */
  def floydWarshall[N, E](
    g:        Graph[N, E],
    weightFn: EdgeObj => Double = _ => 1.0,
    edgeFn:   (Graph[N, E], String) => Nullable[Array[EdgeObj]] = (g: Graph[N, E], v: String) => g.outEdges(v)
  ): mutable.HashMap[String, mutable.HashMap[String, FloydWarshallEntry]] =
    runFloydWarshall(g, weightFn, v => edgeFn(g, v))

  private def runFloydWarshall[N, E](
    g:        Graph[N, E],
    weightFn: EdgeObj => Double,
    edgeFn:   String => Nullable[Array[EdgeObj]]
  ): mutable.HashMap[String, mutable.HashMap[String, FloydWarshallEntry]] = {
    val results  = mutable.HashMap.empty[String, mutable.HashMap[String, FloydWarshallEntry]]
    val allNodes = g.nodes()

    allNodes.foreach { v =>
      results(v) = mutable.HashMap.empty[String, FloydWarshallEntry]
      results(v)(v) = FloydWarshallEntry(distance = 0)
      allNodes.foreach { w =>
        if (v != w) {
          results(v)(w) = FloydWarshallEntry(distance = Double.PositiveInfinity)
        }
      }
      edgeFn(v).foreach { edgesArr =>
        edgesArr.foreach { edge =>
          val w = if (edge.v == v) edge.w else edge.v
          val d = weightFn(edge)
          results(v)(w) = FloydWarshallEntry(distance = d, predecessor = Nullable(v))
        }
      }
    }

    allNodes.foreach { k =>
      val rowK = results(k)
      allNodes.foreach { i =>
        val rowI = results(i)
        allNodes.foreach { j =>
          val ik          = rowI(k)
          val kj          = rowK(j)
          val ij          = rowI(j)
          val altDistance = ik.distance + kj.distance
          if (altDistance < ij.distance) {
            ij.distance = altDistance
            ij.predecessor = kj.predecessor
          }
        }
      }
    }

    results
  }

  // ── Prim's MST ────────────────────────────────────────────────────

  /** Computes a minimum spanning tree of an undirected graph using Prim's algorithm.
    *
    * @param weightFn
    *   edge weight function
    * @return
    *   a new Graph containing only the MST edges
    * @throws IllegalArgumentException
    *   if the input graph is not connected
    */
  def prim[N, E](g: Graph[N, E], weightFn: EdgeObj => Double): Graph[N, E] = {
    val result  = new Graph[N, E]()
    val parents = mutable.HashMap.empty[String, String]
    val pq      = new PriorityQueue()

    if (g.nodeCount == 0) {
      result
    } else {
      g.nodes().foreach { v =>
        pq.add(v, Double.PositiveInfinity)
        result.setNode(v)
      }

      // Start from an arbitrary node
      pq.decrease(g.nodes()(0), 0)

      var init = false
      while (pq.size > 0) {
        val v = pq.removeMin()
        if (parents.contains(v)) {
          result.setEdge(v, parents(v))
        } else if (init) {
          throw new IllegalArgumentException("Input graph is not connected: " + g)
        } else {
          init = true
        }

        g.nodeEdges(v).foreach { edgesArr =>
          edgesArr.foreach { edge =>
            val w   = if (edge.v == v) edge.w else edge.v
            val pri = pq.priority(w)
            pri.foreach { currentPri =>
              val edgeWeight = weightFn(edge)
              if (edgeWeight < currentPri) {
                parents(w) = v
                pq.decrease(w, edgeWeight)
              }
            }
          }
        }
      }

      result
    }
  }
}
