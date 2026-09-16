/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the radar chart data model.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package radar

import scala.collection.mutable

/** A data series in a radar chart. */
final case class RadarSeries(name: String, values: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty)

/** Mutable database for radar chart data. */
final class RadarDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val axes:   mutable.ArrayBuffer[String]      = mutable.ArrayBuffer.empty
  val series: mutable.ArrayBuffer[RadarSeries] = mutable.ArrayBuffer.empty

  def addAxis(label: String): Unit = axes += label

  def addSeries(name: String, values: Seq[Double]): Unit =
    series += RadarSeries(name, mutable.ArrayBuffer.from(values))

  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; axes.clear(); series.clear()
  }
}
