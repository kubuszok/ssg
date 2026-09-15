/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/cylinder.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/cylinder.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9f)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for cylinder body + elliptical caps; Intersect.rect for edge routing
 *   Renames: cylinder() → CylinderShape.render()
 *   Hand-drawn (cylinder.ts): upstream's hand-drawn branch emits TWO rough paths — the body
 *     (`rc.path(createOuterCylinderPathD(...), userNodeOverrides(node, {}))`, filled) and the top-cap
 *     inner line (`rc.path(createInnerCylinderPathD(...), userNodeOverrides(node, { fill: 'none' }))`,
 *     an UNFILLED line) — inserting the inner line then the outer node (`:first-child` twice, so the
 *     outer ends up on top). SSG's CLASSIC cylinder already builds its OWN `bodyPath` (body outline)
 *     and `capPath` (top double-arc) via `PathData`; to keep hand-drawn CONSISTENT with SSG's own
 *     classic geometry (same body + cap), the hand-drawn branch rough-sketches SSG's OWN
 *     `bodyPath.toString` and `capPath.toString` via `rough.svg().path(...)` (Chip 8 `RoughSVG.path`)
 *     rather than porting createOuterCylinderPathD/createInnerCylinderPathD. This is the exact analogue
 *     of the 9d/9e decision (rough-sketch SSG's own classic outline instead of upstream's `create*PathD`).
 *     The body uses the normal `userNodeOverrides(node, {})` (filled); the cap passes a base
 *     `Options(fill = Some("none"))` so `userNodeOverrides` yields an unfilled line (matching upstream's
 *     `userNodeOverrides(node, { fill: 'none' })` — the Object.assign present-key-wins merge keeps the
 *     passed `fill`). Both grafted in SSG's classic child order (body first, then cap) via
 *     `HandDrawnShapes.graftElement`, carrying the classic classes (`node-shape` on both, `cylinder-cap`
 *     on the cap) + inline style. Label + Intersect.rect are unchanged. Classic rendering is
 *     byte-identical. The `d` reused from `PathData.toString` is parseFloat input to rough (integral
 *     coords exact — the 9b number-formatting decision).
 *
 * upstream-commit: 2cfdd1620 (cylinder.ts classic) / 56a2762 (cylinder.ts hand-drawn, ISS-1204)
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.render.labels.ShapeLabel
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.render.Intersect
import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }

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

    // Draw the top cap (visible double-arc to show 3D depth)
    val capPath = PathData()
    capPath.moveTo(left, top + capH)
    // Top arc — convex up
    capPath.arcTo(halfW, capH, 0, largeArc = false, sweep = false, right, top + capH)
    // Return arc — concave down (front face of cylinder top)
    capPath.arcTo(halfW, capH, 0, largeArc = false, sweep = true, left, top + capH)

    if (config.look == "handDrawn") {
      // cylinder.ts: the `node.look === 'handDrawn'` branch. Emit TWO rough paths — the body (filled)
      // and the top-cap inner line (fill: 'none') — reusing SSG's OWN bodyPath/capPath outlines so
      // hand-drawn and classic are the same body + cap.
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      // const outerNode = rc.path(outerPathData, userNodeOverrides(node, {}));
      val bodyOptions = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)
      // const innerLine = rc.path(innerPathData, userNodeOverrides(node, { fill: 'none' }));
      // A base `Options(fill = Some("none"))` makes the cap an unfilled line (the Object.assign
      // present-key-wins merge in userNodeOverrides keeps this `fill`).
      val capOptions = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(fill = Some("none")), config.themeVariables, config.handDrawnSeed)

      val roughBody: SvgElement = Rough.svg().path(bodyPath.toString, Some(bodyOptions))
      val roughCap:  SvgElement = Rough.svg().path(capPath.toString, Some(capOptions))

      // Graft in SSG's classic child order: body first, then cap (upstream inserts inner then outer at
      // :first-child, ending with outer on top; SSG's classic appends body then cap).
      val bodyGroup = HandDrawnShapes.graftElement(group, roughBody)
      bodyGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        bodyGroup.attr("style", config.style)
      }

      val capGroup = HandDrawnShapes.graftElement(group, roughCap)
      capGroup.classed("node-shape", true)
      capGroup.classed("cylinder-cap", true)
      if (config.style.nonEmpty) {
        capGroup.attr("style", config.style)
      }
    } else {
      val body = group.append("path")
      body.attr("d", bodyPath.toString)
      body.classed("node-shape", true)

      if (config.style.nonEmpty) {
        body.attr("style", config.style)
      }

      val cap = group.append("path")
      cap.attr("d", capPath.toString)
      cap.classed("node-shape", true)
      cap.classed("cylinder-cap", true)

      if (config.style.nonEmpty) {
        cap.attr("style", config.style)
      }
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
