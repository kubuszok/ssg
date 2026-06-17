/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/flowchart/flowRenderer-v3-unified.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from FlowchartDb + config → SVG string; dagre layout for positioning
 *   Renames: flowRenderer-v3-unified draw() → FlowchartRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package flowchart

import lowlevel.Nullable
import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.layout.dagre.{ DagreLayout, EdgeLabel, GraphLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.Graph
import ssg.mermaid.render.clusters.{ ClusterConfig, ClusterRenderer }
import ssg.mermaid.render.edges.{ ArrowMarkers, EdgeRenderer, EdgeStyle, MarkerType }
import ssg.mermaid.render.shapes.{ ShapeConfig, ShapeRegistry }
import ssg.mermaid.render.text.{ TextMetrics, TextUtils }
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a flowchart diagram to SVG.
  *
  * Takes a populated [[FlowchartDb]] and produces a complete SVG string. The rendering pipeline:
  *   1. Build a dagre [[Graph]] from the flowchart nodes and edges
  *   1. Set node dimensions based on text measurement + shape padding
  *   1. Run [[DagreLayout.layout]] to compute positions
  *   1. Create SVG root with viewBox
  *   1. Render each node using [[ShapeRegistry]]
  *   1. Render each edge with [[EdgeRenderer]] and [[ArrowMarkers]]
  *   1. Render subgraphs with [[ClusterRenderer]]
  *   1. Add theme styles
  *   1. Return SVG string
  */
object FlowchartRenderer {

  /** Diagram padding around the entire flowchart. */
  private val DiagramPadding: Double = 8.0

  /** Renders a flowchart diagram to an SVG string.
    *
    * @param db
    *   the populated flowchart database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: FlowchartDb, config: MermaidConfig): String = {
    val padding = config.flowchart.padding.toDouble
    // 1. Build dagre graph
    val g = buildDagreGraph(db, config, padding)

    // 2. Run layout
    DagreLayout.layout(g)

    // 3. Get computed graph dimensions
    val gl        = g.graph[GraphLabel]()
    val svgWidth  = gl.width + DiagramPadding * 2
    val svgHeight = gl.height + DiagramPadding * 2

    // 4. Create SVG root
    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "flowchart-v2", db.accTitle, db.accDescription)

    // Add unique marker ID
    val markerId = "flowchart-marker"

    // 5. Add <defs> with markers and styles
    val defs = svg.append("defs")
    ArrowMarkers.createMarkers(defs, markerId, "#333", "#333")

    // Add styles
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = FlowchartStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    // 6. Create main group with diagram padding offset
    val mainGroup = svg.append("g")
    mainGroup.attr("transform", s"translate($DiagramPadding, $DiagramPadding)")

    // 7. Render subgraphs (clusters) — render first so nodes overlay
    renderSubgraphs(mainGroup, db, g, themeVars, config)

    // 8. Render edges
    renderEdges(mainGroup, db, g, config, markerId)

    // 9. Render nodes
    renderNodes(mainGroup, db, g, config, padding)

    // 10. Render title if present
    if (db.title.nonEmpty) {
      renderTitle(mainGroup, db.title, svgWidth, themeVars)
    }

    // Build and serialize
    svg.build().toMarkup()
  }

  /** Builds a dagre graph from the flowchart database.
    *
    * Sets up node labels with dimensions estimated from text, and edge labels with edge properties.
    */
  private def buildDagreGraph(db: FlowchartDb, config: MermaidConfig, padding: Double): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = db.direction
    gl.nodesep = config.flowchart.nodeSpacing
    gl.ranksep = config.flowchart.rankSpacing
    gl.marginx = DiagramPadding
    gl.marginy = DiagramPadding
    g.setGraph(gl)

    // Build a map of subgraph containment for compound graph support
    val nodeToSubgraph = mutable.Map.empty[String, String]

    // Add subgraph nodes
    for (sg <- db.subgraphs) {
      val label     = new NodeLabel
      val titleBBox = TextMetrics.measureText(sg.title, 14, "sans-serif", "bold")
      label.width = math.max(titleBBox.width + padding * 2, 50)
      label.height = math.max(titleBBox.height + padding * 2, 40)
      label.label = sg.title
      g.setNode(sg.id, label)

      // Track node containment
      for (nid <- sg.nodeIds)
        nodeToSubgraph(nid) = sg.id
    }

    // Add nodes
    for ((id, node) <- db.nodes) {
      val label    = new NodeLabel
      val textBBox = TextMetrics.measureText(node.text, 16, "sans-serif")
      label.width = textBBox.width + padding * 2
      label.height = textBBox.height + padding * 2

      // Adjust dimensions for specific shapes
      node.shape match {
        case "circle" | "doublecircle" =>
          val diameter = math.max(label.width, label.height)
          label.width = diameter
          label.height = diameter
        case "diamond" =>
          // Diamonds need extra space for the rotated bounding box
          label.width = label.width * 1.4
          label.height = label.height * 1.4
        case "hexagon" =>
          label.width = label.width + padding
        case _ => ()
      }

      label.label = node.text

      // Ensure minimum dimensions to avoid degenerate layout
      label.width = math.max(label.width, 40)
      label.height = math.max(label.height, 30)

      g.setNode(id, label)

      // Set parent if this node is inside a subgraph
      nodeToSubgraph.get(id).foreach { sgId =>
        g.setParent(id, sgId)
      }
    }

    // Add edges
    for ((edge, idx) <- db.edges.zipWithIndex) {
      val label = new EdgeLabel
      label.minlen = edge.length
      label.weight = 1

      // Set edge label dimensions if present
      if (edge.label.nonEmpty) {
        val labelBBox = TextMetrics.measureText(edge.label, 12, "sans-serif")
        label.width = labelBBox.width + 10
        label.height = labelBBox.height + 10
        label.labelpos = "c"
        label.labeloffset = 10
      }

      val edgeName = s"L-${edge.src}-${edge.dst}-$idx"
      g.setEdge(edge.src, edge.dst, label, edgeName)
    }

    g
  }

  /** Renders all nodes in the graph. */
  private def renderNodes(
    parent:  SvgBuilder,
    db:      FlowchartDb,
    g:       Graph[NodeLabel, EdgeLabel],
    config:  MermaidConfig,
    padding: Double
  ): Unit =
    for ((id, node) <- db.nodes) {
      val nodeLabel = g.nodeOpt(id)
      if (nodeLabel.isEmpty) {
        // Node might have been inside a subgraph that was removed — skip
      } else {
        val nl        = nodeLabel.get
        val shapeName = mapShapeName(node.shape)

        val shapeConfig = ShapeConfig(
          id = id,
          x = nl.x,
          y = nl.y,
          width = nl.width,
          height = nl.height,
          label = node.text,
          rx = if (shapeName == "roundedRect") 5 else 0,
          ry = if (shapeName == "roundedRect") 5 else 0,
          cssClass = buildNodeCssClass(node),
          style = node.styles.mkString("; "),
          padding = padding,
          // shapes/util.js:9 — node.useHtmlLabels || evaluate(flowchart.htmlLabels).
          // SSG has no per-node useHtmlLabels, so resolve from flowchart.htmlLabels.
          htmlLabels = TextUtils.evaluate(config.flowchart.htmlLabels),
          securityLevel = config.securityLevel
        )

        // Add link when appropriate (nodes.js:67-80). When a node has a link,
        // the node group is rendered INTO an <a xlink:href=... target=...>
        // element inserted into the parent, instead of directly into the parent.
        val groupParent =
          if (node.link.isDefined) {
            val target =
              if (config.securityLevel == "sandbox") {
                "_top"
              } else {
                node.linkTarget.fold("_blank")(t => if (t.nonEmpty) t else "_blank")
              }
            val anchor = parent.append("a")
            anchor.attr("xlink:href", node.link.get)
            anchor.attr("target", target)
            anchor
          } else {
            parent
          }

        val nodeGroup = groupParent.append("g")
        nodeGroup.classed("node", true)
        nodeGroup.classed("default", true)
        node.cssClasses.foreach(cls => nodeGroup.classed(cls, true))
        nodeGroup.attr("id", s"flowchart-$id")
        nodeGroup.attr("transform", s"translate(${nl.x}, ${nl.y})")

        ShapeRegistry.render(nodeGroup, shapeName, shapeConfig)
      }
    }

  /** Renders all edges in the graph. */
  private def renderEdges(
    parent:   SvgBuilder,
    db:       FlowchartDb,
    g:        Graph[NodeLabel, EdgeLabel],
    config:   MermaidConfig,
    markerId: String
  ): Unit = {
    val graphEdges = g.edges()

    for ((flowEdge, idx) <- db.edges.zipWithIndex) {
      // Find matching dagre edge
      val edgeName  = s"L-${flowEdge.src}-${flowEdge.dst}-$idx"
      val dagreEdge = graphEdges.find { e =>
        e.v == flowEdge.src && e.w == flowEdge.dst && e.name.exists(_ == edgeName)
      }

      dagreEdge.foreach { de =>
        val edgeLabel = g.edge(de)
        val points    = if (edgeLabel.points.nonEmpty) {
          edgeLabel.points
        } else {
          // Fallback: straight line between nodes
          val srcNode = g.node(flowEdge.src)
          val dstNode = g.node(flowEdge.dst)
          Array(Point(srcNode.x, srcNode.y), Point(dstNode.x, dstNode.y))
        }

        val style = buildEdgeStyle(flowEdge, edgeLabel, config, idx)
        EdgeRenderer.renderEdge(parent, points, style, markerId)
      }
    }
  }

  /** Renders subgraphs as clusters. */
  private def renderSubgraphs(
    parent:    SvgBuilder,
    db:        FlowchartDb,
    g:         Graph[NodeLabel, EdgeLabel],
    themeVars: ssg.mermaid.theme.ThemeVariables,
    config:    MermaidConfig
  ): Unit =
    // Render in reverse order so outer subgraphs are behind inner ones
    for (sg <- db.subgraphs.reverseIterator) {
      val nodeLabel = g.nodeOpt(sg.id)
      nodeLabel.foreach { nl =>
        val clusterConfig = ClusterConfig(
          id = s"cluster-${sg.id}",
          x = nl.x,
          y = nl.y,
          width = nl.width,
          height = nl.height,
          title = sg.title,
          backgroundColor = themeVars.clusterBkg,
          borderColor = themeVars.clusterBorder,
          rx = 5,
          ry = 5,
          cssClass = sg.cssClasses.mkString(" "),
          htmlLabels = TextUtils.evaluate(config.flowchart.htmlLabels),
          securityLevel = config.securityLevel
        )
        ClusterRenderer.renderRoundedCluster(parent, clusterConfig)
      }
    }

  /** Renders a diagram title. */
  private def renderTitle(parent: SvgBuilder, title: String, svgWidth: Double, themeVars: ssg.mermaid.theme.ThemeVariables): Unit = {
    val titleGroup = parent.append("g")
    titleGroup.classed("flowchartTitleText", true)

    val text = titleGroup.append("text")
    text.attr("x", svgWidth / 2.0)
    text.attr("y", 20)
    text.attr("text-anchor", "middle")
    text.attr("dominant-baseline", "auto")
    text.classed("flowchartTitleText", true)
    text.text(title)
  }

  /** Maps flowchart shape names to ShapeRegistry names. */
  private def mapShapeName(shape: String): String =
    shape match {
      case "square"        => "rect"
      case "round"         => "roundedRect"
      case "stadium"       => "stadium"
      case "subroutine"    => "subroutine"
      case "cylinder"      => "cylinder"
      case "circle"        => "circle"
      case "doublecircle"  => "doublecircle"
      case "diamond"       => "diamond"
      case "hexagon"       => "hexagon"
      case "odd"           => "rect" // flag/asymmetric → fallback to rect
      case "trapezoid"     => "trapezoid"
      case "inv_trapezoid" => "trapezoid" // inverted trapezoid uses same shape
      case "lean_right"    => "rect" // parallelogram → fallback
      case "lean_left"     => "rect" // parallelogram → fallback
      case "ellipse"       => "ellipse"
      case _               => "rect" // default fallback
    }

  /** Builds a CSS class string for a node. */
  private def buildNodeCssClass(node: FlowNode): String = {
    val sb = new StringBuilder("node default")
    for (cls <- node.cssClasses) {
      sb.append(" ")
      sb.append(cls)
    }
    sb.toString
  }

  /** Builds an [[EdgeStyle]] from a flow edge and dagre edge label. */
  private def buildEdgeStyle(
    edge:      FlowEdge,
    edgeLabel: EdgeLabel,
    config:    MermaidConfig,
    index:     Int
  ): EdgeStyle = {
    val strokeDash = edge.stroke match {
      case "dotted" => "3"
      case "thick"  => ""
      case _        => ""
    }

    val markerEnd   = resolveMarkerType(edge.edgeType.getOrElse("arrow_point"))
    val markerStart = resolveStartMarkerType(edge.edgeType.getOrElse("arrow_point"))

    EdgeStyle(
      id = s"L-${edge.src}-${edge.dst}-$index",
      stroke = "#333",
      strokeWidth = if (edge.stroke == "thick") 3.0 else 1.5,
      strokeDasharray = strokeDash,
      cssClass = FlowchartStyles.edgeClass(edge.stroke),
      curve = config.flowchart.curve,
      markerStart = markerStart,
      markerEnd = markerEnd,
      labelText = edge.label,
      labelX = edgeLabel.x,
      labelY = edgeLabel.y,
      thickness = edge.stroke,
      // edges.js:22 — useHtmlLabels = evaluate(config.flowchart.htmlLabels) (same as node labels).
      htmlLabels = TextUtils.evaluate(config.flowchart.htmlLabels),
      securityLevel = config.securityLevel
    )
  }

  /** Resolves an edge type string to a MarkerType for the end marker. */
  private def resolveMarkerType(edgeType: String): Nullable[MarkerType] =
    edgeType match {
      case "arrow_point"         => Nullable(MarkerType.Normal)
      case "arrow_cross"         => Nullable(MarkerType.Cross)
      case "arrow_circle"        => Nullable(MarkerType.Circle)
      case "double_arrow_point"  => Nullable(MarkerType.Normal)
      case "double_arrow_cross"  => Nullable(MarkerType.Cross)
      case "double_arrow_circle" => Nullable(MarkerType.Circle)
      case "arrow_open"          => Nullable.empty
      case _                     => Nullable(MarkerType.Normal)
    }

  /** Resolves an edge type string to a MarkerType for the start marker. */
  private def resolveStartMarkerType(edgeType: String): Nullable[MarkerType] =
    edgeType match {
      case "double_arrow_point"  => Nullable(MarkerType.Normal)
      case "double_arrow_cross"  => Nullable(MarkerType.Cross)
      case "double_arrow_circle" => Nullable(MarkerType.Circle)
      case _                     => Nullable.empty
    }
}
