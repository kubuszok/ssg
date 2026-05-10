/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/hexagon.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for polygon path; Intersect.polygon for edge routing
 *   Renames: hexagon() → HexagonShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.layout.dagre.Point
import ssg.mermaid.svg.{ PathData, SvgBuilder }

/** Renders a hexagon shape for flowchart nodes.
  *
  * The hexagon is a horizontally-oriented flat-top hexagon centered at (x, y). The pointed ends extend outward from the bounding rectangle's left and right edges by a fixed margin.
  */
object HexagonShape {

  /** The horizontal inset for the hexagon points, as a fraction of height. */
  private val InsetFraction: Double = 0.25

  /** Renders a hexagon shape.
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

    // Horizontal inset for the pointed ends
    val inset = config.height * InsetFraction

    // Hexagon vertices (clockwise from top-left)
    // Shape: flat top and bottom, pointed left and right
    val points = Array(
      Point(cx - halfW + inset, cy - halfH), // top-left
      Point(cx + halfW - inset, cy - halfH), // top-right
      Point(cx + halfW, cy), // right point
      Point(cx + halfW - inset, cy + halfH), // bottom-right
      Point(cx - halfW + inset, cy + halfH), // bottom-left
      Point(cx - halfW, cy) // left point
    )

    // Build hexagon path
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

    // Add label
    if (config.label.nonEmpty) {
      val text = group.append("text")
      text.attr("x", cx)
      text.attr("y", cy)
      text.attr("dominant-baseline", "central")
      text.attr("text-anchor", "middle")
      text.classed("node-label", true)
      if (config.labelStyle.nonEmpty) {
        text.attr("style", config.labelStyle)
      }
      text.text(config.label)
    }

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.polygon(points, point)
    )
  }
}
