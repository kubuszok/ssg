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
    val result   = GraphBuilder.build(dot)
    val g        = result.graph
    val clusters = result.clusters
    // Dispatch to layout engine
    config.engine match {
      case LayoutEngine.Dot   => DagreLayout.layout(g)
      case LayoutEngine.Neato => SpringLayout.layout(g)
      case LayoutEngine.Circo => CircularLayout.layout(g)
      case LayoutEngine.Twopi => RadialLayout.layout(g)
    }
    // Collect DOT-level attributes for styling
    val dotAttrs = collectDotAttrs(dot)
    renderSvg(g, dot, config, dotAttrs, clusters)
  }

  // -- Attribute collection ---------------------------------------------------

  /** Collects default node/edge attrs and graph attrs from DOT statements. */
  private def collectDotAttrs(dot: DotGraph): DotAttrs = {
    val nodeDefaults  = mutable.LinkedHashMap.empty[String, String]
    val edgeDefaults  = mutable.LinkedHashMap.empty[String, String]
    val nodeOverrides = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[String, String]]
    val edgeOverrides = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[String, String]]
    // Track which (scope, key) pairs have HTML-like values
    val htmlFlags = mutable.Set.empty[String]
    collectStmts(dot.stmts, nodeDefaults, edgeDefaults, nodeOverrides, edgeOverrides, htmlFlags)
    DotAttrs(nodeDefaults, edgeDefaults, nodeOverrides, edgeOverrides, htmlFlags)
  }

  /** Builds a composite key for tracking HTML-like attribute values. */
  private def htmlFlagKey(scope: String, attrKey: String): String =
    scope + "|" + attrKey

  private def collectStmts(
    stmts:         Seq[DotStmt],
    nodeDefaults:  mutable.LinkedHashMap[String, String],
    edgeDefaults:  mutable.LinkedHashMap[String, String],
    nodeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]],
    edgeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]],
    htmlFlags:     mutable.Set[String]
  ): Unit =
    stmts.foreach {
      case DotAttrStmt(DotAttrTarget.Node, attrs) =>
        attrs.foreach { a =>
          nodeDefaults(a.key) = a.value
          if (a.isHtml) { htmlFlags += htmlFlagKey("nodeDefault", a.key) }
        }
      case DotAttrStmt(DotAttrTarget.Edge, attrs) =>
        attrs.foreach { a =>
          edgeDefaults(a.key) = a.value
          if (a.isHtml) { htmlFlags += htmlFlagKey("edgeDefault", a.key) }
        }
      case DotNodeStmt(id, attrs) =>
        if (attrs.nonEmpty) {
          val m = nodeOverrides.getOrElseUpdate(id.id, mutable.LinkedHashMap.empty)
          attrs.foreach { a =>
            m(a.key) = a.value
            if (a.isHtml) { htmlFlags += htmlFlagKey("node:" + id.id, a.key) }
          }
        }
      case DotEdgeStmt(nodes, attrs) =>
        if (attrs.nonEmpty) {
          nodes.sliding(2).foreach { pair =>
            if (pair.size == 2) {
              val key = pair(0).id + "->" + pair(1).id
              val m   = edgeOverrides.getOrElseUpdate(key, mutable.LinkedHashMap.empty)
              attrs.foreach { a =>
                m(a.key) = a.value
                if (a.isHtml) { htmlFlags += htmlFlagKey("edge:" + key, a.key) }
              }
            }
          }
        }
      case DotSubgraphStmt(_, subStmts) =>
        collectStmts(subStmts, nodeDefaults, edgeDefaults, nodeOverrides, edgeOverrides, htmlFlags)
      case _ => ()
    }

  final private case class DotAttrs(
    nodeDefaults:  mutable.LinkedHashMap[String, String],
    edgeDefaults:  mutable.LinkedHashMap[String, String],
    nodeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]],
    edgeOverrides: mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, String]],
    htmlFlags:     mutable.Set[String]
  )

  /** Checks whether a label value for a given node is from an HTML-like label. */
  private def isHtmlLabel(dotAttrs: DotAttrs, nodeId: String): Boolean = {
    // Check node-specific override first, then default
    val nodeAttrs = dotAttrs.nodeOverrides.getOrElse(nodeId, mutable.LinkedHashMap.empty)
    if (nodeAttrs.contains("label")) {
      dotAttrs.htmlFlags.contains(htmlFlagKey("node:" + nodeId, "label"))
    } else {
      dotAttrs.htmlFlags.contains(htmlFlagKey("nodeDefault", "label"))
    }
  }

  /** Checks whether an edge label value is from an HTML-like label. */
  private def isHtmlEdgeLabel(dotAttrs: DotAttrs, edgeKey: String): Boolean = {
    val edgeOverrides = dotAttrs.edgeOverrides.getOrElse(edgeKey, mutable.LinkedHashMap.empty)
    if (edgeOverrides.contains("label")) {
      dotAttrs.htmlFlags.contains(htmlFlagKey("edge:" + edgeKey, "label"))
    } else {
      dotAttrs.htmlFlags.contains(htmlFlagKey("edgeDefault", "label"))
    }
  }

  // -- SVG rendering ----------------------------------------------------------

  private val EmptySvg: String = "<svg xmlns=\"http://www.w3.org/2000/svg\" />"

  private def renderSvg(
    g:        Graph[NodeLabel, EdgeLabel],
    dot:      DotGraph,
    config:   GraphvizConfig,
    dotAttrs: DotAttrs,
    clusters: Map[String, ClusterInfo]
  ): String = {
    val nodes = g.nodes()
    if (nodes.isEmpty) {
      EmptySvg
    } else {
      renderNonEmpty(g, dot, config, dotAttrs, nodes, clusters)
    }
  }

  /** Graphviz default cluster margin in points. */
  private val ClusterMargin: Double = 8.0

  /** Font size for cluster labels (in points). */
  private val ClusterLabelFontSize: Double = 14.0

  private def renderNonEmpty(
    g:        Graph[NodeLabel, EdgeLabel],
    dot:      DotGraph,
    config:   GraphvizConfig,
    dotAttrs: DotAttrs,
    nodes:    Array[String],
    clusters: Map[String, ClusterInfo]
  ): String = {
    // Identify phantom cluster node IDs so they are excluded from bounds
    // computation and node rendering (they are 0x0 layout placeholders,
    // not real graph nodes).
    val clusterIds = clusters.keySet

    // Compute bounds from real nodes only (exclude phantom cluster nodes)
    var minX = Double.MaxValue
    var minY = Double.MaxValue
    var maxX = Double.MinValue
    var maxY = Double.MinValue
    for (id <- nodes)
      if (!clusterIds.contains(id)) {
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

    // Render cluster boxes FIRST (behind edges and nodes — correct z-order,
    // matching Graphviz which draws clusters under their contents)
    if (clusters.nonEmpty) {
      renderClusters(content, g, clusters, config)
    }

    // Render edges (below nodes but above cluster boxes)
    val edgeGroup = content.append("g")
    edgeGroup.attr("class", "edges")
    for (edgeObj <- g.edges()) {
      val el = g.edge(edgeObj)
      renderEdge(edgeGroup, g, edgeObj, el, dot.isDirected, dotAttrs)
    }

    // Render real nodes only (suppress phantom cluster nodes)
    val nodeGroup = content.append("g")
    nodeGroup.attr("class", "nodes")
    for (id <- nodes)
      if (!clusterIds.contains(id)) {
        val nl = g.node(id)
        renderNode(nodeGroup, id, nl, config, dotAttrs)
      }

    // Render graph-level title/label
    renderGraphLabel(content, dot, config, svgWidth, svgHeight, marginX, marginY)

    svg.build().toMarkup()
  }

  // -- Cluster rendering -------------------------------------------------------

  /** Renders cluster subgraph boxes and labels. Each cluster is drawn as a rectangle enclosing its member nodes, with an optional label at the top.
    *
    * Per Graphviz semantics, only subgraphs whose name starts with "cluster" are drawn as boxed regions. The box bounds are computed from the laid-out positions of the cluster's member nodes,
    * expanded by a margin.
    */
  private def renderClusters(
    parent:   SvgBuilder,
    g:        Graph[NodeLabel, EdgeLabel],
    clusters: Map[String, ClusterInfo],
    config:   GraphvizConfig
  ): Unit =
    for ((_, cluster) <- clusters) {
      // Collect all member node positions (only real nodes, not nested
      // cluster phantom nodes)
      val memberNodes = cluster.members.filter { memberId =>
        g.hasNode(memberId) && !clusters.contains(memberId)
      }
      // Skip clusters with no renderable member nodes
      if (memberNodes.nonEmpty) {
        renderSingleCluster(parent, g, cluster, memberNodes, config)
      }
    }

  /** Renders a single cluster's box and label. */
  private def renderSingleCluster(
    parent:      SvgBuilder,
    g:           Graph[NodeLabel, EdgeLabel],
    cluster:     ClusterInfo,
    memberNodes: Seq[String],
    config:      GraphvizConfig
  ): Unit = {
    // Compute bounding box from member nodes' laid-out positions
    var cMinX = Double.MaxValue
    var cMinY = Double.MaxValue
    var cMaxX = Double.MinValue
    var cMaxY = Double.MinValue
    for (memberId <- memberNodes) {
      val nl     = g.node(memberId)
      val left   = nl.x - nl.width / 2.0
      val top    = nl.y - nl.height / 2.0
      val right  = nl.x + nl.width / 2.0
      val bottom = nl.y + nl.height / 2.0
      if (left < cMinX) { cMinX = left }
      if (top < cMinY) { cMinY = top }
      if (right > cMaxX) { cMaxX = right }
      if (bottom > cMaxY) { cMaxY = bottom }
    }

    // Expand by cluster margin
    val margin = ClusterMargin
    cMinX -= margin
    cMinY -= margin
    cMaxX += margin
    cMaxY += margin

    // Resolve cluster styling from its attributes
    val attrs     = cluster.attrs
    val styleAttr = attrs.getOrElse("style", "")
    val isFilled  = styleAttr.contains("filled")

    // Graphviz cluster fill semantics:
    //   - fillcolor takes precedence if set
    //   - bgcolor is used if fillcolor is not set
    //   - color is used as fill if style=filled and neither fillcolor nor bgcolor is set
    //   - If not filled, fill is "none"
    val fillColor = if (isFilled) {
      attrs.getOrElse("fillcolor", attrs.getOrElse("bgcolor", attrs.getOrElse("color", "lightgrey")))
    } else {
      attrs.getOrElse("bgcolor", "none")
    }
    // Border color: pencolor > color > default black
    val strokeColor = attrs.getOrElse("pencolor", attrs.getOrElse("color", "black"))
    val fontColor   = attrs.getOrElse("fontcolor", "#333")
    val fontSizeStr = attrs.getOrElse("fontsize", ClusterLabelFontSize.toString)
    val fontName    = attrs.getOrElse("fontname", config.fontName)

    // Reserve space at the top for the label if present
    val labelText = cluster.label.map { raw =>
      HtmlLabelUtil.stripHtmlTags(raw)
    }
    val labelHeight = if (labelText.exists(_.nonEmpty)) { ClusterLabelFontSize + 4.0 }
    else { 0.0 }
    cMinY -= labelHeight

    // Render the cluster group
    val clusterG = parent.append("g")
    clusterG.attr("class", "cluster")

    // Cluster box (rect)
    val rect = clusterG.append("rect")
    rect.attr("x", cMinX)
    rect.attr("y", cMinY)
    rect.attr("width", cMaxX - cMinX)
    rect.attr("height", cMaxY - cMinY)
    rect.attr("fill", fillColor)
    rect.attr("stroke", strokeColor)
    applyStrokeDash(rect, styleAttr)

    // Cluster label (top-center, just inside the top edge)
    labelText.foreach { lt =>
      if (lt.nonEmpty) {
        val text = clusterG.append("text")
        text.attr("x", (cMinX + cMaxX) / 2.0)
        text.attr("y", cMinY + ClusterLabelFontSize + 2.0)
        text.attr("text-anchor", "middle")
        text.attr("font-size", fontSizeStr)
        text.attr("font-family", fontName)
        text.attr("fill", fontColor)
        text.text(lt)
      }
    }
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

    // Collect the graph-level label from assign statements;
    // strip HTML tags for HTML-like labels (ISS-1078)
    val graphLabel = dot.stmts.collectFirst { case DotAssignStmt("label", v, isHtml) =>
      if (isHtml) { HtmlLabelUtil.stripHtmlTags(v) }
      else { v }
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

    // Resolve label text; strip HTML tags for HTML-like labels (ISS-1078)
    val rawLabelText = nodeAttrs.getOrElse("label", nl.label)
    val labelText    = if (isHtmlLabel(dotAttrs, id)) { HtmlLabelUtil.stripHtmlTags(rawLabelText) }
    else { rawLabelText }

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

    // Edge label (if present); strip HTML tags for HTML-like labels (ISS-1078)
    val rawEdgeLabelText = edgeOverrides.getOrElse("label", dotAttrs.edgeDefaults.getOrElse("label", ""))
    val edgeLabelText    = if (isHtmlEdgeLabel(dotAttrs, edgeKey)) { HtmlLabelUtil.stripHtmlTags(rawEdgeLabelText) }
    else { rawEdgeLabelText }
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
