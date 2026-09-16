/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/xychart/xychartDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: xychartDb module functions -> XyChartDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package xychart

import scala.collection.mutable

/** A single data series in an XY chart.
  *
  * @param name
  *   the series label
  * @param data
  *   the numeric data points
  * @param seriesType
  *   "bar" or "line"
  */
final case class DataSeries(name: String, data: mutable.ArrayBuffer[Double], seriesType: String)

/** Mutable database for XY chart diagram data.
  *
  * Accumulates axis labels, data series (bar/line), and configuration during parsing. The renderer reads from this database to produce SVG output.
  */
final class XyChartDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  var xAxisLabel:      String                      = ""
  val xAxisCategories: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
  var xAxisMin:        Double                      = Double.NaN
  var xAxisMax:        Double                      = Double.NaN

  var yAxisLabel: String = ""
  var yAxisMin:   Double = Double.NaN
  var yAxisMax:   Double = Double.NaN

  val dataSeries: mutable.ArrayBuffer[DataSeries] = mutable.ArrayBuffer.empty

  /** Adds a bar data series. */
  def addBarData(name: String, data: Seq[Double]): Unit =
    dataSeries += DataSeries(name, mutable.ArrayBuffer.from(data), "bar")

  /** Adds a line data series. */
  def addLineData(name: String, data: Seq[Double]): Unit =
    dataSeries += DataSeries(name, mutable.ArrayBuffer.from(data), "line")

  /** Returns the maximum value across all data series. */
  def maxValue: Double =
    if (dataSeries.isEmpty) 0.0
    else dataSeries.flatMap(_.data).maxOption.getOrElse(0.0)

  /** Returns the number of data points in the first series (or 0). */
  def dataPointCount: Int =
    dataSeries.headOption.map(_.data.size).getOrElse(0)

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    title = ""
    accTitle = ""
    accDescription = ""
    xAxisLabel = ""
    xAxisCategories.clear()
    xAxisMin = Double.NaN
    xAxisMax = Double.NaN
    yAxisLabel = ""
    yAxisMin = Double.NaN
    yAxisMax = Double.NaN
    dataSeries.clear()
  }
}
