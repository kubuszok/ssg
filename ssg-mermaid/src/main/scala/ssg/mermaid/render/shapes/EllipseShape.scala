/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/ellipse.ts
 *   (classic path; ISS-1204 Chip 9c hand-drawn branch)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function returning ShapeResult; uses Intersect.ellipse for edge routing
 *   Renames: ellipse() → EllipseShape.render()
 *   Hand-drawn: Mermaid has NO dedicated `ellipse.ts` in rendering-util/.../shapes (ellipse is an
 *     SSG-specific flowchart shape). Following circle.ts's hand-drawn pattern, the classic <ellipse>
 *     is replaced under `config.look == "handDrawn"` by `rough.svg().ellipse(cx, cy, width, height,
 *     options)` (Chip 8 `RoughSVG.ellipse`, whose `width`/`height` are the full axis extents — the
 *     analogue of circle's diameter). Options come from `HandDrawnShapeStyles.userNodeOverrides(node,
 *     {})`; the returned immutable `SvgElement` is grafted via `HandDrawnShapes.graftElement` with the
 *     `node-shape` class + inline style. Label + Intersect.ellipse are unchanged. Classic rendering is
 *     byte-identical.
 *
 * upstream-commit: 2cfdd1620 (classic) / 56a2762 (hand-drawn, ISS-1204)
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

    if (config.look == "handDrawn") {
      // Hand-drawn branch (no upstream ellipse.ts — modeled on circle.ts's hand-drawn path).
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // const roughNode = rc.ellipse(0, 0, width, height, options);
      // `RoughSVG.ellipse` takes the FULL width/height (not the semi-axes), analogous to circle's
      // diameter. SSG centers the sketch at (config.x, config.y) so it aligns with the classic
      // <ellipse>.
      val roughNode: SvgElement = Rough.svg().ellipse(config.x, config.y, config.width, config.height, Some(options))

      val roughGroup = HandDrawnShapes.graftElement(group, roughNode)
      roughGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
      val ell = group.append("ellipse")
      ell.attr("cx", config.x)
      ell.attr("cy", config.y)
      ell.attr("rx", rx)
      ell.attr("ry", ry)
      ell.classed("node-shape", true)

      if (config.style.nonEmpty) {
        ell.attr("style", config.style)
      }
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
