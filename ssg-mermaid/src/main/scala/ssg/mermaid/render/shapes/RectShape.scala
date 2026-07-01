/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/rect.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/drawRect.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9b)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function returning ShapeResult; no DOM side effects
 *   Renames: rect() → RectShape.render()
 *   Hand-drawn (drawRect.ts): when `config.look == "handDrawn"`, the classic <rect> is
 *     replaced by a rough.js sketch node (`rough.svg().rectangle` for sharp corners,
 *     `rough.svg().path(createRoundedRectPathD(...))` when rx||ry is set), threaded with
 *     `HandDrawnShapeStyles.userNodeOverrides` (seed from `config.handDrawnSeed`, stroke/fill
 *     from `config.themeVariables` + `config.cssStyles`/`config.cssCompiledStyles`). The
 *     upstream `rc = rough.svg(shapeSvg)` DOM-selection argument is dropped (see Rough.scala);
 *     the returned immutable `SvgElement` is grafted into the builder tree (`graftElement`, the
 *     analogue of `shapeSvg.insert(() => roughNode, ':first-child')`). SSG keeps its own
 *     `node-shape` class (matching the classic rect) rather than upstream's
 *     'basic label-container'. Label + Intersect.rect are unchanged — hand-drawn only alters
 *     the shape outline. Classic rendering is byte-identical; the new ShapeConfig fields
 *     (`themeVariables`/`cssStyles`/`cssCompiledStyles`) are consumed only on this branch.
 *
 * upstream-commit: 2cfdd1620 (rect.ts) / 56a2762 (drawRect.ts, ISS-1204)
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

/** Renders a rectangle shape for flowchart nodes, class diagrams, and state diagrams.
  *
  * The rectangle is centered at the node's (x, y) position with the given width and height. An optional corner radius (rx, ry) produces rounded corners.
  */
object RectShape {

  /** Renders a rectangle shape.
    *
    * Creates a `<g>` group containing a `<rect>` element and a label. The group is positioned so that the rectangle is centered at (config.x, config.y).
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

    // SSG centers the rectangle at (config.x, config.y); the upstream `x`/`y` (drawRect.ts:19-20,
    // `-totalWidth/2` / `-totalHeight/2`) correspond to this top-left corner. Both the classic
    // <rect> and the hand-drawn sketch use these same coordinates so the sketch aligns with where
    // the classic rect would sit.
    val rectX = config.x - halfW
    val rectY = config.y - halfH
    val rectW = config.width
    val rectH = config.height

    if (config.look == "handDrawn") {
      // drawRect.ts: the `node.look === 'handDrawn'` branch. Build a rough sketch node via
      // `rough.svg(...)` instead of the classic <rect>, and insert it as the shape.
      // const options = userNodeOverrides(node, {});
      val handDrawnNode = HandDrawnNode(
        cssCompiledStyles = config.cssCompiledStyles,
        cssStyles = config.cssStyles
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(handDrawnNode, Options(), config.themeVariables, config.handDrawnSeed)

      // const roughNode =
      //   rx || ry
      //     ? rc.path(createRoundedRectPathD(x, y, totalWidth, totalHeight, rx || 0), options)
      //     : rc.rectangle(x, y, totalWidth, totalHeight, options);
      val roughNode: SvgElement =
        if (config.rx > 0 || config.ry > 0) {
          // rx || 0 — the corner radius fed to createRoundedRectPathD (0 when rx itself is falsy)
          val radius = if (config.rx > 0) config.rx else 0.0
          Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, rectW, rectH, radius), Some(options))
        } else {
          Rough.svg().rectangle(rectX, rectY, rectW, rectH, Some(options))
        }

      // rect = shapeSvg.insert(() => roughNode, ':first-child');
      // rect.attr('class', 'basic label-container').attr('style', cssStyles);
      // Graft the rough <g> (built as an immutable SvgElement) into the mutable builder tree as the
      // shape child, then apply the shape class + inline style to it (SSG keeps its own `node-shape`
      // class convention here, matching the classic <rect>, in place of upstream's
      // 'basic label-container').
      val roughGroup = graftElement(group, roughNode)
      roughGroup.classed("node-shape", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
      // Create the rectangle element
      val rect = group.append("rect")
      rect.attr("x", rectX)
      rect.attr("y", rectY)
      rect.attr("width", rectW)
      rect.attr("height", rectH)
      rect.classed("node-shape", true)

      // Apply corner radius if specified
      if (config.rx > 0) {
        rect.attr("rx", config.rx)
      }
      if (config.ry > 0) {
        rect.attr("ry", config.ry)
      }

      // Apply inline styles
      if (config.style.nonEmpty) {
        rect.attr("style", config.style)
      }
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
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

  /** Grafts an immutable [[SvgElement]] subtree onto the mutable [[SvgBuilder]] tree as a new child of `parent`, returning the builder for the grafted root.
    *
    * `Rough.svg().rectangle/.path` build their output as an immutable `SvgElement` `<g>` (of `<path>` children), whereas the shape renderers assemble into an `SvgBuilder`. This bridges the two by
    * re-creating the element (tag, attributes in insertion order, text/HTML content, then children recursively) inside the builder — the deterministic-output analogue of D3's `insert(() => roughNode,
    * ...)`.
    */
  private def graftElement(parent: SvgBuilder, element: SvgElement): SvgBuilder = {
    val child = parent.append(element.tagName)
    element.attributes.foreach { case (name, value) => child.attr(name, value) }
    element.textContent.foreach(t => child.text(t))
    element.htmlContent.foreach(h => child.html(h))
    element.children.foreach(grandchild => graftElement(child, grandchild))
    child
  }
}
