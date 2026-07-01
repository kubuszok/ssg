/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/doubleCircle.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/doubleCircle.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9c)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Two concentric circles; Intersect.circle uses outer radius
 *   Renames: doublecircle() → DoubleCircleShape.render()
 *   Hand-drawn (doubleCircle.ts): when `config.look == "handDrawn"`, the two classic <circle>s are
 *     replaced by TWO rough.js sketch nodes — `rc.circle(0, 0, outerRadius*2, outerOptions)` and
 *     `rc.circle(0, 0, innerRadius*2, innerOptions)` — appended to a wrapping `<g>`. Upstream seeds
 *     distinct base options per ring: outer `{ roughness: 0.2, strokeWidth: 2.5 }`, inner
 *     `{ roughness: 0.2, strokeWidth: 1.5 }`, each threaded through
 *     `HandDrawnShapeStyles.userNodeOverrides`. SSG centers both sketches at (config.x, config.y) and
 *     grafts them into a `node-shape` <g> via `HandDrawnShapes.graftElement`. Label +
 *     Intersect.circle (outer radius) are unchanged. Classic rendering is byte-identical.
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

    if (config.look == "handDrawn") {
      // doubleCircle.ts: the `node.look === 'handDrawn'` branch. Two rough sketch circles appended to
      // a wrapping <g>, with distinct base options per ring.
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      // const outerOptions = userNodeOverrides(node, { roughness: 0.2, strokeWidth: 2.5 });
      val outerOptions =
        HandDrawnShapeStyles.userNodeOverrides(
          handDrawnNode,
          Options(roughness = Some(0.2), strokeWidth = Some(2.5)),
          config.themeVariables,
          config.handDrawnSeed
        )
      // const innerOptions = userNodeOverrides(node, { roughness: 0.2, strokeWidth: 1.5 });
      val innerOptions =
        HandDrawnShapeStyles.userNodeOverrides(
          handDrawnNode,
          Options(roughness = Some(0.2), strokeWidth = Some(1.5)),
          config.themeVariables,
          config.handDrawnSeed
        )

      // const outerRoughNode = rc.circle(0, 0, outerRadius * 2, outerOptions);
      // const innerRoughNode = rc.circle(0, 0, innerRadius * 2, innerOptions);
      val outerRoughNode: SvgElement = Rough.svg().circle(config.x, config.y, outerRadius * 2, Some(outerOptions))
      val innerRoughNode: SvgElement = Rough.svg().circle(config.x, config.y, innerRadius * 2, Some(innerOptions))

      // circleGroup = shapeSvg.insert('g', ':first-child');
      // circleGroup.attr('class', node.cssClasses).attr('style', cssStyles);
      // circleGroup.node()?.appendChild(outerRoughNode);
      // circleGroup.node()?.appendChild(innerRoughNode);
      val circleGroup = group.append("g")
      circleGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        circleGroup.attr("style", config.style)
      }
      HandDrawnShapes.graftElement(circleGroup, outerRoughNode)
      HandDrawnShapes.graftElement(circleGroup, innerRoughNode)
    } else {
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
    }

    // Add label
    ShapeLabel.renderNodeLabel(group, config)

    val cx = config.x
    val cy = config.y
    val r  = outerRadius

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.circle(cx, cy, r, point)
    )
  }
}
