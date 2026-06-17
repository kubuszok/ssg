/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/trapezoid.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for polygon path; Intersect.polygon for edge routing
 *   Renames: trapezoid() → TrapezoidShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.render.labels.ShapeLabel
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.render.Intersect
import ssg.graphs.commons.svg.{ PathData, SvgBuilder }

/** Renders a trapezoid shape for flowchart nodes.
  *
  * The trapezoid has a wider bottom edge and a narrower top edge. The top edge is inset from the bottom edge's left and right sides.
  */
object TrapezoidShape {

  /** Horizontal inset for the top edge as a fraction of the height. */
  private val InsetFraction: Double = 0.25

  /** Renders a trapezoid shape.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param config
    *   shape configuration with position, size, and style
    * @return
    *   shape result with the rendered group and an intersection function
    */
  def render(parent: SvgBuilder, config: ShapeConfig): ShapeResult = {
    val group = parent.append("g")

    if (config.cssClass.nonEmpty) {
      group.classed(config.cssClass, true)
    }
    if (config.id.nonEmpty) {
      group.attr("id", config.id)
    }

    val halfW = config.width / 2.0
    val halfH = config.height / 2.0
    val cx    = config.x
    val cy    = config.y

    // Horizontal inset for the top edge
    val inset = config.height * InsetFraction

    // Trapezoid vertices (clockwise from top-left)
    // Top is narrower, bottom is wider
    val points = Array(
      Point(cx - halfW + inset, cy - halfH), // top-left (inset)
      Point(cx + halfW - inset, cy - halfH), // top-right (inset)
      Point(cx + halfW, cy + halfH), // bottom-right
      Point(cx - halfW, cy + halfH) // bottom-left
    )

    // Build trapezoid path
    val path = PathData()
    path.moveTo(points(0).x, points(0).y)
    var i = 1
    while (i < points.length) {
      path.lineTo(points(i).x, points(i).y)
      i += 1
    }
    path.close()

    val pathEl = group.append("path")
    pathEl.attr("d", path.toString)
    pathEl.classed("node-shape", true)

    if (config.style.nonEmpty) {
      pathEl.attr("style", config.style)
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
    ShapeLabel.renderNodeLabel(group, config)

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.polygon(points, point)
    )
  }
}
