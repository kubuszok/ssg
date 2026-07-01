/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/diamond.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/question.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9d)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Uses PathData for polygon path; Intersect.polygon for edge routing
 *   Renames: question() → DiamondShape.render()
 *   Hand-drawn (question.ts): registered for "diamond"/"question"/"rhombus", all routed through
 *     DiamondShape, so this one branch covers all three. Upstream's question.ts hand-drawn branch
 *     builds a DECISION-BOX path (`createDecisionBoxPathD(0, 0, s)`, size `s = w + h`) and rough-
 *     sketches it via `rc.path(pathData, options)`, then translates by `(-s/2, s/2)`. SSG's CLASSIC
 *     diamond, however, is NOT that `s = w + h` decision box — it is a sharp rhombus built from the
 *     four vertices top=(cx, cy-halfH), right=(cx+halfW, cy), bottom=(cx, cy+halfH), left=(cx-halfW,
 *     cy) (halfW/halfH = config.width/height / 2), emitted as a moveTo/lineTo×3/close <path>. To keep
 *     hand-drawn CONSISTENT with SSG's own classic geometry (same shape + size), the hand-drawn branch
 *     rough-sketches SSG's OWN rhombus vertices via `rough.svg().polygon(Vector(top, right, bottom,
 *     left), opts)` (Chip 8 `RoughSVG.polygon`) rather than porting `createDecisionBoxPathD` +
 *     `rc.path` at `s = w + h`. This is the exact analogue of the 9c ellipse decision (SSG has no
 *     dedicated upstream ellipse.ts either, so it rough-sketches its own classic ellipse geometry).
 *     Options come from `HandDrawnShapeStyles.userNodeOverrides(node, {})` (seed from
 *     `config.handDrawnSeed`); the returned immutable `SvgElement` is grafted via
 *     `HandDrawnShapes.graftElement` with the `node-shape` class + inline style. Label +
 *     Intersect.polygon are unchanged. Classic rendering is byte-identical.
 *     NOTE (deliberate deviation): the polygon `Point`s are the rough-library `Point`
 *     (`ssg.graphs.commons.rough.Point`, imported as `RoughPoint`), a distinct type from the dagre
 *     `Point` the classic path + intersect use; the coordinates are the same four SSG vertices.
 *
 * upstream-commit: 2cfdd1620 (diamond.ts classic) / 56a2762 (question.ts hand-drawn, ISS-1204)
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

/** Renders a diamond (rhombus) shape for flowchart decision nodes.
  *
  * The diamond is centered at (x, y) with vertices at the midpoints of each side of the bounding rectangle. Commonly used for decision/conditional nodes in flowcharts.
  */
object DiamondShape {

  /** Renders a diamond shape.
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

    // Diamond vertices: top, right, bottom, left
    val top    = Point(cx, cy - halfH)
    val right  = Point(cx + halfW, cy)
    val bottom = Point(cx, cy + halfH)
    val left   = Point(cx - halfW, cy)

    if (config.look == "handDrawn") {
      // question.ts: the `node.look === 'handDrawn'` branch. Instead of the classic sharp <path>,
      // rough-sketch SSG's OWN rhombus vertices (top/right/bottom/left, the same geometry the classic
      // path uses) via rough.svg().polygon(...), so hand-drawn and classic are the same shape + size.
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // const roughNode = rc.polygon([top, right, bottom, left], options);
      val roughNode: SvgElement = Rough
        .svg()
        .polygon(
          Vector(
            RoughPoint(top.x, top.y),
            RoughPoint(right.x, right.y),
            RoughPoint(bottom.x, bottom.y),
            RoughPoint(left.x, left.y)
          ),
          Some(options)
        )

      val roughGroup = HandDrawnShapes.graftElement(group, roughNode)
      roughGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
      // Build diamond path
      val path = PathData()
      path.moveTo(top.x, top.y)
      path.lineTo(right.x, right.y)
      path.lineTo(bottom.x, bottom.y)
      path.lineTo(left.x, left.y)
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

    val polyPoints = Array(top, right, bottom, left)

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.polygon(polyPoints, point)
    )
  }
}
