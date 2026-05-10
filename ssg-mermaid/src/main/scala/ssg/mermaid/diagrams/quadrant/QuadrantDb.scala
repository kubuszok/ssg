/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/quadrant-chart/quadrantDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing
 *   Renames: quadrantDb module functions -> QuadrantDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package quadrant

import scala.collection.mutable

/** A data point in the quadrant chart.
  *
  * @param label
  *   the display label
  * @param x
  *   x-axis position (0.0 to 1.0)
  * @param y
  *   y-axis position (0.0 to 1.0)
  */
final case class QuadrantPoint(label: String, x: Double, y: Double)

/** Mutable database for quadrant chart diagram data. */
final class QuadrantDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  var xAxisLeftLabel:   String = ""
  var xAxisRightLabel:  String = ""
  var yAxisBottomLabel: String = ""
  var yAxisTopLabel:    String = ""

  /** Labels for the four quadrants (top-left, top-right, bottom-left, bottom-right). */
  val quadrantLabels: Array[String] = Array("", "", "", "")

  val points: mutable.ArrayBuffer[QuadrantPoint] = mutable.ArrayBuffer.empty

  /** Sets the label for the given quadrant index (1-4). */
  def setQuadrantLabel(index: Int, label: String): Unit =
    if (index >= 1 && index <= 4) {
      quadrantLabels(index - 1) = label
    }

  /** Adds a data point.
    *
    * @throws IllegalArgumentException
    *   if x or y is out of the [0, 1] range
    */
  def addPoint(label: String, x: Double, y: Double): Unit = {
    if (x < 0 || x > 1 || y < 0 || y > 1) {
      throw new IllegalArgumentException(
        s"Point coordinates must be between 0 and 1, got ($x, $y)"
      )
    }
    points += QuadrantPoint(label, x, y)
  }

  /** Clears all state. */
  def clear(): Unit = {
    title = ""
    accTitle = ""
    accDescription = ""
    xAxisLeftLabel = ""
    xAxisRightLabel = ""
    yAxisBottomLabel = ""
    yAxisTopLabel = ""
    for (i <- quadrantLabels.indices) quadrantLabels(i) = ""
    points.clear()
  }
}
