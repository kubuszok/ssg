/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/stadium.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses rect with rx = height/2 for pill shape; Intersect.rect for edge routing
 *   Renames: stadium() → StadiumShape.render()
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

/** Renders a stadium (pill/capsule) shape for flowchart nodes.
  *
  * A stadium shape is a rectangle with fully rounded ends (corner radius = height/2). It creates a pill or capsule appearance.
  */
object StadiumShape {

  /** Renders a stadium shape.
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

    // Stadium: corner radius = half the height for full pill shape
    val radius = halfH

    val rect = group.append("rect")
    rect.attr("x", config.x - halfW)
    rect.attr("y", config.y - halfH)
    rect.attr("width", config.width)
    rect.attr("height", config.height)
    rect.attr("rx", radius)
    rect.attr("ry", radius)
    rect.classed("node-shape", true)

    if (config.style.nonEmpty) {
      rect.attr("style", config.style)
    }

    // Add label
    ShapeLabel.renderNodeLabel(group, config)

    val cx = config.x
    val cy = config.y
    val w  = config.width
    val h  = config.height

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.rect(cx, cy, w, h, point)
    )
  }
}
