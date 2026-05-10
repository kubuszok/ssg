/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/edges.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function for edge rendering; curve dispatch via pattern match
 *   Renames: drawEdge() → EdgeRenderer.renderEdge()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package edges

import ssg.commons.Nullable
import ssg.mermaid.layout.dagre.Point
import ssg.mermaid.svg.{ PathData, SvgBuilder }

/** Renders edge paths (connections between nodes) as SVG `<path>` elements.
  *
  * Edges are rendered from an array of bend points computed by the dagre layout algorithm. Curve interpolation (linear, basis, cardinal, step) smooths the path between points. Arrow markers are
  * referenced via the SVG `marker-start` and `marker-end` attributes.
  */
object EdgeRenderer {

  /** Renders an edge as an SVG path with optional markers and label.
    *
    * Creates a `<g>` group containing:
    *   - A `<path>` element for the edge line
    *   - An optional text label (if `style.labelText` is non-empty)
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param points
    *   array of bend points from dagre layout
    * @param style
    *   edge style configuration
    * @param markerId
    *   unique ID for marker references
    * @return
    *   the SVG builder for the edge group
    */
  def renderEdge(parent: SvgBuilder, points: Array[Point], style: EdgeStyle, markerId: String): SvgBuilder = {
    val group = parent.append("g")

    if (style.cssClass.nonEmpty) {
      group.classed(style.cssClass, true)
    }
    group.classed("edge-path", true)

    if (style.id.nonEmpty) {
      group.attr("id", style.id)
    }

    // Build the path using curve interpolation
    val pathData = interpolate(points, style.curve)

    val pathEl = group.append("path")
    pathEl.attr("d", pathData.toString)
    pathEl.attr("fill", "none")

    // Apply stroke styling
    pathEl.attr("stroke", style.stroke)
    pathEl.attr("stroke-width", resolveStrokeWidth(style))

    if (style.strokeDasharray.nonEmpty) {
      pathEl.attr("stroke-dasharray", style.strokeDasharray)
    }

    if (style.style.nonEmpty) {
      pathEl.attr("style", style.style)
    }

    // Apply marker references
    style.markerStart.foreach { mt =>
      pathEl.attr("marker-start", ArrowMarkers.markerUrl(mt, markerId))
    }

    style.markerEnd.foreach { mt =>
      pathEl.attr("marker-end", ArrowMarkers.markerUrl(mt, markerId))
    }

    // Add edge label if present
    if (style.labelText.nonEmpty) {
      renderEdgeLabel(group, style)
    }

    group
  }

  /** Renders an edge label as a text element with an optional background rect.
    *
    * @param group
    *   the edge group builder
    * @param style
    *   edge style containing label text and position
    */
  private def renderEdgeLabel(group: SvgBuilder, style: EdgeStyle): Unit = {
    val labelGroup = group.append("g")
    labelGroup.classed("edge-label", true)

    // Background rect for readability
    val bg = labelGroup.append("rect")
    bg.classed("edge-label-bg", true)

    val text = labelGroup.append("text")
    text.attr("x", style.labelX)
    text.attr("y", style.labelY)
    text.attr("dominant-baseline", "central")
    text.attr("text-anchor", "middle")
    text.classed("edge-label-text", true)
    text.text(style.labelText)

    // Estimate label dimensions for background rectangle
    val fontSize        = 12.0
    val padding         = 4.0
    val estimatedWidth  = style.labelText.length * fontSize * 0.6
    val estimatedHeight = fontSize * 1.4
    bg.attr("x", style.labelX - estimatedWidth / 2.0 - padding)
    bg.attr("y", style.labelY - estimatedHeight / 2.0 - padding)
    bg.attr("width", estimatedWidth + padding * 2)
    bg.attr("height", estimatedHeight + padding * 2)
    bg.attr("rx", 3)
    bg.attr("ry", 3)
  }

  /** Interpolates bend points using the specified curve type.
    *
    * @param points
    *   array of bend points
    * @param curveType
    *   curve interpolation type name
    * @return
    *   interpolated path data
    */
  def interpolate(points: Array[Point], curveType: String): PathData =
    curveType match {
      case "linear"     => Curves.linear(points)
      case "basis"      => Curves.basis(points)
      case "cardinal"   => Curves.cardinal(points)
      case "step"       => Curves.step(points)
      case "stepBefore" => Curves.stepBefore(points)
      case "stepAfter"  => Curves.stepAfter(points)
      case "monotoneX"  => Curves.monotoneX(points)
      case _            => Curves.basis(points) // Default to basis
    }

  /** Resolves the stroke-width from the thickness setting.
    *
    * @param style
    *   edge style
    * @return
    *   stroke width as a string (for SVG attribute)
    */
  private def resolveStrokeWidth(style: EdgeStyle): String =
    style.thickness match {
      case "normal" => style.strokeWidth.toString
      case "thick"  => (style.strokeWidth * 2).toString
      case other    =>
        try
          other.toDouble.toString
        catch {
          case _: NumberFormatException => style.strokeWidth.toString
        }
    }
}
