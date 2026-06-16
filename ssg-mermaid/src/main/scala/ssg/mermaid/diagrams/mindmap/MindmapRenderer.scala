/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/mindmap/mindmapRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from MindmapDb + config -> SVG string; custom tree layout algorithm
 *   Renames: mindmapRenderer draw() -> MindmapRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package mindmap

import ssg.mermaid.MermaidConfig
import ssg.mermaid.Accessibility
import ssg.mermaid.render.text.TextMetrics
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a mindmap diagram to SVG.
  *
  * Uses a custom tree layout algorithm that positions nodes radially from the root.
  */
object MindmapRenderer {

  private val DiagramPadding: Double = 20.0
  private val NodePadding:    Double = 10.0
  private val HorizontalGap:  Double = 60.0
  private val VerticalGap:    Double = 30.0
  private val RootNodeWidth:  Double = 120.0

  /** Positioned node for layout computation. */
  final private case class PositionedNode(
    node:       MindmapNode,
    var x:      Double = 0.0,
    var y:      Double = 0.0,
    var width:  Double = 0.0,
    var height: Double = 0.0,
    children:   mutable.ArrayBuffer[PositionedNode] = mutable.ArrayBuffer.empty
  )

  /** Renders a mindmap diagram to an SVG string.
    *
    * @param db
    *   the populated mindmap database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: MindmapDb, config: MermaidConfig): String =
    if (db.root.isEmpty) {
      val svg = SvgBuilder.createSvg("0 0 400 100")
      svg.attr("role", "img")
      // Accessibility: role + aria-roledescription always; a11y title/desc when present.
      // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
      Accessibility.applyTo(svg, "mindmap", db.accTitle, db.accDescription)
      val text = svg.append("text")
      text.attr("x", 200)
      text.attr("y", 50)
      text.attr("text-anchor", "middle")
      text.text("Empty mindmap")
      svg.build().toMarkup()
    } else {
      renderTree(db, config)
    }

  private def renderTree(db: MindmapDb, config: MermaidConfig): String = {
    val rootNode = db.root.get

    // Build positioned tree
    val posTree = buildPositionedTree(rootNode)

    // Compute sizes
    computeSizes(posTree)

    // Layout the tree
    layoutTree(posTree, DiagramPadding, DiagramPadding)

    // Compute total dimensions
    var maxX = 0.0
    var maxY = 0.0
    collectMaxBounds(posTree, (x, y) => { if (x > maxX) maxX = x; if (y > maxY) maxY = y })

    val svgWidth  = maxX + DiagramPadding * 2
    val svgHeight = maxY + DiagramPadding * 2

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "mindmap", db.accTitle, db.accDescription)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = MindmapStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    // Main group
    val mainGroup = svg.append("g")

    // Render edges first (behind nodes)
    renderEdges(mainGroup, posTree)

    // Render nodes
    renderNodes(mainGroup, posTree, themeVars)

    svg.build().toMarkup()
  }

  /** Builds a positioned tree from the mindmap node tree. */
  private def buildPositionedTree(node: MindmapNode): PositionedNode = {
    val pos = PositionedNode(node = node)
    for (child <- node.children)
      pos.children += buildPositionedTree(child)
    pos
  }

  /** Computes dimensions for each node based on text content. */
  private def computeSizes(node: PositionedNode): Unit = {
    val bbox = TextMetrics.measureText(node.node.text, 14, "sans-serif")
    node.width = math.max(bbox.width + NodePadding * 2, 40)
    node.height = math.max(bbox.height + NodePadding * 2, 30)

    // Root node is larger
    if (node.node.level == 0) {
      node.width = math.max(node.width, RootNodeWidth)
    }

    for (child <- node.children)
      computeSizes(child)
  }

  /** Lays out the tree using a simple top-down approach.
    *
    * The root is placed at the top center, children are arranged horizontally below.
    */
  private def layoutTree(node: PositionedNode, startX: Double, startY: Double): Unit = {
    node.x = startX
    node.y = startY

    if (node.children.nonEmpty) {
      // Calculate total width needed for children
      var totalChildWidth = 0.0
      for (child <- node.children) {
        val subtreeWidth = computeSubtreeWidth(child)
        totalChildWidth += subtreeWidth
      }
      totalChildWidth += (node.children.length - 1) * HorizontalGap

      // Position children below, centered under parent
      var childX = node.x + node.width / 2.0 - totalChildWidth / 2.0
      val childY = node.y + node.height + VerticalGap

      for (child <- node.children) {
        val subtreeWidth = computeSubtreeWidth(child)
        layoutTree(child, childX + subtreeWidth / 2.0 - child.width / 2.0, childY)
        childX += subtreeWidth + HorizontalGap
      }
    }
  }

  /** Computes the width of a subtree (for centering children under parent). */
  private def computeSubtreeWidth(node: PositionedNode): Double =
    if (node.children.isEmpty) {
      node.width
    } else {
      var total = 0.0
      for (child <- node.children)
        total += computeSubtreeWidth(child)
      total += (node.children.length - 1) * HorizontalGap
      math.max(total, node.width)
    }

  /** Collects maximum x and y bounds across all nodes. */
  private def collectMaxBounds(node: PositionedNode, update: (Double, Double) => Unit): Unit = {
    update(node.x + node.width, node.y + node.height)
    for (child <- node.children)
      collectMaxBounds(child, update)
  }

  /** Renders all edges (connections between nodes). */
  private def renderEdges(parent: SvgBuilder, node: PositionedNode): Unit =
    for (child <- node.children) {
      val parentCx = node.x + node.width / 2.0
      val parentCy = node.y + node.height
      val childCx  = child.x + child.width / 2.0
      val childCy  = child.y

      val path = parent.append("path")
      val midY = (parentCy + childCy) / 2.0
      path.attr("d", s"M $parentCx $parentCy C $parentCx $midY $childCx $midY $childCx $childCy")
      path.classed("mindmap-edge", true)
      path.style("fill", "none")
      path.style("stroke", "#333")
      path.style("stroke-width", "1.5")

      renderEdges(parent, child)
    }

  /** Renders all nodes. */
  private def renderNodes(parent: SvgBuilder, node: PositionedNode, themeVars: ssg.mermaid.theme.ThemeVariables): Unit = {
    val g = parent.append("g")
    g.classed("mindmap-node", true)
    g.attr("transform", s"translate(${node.x}, ${node.y})")

    // Color based on level
    val colorIdx  = node.node.level % themeVars.cScale.length
    val fillColor =
      if (themeVars.cScale(colorIdx).nonEmpty) themeVars.cScale(colorIdx)
      else defaultColor(node.node.level)

    // Render shape based on type
    node.node.shape match {
      case MindmapShape.Circle =>
        val r      = math.max(node.width, node.height) / 2.0
        val circle = g.append("circle")
        circle.attr("cx", node.width / 2.0)
        circle.attr("cy", node.height / 2.0)
        circle.attr("r", r)
        circle.classed("mindmap-shape", true)
        circle.style("fill", fillColor)
        circle.style("stroke", "#333")

      case MindmapShape.Hexagon =>
        val w       = node.width
        val h       = node.height
        val offset  = 10.0
        val points  = s"$offset,0 ${w - offset},0 $w,${h / 2.0} ${w - offset},$h $offset,$h 0,${h / 2.0}"
        val polygon = g.append("polygon")
        polygon.attr("points", points)
        polygon.classed("mindmap-shape", true)
        polygon.style("fill", fillColor)
        polygon.style("stroke", "#333")

      case MindmapShape.Cloud | MindmapShape.Bang =>
        // Cloud/bang approximated as rounded rect with larger corners
        val rect = g.append("rect")
        rect.attr("width", node.width)
        rect.attr("height", node.height)
        rect.attr("rx", 15)
        rect.attr("ry", 15)
        rect.classed("mindmap-shape", true)
        rect.style("fill", fillColor)
        rect.style("stroke", "#333")
        rect.style("stroke-dasharray", "5,3")

      case MindmapShape.Square =>
        val rect = g.append("rect")
        rect.attr("width", node.width)
        rect.attr("height", node.height)
        rect.attr("rx", 0)
        rect.attr("ry", 0)
        rect.classed("mindmap-shape", true)
        rect.style("fill", fillColor)
        rect.style("stroke", "#333")

      case _ =>
        // Default: rounded rectangle
        val rect = g.append("rect")
        rect.attr("width", node.width)
        rect.attr("height", node.height)
        rect.attr("rx", 5)
        rect.attr("ry", 5)
        rect.classed("mindmap-shape", true)
        rect.style("fill", fillColor)
        rect.style("stroke", "#333")
    }

    // Node text
    val text = g.append("text")
    text.attr("x", node.width / 2.0)
    text.attr("y", node.height / 2.0 + 5)
    text.attr("text-anchor", "middle")
    text.classed("mindmap-text", true)
    text.text(node.node.text)

    // Render children
    for (child <- node.children)
      renderNodes(parent, child, themeVars)
  }

  /** Default colors by level. */
  private def defaultColor(level: Int): String = {
    val colors = Array("#ECECFF", "#ffffde", "#bde0fe", "#ffc8dd", "#caffbf", "#ffd6a5")
    colors(level % colors.length)
  }
}
