/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/diamond.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for polygon path; Intersect.polygon for edge routing
 *   Renames: question() → DiamondShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.layout.dagre.Point
import ssg.mermaid.svg.{ PathData, SvgBuilder }

/** Renders a diamond (rhombus) shape for flowchart decision nodes.
  *
  * The diamond is centered at (x, y) with vertices at the midpoints of each side of the bounding rectangle. Commonly used for decision/conditional nodes in flowcharts.
  */
object DiamondShape {

  /** Renders a diamond shape.
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

    // Diamond vertices: top, right, bottom, left
    val top    = Point(cx, cy - halfH)
    val right  = Point(cx + halfW, cy)
    val bottom = Point(cx, cy + halfH)
    val left   = Point(cx - halfW, cy)

    // Build diamond path
    val path = PathData()
    path.moveTo(top.x, top.y)
    path.lineTo(right.x, right.y)
    path.lineTo(bottom.x, bottom.y)
    path.lineTo(left.x, left.y)
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

    val polyPoints = Array(top, right, bottom, left)

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.polygon(polyPoints, point)
    )
  }
}
