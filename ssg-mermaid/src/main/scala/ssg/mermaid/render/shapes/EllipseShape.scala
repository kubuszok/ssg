/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/ellipse.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function returning ShapeResult; uses Intersect.ellipse for edge routing
 *   Renames: ellipse() → EllipseShape.render()
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

/** Renders an ellipse shape for flowchart nodes.
  *
  * The ellipse is centered at (x, y) with semi-axes derived from width/2 and height/2.
  */
object EllipseShape {

  /** Renders an ellipse shape.
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

    val rx = config.width / 2.0
    val ry = config.height / 2.0

    val ell = group.append("ellipse")
    ell.attr("cx", config.x)
    ell.attr("cy", config.y)
    ell.attr("rx", rx)
    ell.attr("ry", ry)
    ell.classed("node-shape", true)

    if (config.style.nonEmpty) {
      ell.attr("style", config.style)
    }

    // Add label
    ShapeLabel.renderNodeLabel(group, config)

    val cx = config.x
    val cy = config.y

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.ellipse(cx, cy, rx, ry, point)
    )
  }
}
