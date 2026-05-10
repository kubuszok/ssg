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
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package clusters

import ssg.mermaid.render.labels.{ LabelRenderer, LabelStyle }
import ssg.mermaid.render.text.TextMetrics
import ssg.mermaid.svg.SvgBuilder

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

    // Title label at the top center
    if (config.title.nonEmpty) {
      val titleStyle = LabelStyle(
        fontSize = TitleFontSize,
        fontWeight = "bold",
        cssClass = "cluster-label"
      )

      // Position the title at the top center of the cluster
      val titleX = config.x
      val titleY = config.y - halfH + TitleOffsetY

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
