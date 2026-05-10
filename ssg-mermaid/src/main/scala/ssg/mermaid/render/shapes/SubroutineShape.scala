/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/subroutine.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Double-bordered rectangle using two vertical lines; Intersect.rect for edge routing
 *   Renames: subroutine() → SubroutineShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.layout.dagre.Point
import ssg.mermaid.svg.SvgBuilder

/** Renders a subroutine shape for flowchart nodes.
  *
  * A subroutine is a rectangle with an additional vertical line inset from each side, creating a double-bordered appearance. This indicates a predefined process or subroutine call.
  */
object SubroutineShape {

  /** Inset distance for the inner vertical lines, in pixels. */
  private val BorderInset: Double = 8.0

  /** Renders a subroutine shape.
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

    val left   = cx - halfW
    val right  = cx + halfW
    val top    = cy - halfH
    val bottom = cy + halfH

    // Outer rectangle
    val rect = group.append("rect")
    rect.attr("x", left)
    rect.attr("y", top)
    rect.attr("width", config.width)
    rect.attr("height", config.height)
    rect.classed("node-shape", true)

    if (config.style.nonEmpty) {
      rect.attr("style", config.style)
    }

    // Left inner vertical line
    val leftLine = group.append("line")
    leftLine.attr("x1", left + BorderInset)
    leftLine.attr("y1", top)
    leftLine.attr("x2", left + BorderInset)
    leftLine.attr("y2", bottom)
    leftLine.classed("subroutine-border", true)

    // Right inner vertical line
    val rightLine = group.append("line")
    rightLine.attr("x1", right - BorderInset)
    rightLine.attr("y1", top)
    rightLine.attr("x2", right - BorderInset)
    rightLine.attr("y2", bottom)
    rightLine.classed("subroutine-border", true)

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

    val w = config.width
    val h = config.height

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.rect(cx, cy, w, h, point)
    )
  }
}
