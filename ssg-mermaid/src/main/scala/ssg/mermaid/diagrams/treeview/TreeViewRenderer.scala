/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid tree view diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package treeview

import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a tree view diagram to SVG. */
object TreeViewRenderer {

  private val IndentSize: Double = 30.0
  private val LineHeight: Double = 25.0
  private val Padding:    Double = 20.0

  def render(db: TreeViewDb, config: MermaidConfig): String = {
    // Count total nodes for sizing
    var totalNodes = 0
    var maxDepth   = 0
    def count(node: TreeNode, depth: Int): Unit = {
      totalNodes += 1; maxDepth = math.max(maxDepth, depth)
      node.children.foreach(count(_, depth + 1))
    }
    db.roots.foreach(count(_, 0))
    totalNodes = totalNodes.max(1)

    val svgWidth  = (maxDepth + 1) * IndentSize + 300 + Padding * 2
    val svgHeight = totalNodes * LineHeight + Padding * 2 + 40
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = TreeViewStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val mainGroup = svg.append("g")

    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", svgWidth / 2).attr("y", 20).attr("text-anchor", "middle").classed("treeTitle", true).text(db.title)
    }

    var yPos = Padding + (if (db.title.nonEmpty) 30 else 0)

    def renderNode(node: TreeNode, depth: Int, parentX: Double, parentY: Double): Unit = {
      val x = Padding + depth * IndentSize
      val y = yPos
      yPos += LineHeight

      // Connector line from parent
      if (depth > 0) {
        mainGroup.append("line").attr("x1", parentX + 5).attr("y1", parentY).attr("x2", x).attr("y2", y).classed("treeConnector", true)
      }

      // Node circle
      mainGroup.append("circle").attr("cx", x + 5).attr("cy", y).attr("r", 4).classed("treeNode", true)

      // Label
      mainGroup.append("text").attr("x", x + 15).attr("y", y + 4).classed("treeLabel", true).text(node.label)

      for (child <- node.children)
        renderNode(child, depth + 1, x, y)
    }

    db.roots.foreach(renderNode(_, 0, 0, 0))

    svg.build().toMarkup()
  }
}
