/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/ (rect,
 *   circle, ellipse, doubleCircle, ...) — the shared `node.look === 'handDrawn'` graft bridge
 *   (ISS-1204 Chip 9)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Idiom: Shared helper extracted from RectShape (Chip 9b) so every hand-drawn shape
 *     renderer (rect, circle, ellipse, doublecircle, ...) reuses one graft bridge rather
 *     than copy-pasting it.
 *   Convention: `graftElement` is the deterministic-output analogue of D3's
 *     `shapeSvg.insert(() => roughNode, ':first-child')` / `node().appendChild(roughNode)`.
 *
 * upstream-commit: 56a2762 (ISS-1204)
 */
package ssg
package mermaid
package render
package shapes

import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }

/** Shared helpers for the hand-drawn (`look: "handDrawn"`) shape renderers — the bridge between rough.js's immutable [[SvgElement]] output and the mutable [[SvgBuilder]] tree the shape renderers
  * assemble into.
  */
object HandDrawnShapes {

  /** Grafts an immutable [[SvgElement]] subtree onto the mutable [[SvgBuilder]] tree as a new child of `parent`, returning the builder for the grafted root.
    *
    * `Rough.svg().rectangle/.path/.circle/.ellipse` build their output as an immutable `SvgElement` `<g>` (of `<path>` children), whereas the shape renderers assemble into an `SvgBuilder`. This
    * bridges the two by re-creating the element (tag, attributes in insertion order, text/HTML content, then children recursively) inside the builder — the deterministic-output analogue of D3's
    * `insert(() => roughNode, ...)`.
    */
  def graftElement(parent: SvgBuilder, element: SvgElement): SvgBuilder = {
    val child = parent.append(element.tagName)
    element.attributes.foreach { case (name, value) => child.attr(name, value) }
    element.textContent.foreach(t => child.text(t))
    element.htmlContent.foreach(h => child.html(h))
    element.children.foreach(grandchild => graftElement(child, grandchild))
    child
  }
}
