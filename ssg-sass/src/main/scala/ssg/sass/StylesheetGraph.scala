/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/stylesheet_graph.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: stylesheet_graph.dart -> StylesheetGraph.scala
 *   Convention: Tracks canonical-URL edges and detects import cycles;
 *               callers pass (from, to) pairs via addEdge/wouldCycle.
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.sass.Stylesheet
import ssg.sass.importer.Importer

/** A graph of the import/use/forward dependencies between stylesheets, tracking edges between canonical URLs so cycles can be detected.
  */
final class StylesheetGraph(val importCache: ImportCache) {

  private val nodes: mutable.Map[String, StylesheetNode] = mutable.Map.empty

  /** Directed adjacency from canonical URL -> set of canonical URLs it depends on. An edge `a -> b` means `a` imports/uses/forwards `b`.
    */
  private val edges: mutable.Map[String, mutable.Set[String]] = mutable.Map.empty

  /** Adds a canonical stylesheet to the graph. */
  def addCanonical(importer: Importer, canonicalUrl: String, originalUrl: String): Nullable[Stylesheet] = {
    val _     = originalUrl
    val sheet = importCache.importCanonical(importer, canonicalUrl)
    sheet.foreach { s =>
      if (!nodes.contains(canonicalUrl)) {
        nodes.update(canonicalUrl, new StylesheetNode(s, importer, canonicalUrl))
      }
    }
    sheet
  }

  /** Returns the node for [canonicalUrl], if any. */
  def nodeFor(canonicalUrl: String): Nullable[StylesheetNode] =
    nodes.get(canonicalUrl) match {
      case Some(n)    => n
      case scala.None => Nullable.empty
    }

  /** Registers an edge `from -> to`. Returns true if the edge was added, or false if it would introduce (or already participates in) a cycle.
    */
  def addEdge(from: String, to: String): Boolean =
    if (from == to) false
    else if (reaches(to, from)) false
    else {
      edges.getOrElseUpdate(from, mutable.Set.empty).add(to)
      true
    }

  /** Returns true if adding an edge `from -> to` would introduce a cycle. */
  def wouldCycle(from: String, to: String): Boolean =
    from == to || reaches(to, from)

  /** Returns true if [start] can reach [target] by following edges. */
  private def reaches(start: String, target: String): Boolean = {
    val visited = mutable.Set.empty[String]
    val stack   = mutable.ArrayDeque.empty[String]
    stack.append(start)
    var found = start == target
    while (stack.nonEmpty && !found) {
      val cur = stack.removeLast()
      if (cur == target) {
        found = true
      } else if (visited.add(cur)) {
        edges.get(cur).foreach { outs =>
          for (n <- outs) stack.append(n)
        }
      }
    }
    found
  }
}

/** A single node in a [[StylesheetGraph]]. */
final class StylesheetNode(
  val stylesheet:   Stylesheet,
  val importer:     Importer,
  val canonicalUrl: String
) {

  /** Nodes this stylesheet depends on via `@use`. */
  val upstream: mutable.Set[StylesheetNode] = mutable.Set.empty

  /** Nodes that depend on this stylesheet. */
  val downstream: mutable.Set[StylesheetNode] = mutable.Set.empty
}
