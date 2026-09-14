/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/trapezoid.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/trapezoid.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9e)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for polygon path; Intersect.polygon for edge routing
 *   Renames: trapezoid() → TrapezoidShape.render()
 *   Hand-drawn (trapezoid.ts): upstream's hand-drawn branch builds a trapezoid path and rough-
 *     sketches it with `rc.path(pathData, options)`, then translates the group. SSG's CLASSIC
 *     trapezoid, however, is built from its OWN four vertices (top-left/top-right insetting the top
 *     edge by `config.height * 0.25`, plus the wider bottom-right/bottom-left), emitted as a
 *     moveTo/lineTo×3/close <path>. To keep hand-drawn CONSISTENT with SSG's own classic geometry
 *     (same shape + size), the hand-drawn branch rough-sketches SSG's OWN four trapezoid vertices via
 *     `rough.svg().polygon(Vector(points…), opts)` (Chip 8 `RoughSVG.polygon`) rather than porting
 *     the upstream path + `rc.path`. This is the exact analogue of the 9d diamond decision (SSG
 *     rough-polygons its own classic rhombus vertices instead of upstream's `createDecisionBoxPathD`
 *     at `s = w + h`). Options come from `HandDrawnShapeStyles.userNodeOverrides(node, {})` (seed from
 *     `config.handDrawnSeed`); the returned immutable `SvgElement` is grafted via
 *     `HandDrawnShapes.graftElement` with the `node-shape` class + inline style. Label +
 *     Intersect.polygon are unchanged. Classic rendering is byte-identical.
 *     NOTE (deliberate deviation): the polygon `Point`s are the rough-library `Point`
 *     (`ssg.graphs.commons.rough.Point`, imported as `RoughPoint`), a distinct type from the dagre
 *     `Point` the classic path + intersect use; the coordinates are the same four SSG vertices.
 *
 * upstream-commit: 2cfdd1620 (trapezoid.ts classic) / 56a2762 (trapezoid.ts hand-drawn, ISS-1204)
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.render.labels.ShapeLabel
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.render.Intersect
import ssg.graphs.commons.rough.{ Options, Point as RoughPoint, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }

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

    if (config.look == "handDrawn") {
      // trapezoid.ts: the `node.look === 'handDrawn'` branch. Instead of the classic sharp <path>,
      // rough-sketch SSG's OWN four trapezoid vertices (the same `points` the classic path uses) via
      // rough.svg().polygon(...), so hand-drawn and classic are the same shape + size.
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // const roughNode = rc.polygon(points, options);
      val roughNode: SvgElement = Rough
        .svg()
        .polygon(
          points.iterator.map(p => RoughPoint(p.x, p.y)).toVector,
          Some(options)
        )

      val roughGroup = HandDrawnShapes.graftElement(group, roughNode)
      roughGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
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
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
    ShapeLabel.renderNodeLabel(group, config)

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.polygon(points, point)
    )
  }
}
