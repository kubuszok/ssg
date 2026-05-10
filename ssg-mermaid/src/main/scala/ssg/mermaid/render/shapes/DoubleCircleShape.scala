/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/doubleCircle.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Two concentric circles; Intersect.circle uses outer radius
 *   Renames: doublecircle() → DoubleCircleShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.layout.dagre.Point
import ssg.mermaid.svg.SvgBuilder

/** Renders a double circle shape for state diagram final states.
  *
  * Two concentric circles with a gap between them. The outer circle defines the node boundary for edge routing.
  */
object DoubleCircleShape {

  /** Gap between the inner and outer circles, in pixels. */
  private val CircleGap: Double = 5.0

  /** Renders a double circle shape.
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

    val outerRadius = math.max(config.width, config.height) / 2.0
    val innerRadius = outerRadius - CircleGap

    // Outer circle
    val outer = group.append("circle")
    outer.attr("cx", config.x)
    outer.attr("cy", config.y)
    outer.attr("r", outerRadius)
    outer.classed("node-shape", true)
    outer.classed("outer-circle", true)

    if (config.style.nonEmpty) {
      outer.attr("style", config.style)
    }

    // Inner circle
    val inner = group.append("circle")
    inner.attr("cx", config.x)
    inner.attr("cy", config.y)
    inner.attr("r", innerRadius)
    inner.classed("node-shape", true)
    inner.classed("inner-circle", true)

    if (config.style.nonEmpty) {
      inner.attr("style", config.style)
    }

    // Add label
    if (config.label.nonEmpty) {
      val text = group.append("text")
      text.attr("x", config.x)
      text.attr("y", config.y)
      text.attr("dominant-baseline", "central")
      text.attr("text-anchor", "middle")
      text.classed("node-label", true)
      if (config.labelStyle.nonEmpty) {
        text.attr("style", config.labelStyle)
      }
      text.text(config.label)
    }

    val cx = config.x
    val cy = config.y
    val r  = outerRadius

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.circle(cx, cy, r, point)
    )
  }
}
