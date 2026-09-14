/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/clusters.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Pure function for cluster rendering; label positioned at top center
 *   Renames: insertCluster() → ClusterRenderer.renderCluster()
 *   Hand-drawn (clusters.js:66-84 `rect`, ISS-1204 Chip 9i): when `config.look == "handDrawn"`
 *     the classic background <rect> is replaced by a rough.js sketch node
 *     (`rough.svg().path(createRoundedRectPathD(x, y, width, height, 0), options)`). Unlike the
 *     shape/edge/note branches, the cluster passes a BASE options object (roughness 0.7, fill =
 *     clusterBkg, stroke = clusterBorder, fillWeight 3, seed = handDrawnSeed) THROUGH
 *     `HandDrawnShapeStyles.userNodeOverrides` (upstream `userNodeOverrides(node, { ... })`), which
 *     keeps every supplied key and fills defaults for the rest. clusterBkg/clusterBorder arrive as
 *     `config.backgroundColor`/`config.borderColor` (set by `FlowchartRenderer.renderSubgraphs` from
 *     `themeVars.clusterBkg`/`clusterBorder`).
 *   Geometry (radius 0 — DELIBERATE, adjudicated Chip 9i): upstream's OWN hand-drawn cluster uses
 *     `createRoundedRectPathD(x, y, width, height, 0)` — a SHARP-cornered rect — even though its
 *     classic cluster (and SSG's, via `renderRoundedCluster` rx=ry=5) is ROUNDED. Because upstream's
 *     classic cluster == SSG's classic cluster (both rounded), this is NOT the 9g situation where SSG
 *     had simplified its own geometry; here the port faithfully reproduces upstream's intentional
 *     radius-0 hand-drawn cluster. So hand-drawn clusters are sharp-cornered per upstream while the
 *     classic cluster stays rounded (rx/ry preserved on the classic path).
 *   Styling (clusters.js:83-84): upstream styles the inner rough paths from the node's OWN cssStyles
 *     (`path:nth-child(2)` ← borderStyles, `path` ← backgroundStyles with fill→stroke). SSG clusters
 *     carry no per-node cssStyles, so those style strings are empty; the cluster border/background
 *     COLOURS are instead baked into the rough element by the `fill`/`stroke` options (clusterBkg /
 *     clusterBorder). The grafted rough <g> keeps SSG's classic `cluster-bg` class + `config.style`
 *     (matching the classic <rect>), so the cluster renders with its border + background. Title/label
 *     + structure are unchanged. Classic rendering is byte-identical.
 *
 * upstream-commit: 2cfdd1620 (classic) / 56a2762 (clusters.js hand-drawn, ISS-1204)
 */
package ssg
package mermaid
package render
package clusters

import ssg.mermaid.render.labels.{ HtmlLabelHelper, LabelRenderer, LabelStyle }
import ssg.mermaid.render.shapes.{ HandDrawnNode, HandDrawnShapeStyles, HandDrawnShapes, RoundedRectPath }
import ssg.mermaid.render.text.{ TextMetrics, TextUtils }
import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }

/** Renders cluster (subgraph) containers as SVG elements.
  *
  * Clusters are the visual containers for subgraphs in flowcharts. They render as a bordered rectangle with a title at the top. Child nodes and edges are rendered inside the cluster boundaries.
  *
  * In Mermaid, clusters can be nested and styled independently.
  */
object ClusterRenderer {

  /** Vertical offset for the cluster title from the top edge. */
  private val TitleOffsetY: Double = 14.0

  /** Title font size used for cluster labels. */
  private val TitleFontSize: Double = 14.0

  /** Renders a cluster container as an SVG group.
    *
    * Creates a `<g>` group containing:
    *   - A `<rect>` for the cluster background and border
    *   - A title label at the top center of the cluster
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param config
    *   cluster configuration with position, size, and style
    * @return
    *   the SVG builder for the cluster group
    */
  def renderCluster(parent: SvgBuilder, config: ClusterConfig): SvgBuilder = {
    val group = parent.append("g")
    group.classed("cluster", true)

    if (config.cssClass.nonEmpty) {
      group.classed(config.cssClass, true)
    }
    if (config.id.nonEmpty) {
      group.attr("id", config.id)
    }

    val halfW = config.width / 2.0
    val halfH = config.height / 2.0

    // Background rectangle
    if (config.look == "handDrawn") {
      // clusters.js:66-84 — the `node.look === 'handDrawn'` branch. Build a rough sketch node via
      // `rough.svg(...)` instead of the classic background <rect>, and insert it as the first child.
      // const { clusterBkg, clusterBorder } = themeVariables;  → config.backgroundColor / borderColor
      // const options = userNodeOverrides(node, {
      //   roughness: 0.7, fill: clusterBkg, stroke: clusterBorder, fillWeight: 3, seed: handDrawnSeed });
      // Cluster passes a BASE options object THROUGH userNodeOverrides (unlike edges/notes which pass
      // raw opts). SSG clusters carry no cssStyles, so HandDrawnNode() is empty.
      val clusterNode = HandDrawnNode()
      val baseOptions = Options(
        roughness = Some(0.7),
        fill = Some(config.backgroundColor),
        stroke = Some(config.borderColor),
        fillWeight = Some(3),
        seed = Some(config.handDrawnSeed)
      )
      val options = HandDrawnShapeStyles.userNodeOverrides(clusterNode, baseOptions, config.themeVariables, config.handDrawnSeed)

      // const roughNode = rc.path(createRoundedRectPathD(x, y, width, height, 0), options);
      // radius 0 — upstream's hand-drawn cluster is DELIBERATELY sharp-cornered (classic stays rounded).
      val roughNode: SvgElement =
        Rough.svg().path(RoundedRectPath.createRoundedRectPathD(config.x - halfW, config.y - halfH, config.width, config.height, 0), Some(options))

      // rect = shapeSvg.insert(() => roughNode, ':first-child');
      // Graft the rough <g> into the builder tree as the first (background) child. The cluster
      // border/background colours are baked into the rough element via the fill/stroke options; keep
      // SSG's classic cluster-bg class + inline style (matching the classic <rect>).
      val roughGroup = HandDrawnShapes.graftElement(group, roughNode)
      roughGroup.classed("cluster-bg", true)
      if (config.style.nonEmpty) {
        roughGroup.attr("style", config.style)
      }
    } else {
      val rect = group.append("rect")
      rect.attr("x", config.x - halfW)
      rect.attr("y", config.y - halfH)
      rect.attr("width", config.width)
      rect.attr("height", config.height)
      rect.attr("fill", config.backgroundColor)
      rect.attr("stroke", config.borderColor)
      rect.attr("stroke-width", "1")
      rect.classed("cluster-bg", true)

      if (config.rx > 0) {
        rect.attr("rx", config.rx)
      }
      if (config.ry > 0) {
        rect.attr("ry", config.ry)
      }

      if (config.style.nonEmpty) {
        rect.attr("style", config.style)
      }
    }

    // Title label at the top center
    if (config.title.nonEmpty) {
      // Position the title at the top center of the cluster
      val titleX = config.x
      val titleY = config.y - halfH + TitleOffsetY

      if (config.htmlLabels) {
        // HTML label (ISS-1205): foreignObject centred at the title position.
        val labelGroup = group.append("g")
        labelGroup.classed("label", true)
        labelGroup.classed("cluster-label", true)
        labelGroup.attr("transform", s"translate(${fmtCoord(titleX)},${fmtCoord(titleY)})")
        val sanitized = TextUtils.sanitizeTextHtml(config.title, config.securityLevel, config.htmlLabels)
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
        val titleStyle = LabelStyle(
          fontSize = TitleFontSize,
          fontWeight = "bold",
          cssClass = "cluster-label"
        )

        val titleGroup = LabelRenderer.renderLabel(
          group,
          config.title,
          titleX,
          titleY,
          titleStyle
        )

        if (config.labelStyle.nonEmpty) {
          // Apply additional label styling
          titleGroup.attr("style", config.labelStyle)
        }
      }
    }

    group
  }

  /** Renders a cluster with a rounded-rectangle style (default for flowcharts).
    *
    * Convenience method that applies a default corner radius.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param config
    *   cluster configuration
    * @return
    *   the SVG builder for the cluster group
    */
  def renderRoundedCluster(parent: SvgBuilder, config: ClusterConfig): SvgBuilder = {
    val roundedConfig = if (config.rx <= 0 && config.ry <= 0) {
      config.copy(rx = 5, ry = 5)
    } else {
      config
    }
    renderCluster(parent, roundedConfig)
  }

  /** Formats a coordinate without a trailing `.0` for integral values. */
  private def fmtCoord(v: Double): String =
    if (v == v.toLong.toDouble) v.toLong.toString else v.toString

  /** Estimates the height needed for the cluster title.
    *
    * This is used by the layout algorithm to add space above the cluster contents for the title.
    *
    * @param title
    *   the cluster title text
    * @param fontSize
    *   font size (default matches [[TitleFontSize]])
    * @return
    *   estimated title height in pixels
    */
  def estimateTitleHeight(title: String, fontSize: Double = TitleFontSize): Double =
    if (title.isEmpty) {
      0.0
    } else {
      val bbox = TextMetrics.measureText(title, fontSize, "sans-serif", "bold")
      bbox.height + TitleOffsetY
    }
}
