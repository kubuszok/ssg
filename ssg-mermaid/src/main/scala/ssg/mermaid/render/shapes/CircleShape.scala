/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/circle.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/circle.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9c)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function returning ShapeResult; uses Intersect.circle for edge routing
 *   Renames: circle() → CircleShape.render()
 *   Hand-drawn (circle.ts): when `config.look == "handDrawn"`, the classic <circle> is replaced by a
 *     rough.js sketch node (`rough.svg().circle(cx, cy, radius*2, options)`; upstream centers at
 *     (0,0) with `radius*2` as the diameter). Options come from
 *     `HandDrawnShapeStyles.userNodeOverrides(node, {})` (seed from `config.handDrawnSeed`, stroke/fill
 *     from `config.themeVariables` + `config.cssStyles`/`config.cssCompiledStyles`). SSG centers the
 *     sketch at (config.x, config.y) — where the classic <circle> sits — and grafts the returned
 *     immutable `SvgElement` into the builder tree via `HandDrawnShapes.graftElement`, applying the
 *     `node-shape` class + inline style. Label + Intersect.circle are unchanged. Classic rendering is
 *     byte-identical.
 *
 * upstream-commit: 2cfdd1620 (circle.ts classic) / 56a2762 (circle.ts hand-drawn, ISS-1204)
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

    if (config.look == "handDrawn") {
      // circle.ts: the `node.look === 'handDrawn'` branch. Build a rough sketch node via
      // `rough.svg(...)` instead of the classic <circle>, and insert it as the shape.
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // const roughNode = rc.circle(0, 0, radius * 2, options);
      // SSG centers the sketch at (config.x, config.y) so it aligns with the classic <circle>; the
      // diameter is `radius * 2` (upstream passes the diameter, not the radius).
      val roughNode: SvgElement = Rough.svg().circle(config.x, config.y, radius * 2, Some(options))

      // circleElem = shapeSvg.insert(() => roughNode, ':first-child');
      // circleElem.attr('class', 'basic label-container').attr('style', cssStyles);
      val roughGroup = HandDrawnShapes.graftElement(group, roughNode)
      roughGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
      val circ = group.append("circle")
      circ.attr("cx", config.x)
      circ.attr("cy", config.y)
      circ.attr("r", radius)
      circ.classed("node-shape", true)

      if (config.style.nonEmpty) {
        circ.attr("style", config.style)
      }
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
