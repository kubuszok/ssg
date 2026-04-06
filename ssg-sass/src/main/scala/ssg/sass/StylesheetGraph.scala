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
 *   Convention: Skeleton — public API surface only
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.sass.Stylesheet
import ssg.sass.importer.Importer

/** A graph of the import/use/forward dependencies between stylesheets, used
  * to determine whether a stylesheet needs to be re-evaluated.
  */
final class StylesheetGraph(val importCache: ImportCache) {

  private val nodes: mutable.Map[String, StylesheetNode] = mutable.Map.empty

  /** Adds a canonical stylesheet to the graph. TODO. */
  def addCanonical(importer: Importer, canonicalUrl: String, originalUrl: String): Nullable[Stylesheet] =
    Nullable.empty

  /** Returns the node for [canonicalUrl], if any. */
  def nodeFor(canonicalUrl: String): Nullable[StylesheetNode] =
    nodes.get(canonicalUrl) match {
      case Some(n) => n
      case scala.None => Nullable.empty
    }
}

/** A single node in a [[StylesheetGraph]]. */
final class StylesheetNode(
  val stylesheet: Stylesheet,
  val importer: Importer,
  val canonicalUrl: String
) {

  /** Nodes this stylesheet depends on via `@use`. */
  val upstream: mutable.Set[StylesheetNode] = mutable.Set.empty

  /** Nodes that depend on this stylesheet. */
  val downstream: mutable.Set[StylesheetNode] = mutable.Set.empty
}
