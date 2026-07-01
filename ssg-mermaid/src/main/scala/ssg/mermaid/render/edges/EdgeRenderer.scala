/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/edges.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function for edge rendering; curve dispatch via pattern match
 *   Renames: drawEdge() → EdgeRenderer.renderEdge()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package edges

import lowlevel.Nullable
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.render.Curves
import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }
import ssg.mermaid.render.labels.HtmlLabelHelper
import ssg.mermaid.render.shapes.HandDrawnShapes
import ssg.mermaid.render.text.TextUtils

/** Renders edge paths (connections between nodes) as SVG `<path>` elements.
  *
  * Edges are rendered from an array of bend points computed by the dagre layout algorithm. Curve interpolation (linear, basis, cardinal, step) smooths the path between points. Arrow markers are
  * referenced via the SVG `marker-start` and `marker-end` attributes.
  */
object EdgeRenderer {

  /** Renders an edge as an SVG path with optional markers and label.
    *
    * Creates a `<g>` group containing:
    *   - A `<path>` element for the edge line
    *   - An optional text label (if `style.labelText` is non-empty)
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param points
    *   array of bend points from dagre layout
    * @param style
    *   edge style configuration
    * @param markerId
    *   unique ID for marker references
    * @return
    *   the SVG builder for the edge group
    */
  def renderEdge(parent: SvgBuilder, points: Array[Point], style: EdgeStyle, markerId: String): SvgBuilder = {
    val group = parent.append("g")

    if (style.cssClass.nonEmpty) {
      group.classed(style.cssClass, true)
    }
    group.classed("edge-path", true)

    if (style.id.nonEmpty) {
      group.attr("id", style.id)
    }

    // Build the path using curve interpolation
    val pathData = interpolate(points, style.curve)

    if (style.look == "handDrawn") {
      // edges.js:513 — the `edge.look === 'handDrawn'` branch. Upstream builds
      //   const rc = rough.svg(elem);
      //   const svgPathNode = rc.path(linePath, { roughness: 0.3, seed: handDrawnSeed });
      //   strokeClasses += ' transition';
      //   svgPath = select(svgPathNode).select('path').attr('id', edge.id)
      //     .attr('class', ' ' + strokeClasses + ...).attr('style', ...);
      //   elem.node().appendChild(svgPath.node());
      // i.e. it rough-sketches the edge's OWN line path (`linePath`) with the EDGE-SPECIFIC options
      // (roughness 0.3, seed = handDrawnSeed — NOT `userNodeOverrides`), keeps the classic edge
      // styling/markers, and adds the `transition` class. SSG reuses its OWN `pathData` (the exact
      // `d` the classic edge would draw) as the rough `d` so hand-drawn and classic edges share the
      // same geometry — markers/stroke land on the same endpoints (the ISS-1363 geometry-consistency
      // rule from 9g).
      val roughEl: SvgElement =
        Rough.svg().path(pathData.toString, Some(Options(roughness = Some(0.3), seed = Some(style.handDrawnSeed))))

      // Graft the rough `<g>` (of `<path>` children) into the edge group, then style its inner
      // `<path>` — the deterministic-output analogue of upstream's
      // `select(svgPathNode).select('path').attr(...)`. The styling/markers go on the inner path
      // (which carries the sketchy `d`) so CSS + markers work exactly as for the classic pathEl;
      // rough already set `stroke`/`fill=none` on that path from its default options, so the edge
      // stroke/width/markers OVERWRITE them here (only the LINE geometry becomes sketchy).
      val roughGroup = HandDrawnShapes.graftElement(group, roughEl)
      roughGroup.select("path").foreach { pathEl =>
        pathEl.attr("fill", "none")

        // Apply stroke styling
        pathEl.attr("stroke", style.stroke)
        pathEl.attr("stroke-width", resolveStrokeWidth(style))

        if (style.strokeDasharray.nonEmpty) {
          pathEl.attr("stroke-dasharray", style.strokeDasharray)
        }

        if (style.style.nonEmpty) {
          pathEl.attr("style", style.style)
        }

        // Apply marker references
        style.markerStart.foreach { mt =>
          pathEl.attr("marker-start", ArrowMarkers.markerUrl(mt, markerId))
        }

        style.markerEnd.foreach { mt =>
          pathEl.attr("marker-end", ArrowMarkers.markerUrl(mt, markerId))
        }

        // strokeClasses += ' transition' — the hand-drawn edge carries the `transition` class on the
        // sketch path (upstream appends it to the path's class list).
        pathEl.classed("transition", true)
      }
    } else {
      val pathEl = group.append("path")
      pathEl.attr("d", pathData.toString)
      pathEl.attr("fill", "none")

      // Apply stroke styling
      pathEl.attr("stroke", style.stroke)
      pathEl.attr("stroke-width", resolveStrokeWidth(style))

      if (style.strokeDasharray.nonEmpty) {
        pathEl.attr("stroke-dasharray", style.strokeDasharray)
      }

      if (style.style.nonEmpty) {
        pathEl.attr("style", style.style)
      }

      // Apply marker references
      style.markerStart.foreach { mt =>
        pathEl.attr("marker-start", ArrowMarkers.markerUrl(mt, markerId))
      }

      style.markerEnd.foreach { mt =>
        pathEl.attr("marker-end", ArrowMarkers.markerUrl(mt, markerId))
      }
    }

    // Add edge label if present
    if (style.labelText.nonEmpty) {
      renderEdgeLabel(group, style)
    }

    group
  }

  /** Renders an edge label as a text element with an optional background rect.
    *
    * @param group
    *   the edge group builder
    * @param style
    *   edge style containing label text and position
    */
  private def renderEdgeLabel(group: SvgBuilder, style: EdgeStyle): Unit =
    if (style.htmlLabels) {
      // HTML edge label (ISS-1205, edges.js:20-54): when `evaluate(config.flowchart.htmlLabels)`
      // is true the label is emitted as a `<foreignObject>` with an inner `<span class="edgeLabel">`
      // (isNode=false; createText.ts:28 / classRenderer-v2.ts:263-266), inside the upstream
      // `<g class="edgeLabel"> > <g class="label">` wrapper (edges.js:39-43). The label group is
      // centred at (labelX, labelY); the foreignObject geometry is sized from TextMetrics inside
      // [[HtmlLabelHelper.createText]] so dagre layout inputs are unchanged.
      val edgeLabel = group.append("g")
      edgeLabel.classed("edgeLabel", true)
      val label = edgeLabel.append("g")
      label.classed("label", true)
      label.attr("transform", s"translate(${fmtCoord(style.labelX)},${fmtCoord(style.labelY)})")
      // Security gate identical to the node path (sanitizeMore via sanitizeTextHtml).
      val sanitized = TextUtils.sanitizeTextHtml(style.labelText, style.securityLevel, style.htmlLabels)
      HtmlLabelHelper.createText(
        el = label,
        text = sanitized,
        useHtmlLabels = true,
        isNode = false,
        classes = "",
        width = 200.0,
        style = style.style,
        // edges.js:32 — markdown edge labels pass `addSvgBackground: true`.
        addBackground = true
      )
      ()
    } else {
      // SVG-text edge label — byte-identical to the legacy inline block (default path).
      val labelGroup = group.append("g")
      labelGroup.classed("edge-label", true)

      // Background rect for readability
      val bg = labelGroup.append("rect")
      bg.classed("edge-label-bg", true)

      val text = labelGroup.append("text")
      text.attr("x", style.labelX)
      text.attr("y", style.labelY)
      text.attr("dominant-baseline", "central")
      text.attr("text-anchor", "middle")
      text.classed("edge-label-text", true)
      text.text(style.labelText)

      // Estimate label dimensions for background rectangle
      val fontSize        = 12.0
      val padding         = 4.0
      val estimatedWidth  = style.labelText.length * fontSize * 0.6
      val estimatedHeight = fontSize * 1.4
      bg.attr("x", style.labelX - estimatedWidth / 2.0 - padding)
      bg.attr("y", style.labelY - estimatedHeight / 2.0 - padding)
      bg.attr("width", estimatedWidth + padding * 2)
      bg.attr("height", estimatedHeight + padding * 2)
      bg.attr("rx", 3)
      bg.attr("ry", 3)
      ()
    }

  /** Formats a coordinate without a trailing `.0` for integral values. */
  private def fmtCoord(v: Double): String =
    if (v == v.toLong.toDouble) v.toLong.toString else v.toString

  /** Interpolates bend points using the specified curve type.
    *
    * @param points
    *   array of bend points
    * @param curveType
    *   curve interpolation type name
    * @return
    *   interpolated path data
    */
  def interpolate(points: Array[Point], curveType: String): PathData =
    curveType match {
      case "linear"     => Curves.linear(points)
      case "basis"      => Curves.basis(points)
      case "cardinal"   => Curves.cardinal(points)
      case "step"       => Curves.step(points)
      case "stepBefore" => Curves.stepBefore(points)
      case "stepAfter"  => Curves.stepAfter(points)
      case "monotoneX"  => Curves.monotoneX(points)
      case _            => Curves.basis(points) // Default to basis
    }

  /** Resolves the stroke-width from the thickness setting.
    *
    * @param style
    *   edge style
    * @return
    *   stroke width as a string (for SVG attribute)
    */
  private def resolveStrokeWidth(style: EdgeStyle): String =
    style.thickness match {
      case "normal" => style.strokeWidth.toString
      case "thick"  => (style.strokeWidth * 2).toString
      case other    =>
        try
          other.toDouble.toString
        catch {
          case _: NumberFormatException => style.strokeWidth.toString
        }
    }
}
