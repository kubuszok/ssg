/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Ported from: dagre-d3-es/src/graphlib/graph.js
 * Original author: Chris Pettitt and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: JS Graph class -> Scala generic Graph[N, E] class
 *   Idiom: mutable.LinkedHashMap for deterministic iteration; Nullable for optional returns
 *   Renames: nodeCount()/edgeCount() -> nodeCount/edgeCount (def, not method call)
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package layout
package graph

// Implementation notes:
//
//  * Node id query functions should return string ids for the nodes
//  * Edge id query functions should return an "edgeObj", edge object, that is
//    composed of enough information to uniquely identify an edge: {v, w, name}.
//  * Internally we use an "edgeId", a stringified form of the edgeObj, to
//    reference edges. This is because we need a performant way to look these
//    edges up and, object properties, which have string keys, are the closest
//    we're going to get to a performant hashtable in JavaScript.

import scala.collection.mutable

import lowlevel.Nullable

/** A directed multigraph with compound graph (parent/child) support.
  *
  * This is the foundational data structure used by dagre for graph layout.
  *
  * @tparam N
  *   the node label type
  * @tparam E
  *   the edge label type
  * @param isDirected
  *   whether the graph is directed (default: true)
  * @param isMultigraph
  *   whether the graph allows multiple edges between the same pair of nodes (default: false)
  * @param isCompound
  *   whether the graph supports parent/child relationships between nodes (default: false)
  */
class Graph[N, E](
  val isDirected:   Boolean = true,
  val isMultigraph: Boolean = false,
  val isCompound:   Boolean = false
) {

  // Label for the graph itself
  private var _label: Any = Nullable.Null // scalastyle:ignore

  // Defaults to be set when creating a new node
  private var _defaultNodeLabelFn: String => N = { (_: String) =>
    Nullable.Null.asInstanceOf[N]
  }

  // Defaults to be set when creating a new edge
  private var _defaultEdgeLabelFn: (String, String, Nullable[String]) => E = { (_: String, _: String, _: Nullable[String]) =>
    Nullable.Null.asInstanceOf[E]
  }

  // v -> label
  private val _nodes: mutable.LinkedHashMap[String, N] = mutable.LinkedHashMap.empty

  // v -> parent (only if compound; Nullable.Null when !isCompound)
  private val _parent: Nullable[mutable.HashMap[String, String]] =
    if (isCompound) Nullable(mutable.HashMap.empty) else Nullable.Null

  // v -> children (only if compound; Nullable.Null when !isCompound)
  private val _children: Nullable[mutable.HashMap[String, mutable.LinkedHashSet[String]]] =
    if (isCompound) {
      val m = mutable.HashMap.empty[String, mutable.LinkedHashSet[String]]
      m(EdgeObj.GraphNode) = mutable.LinkedHashSet.empty
      Nullable(m)
    } else {
      Nullable.Null
    }

  // v -> (edgeId -> EdgeObj)
  private val _in: mutable.HashMap[String, mutable.LinkedHashMap[String, EdgeObj]] =
    mutable.HashMap.empty

  // u -> v -> count
  private val _preds: mutable.HashMap[String, mutable.HashMap[String, Int]] =
    mutable.HashMap.empty

  // v -> (edgeId -> EdgeObj)
  private val _out: mutable.HashMap[String, mutable.LinkedHashMap[String, EdgeObj]] =
    mutable.HashMap.empty

  // v -> w -> count
  private val _sucs: mutable.HashMap[String, mutable.HashMap[String, Int]] =
    mutable.HashMap.empty

  // e -> EdgeObj
  private val _edgeObjs: mutable.LinkedHashMap[String, EdgeObj] = mutable.LinkedHashMap.empty

  // e -> label
  private val _edgeLabels: mutable.HashMap[String, E] = mutable.HashMap.empty

  /* Number of nodes in the graph. Should only be changed by the implementation. */
  private var _nodeCount: Int = 0

  /* Number of edges in the graph. Should only be changed by the implementation. */
  private var _edgeCount: Int = 0

  /* === Graph functions ========= */

  /** Sets the label for the graph itself. */
  def setGraph(label: Any): this.type = {
    _label = label
    this
  }

  /** Returns the label for the graph itself. */
  def graph[T](): T = _label.asInstanceOf[T]

  /* === Node functions ========== */

  /** Sets the default node label function. When a node is created without an explicit label, this function is called with the node id to produce the default label.
    */
  def setDefaultNodeLabel(newDefault: String => N): this.type = {
    _defaultNodeLabelFn = newDefault
    this
  }

  /** Sets a constant default node label. When a node is created without an explicit label, this value is used. */
  def setDefaultNodeLabel(value: N): this.type = {
    _defaultNodeLabelFn = (_: String) => value
    this
  }

  /** Returns the number of nodes in the graph. */
  def nodeCount: Int = _nodeCount

  /** Returns an array of all node IDs in the graph. */
  def nodes(): Array[String] = _nodes.keys.toArray

  /** Returns nodes with no in-edges. */
  def sources(): Array[String] =
    _nodes.keys.filter(v => _in(v).isEmpty).toArray

  /** Returns nodes with no out-edges. */
  def sinks(): Array[String] =
    _nodes.keys.filter(v => _out(v).isEmpty).toArray

  /** Sets multiple nodes with optional value. */
  def setNodes(vs: Array[String], value: Nullable[N] = Nullable.Null): this.type = {
    vs.foreach { v =>
      value.fold(setNode(v))(label => setNode(v, label))
    }
    this
  }

  /** Sets a node with the given value. If the node already exists, updates its label. If the node does not exist, creates it with the given label (or the default label if no value is provided).
    */
  def setNode(v: String, value: N): this.type =
    if (_nodes.contains(v)) {
      _nodes(v) = value
      this
    } else {
      _nodes(v) = value
      if (isCompound) {
        _parent.get(v) = EdgeObj.GraphNode
        _children.get(v) = mutable.LinkedHashSet.empty
        _children.get(EdgeObj.GraphNode) += v
      }
      _in(v) = mutable.LinkedHashMap.empty
      _preds(v) = mutable.HashMap.empty
      _out(v) = mutable.LinkedHashMap.empty
      _sucs(v) = mutable.HashMap.empty
      _nodeCount += 1
      this
    }

  /** Sets a node using the default label function. */
  def setNode(v: String): this.type =
    if (_nodes.contains(v)) {
      this
    } else {
      setNode(v, _defaultNodeLabelFn(v))
    }

  /** Returns the label for node `v`. */
  def node(v: String): N = _nodes(v)

  /** Returns the label for node `v`, or Nullable.Null if the node does not exist. */
  def nodeOpt(v: String): Nullable[N] =
    _nodes.get(v) match {
      case Some(label) => Nullable(label)
      case scala.None  => Nullable.Null
    }

  /** Returns true if the graph contains node `v`. */
  def hasNode(v: String): Boolean = _nodes.contains(v)

  /** Removes node `v` and all edges incident to it. */
  def removeNode(v: String): this.type = {
    if (_nodes.contains(v)) {
      val removeEdgeFn = (e: String) => removeEdgeById(e)
      _nodes.remove(v)
      if (isCompound) {
        removeFromParentsChildList(v)
        _parent.get.remove(v)
        children(v).foreach(child => setParent(child))
        _children.get.remove(v)
      }
      _in(v).keys.toArray.foreach(removeEdgeFn)
      _in.remove(v)
      _preds.remove(v)
      _out(v).keys.toArray.foreach(removeEdgeFn)
      _out.remove(v)
      _sucs.remove(v)
      _nodeCount -= 1
    }
    this
  }

  /** Sets the parent of node `v`. Only valid for compound graphs. */
  def setParent(v: String, parent: String): this.type = {
    if (!isCompound) {
      throw new IllegalStateException("Cannot set parent in a non-compound graph")
    }

    val p = parent
    // Coerce parent to string (already a String in Scala)
    // Check for cycles
    var ancestor: Nullable[String] = Nullable(p)
    while (ancestor.isDefined) {
      if (ancestor.get == v) {
        throw new IllegalArgumentException(
          "Setting " + parent + " as parent of " + v + " would create a cycle"
        )
      }
      ancestor = this.parent(ancestor.get)
    }

    setNode(p)

    setNode(v)
    removeFromParentsChildList(v)
    _parent.get(v) = p
    _children.get(p) += v
    this
  }

  /** Removes the parent of node `v` (makes it a root node). Only valid for compound graphs. */
  def setParent(v: String): this.type = {
    if (!isCompound) {
      throw new IllegalStateException("Cannot set parent in a non-compound graph")
    }

    setNode(v)
    removeFromParentsChildList(v)
    _parent.get(v) = EdgeObj.GraphNode
    _children.get(EdgeObj.GraphNode) += v
    this
  }

  private def removeFromParentsChildList(v: String): Unit =
    _children.get(_parent.get(v)) -= v

  /** Returns the parent of node `v`, or Nullable.Null if `v` has no parent (is a root) or the graph is not compound. */
  def parent(v: String): Nullable[String] =
    if (isCompound) {
      val p = _parent.get.get(v)
      p match {
        case Some(parent) if parent != EdgeObj.GraphNode => Nullable(parent)
        case _                                           => Nullable.Null
      }
    } else {
      Nullable.Null
    }

  /** Returns the children of node `v`. For compound graphs, returns children of `v`. For non-compound graphs with `v == GraphNode`, returns all nodes. For non-compound graphs with a specific node,
    * returns empty array.
    */
  def children(v: String): Array[String] =
    if (isCompound) {
      _children.get.get(v) match {
        case Some(kids) => kids.toArray
        case scala.None => Array.empty
      }
    } else if (v == EdgeObj.GraphNode) {
      nodes()
    } else if (hasNode(v)) {
      Array.empty
    } else {
      Array.empty
    }

  /** Returns the root-level children. For compound graphs, returns children of the sentinel root. For non-compound graphs, returns all nodes.
    */
  def children(): Array[String] = children(EdgeObj.GraphNode)

  /** Returns the predecessors of node `v` (nodes with edges pointing to `v`). */
  def predecessors(v: String): Nullable[Array[String]] =
    _preds.get(v) match {
      case Some(predsV) => Nullable(predsV.keys.toArray)
      case scala.None   => Nullable.Null
    }

  /** Returns the successors of node `v` (nodes that `v` has edges pointing to). */
  def successors(v: String): Nullable[Array[String]] =
    _sucs.get(v) match {
      case Some(sucsV) => Nullable(sucsV.keys.toArray)
      case scala.None  => Nullable.Null
    }

  /** Returns the union of predecessors and successors of node `v`. */
  def neighbors(v: String): Nullable[Array[String]] = {
    val preds = predecessors(v)
    preds.map { p =>
      val s = successors(v).getOrElse(Array.empty)
      (p ++ s).distinct
    }
  }

  /** Returns true if node `v` is a leaf (no successors for directed, no neighbors for undirected). */
  def isLeaf(v: String): Boolean = {
    val n = if (isDirected) {
      successors(v).getOrElse(Array.empty)
    } else {
      neighbors(v).getOrElse(Array.empty)
    }
    n.isEmpty
  }

  /** Creates a new graph that includes only nodes for which `filter` returns true. Edges between included nodes are preserved. For compound graphs, parent relationships are adjusted to skip
    * filtered-out ancestors.
    */
  def filterNodes(filter: String => Boolean): Graph[N, E] = {
    val copy = new Graph[N, E](
      isDirected = this.isDirected,
      isMultigraph = this.isMultigraph,
      isCompound = this.isCompound
    )

    copy.setGraph(this.graph())

    _nodes.foreach { (v, value) =>
      if (filter(v)) {
        copy.setNode(v, value)
      }
    }

    _edgeObjs.foreach { (_, e) =>
      if (copy.hasNode(e.v) && copy.hasNode(e.w)) {
        copy.setEdgeObj(e, edge(e))
      }
    }

    val parents = mutable.HashMap.empty[String, Nullable[String]]
    def findParent(v: String): Nullable[String] = {
      val par = parent(v)
      if (par.isEmpty || copy.hasNode(par.get)) {
        parents(v) = par
        par
      } else if (parents.contains(par.get)) {
        parents(par.get)
      } else {
        findParent(par.get)
      }
    }

    if (isCompound) {
      copy.nodes().foreach { v =>
        val p = findParent(v)
        p.foreach(parent => copy.setParent(v, parent))
      }
    }

    copy
  }

  /* === Edge functions ========== */

  /** Sets the default edge label function. When an edge is created without an explicit label, this function is called to produce the default label.
    */
  def setDefaultEdgeLabel(newDefault: (String, String, Nullable[String]) => E): this.type = {
    _defaultEdgeLabelFn = newDefault
    this
  }

  /** Sets a constant default edge label. */
  def setDefaultEdgeLabel(value: E): this.type = {
    _defaultEdgeLabelFn = (_: String, _: String, _: Nullable[String]) => value
    this
  }

  /** Returns the number of edges in the graph. */
  def edgeCount: Int = _edgeCount

  /** Returns an array of all edge objects in the graph. */
  def edges(): Array[EdgeObj] = _edgeObjs.values.toArray

  /** Sets edges along a path of nodes. */
  def setPath(vs: Array[String], value: Nullable[E] = Nullable.Null): this.type = {
    vs.sliding(2).foreach { pair =>
      if (pair.length == 2) {
        value.fold(setEdge(pair(0), pair(1)))(v => setEdge(pair(0), pair(1), v))
      }
    }
    this
  }

  /** Sets an edge from `v` to `w` with the given label. If the edge already exists, updates the label. If either node does not exist, it is created first.
    */
  def setEdge(v: String, w: String, value: E): this.type =
    setEdgeInternal(v, w, Nullable(value), Nullable.Null)

  /** Sets an edge from `v` to `w` with the given label and name (for multigraphs). */
  def setEdge(v: String, w: String, value: E, name: String): this.type =
    setEdgeInternal(v, w, Nullable(value), Nullable(name))

  /** Sets an edge from `v` to `w` with no explicit label (uses default). */
  def setEdge(v: String, w: String): this.type =
    setEdgeInternal(v, w, Nullable.Null, Nullable.Null)

  /** Sets an edge using an EdgeObj. */
  def setEdgeObj(edgeObj: EdgeObj, value: E): this.type =
    setEdgeInternal(edgeObj.v, edgeObj.w, Nullable(value), edgeObj.name)

  /** Sets an edge using an EdgeObj with no explicit label (uses default). */
  def setEdgeObj(edgeObj: EdgeObj): this.type =
    setEdgeInternal(edgeObj.v, edgeObj.w, Nullable.Null, edgeObj.name)

  private def setEdgeInternal(
    v:     String,
    w:     String,
    value: Nullable[E],
    name:  Nullable[String]
  ): this.type = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, name)
    if (_edgeLabels.contains(e)) {
      value.foreach(label => _edgeLabels(e) = label)
      this
    } else {
      if (name.isDefined && !isMultigraph) {
        throw new IllegalStateException("Cannot set a named edge when isMultigraph = false")
      }

      // It didn't exist, so we need to create it.
      // First ensure the nodes exist.
      setNode(v)
      setNode(w)

      _edgeLabels(e) = value.getOrElse(_defaultEdgeLabelFn(v, w, name))

      val edgeObj = EdgeObj.edgeArgsToObj(isDirected, v, w, name)
      // Ensure we add undirected edges in a consistent way.
      val vv = edgeObj.v
      val ww = edgeObj.w

      _edgeObjs(e) = edgeObj
      incrementOrInitEntry(_preds(ww), vv)
      incrementOrInitEntry(_sucs(vv), ww)
      _in(ww)(e) = edgeObj
      _out(vv)(e) = edgeObj
      _edgeCount += 1
      this
    }
  }

  /** Returns the label for the edge from `v` to `w`. */
  def edge(v: String, w: String): E = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable.Null)
    _edgeLabels(e)
  }

  /** Returns the label for the edge from `v` to `w` with the given name. */
  def edge(v: String, w: String, name: String): E = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable(name))
    _edgeLabels(e)
  }

  /** Returns the label for the edge described by the given EdgeObj. */
  def edge(edgeObj: EdgeObj): E = {
    val e = EdgeObj.edgeArgsToId(isDirected, edgeObj.v, edgeObj.w, edgeObj.name)
    _edgeLabels(e)
  }

  /** Returns the label for the edge from `v` to `w`, or Nullable.Null if not found. */
  def edgeOpt(v: String, w: String): Nullable[E] = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable.Null)
    _edgeLabels.get(e) match {
      case Some(label) => Nullable(label)
      case scala.None  => Nullable.Null
    }
  }

  /** Returns the label for the edge described by the given EdgeObj, or Nullable.Null if not found. */
  def edgeOpt(edgeObj: EdgeObj): Nullable[E] = {
    val e = EdgeObj.edgeArgsToId(isDirected, edgeObj.v, edgeObj.w, edgeObj.name)
    _edgeLabels.get(e) match {
      case Some(label) => Nullable(label)
      case scala.None  => Nullable.Null
    }
  }

  /** Returns true if the graph has an edge from `v` to `w`. */
  def hasEdge(v: String, w: String): Boolean = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable.Null)
    _edgeLabels.contains(e)
  }

  /** Returns true if the graph has an edge from `v` to `w` with the given name. */
  def hasEdge(v: String, w: String, name: String): Boolean = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable(name))
    _edgeLabels.contains(e)
  }

  /** Returns true if the graph has an edge described by the given EdgeObj. */
  def hasEdge(edgeObj: EdgeObj): Boolean = {
    val e = EdgeObj.edgeArgsToId(isDirected, edgeObj.v, edgeObj.w, edgeObj.name)
    _edgeLabels.contains(e)
  }

  /** Removes the edge from `v` to `w`. */
  def removeEdge(v: String, w: String): this.type = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable.Null)
    removeEdgeById(e)
    this
  }

  /** Removes the edge from `v` to `w` with the given name. */
  def removeEdge(v: String, w: String, name: String): this.type = {
    val e = EdgeObj.edgeArgsToId(isDirected, v, w, Nullable(name))
    removeEdgeById(e)
    this
  }

  /** Removes the edge described by the given EdgeObj. */
  def removeEdge(edgeObj: EdgeObj): this.type = {
    val e = EdgeObj.edgeArgsToId(isDirected, edgeObj.v, edgeObj.w, edgeObj.name)
    removeEdgeById(e)
    this
  }

  private def removeEdgeById(e: String): Unit =
    _edgeObjs.get(e).foreach { edgeObj =>
      val vv = edgeObj.v
      val ww = edgeObj.w
      _edgeLabels.remove(e)
      _edgeObjs.remove(e)
      decrementOrRemoveEntry(_preds(ww), vv)
      decrementOrRemoveEntry(_sucs(vv), ww)
      _in(ww).remove(e)
      _out(vv).remove(e)
      _edgeCount -= 1
    }

  /** Returns incoming edges to node `v`. Returns Nullable.Null if `v` is not in the graph. */
  def inEdges(v: String): Nullable[Array[EdgeObj]] =
    _in.get(v) match {
      case Some(inV)  => Nullable(inV.values.toArray)
      case scala.None => Nullable.Null
    }

  /** Returns incoming edges to node `v` from node `u`. Returns Nullable.Null if `v` is not in the graph. */
  def inEdges(v: String, u: String): Nullable[Array[EdgeObj]] =
    _in.get(v) match {
      case Some(inV)  => Nullable(inV.values.filter(_.v == u).toArray)
      case scala.None => Nullable.Null
    }

  /** Returns outgoing edges from node `v`. Returns Nullable.Null if `v` is not in the graph. */
  def outEdges(v: String): Nullable[Array[EdgeObj]] =
    _out.get(v) match {
      case Some(outV) => Nullable(outV.values.toArray)
      case scala.None => Nullable.Null
    }

  /** Returns outgoing edges from node `v` to node `w`. Returns Nullable.Null if `v` is not in the graph. */
  def outEdges(v: String, w: String): Nullable[Array[EdgeObj]] =
    _out.get(v) match {
      case Some(outV) => Nullable(outV.values.filter(_.w == w).toArray)
      case scala.None => Nullable.Null
    }

  /** Returns all edges incident to node `v` (both in and out). Returns Nullable.Null if `v` is not in the graph. */
  def nodeEdges(v: String): Nullable[Array[EdgeObj]] = {
    val ie = inEdges(v)
    ie.map { inE =>
      inE ++ outEdges(v).getOrElse(Array.empty)
    }
  }

  /** Returns all edges incident to node `v` filtered by other endpoint `w`. Returns Nullable.Null if `v` is not in the graph.
    */
  def nodeEdges(v: String, w: String): Nullable[Array[EdgeObj]] = {
    val ie = inEdges(v, w)
    ie.map { inE =>
      inE ++ outEdges(v, w).getOrElse(Array.empty)
    }
  }

  private def incrementOrInitEntry(map: mutable.HashMap[String, Int], k: String): Unit =
    map.get(k) match {
      case Some(count) => map(k) = count + 1
      case scala.None  => map(k) = 1
    }

  private def decrementOrRemoveEntry(map: mutable.HashMap[String, Int], k: String): Unit = {
    val newCount = map(k) - 1
    if (newCount == 0) {
      map.remove(k)
    } else {
      map(k) = newCount
    }
  }
}
