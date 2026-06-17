/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/rect.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function returning ShapeResult; no DOM side effects
 *   Renames: rect() → RectShape.render()
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

/** Renders a rectangle shape for flowchart nodes, class diagrams, and state diagrams.
  *
  * The rectangle is centered at the node's (x, y) position with the given width and height. An optional corner radius (rx, ry) produces rounded corners.
  */
object RectShape {

  /** Renders a rectangle shape.
    *
    * Creates a `<g>` group containing a `<rect>` element and a label. The group is positioned so that the rectangle is centered at (config.x, config.y).
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

    // Create the rectangle element
    val rect = group.append("rect")
    rect.attr("x", config.x - halfW)
    rect.attr("y", config.y - halfH)
    rect.attr("width", config.width)
    rect.attr("height", config.height)
    rect.classed("node-shape", true)

    // Apply corner radius if specified
    if (config.rx > 0) {
      rect.attr("rx", config.rx)
    }
    if (config.ry > 0) {
      rect.attr("ry", config.ry)
    }

    // Apply inline styles
    if (config.style.nonEmpty) {
      rect.attr("style", config.style)
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
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
