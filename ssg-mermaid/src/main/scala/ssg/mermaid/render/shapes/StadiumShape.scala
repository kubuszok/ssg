/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/stadium.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/stadium.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9f)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses rect with rx = height/2 for pill shape; Intersect.rect for edge routing
 *   Renames: stadium() → StadiumShape.render()
 *   Hand-drawn (stadium.ts): upstream's hand-drawn branch builds a fully-rounded rectangle path via
 *     `createRoundedRectPathD(-w/2, -h/2, w, h, h/2)` (radius = h/2 → pill ends) and rough-sketches it
 *     with `rc.path(pathData, options)`. SSG's CLASSIC stadium is a `<rect>` with `rx = ry = height/2`
 *     centered at (config.x, config.y); the same outline is traced by `createRoundedRectPathD` at
 *     radius = height/2 with the same top-left corner (config.x - w/2, config.y - h/2). To keep
 *     hand-drawn CONSISTENT with SSG's own classic pill geometry (same shape + size), the hand-drawn
 *     branch reuses [[RoundedRectPath.createRoundedRectPathD]] (the 9b helper) at radius = halfH and
 *     feeds the resulting `d` to `rough.svg().path(...)` (Chip 8 `RoughSVG.path`). Options come from
 *     `HandDrawnShapeStyles.userNodeOverrides(node, {})` (seed from `config.handDrawnSeed`, stroke/fill
 *     from `config.themeVariables` + `config.cssStyles`/`config.cssCompiledStyles`); the returned
 *     immutable `SvgElement` is grafted via `HandDrawnShapes.graftElement` with the `node-shape` class
 *     + inline style (SSG keeps its own `node-shape` class convention in place of upstream's
 *     'basic label-container'). Label + Intersect.rect are unchanged. Classic rendering is
 *     byte-identical. The number formatting of the reused `createRoundedRectPathD` `d` follows the 9b
 *     decision (FormatUtil.formatNumber; the `d` is parseFloat input to rough, so integral coords are
 *     exact — documented-acceptable deviation from ECMA numToString).
 *
 * upstream-commit: 2cfdd1620 (stadium.ts classic) / 56a2762 (stadium.ts hand-drawn, ISS-1204)
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.render.labels.ShapeLabel
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.render.Intersect
import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }

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

    if (config.look == "handDrawn") {
      // stadium.ts: the `node.look === 'handDrawn'` branch. Instead of the classic <rect>, build a
      // fully-rounded rectangle path (radius = height/2 → pill ends) and rough-sketch it via
      // rough.svg().path(...), so hand-drawn and classic trace the same pill outline.
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // const pathData = createRoundedRectPathD(-w / 2, -h / 2, w, h, h / 2);
      // const roughNode = rc.path(pathData, options);
      // SSG centers the pill at (config.x, config.y); the top-left corner + radius = halfH match the
      // classic <rect> (rx = ry = halfH), so hand-drawn == classic pill geometry.
      val pathData = RoundedRectPath.createRoundedRectPathD(config.x - halfW, config.y - halfH, config.width, config.height, radius)
      val roughNode: SvgElement = Rough.svg().path(pathData, Some(options))

      val roughGroup = HandDrawnShapes.graftElement(group, roughNode)
      roughGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
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
