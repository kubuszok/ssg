/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/util.js labelHelper (:9, :44-120)
 *   and the htmlLabels branch of rendering-util/createText.ts.
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: The 11 flowchart shapes (CircleShape, RectShape, …) previously each inlined
 *     `group.append("text")`. They now route their node-label emission through
 *     [[ShapeLabel.renderNodeLabel]] so the htmlLabels-vs-SVG-text decision is made in one place
 *     (mirroring upstream's single `labelHelper` chokepoint). The SVG-text branch reproduces the
 *     legacy inline block byte-for-byte so the default (htmlLabels off for SVG) render geometry
 *     is unchanged.
 *   Idiom: Pure SvgBuilder construction; htmlLabels resolution via [[ShapeConfig]] fields.
 *   Out of scope: `look` handDrawn branch (ISS-1204) — slots in alongside the htmlLabels branch.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-06-17
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package labels

import ssg.mermaid.render.shapes.ShapeConfig
import ssg.mermaid.render.text.TextUtils
import ssg.graphs.commons.svg.SvgBuilder

/** Shared node-label chokepoint for flowchart shapes.
  *
  * Mirrors the label half of `labelHelper` (shapes/util.js:9, :44-120): it resolves `useHtmlLabels` and emits either an HTML `<foreignObject>` (via [[HtmlLabelHelper]]) or the legacy SVG `<text>`
  * element.
  */
object ShapeLabel {

  /** Renders the node label for a shape, choosing HTML vs SVG text.
    *
    * SVG-text branch (htmlLabels off) is byte-identical to the inline block the shapes used before this chokepoint was introduced:
    * {{{
    * val text = group.append("text")
    * text.attr("x", config.x)
    * text.attr("y", config.y)
    * text.attr("dominant-baseline", "central")
    * text.attr("text-anchor", "middle")
    * text.classed("node-label", true)
    * if (config.labelStyle.nonEmpty) text.attr("style", config.labelStyle)
    * text.text(config.label)
    * }}}
    *
    * HTML branch (htmlLabels on): a `<g class="label">` translated to the node centre containing the `<foreignObject>` produced by [[HtmlLabelHelper.createText]] — analogous to `labelHelper`
    * inserting the label `<g>` and centring it (shapes/util.js:25, :110-111).
    *
    * The security gate ([[TextUtils.sanitizeTextHtml]]) runs for the HTML branch, mirroring `sanitizeText(decodeEntities(labelText), config)` (shapes/util.js:44/:51); the SVG-text branch keeps its
    * existing [[LabelRenderer]] sanitization.
    *
    * The optional `labelY` override only affects the SVG-text branch (e.g. CylinderShape, which nudges its label below centre to clear the top cap). The HTML branch always centres the
    * `<foreignObject>` at the node centre, mirroring `labelHelper` (shapes/util.js:110-111).
    *
    * @param group
    *   the shape group `<g>` to append the label to
    * @param config
    *   shape configuration carrying label text, position, style and htmlLabels/securityLevel
    * @param labelY
    *   y position for the SVG-text label (defaults to `config.y`)
    */
  def renderNodeLabel(group: SvgBuilder, config: ShapeConfig, labelY: Double = Double.NaN): Unit =
    if (config.label.nonEmpty) {
      if (config.htmlLabels) {
        // HTML label: centre a label group at (x, y) and append the foreignObject.
        val labelGroup = group.append("g")
        labelGroup.classed("label", true)
        labelGroup.attr("transform", s"translate(${fmt(config.x)},${fmt(config.y)})")
        val sanitized = TextUtils.sanitizeTextHtml(config.label, config.securityLevel, config.htmlLabels)
        HtmlLabelHelper.createText(
          el = labelGroup,
          text = sanitized,
          useHtmlLabels = true,
          isNode = true,
          classes = "",
          width = config.width,
          style = config.labelStyle,
          addBackground = false
        )
        ()
      } else {
        // SVG text — byte-identical to the legacy inline shape block.
        val ty   = if (labelY.isNaN) config.y else labelY
        val text = group.append("text")
        text.attr("x", config.x)
        text.attr("y", ty)
        text.attr("dominant-baseline", "central")
        text.attr("text-anchor", "middle")
        text.classed("node-label", true)
        if (config.labelStyle.nonEmpty) {
          text.attr("style", config.labelStyle)
        }
        text.text(config.label)
        ()
      }
    }

  /** Formats a coordinate without a trailing `.0` for integral values. */
  private def fmt(v: Double): String =
    if (v == v.toLong.toDouble) v.toLong.toString else v.toString
}
