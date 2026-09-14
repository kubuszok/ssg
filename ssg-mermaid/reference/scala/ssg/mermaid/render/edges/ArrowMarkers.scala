/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/markers.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Enum for marker types; factory methods for each marker kind
 *   Renames: markers.js → ArrowMarkers
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package edges

import ssg.graphs.commons.svg.{ PathData, SvgBuilder }

/** Arrow marker type identifiers.
  *
  * Each variant corresponds to a distinct arrowhead or tail shape used in Mermaid diagrams. The [[ArrowMarkers]] object creates SVG `<marker>` definitions for each type.
  */
enum MarkerType extends java.lang.Enum[MarkerType] {

  /** Standard filled arrowhead (triangle). */
  case Normal

  /** Open vee arrowhead (two lines meeting at a point). */
  case Vee

  /** Cross/X mark at the end of an edge. */
  case Cross

  /** Small circle at the end of an edge. */
  case Circle

  /** Filled diamond (aggregation in class diagrams). */
  case Aggregation

  /** Open diamond (composition in class diagrams). */
  case Dependency

  /** Lollipop — small circle on a stick (interface realization). */
  case Lollipop

  /** Filled arrowhead pointing at the target. */
  case Point

  /** Bar/pipe end — flat line perpendicular to the edge. */
  case Bar
}

/** Creates SVG `<marker>` elements for arrow heads and tails.
  *
  * SVG markers are placed in a `<defs>` section and referenced by edges via the `marker-start` and `marker-end` attributes. Each marker type has a unique ID pattern: `{prefix}_{markerType}_{suffix}`.
  *
  * Mermaid generates multiple marker variants per diagram (one per style/color combination), identified by unique suffixes.
  */
object ArrowMarkers {

  /** Default marker width/height. */
  private val MarkerSize: Int = 12

  /** Radius for circle-style markers. */
  private val CircleRadius: Double = 5.0

  /** Creates all standard marker definitions in the given defs element.
    *
    * @param defs
    *   the `<defs>` SVG builder to append marker elements to
    * @param id
    *   unique identifier suffix for this set of markers
    * @param fill
    *   fill color for the markers
    * @param stroke
    *   stroke color for the markers
    */
  def createMarkers(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    createNormalMarker(defs, id, fill, stroke)
    createVeeMarker(defs, id, fill, stroke)
    createCrossMarker(defs, id, stroke)
    createCircleMarker(defs, id, fill, stroke)
    createAggregationMarker(defs, id, fill, stroke)
    createDependencyMarker(defs, id, fill, stroke)
    createLollipopMarker(defs, id, fill, stroke)
    createPointMarker(defs, id, fill, stroke)
    createBarMarker(defs, id, stroke)
  }

  /** Returns the marker reference URL for a given marker type and ID.
    *
    * @param markerType
    *   the type of marker
    * @param id
    *   the unique identifier suffix
    * @return
    *   SVG url() reference string (e.g. "url(#normal_abc123)")
    */
  def markerUrl(markerType: MarkerType, id: String): String =
    s"url(#${markerId(markerType, id)})"

  /** Returns the marker element ID for a given type and suffix.
    *
    * @param markerType
    *   the type of marker
    * @param id
    *   the unique identifier suffix
    * @return
    *   marker element ID string
    */
  def markerId(markerType: MarkerType, id: String): String = {
    val typeName = markerType match {
      case MarkerType.Normal      => "normal"
      case MarkerType.Vee         => "vee"
      case MarkerType.Cross       => "cross"
      case MarkerType.Circle      => "circle"
      case MarkerType.Aggregation => "aggregation"
      case MarkerType.Dependency  => "dependency"
      case MarkerType.Lollipop    => "lollipop"
      case MarkerType.Point       => "point"
      case MarkerType.Bar         => "bar"
    }
    s"${typeName}_$id"
  }

  /** Creates a standard filled triangle arrowhead marker. */
  private def createNormalMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val marker = createBaseMarker(defs, markerId(MarkerType.Normal, id), MarkerSize, MarkerSize, MarkerSize - 2, MarkerSize / 2)

    val path = PathData()
    path.moveTo(0, 0)
    path.lineTo(MarkerSize, MarkerSize / 2.0)
    path.lineTo(0, MarkerSize)
    path.close()

    val pathEl = marker.append("path")
    pathEl.attr("d", path.toString)
    pathEl.attr("fill", fill)
    pathEl.attr("stroke", stroke)
    pathEl.attr("stroke-width", "1")
  }

  /** Creates an open vee arrowhead marker (two lines meeting at a point). */
  private def createVeeMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val marker = createBaseMarker(defs, markerId(MarkerType.Vee, id), MarkerSize, MarkerSize, MarkerSize - 2, MarkerSize / 2)

    val path = PathData()
    path.moveTo(0, 0)
    path.lineTo(MarkerSize, MarkerSize / 2.0)
    path.lineTo(0, MarkerSize)
    path.lineTo(MarkerSize / 4.0, MarkerSize / 2.0)
    path.close()

    val pathEl = marker.append("path")
    pathEl.attr("d", path.toString)
    pathEl.attr("fill", fill)
    pathEl.attr("stroke", stroke)
    pathEl.attr("stroke-width", "1")
  }

  /** Creates a cross/X marker. */
  private def createCrossMarker(defs: SvgBuilder, id: String, stroke: String): Unit = {
    val size   = MarkerSize
    val marker = createBaseMarker(defs, markerId(MarkerType.Cross, id), size, size, size / 2, size / 2)

    // First diagonal: top-left to bottom-right
    val line1 = marker.append("line")
    line1.attr("x1", 0)
    line1.attr("y1", 0)
    line1.attr("x2", size)
    line1.attr("y2", size)
    line1.attr("stroke", stroke)
    line1.attr("stroke-width", "2")

    // Second diagonal: top-right to bottom-left
    val line2 = marker.append("line")
    line2.attr("x1", size)
    line2.attr("y1", 0)
    line2.attr("x2", 0)
    line2.attr("y2", size)
    line2.attr("stroke", stroke)
    line2.attr("stroke-width", "2")
  }

  /** Creates a small circle marker. */
  private def createCircleMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val size   = (CircleRadius * 2).toInt + 2
    val marker = createBaseMarker(defs, markerId(MarkerType.Circle, id), size, size, size / 2, size / 2)

    val circle = marker.append("circle")
    circle.attr("cx", size / 2.0)
    circle.attr("cy", size / 2.0)
    circle.attr("r", CircleRadius)
    circle.attr("fill", fill)
    circle.attr("stroke", stroke)
    circle.attr("stroke-width", "1")
  }

  /** Creates a filled diamond marker (aggregation in UML). */
  private def createAggregationMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val w      = MarkerSize
    val h      = MarkerSize
    val marker = createBaseMarker(defs, markerId(MarkerType.Aggregation, id), w, h, w / 2, h / 2)

    val halfW = w / 2.0
    val halfH = h / 2.0

    val path = PathData()
    path.moveTo(halfW, 0)
    path.lineTo(w, halfH)
    path.lineTo(halfW, h)
    path.lineTo(0, halfH)
    path.close()

    val pathEl = marker.append("path")
    pathEl.attr("d", path.toString)
    pathEl.attr("fill", fill)
    pathEl.attr("stroke", stroke)
    pathEl.attr("stroke-width", "1")
  }

  /** Creates an open diamond marker (dependency/composition in UML). */
  private def createDependencyMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val w      = MarkerSize
    val h      = MarkerSize
    val marker = createBaseMarker(defs, markerId(MarkerType.Dependency, id), w, h, w / 2, h / 2)

    val halfW = w / 2.0
    val halfH = h / 2.0

    val path = PathData()
    path.moveTo(halfW, 0)
    path.lineTo(w, halfH)
    path.lineTo(halfW, h)
    path.lineTo(0, halfH)
    path.close()

    val pathEl = marker.append("path")
    pathEl.attr("d", path.toString)
    pathEl.attr("fill", "white")
    pathEl.attr("stroke", stroke)
    pathEl.attr("stroke-width", "1")
  }

  /** Creates a lollipop marker (circle on a stick — interface realization). */
  private def createLollipopMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val size   = (CircleRadius * 2).toInt + 4
    val marker = createBaseMarker(defs, markerId(MarkerType.Lollipop, id), size, size, size / 2, size / 2)

    val center = size / 2.0

    val circle = marker.append("circle")
    circle.attr("cx", center)
    circle.attr("cy", center)
    circle.attr("r", CircleRadius)
    circle.attr("fill", "white")
    circle.attr("stroke", stroke)
    circle.attr("stroke-width", "1")
  }

  /** Creates a filled point (smaller triangle) marker. */
  private def createPointMarker(defs: SvgBuilder, id: String, fill: String, stroke: String): Unit = {
    val size   = 8
    val marker = createBaseMarker(defs, markerId(MarkerType.Point, id), size, size, size - 1, size / 2)

    val path = PathData()
    path.moveTo(0, 0)
    path.lineTo(size, size / 2.0)
    path.lineTo(0, size)
    path.close()

    val pathEl = marker.append("path")
    pathEl.attr("d", path.toString)
    pathEl.attr("fill", fill)
    pathEl.attr("stroke", stroke)
    pathEl.attr("stroke-width", "1")
  }

  /** Creates a bar/pipe marker (flat line perpendicular to the edge). */
  private def createBarMarker(defs: SvgBuilder, id: String, stroke: String): Unit = {
    val w      = 4
    val h      = MarkerSize
    val marker = createBaseMarker(defs, markerId(MarkerType.Bar, id), w, h, w / 2, h / 2)

    val line = marker.append("line")
    line.attr("x1", w / 2.0)
    line.attr("y1", 0)
    line.attr("x2", w / 2.0)
    line.attr("y2", h)
    line.attr("stroke", stroke)
    line.attr("stroke-width", "2")
  }

  /** Creates a base `<marker>` element with standard attributes.
    *
    * @param defs
    *   the `<defs>` builder to append to
    * @param id
    *   the marker element ID
    * @param markerWidth
    *   marker viewport width
    * @param markerHeight
    *   marker viewport height
    * @param refX
    *   reference point x (where the marker attaches to the path)
    * @param refY
    *   reference point y
    * @return
    *   the marker builder for adding child elements
    */
  private def createBaseMarker(
    defs:         SvgBuilder,
    id:           String,
    markerWidth:  Int,
    markerHeight: Int,
    refX:         Int,
    refY:         Int
  ): SvgBuilder = {
    val marker = defs.append("marker")
    marker.attr("id", id)
    marker.attr("viewBox", s"0 0 $markerWidth $markerHeight")
    marker.attr("refX", refX)
    marker.attr("refY", refY)
    marker.attr("markerWidth", markerWidth)
    marker.attr("markerHeight", markerHeight)
    marker.attr("orient", "auto")
    marker.attr("markerUnits", "userSpaceOnUse")
    marker
  }
}
