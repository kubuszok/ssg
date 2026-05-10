/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/edges.js (edge style config)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Extracts edge style configuration into a dedicated case class
 *   Idiom: Immutable case class with defaults; replaces ad-hoc JS options objects
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package edges

import ssg.commons.Nullable

/** Configuration for rendering an edge (connection between nodes).
  *
  * Carries visual style properties and endpoint markers for an edge. The actual path is computed from the dagre layout's bend points.
  *
  * @param id
  *   unique edge identifier
  * @param stroke
  *   stroke color for the edge path
  * @param strokeWidth
  *   stroke width in pixels
  * @param strokeDasharray
  *   dash pattern (e.g. "5,5" for dashed lines); empty for solid
  * @param fill
  *   fill color for markers
  * @param cssClass
  *   CSS class to apply to the edge group
  * @param style
  *   inline CSS styles for the edge path
  * @param curve
  *   curve interpolation type (linear, basis, cardinal, step)
  * @param markerStart
  *   marker type for the start of the edge, or empty for none
  * @param markerEnd
  *   marker type for the end of the edge, or empty for none
  * @param labelText
  *   text label to display on the edge
  * @param labelX
  *   label center x coordinate (from dagre layout)
  * @param labelY
  *   label center y coordinate (from dagre layout)
  * @param thickness
  *   edge thickness category: "normal", "thick", or an explicit pixel value
  */
final case class EdgeStyle(
  id:              String = "",
  stroke:          String = "#333",
  strokeWidth:     Double = 1.5,
  strokeDasharray: String = "",
  fill:            String = "none",
  cssClass:        String = "",
  style:           String = "",
  curve:           String = "basis",
  markerStart:     Nullable[MarkerType] = Nullable.empty,
  markerEnd:       Nullable[MarkerType] = Nullable.empty,
  labelText:       String = "",
  labelX:          Double = 0,
  labelY:          Double = 0,
  thickness:       String = "normal"
)
