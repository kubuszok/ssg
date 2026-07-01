/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/roundedRectPath.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: the upstream `createRoundedRectPathD` is a pure string builder — a JS
 *     array of interleaved SVG path commands (`'M'`/`'H'`/`'A'`/`'V'`/`'Z'`) and numbers
 *     joined with a single space (`[...].join(' ')`). Ported verbatim as a `Vector` of
 *     tokens joined with `" "`, preserving element order and every original comment.
 *   Idiom (number -> string): upstream's `Array.join(' ')` coerces each numeric element
 *     through ECMA-262 Number::toString (`String(n)`). SSG emits every SVG coordinate
 *     through [[ssg.graphs.commons.util.FormatUtil.formatNumber]] (the same formatter
 *     `SvgBuilder`'s `attr(name, Double)` uses), so this port uses `formatNumber` too:
 *     the resulting `d` string is consumed ONLY as input to `rough.svg().path(...)` (it
 *     is parsed back to numbers by the rough path tokenizer, never emitted directly), so
 *     matching SSG's own coordinate formatting keeps the hand-drawn sketch aligned with
 *     where the classic `<rect>` — also `formatNumber`-formatted — would sit. This differs
 *     from upstream's raw `String(n)` only for coordinates carrying more than four decimal
 *     places (`formatNumber` rounds to four); for the integral radii/dimensions typical of
 *     laid-out flowchart nodes the two are byte-identical.
 *
 * upstream-commit: 56a2762 (mermaid roundedRectPath.ts)
 */
package ssg
package mermaid
package render
package shapes

import ssg.graphs.commons.util.FormatUtil.formatNumber

/** SVG path builder for a rounded rectangle — port of `roundedRectPath.ts`.
  *
  * Produces the `d` attribute string for a rounded rectangle outline, used on the hand-drawn (`look: "handDrawn"`) rendering path where the outline is fed to `rough.svg().path(...)` to be
  * re-sketched (see [[RectShape]]).
  */
object RoundedRectPath {

  /** Port of `createRoundedRectPathD(x, y, totalWidth, totalHeight, radius)`.
    *
    * Builds an SVG path `d` string tracing a rectangle with rounded corners: a move to the first straight-edge point, then horizontal/vertical lines and quarter-circle arcs around the four corners,
    * closed with `Z`.
    *
    * @param x
    *   left edge x coordinate
    * @param y
    *   top edge y coordinate
    * @param totalWidth
    *   full rectangle width
    * @param totalHeight
    *   full rectangle height
    * @param radius
    *   corner radius
    * @return
    *   the SVG path `d` string
    */
  def createRoundedRectPathD(
    x:           Double,
    y:           Double,
    totalWidth:  Double,
    totalHeight: Double,
    radius:      Double
  ): String =
    Vector(
      "M",
      formatNumber(x + radius),
      formatNumber(y), // Move to the first point
      "H",
      formatNumber(x + totalWidth - radius), // Draw horizontal line to the beginning of the right corner
      "A",
      formatNumber(radius),
      formatNumber(radius),
      formatNumber(0),
      formatNumber(0),
      formatNumber(1),
      formatNumber(x + totalWidth),
      formatNumber(y + radius), // Draw arc to the right top corner
      "V",
      formatNumber(y + totalHeight - radius), // Draw vertical line down to the beginning of the right bottom corner
      "A",
      formatNumber(radius),
      formatNumber(radius),
      formatNumber(0),
      formatNumber(0),
      formatNumber(1),
      formatNumber(x + totalWidth - radius),
      formatNumber(y + totalHeight), // Draw arc to the right bottom corner
      "H",
      formatNumber(x + radius), // Draw horizontal line to the beginning of the left bottom corner
      "A",
      formatNumber(radius),
      formatNumber(radius),
      formatNumber(0),
      formatNumber(0),
      formatNumber(1),
      formatNumber(x),
      formatNumber(y + totalHeight - radius), // Draw arc to the left bottom corner
      "V",
      formatNumber(y + radius), // Draw vertical line up to the beginning of the left top corner
      "A",
      formatNumber(radius),
      formatNumber(radius),
      formatNumber(0),
      formatNumber(0),
      formatNumber(1),
      formatNumber(x + radius),
      formatNumber(y), // Draw arc to the left top corner
      "Z" // Close the path
    ).mkString(" ")
}
