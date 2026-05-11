/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Rendering pipeline: DOT AST -> layout -> SVG string.
 */
package ssg
package graphviz
package render

import scala.collection.mutable

import ssg.graphs.commons.layout.dagre.{ DagreLayout, EdgeLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.{ EdgeObj, Graph }
import ssg.graphs.commons.layout.spring.SpringLayout
import ssg.graphs.commons.layout.circular.CircularLayout
import ssg.graphs.commons.layout.radial.RadialLayout
import ssg.graphs.commons.render.Curves
import ssg.graphs.commons.svg.SvgBuilder
import ssg.graphs.commons.util.FormatUtil.formatNumber
import ssg.graphviz.parse._

/** Renders a DotGraph AST to SVG markup. */
object DotRenderer {

  /** Renders a DOT graph to an SVG string.
    *
    * The pipeline:
    *   1. Build a Graph[NodeLabel, EdgeLabel] from the AST
    *   2. Dispatch to the appropriate layout engine
    *   3. Render positioned nodes and edges as SVG
    */
  def render(dot: DotGraph, config: GraphvizConfig): String = {
    val g = GraphBuilder.build(dot)
    // Dispatch to layout engine
    config.engine match {
      case LayoutEngine.Dot   => DagreLayout.layout(g)
      case LayoutEngine.Neato => SpringLayout.layout(g)
      case LayoutEngine.Circo => CircularLayout.layout(g)
      case LayoutEngine.Twopi => RadialLayout.layout(g)
    }
    // Collect DOT-level attributes for styling
    val dotAttrs = collectDotAttrs(dot)
    renderSvg(g, dot, config, dotAttrs)
  }

  // -- Attribute collection ---------------------------------------------------

  /** Collects default node/edge attrs and graph attrs from DOT statements. */
  private def collectDotAttrs(dot: DotGraph): DotAttrs = {
    val nodeDefaults  = mutable.LinkedHashMap.empty[String, String]
    val edgeDefaults  = mutable.LinkedHashMap.empty[String, String]
    val nodeOverrides = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[String, String]]
    val edgeOverrides = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[String, String]]
    collectStmts(dot.stmts, nodeDefaults, edgeDefaults, nodeOverrides, edgeOverrides)
    DotAttrs(nodeDefaults, edgeDefaults, nodeOverrides, edgeOverrides)
  }

  private def collectStmts(
    stmts:         Seq[DotStmt],
    nodeDefaults:  mutable.LinkedHashMap[String, String],
    edgeDefaults:  mutable.LinkedHashMap[String, String],
    nodeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]],
    edgeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]]
  ): Unit =
    stmts.foreach {
      case DotAttrStmt(DotAttrTarget.Node, attrs) =>
        attrs.foreach(a => nodeDefaults(a.key) = a.value)
      case DotAttrStmt(DotAttrTarget.Edge, attrs) =>
        attrs.foreach(a => edgeDefaults(a.key) = a.value)
      case DotNodeStmt(id, attrs) =>
        if (attrs.nonEmpty) {
          val m = nodeOverrides.getOrElseUpdate(id.id, mutable.LinkedHashMap.empty)
          attrs.foreach(a => m(a.key) = a.value)
        }
      case DotEdgeStmt(nodes, attrs) =>
        if (attrs.nonEmpty) {
          nodes.sliding(2).foreach { pair =>
            if (pair.size == 2) {
              val key = pair(0).id + "->" + pair(1).id
              val m   = edgeOverrides.getOrElseUpdate(key, mutable.LinkedHashMap.empty)
              attrs.foreach(a => m(a.key) = a.value)
            }
          }
        }
      case DotSubgraphStmt(_, subStmts) =>
        collectStmts(subStmts, nodeDefaults, edgeDefaults, nodeOverrides, edgeOverrides)
      case _ => ()
    }

  final private case class DotAttrs(
    nodeDefaults:  mutable.LinkedHashMap[String, String],
    edgeDefaults:  mutable.LinkedHashMap[String, String],
    nodeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]],
    edgeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]]
  )

  // -- SVG rendering ----------------------------------------------------------

  private val EmptySvg: String = "<svg xmlns=\"http://www.w3.org/2000/svg\" />"

  private def renderSvg(
    g:        Graph[NodeLabel, EdgeLabel],
    dot:      DotGraph,
    config:   GraphvizConfig,
    dotAttrs: DotAttrs
  ): String = {
    val nodes = g.nodes()
    if (nodes.isEmpty) {
      EmptySvg
    } else {
      renderNonEmpty(g, dot, config, dotAttrs, nodes)
    }
  }

  private def renderNonEmpty(
    g:        Graph[NodeLabel, EdgeLabel],
    dot:      DotGraph,
    config:   GraphvizConfig,
    dotAttrs: DotAttrs,
    nodes:    Array[String]
  ): String = {
    // Compute bounds
    var minX = Double.MaxValue
    var minY = Double.MaxValue
    var maxX = Double.MinValue
    var maxY = Double.MinValue
    for (id <- nodes) {
      val nl     = g.node(id)
      val left   = nl.x - nl.width / 2.0
      val top    = nl.y - nl.height / 2.0
      val right  = nl.x + nl.width / 2.0
      val bottom = nl.y + nl.height / 2.0
      if (left < minX) { minX = left }
      if (top < minY) { minY = top }
      if (right > maxX) { maxX = right }
      if (bottom > maxY) { maxY = bottom }
    }

    val marginX   = config.marginX
    val marginY   = config.marginY
    val svgWidth  = maxX - minX + 2 * marginX
    val svgHeight = maxY - minY + 2 * marginY

    val viewBox = s"0 0 ${formatNumber(svgWidth)} ${formatNumber(svgHeight)}"
    val svg     = SvgBuilder.createSvg(svgWidth, svgHeight, viewBox)

    // Arrow marker definition for directed graphs
    if (dot.isDirected) {
      val defs   = svg.append("defs")
      val marker = defs.append("marker")
      marker.attr("id", "arrowhead")
      marker.attr("viewBox", "0 0 10 10")
      marker.attr("refX", "10")
      marker.attr("refY", "5")
      marker.attr("markerWidth", "6")
      marker.attr("markerHeight", "6")
      marker.attr("orient", "auto")
      val arrowPath = marker.append("path")
      arrowPath.attr("d", "M 0 0 L 10 5 L 0 10 z")
      arrowPath.attr("fill", "#333")
    }

    val offsetX = -minX + marginX
    val offsetY = -minY + marginY

    val content = svg.append("g")
    content.attr("transform", s"translate(${formatNumber(offsetX)}, ${formatNumber(offsetY)})")

    // Render edges first (below nodes)
    val edgeGroup = content.append("g")
    edgeGroup.attr("class", "edges")
    for (edgeObj <- g.edges()) {
      val el = g.edge(edgeObj)
      renderEdge(edgeGroup, g, edgeObj, el, dot.isDirected, dotAttrs)
    }

    // Render nodes
    val nodeGroup = content.append("g")
    nodeGroup.attr("class", "nodes")
    for (id <- nodes) {
      val nl = g.node(id)
      renderNode(nodeGroup, id, nl, config, dotAttrs)
    }

    // Render graph-level title/label
    renderGraphLabel(content, dot, config, svgWidth, svgHeight, marginX, marginY)

    svg.build().toMarkup()
  }

  // -- Graph label rendering ---------------------------------------------------

  /** Renders the graph-level `label` attribute as a text element at the bottom of the SVG. Also emits a `<title>` element with the graph name (if present) for accessibility.
    */
  private def renderGraphLabel(
    content:   SvgBuilder,
    dot:       DotGraph,
    config:    GraphvizConfig,
    svgWidth:  Double,
    svgHeight: Double,
    marginX:   Double,
    marginY:   Double
  ): Unit = {
    // Emit <title> with graph name for accessibility
    dot.id.foreach { name =>
      val title = content.append("title")
      title.text(name)
    }

    // Collect the graph-level label from assign statements
    val graphLabel = dot.stmts.collectFirst { case DotAssignStmt("label", v) =>
      v
    }
    graphLabel.foreach { labelText =>
      val text = content.append("text")
      text.attr("x", svgWidth / 2.0 - marginX)
      text.attr("y", svgHeight - marginY * 0.4)
      text.attr("text-anchor", "middle")
      text.attr("font-size", config.fontSize.toString)
      text.attr("font-family", config.fontName)
      text.attr("fill", "#333")
      text.text(labelText)
    }
  }

  // -- Node rendering ---------------------------------------------------------

  private def renderNode(
    parent:   SvgBuilder,
    id:       String,
    nl:       NodeLabel,
    config:   GraphvizConfig,
    dotAttrs: DotAttrs
  ): Unit = {
    val nodeG = parent.append("g")
    nodeG.attr("class", "node")

    // Resolve shape: node-specific override > default node attrs > config default
    val nodeAttrs = dotAttrs.nodeOverrides.getOrElse(id, mutable.LinkedHashMap.empty)
    val shape     = nodeAttrs.getOrElse("shape", dotAttrs.nodeDefaults.getOrElse("shape", config.defaultNodeShape))

    // Resolve styling attributes
    val fillColor   = nodeAttrs.getOrElse("fillcolor", dotAttrs.nodeDefaults.getOrElse("fillcolor", "none"))
    val strokeColor = nodeAttrs.getOrElse("color", dotAttrs.nodeDefaults.getOrElse("color", "#333"))
    val fontColor   = nodeAttrs.getOrElse("fontcolor", dotAttrs.nodeDefaults.getOrElse("fontcolor", "#333"))
    val fontSizeStr = nodeAttrs.getOrElse("fontsize", dotAttrs.nodeDefaults.getOrElse("fontsize", config.fontSize.toString))
    val fontName    = nodeAttrs.getOrElse("fontname", dotAttrs.nodeDefaults.getOrElse("fontname", config.fontName))
    val styleAttr   = nodeAttrs.getOrElse("style", dotAttrs.nodeDefaults.getOrElse("style", ""))

    // Resolve label text
    val labelText = nodeAttrs.getOrElse("label", nl.label)

    val w = nl.width
    val h = nl.height

    shape match {
      case "box" | "rect" | "rectangle" | "square" =>
        val rect = nodeG.append("rect")
        rect.attr("x", nl.x - w / 2.0)
        rect.attr("y", nl.y - h / 2.0)
        rect.attr("width", w)
        rect.attr("height", h)
        rect.attr("fill", fillColor)
        rect.attr("stroke", strokeColor)
        applyStrokeDash(rect, styleAttr)

      case "circle" =>
        val r    = math.max(w, h) / 2.0
        val circ = nodeG.append("circle")
        circ.attr("cx", nl.x)
        circ.attr("cy", nl.y)
        circ.attr("r", r)
        circ.attr("fill", fillColor)
        circ.attr("stroke", strokeColor)
        applyStrokeDash(circ, styleAttr)

      case "diamond" =>
        val pts = Array(
          s"${formatNumber(nl.x)},${formatNumber(nl.y - h / 2.0)}",
          s"${formatNumber(nl.x + w / 2.0)},${formatNumber(nl.y)}",
          s"${formatNumber(nl.x)},${formatNumber(nl.y + h / 2.0)}",
          s"${formatNumber(nl.x - w / 2.0)},${formatNumber(nl.y)}"
        )
        val poly = nodeG.append("polygon")
        poly.attr("points", pts.mkString(" "))
        poly.attr("fill", fillColor)
        poly.attr("stroke", strokeColor)
        applyStrokeDash(poly, styleAttr)

      case "triangle" =>
        val pts = Array(
          s"${formatNumber(nl.x)},${formatNumber(nl.y - h / 2.0)}",
          s"${formatNumber(nl.x + w / 2.0)},${formatNumber(nl.y + h / 2.0)}",
          s"${formatNumber(nl.x - w / 2.0)},${formatNumber(nl.y + h / 2.0)}"
        )
        val poly = nodeG.append("polygon")
        poly.attr("points", pts.mkString(" "))
        poly.attr("fill", fillColor)
        poly.attr("stroke", strokeColor)
        applyStrokeDash(poly, styleAttr)

      case "polygon" | "pentagon" | "hexagon" | "septagon" | "octagon" =>
        val sides = shape match {
          case "pentagon" => 5
          case "hexagon"  => 6
          case "septagon" => 7
          case "octagon"  => 8
          case _          => 6 // default polygon is hexagon
        }
        val rx  = w / 2.0
        val ry  = h / 2.0
        val pts = (0 until sides).map { i =>
          val angle = 2.0 * math.Pi * i / sides - math.Pi / 2.0
          s"${formatNumber(nl.x + rx * math.cos(angle))},${formatNumber(nl.y + ry * math.sin(angle))}"
        }
        val poly = nodeG.append("polygon")
        poly.attr("points", pts.mkString(" "))
        poly.attr("fill", fillColor)
        poly.attr("stroke", strokeColor)
        applyStrokeDash(poly, styleAttr)

      case "point" =>
        val r    = math.min(w, h) / 6.0
        val circ = nodeG.append("circle")
        circ.attr("cx", nl.x)
        circ.attr("cy", nl.y)
        circ.attr("r", r)
        circ.attr("fill", strokeColor)
        circ.attr("stroke", strokeColor)

      case "cylinder" =>
        // Cylinder rendered as rect with top/bottom elliptic caps
        val rx    = w / 2.0
        val capH  = h / 6.0
        val bodyH = h - 2.0 * capH
        // Body rectangle
        val rect = nodeG.append("rect")
        rect.attr("x", nl.x - rx)
        rect.attr("y", nl.y - bodyH / 2.0)
        rect.attr("width", w)
        rect.attr("height", bodyH)
        rect.attr("fill", fillColor)
        rect.attr("stroke", strokeColor)
        applyStrokeDash(rect, styleAttr)
        // Top ellipse
        val topEll = nodeG.append("ellipse")
        topEll.attr("cx", nl.x)
        topEll.attr("cy", nl.y - bodyH / 2.0)
        topEll.attr("rx", rx)
        topEll.attr("ry", capH)
        topEll.attr("fill", fillColor)
        topEll.attr("stroke", strokeColor)
        // Bottom ellipse
        val botEll = nodeG.append("ellipse")
        botEll.attr("cx", nl.x)
        botEll.attr("cy", nl.y + bodyH / 2.0)
        botEll.attr("rx", rx)
        botEll.attr("ry", capH)
        botEll.attr("fill", fillColor)
        botEll.attr("stroke", strokeColor)

      case "plain" | "plaintext" | "none" =>
        // No shape — text only
        ()

      case _ => // ellipse (default)
        val rx  = w / 2.0
        val ry  = h / 2.0
        val ell = nodeG.append("ellipse")
        ell.attr("cx", nl.x)
        ell.attr("cy", nl.y)
        ell.attr("rx", rx)
        ell.attr("ry", ry)
        ell.attr("fill", fillColor)
        ell.attr("stroke", strokeColor)
        applyStrokeDash(ell, styleAttr)
    }

    // Label text (suppressed for none and point shapes)
    if (labelText.nonEmpty && shape != "none" && shape != "point") {
      val text = nodeG.append("text")
      text.attr("x", nl.x)
      text.attr("y", nl.y)
      text.attr("text-anchor", "middle")
      text.attr("dominant-baseline", "central")
      text.attr("fill", fontColor)
      text.attr("font-size", fontSizeStr)
      text.attr("font-family", fontName)
      text.text(labelText)
    }
  }

  // -- Edge rendering ---------------------------------------------------------

  private def renderEdge(
    parent:     SvgBuilder,
    g:          Graph[NodeLabel, EdgeLabel],
    edgeObj:    EdgeObj,
    el:         EdgeLabel,
    isDirected: Boolean,
    dotAttrs:   DotAttrs
  ): Unit = {
    val edgeG = parent.append("g")
    edgeG.attr("class", "edge")

    // Resolve styling
    val edgeKey       = edgeObj.v + "->" + edgeObj.w
    val edgeOverrides = dotAttrs.edgeOverrides.getOrElse(edgeKey, mutable.LinkedHashMap.empty)
    val strokeColor   = edgeOverrides.getOrElse("color", dotAttrs.edgeDefaults.getOrElse("color", "#333"))
    val styleAttr     = edgeOverrides.getOrElse("style", dotAttrs.edgeDefaults.getOrElse("style", ""))

    val srcLabel = g.node(edgeObj.v)
    val tgtLabel = g.node(edgeObj.w)

    // Build path from edge points
    val points = if (el.points.nonEmpty) {
      el.points
    } else {
      Array(Point(srcLabel.x, srcLabel.y), Point(tgtLabel.x, tgtLabel.y))
    }

    val pathData = Curves.linear(points)
    val path     = edgeG.append("path")
    path.attr("d", pathData.toString)
    path.attr("fill", "none")
    path.attr("stroke", strokeColor)
    applyStrokeDash(path, styleAttr)
    if (isDirected) {
      path.attr("marker-end", "url(#arrowhead)")
    }

    // Edge label (if present)
    val edgeLabelText = edgeOverrides.getOrElse("label", dotAttrs.edgeDefaults.getOrElse("label", ""))
    if (edgeLabelText.nonEmpty && el.x != 0.0 && el.y != 0.0) {
      val text = edgeG.append("text")
      text.attr("x", el.x)
      text.attr("y", el.y)
      text.attr("text-anchor", "middle")
      text.attr("font-size", "12")
      text.attr("fill", "#333")
      text.text(edgeLabelText)
    }
  }

  // -- Style helpers ----------------------------------------------------------

  /** Applies stroke-dasharray for DOT `style=dashed`, `style=dotted`, or `style=invis`. */
  private def applyStrokeDash(builder: SvgBuilder, style: String): Unit =
    if (style.contains("invis")) {
      builder.attr("visibility", "hidden")
    } else if (style.contains("dashed")) {
      builder.attr("stroke-dasharray", "5,2")
    } else if (style.contains("dotted")) {
      builder.attr("stroke-dasharray", "1,2")
    }
}
