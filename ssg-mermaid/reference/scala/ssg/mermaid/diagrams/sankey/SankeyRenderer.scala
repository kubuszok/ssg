/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sankey/sankeyRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3-sankey layout with simplified custom layout
 *   Idiom: Pure function from SankeyDb + config -> SVG string
 *   Renames: sankeyRenderer draw() -> SankeyRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sankey

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a Sankey diagram to SVG.
  *
  * Uses a simplified left-to-right layout. Nodes are organized into columns (layers) based on their connections.
  */
object SankeyRenderer {

  private val Padding:     Double        = 30.0
  private val NodeWidth:   Double        = 20.0
  private val NodeSpacing: Double        = 15.0
  private val Colors:      Array[String] = Array(
    "#4e79a7",
    "#f28e2b",
    "#e15759",
    "#76b7b2",
    "#59a14f",
    "#edc948",
    "#b07aa1",
    "#ff9da7"
  )

  /** Renders a Sankey diagram to an SVG string. */
  def render(db: SankeyDb, config: MermaidConfig): String =
    if (db.nodes.isEmpty) {
      emptySvg(db, config)
    } else {
      renderNonEmpty(db, config)
    }

  private def renderNonEmpty(db: SankeyDb, config: MermaidConfig): String = {
    // Assign nodes to layers (columns)
    val layers     = assignLayers(db)
    val layerCount = if (layers.isEmpty) 1 else layers.values.max + 1

    // Calculate node heights based on their total flow
    val nodeValues = mutable.Map.empty[String, Double]
    for (node <- db.nodes) {
      val outflow = db.outflowFor(node)
      val inflow  = db.inflowFor(node)
      nodeValues(node) = math.max(outflow, inflow).max(1.0)
    }

    val maxNodeValue  = nodeValues.values.maxOption.getOrElse(1.0)
    val maxNodeHeight = 300.0

    // Compute positions
    val layerX     = mutable.Map.empty[Int, Double]
    val layerWidth = 600.0 / math.max(1, layerCount)
    for (l <- 0 until layerCount)
      layerX(l) = Padding + l * layerWidth

    val nodePositions = mutable.Map.empty[String, (Double, Double, Double)] // (x, y, height)
    for (l <- 0 until layerCount) {
      val nodesInLayer = db.nodes.filter(n => layers.getOrElse(n, 0) == l).toSeq
      var yOffset      = Padding
      for (node <- nodesInLayer) {
        val height = (nodeValues(node) / maxNodeValue) * maxNodeHeight
        nodePositions(node) = (layerX(l), yOffset, height)
        yOffset += height + NodeSpacing
      }
    }

    val svgHeight = nodePositions.values.map { case (_, y, h) => y + h }.maxOption.getOrElse(400.0) + Padding * 2
    val svgWidth  = Padding * 2 + layerCount * layerWidth
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)
    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "sankey", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = SankeyStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    val mainGroup = svg.append("g")

    // Draw nodes
    val nodeColorMap = mutable.Map.empty[String, String]
    var colorIdx     = 0
    for (node <- db.nodes) {
      val color = Colors(colorIdx % Colors.length)
      nodeColorMap(node) = color
      colorIdx += 1

      nodePositions.get(node).foreach { case (x, y, height) =>
        val rect = mainGroup.append("rect")
        rect.attr("x", x).attr("y", y)
        rect.attr("width", NodeWidth).attr("height", height)
        rect.style("fill", color)
        rect.classed("sankeyNode", true)

        // Label
        val label = mainGroup.append("text")
        label.attr("x", x + NodeWidth + 5).attr("y", y + height / 2 + 4)
        label.attr("text-anchor", "start")
        label.classed("sankeyLabel", true)
        label.text(node)
      }
    }

    // Draw flows (curved paths)
    val sourceYOffsets = mutable.Map.empty[String, Double]
    val targetYOffsets = mutable.Map.empty[String, Double]
    for (flow <- db.flows)
      for {
        (sx, sy, sh) <- nodePositions.get(flow.source)
        (tx, ty, th) <- nodePositions.get(flow.target)
      } {
        val sourceMaxVal     = nodeValues.getOrElse(flow.source, 1.0)
        val targetMaxVal     = nodeValues.getOrElse(flow.target, 1.0)
        val flowHeight       = (flow.value / sourceMaxVal) * sh
        val targetFlowHeight = (flow.value / targetMaxVal) * th

        val sYOff = sourceYOffsets.getOrElse(flow.source, 0.0)
        val tYOff = targetYOffsets.getOrElse(flow.target, 0.0)

        val y0 = sy + sYOff
        val y1 = ty + tYOff
        val x0 = sx + NodeWidth
        val x1 = tx
        val mx = (x0 + x1) / 2.0

        val path = mainGroup.append("path")
        val d    = s"M $x0 $y0 C $mx $y0 $mx $y1 $x1 $y1 " +
          s"L $x1 ${y1 + targetFlowHeight} " +
          s"C $mx ${y1 + targetFlowHeight} $mx ${y0 + flowHeight} $x0 ${y0 + flowHeight} Z"
        path.attr("d", d)
        path.style("fill", nodeColorMap.getOrElse(flow.source, "#ccc"))
        path.style("opacity", "0.4")
        path.classed("sankeyFlow", true)

        sourceYOffsets(flow.source) = sYOff + flowHeight
        targetYOffsets(flow.target) = tYOff + targetFlowHeight
      }

    svg.build().toMarkup()
  }

  /** Assigns each node to a layer (column index) using a simple topological approach. */
  private def assignLayers(db: SankeyDb): mutable.Map[String, Int] = {
    val layers  = mutable.Map.empty[String, Int]
    val sources = db.flows.map(_.source).toSet
    val targets = db.flows.map(_.target).toSet

    // Source-only nodes go to layer 0
    val roots = sources -- targets
    for (root <- roots) layers(root) = 0

    // Assign layers by traversal
    var changed = true
    while (changed) {
      changed = false
      for (flow <- db.flows) {
        val srcLayer      = layers.getOrElse(flow.source, 0)
        val expectedLayer = srcLayer + 1
        val currentLayer  = layers.getOrElse(flow.target, -1)
        if (expectedLayer > currentLayer) {
          layers(flow.target) = expectedLayer
          changed = true
        }
      }
    }

    // Ensure all nodes are assigned
    for (node <- db.nodes)
      if (!layers.contains(node)) layers(node) = 0

    layers
  }

  private def emptySvg(db: SankeyDb, config: MermaidConfig): String = {
    val svg = SvgBuilder.createSvg("0 0 100 50")
    svg.attr("role", "img")
    svg.classed("mermaid", true)
    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "sankey", db.accTitle, db.accDescription)
    svg.build().toMarkup()
  }
}
