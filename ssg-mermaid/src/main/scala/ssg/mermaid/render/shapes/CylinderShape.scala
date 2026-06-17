/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/cylinder.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for cylinder body + elliptical caps; Intersect.rect for edge routing
 *   Renames: cylinder() → CylinderShape.render()
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

/** Renders a cylinder (database) shape for flowchart nodes.
  *
  * A cylinder is drawn as a rectangle with elliptical caps on top and bottom. The top cap has a visible double-arc to suggest 3D depth. This is commonly used to represent databases.
  */
object CylinderShape {

  /** Height of the elliptical cap as a fraction of total height. */
  private val CapFraction: Double = 0.15

  /** Renders a cylinder shape.
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
    val capH  = config.height * CapFraction

    val left   = cx - halfW
    val right  = cx + halfW
    val top    = cy - halfH
    val bottom = cy + halfH

    // Build the cylinder body path
    val bodyPath = PathData()
    // Start at top-left
    bodyPath.moveTo(left, top + capH)
    // Top elliptical arc (front face — concave down)
    bodyPath.arcTo(halfW, capH, 0, largeArc = false, sweep = true, right, top + capH)
    // Right side down
    bodyPath.lineTo(right, bottom - capH)
    // Bottom elliptical arc
    bodyPath.arcTo(halfW, capH, 0, largeArc = false, sweep = true, left, bottom - capH)
    // Left side up
    bodyPath.close()

    val body = group.append("path")
    body.attr("d", bodyPath.toString)
    body.classed("node-shape", true)

    if (config.style.nonEmpty) {
      body.attr("style", config.style)
    }

    // Draw the top cap (visible double-arc to show 3D depth)
    val capPath = PathData()
    capPath.moveTo(left, top + capH)
    // Top arc — convex up
    capPath.arcTo(halfW, capH, 0, largeArc = false, sweep = false, right, top + capH)
    // Return arc — concave down (front face of cylinder top)
    capPath.arcTo(halfW, capH, 0, largeArc = false, sweep = true, left, top + capH)

    val cap = group.append("path")
    cap.attr("d", capPath.toString)
    cap.classed("node-shape", true)
    cap.classed("cylinder-cap", true)

    if (config.style.nonEmpty) {
      cap.attr("style", config.style)
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205).
    // SVG text is nudged below centre (cy + capH / 2.0) to clear the top cap.
    ShapeLabel.renderNodeLabel(group, config, cy + capH / 2.0)

    val w = config.width
    val h = config.height

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.rect(cx, cy, w, h, point)
    )
  }
}
