/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/subroutine.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/subroutine.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9g)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Double-bordered rectangle using two vertical lines; Intersect.rect for edge routing
 *   Renames: subroutine() → SubroutineShape.render()
 *   Hand-drawn (subroutine.ts): upstream's hand-drawn branch emits THREE rough elements — the OUTER
 *     rectangle (`rc.rectangle(x - 8, y, w + 16, h, options)`) plus two vertical inner lines
 *     (`rc.line(x, y, x, y + h, options)`, `rc.line(x + w, y, x + w, y + h, options)`), all threaded
 *     with `options = userNodeOverrides(node, {})`. SSG rough-sketches its OWN classic subroutine
 *     geometry (the SSG-analog rule shared with 9c/9d/9e/9f and the note half of this chip): the outer
 *     rect at the NOMINAL width (`left, top, config.width, config.height` — the exact classic `<rect>`
 *     coords) plus the two lines INSET by `BorderInset` (`left + BorderInset` / `right - BorderInset`,
 *     the exact classic line positions), each from `top` to `bottom`. This differs from upstream's
 *     literal geometry (which widens the outer rect by 8px per side with the lines at the nominal
 *     edges); the SSG-analog form keeps hand-drawn CONSISTENT with the classic subroutine so the drawn
 *     outline coincides with the classic one and with `Intersect.rect(cx, cy, config.width,
 *     config.height)` (which uses the nominal width for both looks) — edges route onto the drawn
 *     border rather than 8px inside it (ISS-1363). Grafted in body-first child order (rect, then left
 *     line, then right line) via `HandDrawnShapes.graftElement` — matching SSG's classic append order
 *     (rect, leftLine, rightLine) rather than upstream's reversed `:first-child` insertion. The rect
 *     carries the `node-shape` class + inline style; the two lines carry `subroutine-border` (as
 *     classic). Label + Intersect.rect are unchanged. Classic rendering is byte-identical.
 *
 * upstream-commit: 2cfdd1620 (subroutine.ts classic) / 56a2762 (subroutine.ts hand-drawn, ISS-1204)
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

    if (config.look == "handDrawn") {
      // subroutine.ts: the `node.look === 'handDrawn'` branch. Emit THREE rough elements — the outer
      // rectangle + two vertical inner lines — rough-sketching SSG's OWN classic subroutine geometry
      // (nominal-width rect + the two lines INSET by BorderInset), the SSG-analog rule shared with 9c/
      // 9d/9e/9f and the note half of this chip. This keeps hand-drawn CONSISTENT with the classic
      // subroutine (same nominal rect + inset bars) so the drawn outline coincides with the classic
      // one and with `Intersect.rect(cx, cy, config.width, config.height)` (which uses the nominal
      // width for both looks) — edges route onto the drawn border rather than 8px inside it.
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // Outer rectangle — SSG's classic nominal geometry (left, top, config.width, config.height),
      // the exact coords the classic <rect> uses below.
      val roughRect: SvgElement =
        Rough.svg().rectangle(left, top, config.width, config.height, Some(options))
      // Left inner vertical line — inset by BorderInset from the left edge (matching the classic
      // leftLine at left + BorderInset), top → bottom.
      val roughLeftLine: SvgElement = Rough.svg().line(left + BorderInset, top, left + BorderInset, bottom, Some(options))
      // Right inner vertical line — inset by BorderInset from the right edge (matching the classic
      // rightLine at right - BorderInset), top → bottom.
      val roughRightLine: SvgElement = Rough.svg().line(right - BorderInset, top, right - BorderInset, bottom, Some(options))

      // Graft in SSG's classic child order: rect first, then left line, then right line (matching the
      // classic append order below — rect, leftLine, rightLine). The rect carries node-shape + inline
      // style; both lines carry subroutine-border.
      val rectGroup = HandDrawnShapes.graftElement(group, roughRect)
      rectGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        rectGroup.attr("style", config.style)
      }

      val leftLineGroup = HandDrawnShapes.graftElement(group, roughLeftLine)
      leftLineGroup.classed("subroutine-border", true)

      val rightLineGroup = HandDrawnShapes.graftElement(group, roughRightLine)
      rightLineGroup.classed("subroutine-border", true)
    } else {
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
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
    ShapeLabel.renderNodeLabel(group, config)

    val w = config.width
    val h = config.height

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.rect(cx, cy, w, h, point)
    )
  }
}
