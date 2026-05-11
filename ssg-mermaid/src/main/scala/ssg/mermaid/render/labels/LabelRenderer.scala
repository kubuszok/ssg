/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/createLabel.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3/DOM-based label creation with SvgBuilder API
 *   Idiom: Pure functions for label rendering; uses TextUtils for text processing
 *   Renames: createLabel() → LabelRenderer.renderLabel()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package labels

import ssg.mermaid.render.text.{ TextMetrics, TextUtils }
import ssg.graphs.commons.svg.{ BBox, SvgBuilder }

/** Renders text labels as SVG elements.
  *
  * Labels are the text displayed inside shapes, along edges, and in other diagram elements. This renderer handles multi-line text (splitting on `<br>` and `\n`), font styling, and text alignment.
  *
  * In browser-based Mermaid, labels can be HTML (via `<foreignObject>`) or SVG `<text>` elements. For server-side rendering, we always use SVG `<text>` with `<tspan>` elements for multi-line text.
  */
object LabelRenderer {

  /** Default vertical line spacing factor (relative to font size). */
  private val LineSpacingFactor: Double = 1.4

  /** Renders a text label at the given position.
    *
    * Creates a `<g>` group containing a `<text>` element. Multi-line text is rendered using `<tspan>` elements with appropriate `dy` offsets.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param text
    *   the label text (may contain `<br>` or `\n` for line breaks)
    * @param x
    *   center x coordinate for the label
    * @param y
    *   center y coordinate for the label
    * @param style
    *   label style configuration
    * @return
    *   the SVG builder for the label group
    */
  def renderLabel(parent: SvgBuilder, text: String, x: Double, y: Double, style: LabelStyle = LabelStyle()): SvgBuilder = {
    val group = parent.append("g")
    group.classed("label", true)

    if (style.cssClass.nonEmpty) {
      group.classed(style.cssClass, true)
    }

    // Sanitize and split text into lines
    val sanitized = TextUtils.sanitizeText(text)
    val lines     = TextUtils.getRows(sanitized)

    if (lines.isEmpty || (lines.length == 1 && lines(0).isEmpty)) {
      // Empty label — return empty group
      group
    } else {
      val textEl = group.append("text")
      textEl.attr("x", x)
      textEl.attr("y", y)
      textEl.attr("dominant-baseline", "central")

      // Set text anchor based on alignment
      val anchor = style.textAlign match {
        case "left"  => "start"
        case "right" => "end"
        case _       => "middle"
      }
      textEl.attr("text-anchor", anchor)

      // Apply font styling
      applyFontStyle(textEl, style)

      if (lines.length == 1) {
        // Single line — just set text content
        textEl.text(TextUtils.entityDecode(lines(0)))
      } else {
        // Multi-line — use tspan elements
        val lineHeight  = style.fontSize * LineSpacingFactor
        val totalHeight = lineHeight * lines.length
        // Offset to center the text block vertically
        val startY = y - totalHeight / 2.0 + lineHeight / 2.0

        var i = 0
        while (i < lines.length) {
          val tspan = textEl.append("tspan")
          tspan.attr("x", x)

          if (i == 0) {
            tspan.attr("y", startY)
          } else {
            tspan.attr("dy", lineHeight)
          }

          tspan.text(TextUtils.entityDecode(lines(i)))
          i += 1
        }
      }

      group
    }
  }

  /** Renders a label with a background rectangle for readability.
    *
    * Creates a label group with a `<rect>` background behind the text. The rectangle size is estimated from the text dimensions.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param text
    *   the label text
    * @param x
    *   center x coordinate
    * @param y
    *   center y coordinate
    * @param padding
    *   padding around the text within the background rectangle
    * @param bgFill
    *   background rectangle fill color
    * @param bgStroke
    *   background rectangle stroke color
    * @param style
    *   label style configuration
    * @return
    *   the SVG builder for the label group
    */
  def renderLabelWithBackground(
    parent:   SvgBuilder,
    text:     String,
    x:        Double,
    y:        Double,
    padding:  Double = 4.0,
    bgFill:   String = "white",
    bgStroke: String = "none",
    style:    LabelStyle = LabelStyle()
  ): SvgBuilder = {
    val group = parent.append("g")
    group.classed("label", true)

    if (style.cssClass.nonEmpty) {
      group.classed(style.cssClass, true)
    }

    // Estimate text dimensions
    val bbox = estimateLabelBBox(text, style)

    // Background rectangle
    val bg = group.append("rect")
    bg.attr("x", x - bbox.width / 2.0 - padding)
    bg.attr("y", y - bbox.height / 2.0 - padding)
    bg.attr("width", bbox.width + padding * 2)
    bg.attr("height", bbox.height + padding * 2)
    bg.attr("rx", 3)
    bg.attr("ry", 3)
    bg.attr("fill", bgFill)
    if (bgStroke != "none") {
      bg.attr("stroke", bgStroke)
    }
    bg.classed("label-bg", true)

    // Render the text on top of the background
    renderLabel(group, text, x, y, style)

    group
  }

  /** Estimates the bounding box of a label based on text content and style.
    *
    * @param text
    *   the label text (may contain line breaks)
    * @param style
    *   label style configuration
    * @return
    *   estimated bounding box (x=0, y=0)
    */
  def estimateLabelBBox(text: String, style: LabelStyle = LabelStyle()): BBox = {
    val sanitized = TextUtils.sanitizeText(text)
    TextMetrics.measureText(sanitized, style.fontSize, style.fontFamily, style.fontWeight)
  }

  /** Applies font style attributes to a text or tspan element.
    *
    * @param textEl
    *   the SVG text/tspan builder
    * @param style
    *   label style configuration
    */
  private def applyFontStyle(textEl: SvgBuilder, style: LabelStyle): Unit = {
    if (style.fontSize != 14.0) {
      textEl.style("font-size", s"${style.fontSize}px")
    }
    if (style.fontFamily != "sans-serif") {
      textEl.style("font-family", style.fontFamily)
    }
    if (style.fontWeight != "normal") {
      textEl.style("font-weight", style.fontWeight)
    }
    if (style.fontStyle != "normal") {
      textEl.style("font-style", style.fontStyle)
    }
    if (style.fill != "#333") {
      textEl.attr("fill", style.fill)
    }
    if (style.style.nonEmpty) {
      textEl.attr("style", style.style)
    }
  }
}
