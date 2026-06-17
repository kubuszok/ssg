/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/circle.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function returning ShapeResult; uses Intersect.circle for edge routing
 *   Renames: circle() → CircleShape.render()
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
import ssg.graphs.commons.svg.SvgBuilder

/** Renders a circle shape for flowchart and state diagram nodes.
  *
  * The circle is centered at (x, y) with a radius derived from the larger of width/2 and height/2 to ensure the label fits within the shape.
  */
object CircleShape {

  /** Renders a circle shape.
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

    // Radius is the max of half-width and half-height to ensure the label fits
    val radius = math.max(config.width, config.height) / 2.0

    val circ = group.append("circle")
    circ.attr("cx", config.x)
    circ.attr("cy", config.y)
    circ.attr("r", radius)
    circ.classed("node-shape", true)

    if (config.style.nonEmpty) {
      circ.attr("style", config.style)
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
    ShapeLabel.renderNodeLabel(group, config)

    val cx = config.x
    val cy = config.y
    val r  = radius

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.circle(cx, cy, r, point)
    )
  }
}
