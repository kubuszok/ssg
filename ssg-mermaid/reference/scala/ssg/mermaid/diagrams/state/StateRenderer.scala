/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/state/stateRenderer-v3-unified.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from StateDb + config -> SVG string; dagre layout for positioning
 *   Renames: stateRenderer-v3-unified draw() -> StateRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package state

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.layout.dagre.{ DagreLayout, EdgeLabel, GraphLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.Graph
import ssg.mermaid.render.labels.HtmlLabelHelper
import ssg.mermaid.render.text.{ TextMetrics, TextUtils }
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a state diagram to SVG.
  *
  * Takes a populated [[StateDb]] and produces a complete SVG string. Uses dagre for layout, with composite states rendered as clusters.
  */
object StateRenderer {

  private val DiagramPadding: Double = 8.0
  private val StatePadding:   Double = 15.0
  private val StartEndRadius: Double = 7.0
  private val ForkWidth:      Double = 70.0
  private val ForkHeight:     Double = 7.0
  // Concurrency divider dimensions (ports the `drawDivider` line geometry from shapes.js, where the
  // line runs from `state.textHeight` to `state.textHeight * 2`; textHeight defaults to 10).
  private val DividerWidth:  Double = 10.0
  private val DividerHeight: Double = 1.0

  /** Renders a state diagram to an SVG string.
    *
    * @param db
    *   the populated state diagram database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: StateDb, config: MermaidConfig): String = {
    // Build dagre graph
    val g = buildDagreGraph(db)

    // Run layout
    DagreLayout.layout(g)

    // Get computed dimensions
    val gl        = g.graph[GraphLabel]()
    val svgWidth  = gl.width + DiagramPadding * 2
    val svgHeight = gl.height + DiagramPadding * 2

    // Create SVG
    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "stateDiagram", db.accTitle, db.accDescription)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = StateStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    // Add arrow marker
    addArrowMarker(defs)

    // Main group
    val mainGroup = svg.append("g")
    mainGroup.attr("transform", s"translate($DiagramPadding, $DiagramPadding)")

    // Render composite state backgrounds (clusters)
    renderCompositeStates(mainGroup, db, g)

    // Render transitions (edges)
    renderTransitions(mainGroup, db, g, config)

    // Render states (nodes)
    renderStates(mainGroup, db, g, config)

    // Title
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", gl.width / 2.0)
      titleText.attr("y", -5)
      titleText.attr("text-anchor", "middle")
      titleText.classed("stateTitleText", true)
      titleText.text(db.title)
    }

    svg.build().toMarkup()
  }

  /** Builds a dagre graph from the state diagram database. */
  private def buildDagreGraph(db: StateDb): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = db.direction
    gl.nodesep = 50
    gl.ranksep = 50
    gl.marginx = DiagramPadding
    gl.marginy = DiagramPadding
    g.setGraph(gl)

    // Add state nodes
    for ((id, state) <- db.states) {
      val label = new NodeLabel
      val desc  = if (state.description.nonEmpty) state.description else id

      state.stateType match {
        case StateType.Start | StateType.End =>
          label.width = StartEndRadius * 2
          label.height = StartEndRadius * 2
        case StateType.Fork | StateType.Join =>
          label.width = ForkWidth
          label.height = ForkHeight
        case StateType.Choice =>
          label.width = 28
          label.height = 28
        case StateType.Divider =>
          // Concurrency divider: a thin dashed separator line between concurrent regions.
          // Ports `drawDivider()` from shapes.js (a short grey dashed `line`).
          label.width = DividerWidth
          label.height = DividerHeight
        case _ =>
          val textBBox = TextMetrics.measureText(desc, 14, "sans-serif")
          label.width = math.max(textBBox.width + StatePadding * 2, 50)
          label.height = math.max(textBBox.height + StatePadding * 2, 30)
      }

      label.label = desc
      g.setNode(id, label)
    }

    // Set parent-child relationships for composite states
    for ((id, state) <- db.states)
      for (childId <- state.children)
        if (db.states.contains(childId)) {
          g.setParent(childId, id)
        }

    // Add transition edges
    for ((transition, idx) <- db.transitions.zipWithIndex) {
      val label = new EdgeLabel
      label.minlen = 1
      label.weight = 1

      if (transition.label.nonEmpty) {
        val labelBBox = TextMetrics.measureText(transition.label, 12, "sans-serif")
        label.width = labelBBox.width + 10
        label.height = labelBBox.height + 10
        label.labelpos = "c"
        label.labeloffset = 10
      }

      val edgeName = s"T-${transition.from}-${transition.to}-$idx"
      g.setEdge(transition.from, transition.to, label, edgeName)
    }

    g
  }

  /** Renders all state nodes. */
  private def renderStates(parent: SvgBuilder, db: StateDb, g: Graph[NodeLabel, EdgeLabel], config: MermaidConfig): Unit =
    for ((id, state) <- db.states) {
      val nodeLabel = g.nodeOpt(id)
      if (nodeLabel.isDefined) {
        val nl         = nodeLabel.get
        val stateGroup = parent.append("g")
        stateGroup.classed("stateGroup", true)
        stateGroup.attr("transform", s"translate(${nl.x}, ${nl.y})")

        state.stateType match {
          case StateType.Start =>
            renderStartState(stateGroup, nl)
          case StateType.End =>
            renderEndState(stateGroup, nl)
          case StateType.Fork | StateType.Join =>
            renderForkJoin(stateGroup, nl)
          case StateType.Choice =>
            renderChoice(stateGroup, nl)
          case StateType.Divider =>
            renderDivider(stateGroup, nl)
          case _ =>
            renderDefaultState(stateGroup, state, nl, config)
        }
      }
    }

  /** Renders a start state (filled circle). */
  private def renderStartState(g: SvgBuilder, nl: NodeLabel): Unit = {
    val circle = g.append("circle")
    circle.attr("r", StartEndRadius)
    circle.attr("cx", 0)
    circle.attr("cy", 0)
    circle.classed("start-state", true)
    circle.style("fill", "black")
  }

  /** Renders an end state (filled circle with ring). */
  private def renderEndState(g: SvgBuilder, nl: NodeLabel): Unit = {
    val outer = g.append("circle")
    outer.attr("r", StartEndRadius + 3)
    outer.attr("cx", 0)
    outer.attr("cy", 0)
    outer.style("fill", "none")
    outer.style("stroke", "black")
    outer.style("stroke-width", "1")

    val inner = g.append("circle")
    inner.attr("r", StartEndRadius)
    inner.attr("cx", 0)
    inner.attr("cy", 0)
    inner.classed("end-state-inner", true)
    inner.style("fill", "black")
  }

  /** Renders a fork or join bar. */
  private def renderForkJoin(g: SvgBuilder, nl: NodeLabel): Unit = {
    val rect = g.append("rect")
    rect.attr("x", -ForkWidth / 2.0)
    rect.attr("y", -ForkHeight / 2.0)
    rect.attr("width", ForkWidth)
    rect.attr("height", ForkHeight)
    rect.attr("rx", 1)
    rect.attr("ry", 1)
    rect.style("fill", "black")
  }

  /** Renders a choice diamond. */
  private def renderChoice(g: SvgBuilder, nl: NodeLabel): Unit = {
    val size = 14.0
    val path = g.append("polygon")
    path.attr("points", s"0,${-size} $size,0 0,$size ${-size},0")
    path.style("fill", "#f0f0f0")
    path.style("stroke", "black")
    path.style("stroke-width", "1")
  }

  /** Renders a standalone divider visual to an SVG string.
    *
    * Exposes the [[renderDivider]] drawing (the `drawDivider()` line from shapes.js) for differential testing of the divider visual in isolation from compound-cluster layout. Produces a `<g>` holding
    * the grey dashed `line.divider` sized to a default divider node.
    */
  def renderDividerSvg: String = {
    val svg = SvgBuilder.createSvg("0 0 20 4")
    val nl  = new NodeLabel
    nl.width = DividerWidth
    nl.height = DividerHeight
    val group = svg.append("g")
    group.classed("stateGroup", true)
    renderDivider(group, nl)
    svg.build().toMarkup()
  }

  /** Renders a concurrency divider (the dashed line separating concurrent regions).
    *
    * Ports `drawDivider()` from `shapes.js`:
    * {{{
    *   g.append('line')
    *     .style('stroke', 'grey')
    *     .style('stroke-dasharray', '3')
    *     .attr('x1', textHeight).attr('x2', textHeight * 2)
    *     .attr('y1', 0).attr('y2', 0)
    *     .attr('class', 'divider');
    * }}}
    * The node is centred at `(nl.x, nl.y)` by the enclosing `translate`, so the line is drawn symmetrically about the origin.
    */
  private def renderDivider(g: SvgBuilder, nl: NodeLabel): Unit = {
    val half = nl.width / 2.0
    val line = g.append("line")
    line.attr("x1", -half)
    line.attr("x2", half)
    line.attr("y1", 0)
    line.attr("y2", 0)
    line.classed("divider", true)
    line.style("stroke", "grey")
    line.style("stroke-dasharray", "3")
  }

  /** Renders a default state box with description. */
  private def renderDefaultState(g: SvgBuilder, state: StateNode, nl: NodeLabel, config: MermaidConfig): Unit = {
    val w = nl.width
    val h = nl.height

    val rect = g.append("rect")
    rect.attr("x", -w / 2.0)
    rect.attr("y", -h / 2.0)
    rect.attr("width", w)
    rect.attr("height", h)
    rect.attr("rx", 5)
    rect.attr("ry", 5)
    rect.classed("statebox", true)
    for (cls <- state.cssClasses)
      rect.classed(cls, true)
    if (state.styles.nonEmpty) {
      rect.attr("style", state.styles.mkString("; "))
    }

    // State label
    val desc       = if (state.description.nonEmpty) state.description else state.id
    val htmlLabels = TextUtils.evaluate(config.htmlLabels)
    if (htmlLabels) {
      // HTML label (ISS-1205): foreignObject for the state node label.
      val labelGroup = g.append("g")
      labelGroup.classed("label", true)
      labelGroup.classed("stateLabel", true)
      labelGroup.attr("transform", "translate(0,4)")
      val sanitized = TextUtils.sanitizeTextHtml(desc, config.securityLevel, htmlLabels)
      HtmlLabelHelper.createText(
        el = labelGroup,
        text = sanitized,
        useHtmlLabels = true,
        isNode = true,
        classes = "",
        width = w,
        style = "",
        addBackground = false
      )
      ()
    } else {
      val text = g.append("text")
      text.attr("x", 0)
      text.attr("y", 4)
      text.attr("text-anchor", "middle")
      text.classed("stateLabel", true)
      text.text(desc)
    }

    // Render note if present
    state.note.foreach { noteText =>
      val noteWidth  = TextMetrics.measureText(noteText, 12, "sans-serif").width + 20
      val noteHeight = TextMetrics.measureText(noteText, 12, "sans-serif").height + 10
      val noteX      = if (state.notePosition == "left of") -w / 2.0 - noteWidth - 10 else w / 2.0 + 10
      val noteY      = -h / 2.0

      val noteRect = g.append("rect")
      noteRect.attr("x", noteX)
      noteRect.attr("y", noteY)
      noteRect.attr("width", noteWidth)
      noteRect.attr("height", noteHeight)
      noteRect.attr("rx", 0)
      noteRect.attr("ry", 0)
      noteRect.classed("note", true)

      val noteLabel = g.append("text")
      noteLabel.attr("x", noteX + 10)
      noteLabel.attr("y", noteY + noteHeight / 2.0 + 4)
      noteLabel.classed("noteText", true)
      noteLabel.text(noteText)
    }
  }

  /** Renders composite state backgrounds. */
  private def renderCompositeStates(parent: SvgBuilder, db: StateDb, g: Graph[NodeLabel, EdgeLabel]): Unit =
    for ((id, state) <- db.states)
      if (state.children.nonEmpty) {
        val nodeLabel = g.nodeOpt(id)
        nodeLabel.foreach { nl =>
          val clusterGroup = parent.append("g")
          clusterGroup.classed("cluster", true)

          val rect = clusterGroup.append("rect")
          rect.attr("x", nl.x - nl.width / 2.0)
          rect.attr("y", nl.y - nl.height / 2.0)
          rect.attr("width", nl.width)
          rect.attr("height", nl.height)
          rect.attr("rx", 5)
          rect.attr("ry", 5)
          rect.classed("compositeState", true)

          val desc  = if (state.description.nonEmpty) state.description else id
          val title = clusterGroup.append("text")
          title.attr("x", nl.x)
          title.attr("y", nl.y - nl.height / 2.0 + 15)
          title.attr("text-anchor", "middle")
          title.classed("compositeLabel", true)
          title.text(desc)
        }
      }

  /** Renders all transitions. */
  private def renderTransitions(parent: SvgBuilder, db: StateDb, g: Graph[NodeLabel, EdgeLabel], config: MermaidConfig): Unit = {
    val graphEdges = g.edges()

    for ((transition, idx) <- db.transitions.zipWithIndex) {
      val edgeName  = s"T-${transition.from}-${transition.to}-$idx"
      val dagreEdge = graphEdges.find { e =>
        e.v == transition.from && e.w == transition.to && e.name.exists(_ == edgeName)
      }

      dagreEdge.foreach { de =>
        val edgeLabel = g.edge(de)
        val points    = if (edgeLabel.points.nonEmpty) {
          edgeLabel.points
        } else {
          val srcNode = g.node(transition.from)
          val dstNode = g.node(transition.to)
          Array(Point(srcNode.x, srcNode.y), Point(dstNode.x, dstNode.y))
        }

        val pathData = new StringBuilder("M ")
        for ((pt, i) <- points.zipWithIndex)
          if (i == 0) pathData.append(s"${pt.x} ${pt.y}")
          else pathData.append(s" L ${pt.x} ${pt.y}")

        val edgeGroup = parent.append("g")
        edgeGroup.classed("transition", true)

        val path = edgeGroup.append("path")
        path.attr("d", pathData.toString)
        path.classed("transition", true)
        path.style("fill", "none")
        path.style("stroke", "#333")
        path.style("stroke-width", "1.5")
        path.attr("marker-end", "url(#state-arrow)")

        // Label
        if (transition.label.nonEmpty) {
          val midIdx = points.length / 2
          val labelX = points(midIdx).x
          val labelY = points(midIdx).y - 5

          // htmlLabels resolution mirrors the state node path (config.htmlLabels). The unified
          // v3 renderer routes edge labels through the dagre-wrapper `insertEdgeLabel`
          // (edges.js:20-54) which emits a `<foreignObject>` with `<span class="edgeLabel">`
          // (isNode=false) inside `<g class="edgeLabel"> > <g class="label">` when htmlLabels is on.
          val htmlLabels = TextUtils.evaluate(config.htmlLabels)
          if (htmlLabels) {
            val edgeLabel = edgeGroup.append("g")
            edgeLabel.classed("edgeLabel", true)
            val labelGroup = edgeLabel.append("g")
            labelGroup.classed("label", true)
            labelGroup.attr("transform", s"translate(${fmtCoord(labelX)},${fmtCoord(labelY)})")
            val sanitized = TextUtils.sanitizeTextHtml(transition.label, config.securityLevel, htmlLabels)
            HtmlLabelHelper.createText(
              el = labelGroup,
              text = sanitized,
              useHtmlLabels = true,
              isNode = false,
              classes = "",
              width = 200.0,
              style = "",
              addBackground = true
            )
            ()
          } else {
            val labelBg = edgeGroup.append("rect")
            val bbox    = TextMetrics.measureText(transition.label, 12, "sans-serif")
            labelBg.attr("x", labelX - bbox.width / 2.0 - 3)
            labelBg.attr("y", labelY - bbox.height + 2)
            labelBg.attr("width", bbox.width + 6)
            labelBg.attr("height", bbox.height + 4)
            labelBg.style("fill", "white")
            labelBg.style("stroke", "none")

            val label = edgeGroup.append("text")
            label.attr("x", labelX)
            label.attr("y", labelY)
            label.attr("text-anchor", "middle")
            label.attr("font-size", "12")
            label.classed("transitionLabel", true)
            label.text(transition.label)
            ()
          }
        }
      }
    }
  }

  /** Adds an arrow marker for transition endpoints. */
  private def addArrowMarker(defs: SvgBuilder): Unit = {
    val marker = defs.append("marker")
    marker.attr("id", "state-arrow")
    marker.attr("viewBox", "0 0 10 10")
    marker.attr("refX", 9)
    marker.attr("refY", 5)
    marker.attr("markerWidth", 6)
    marker.attr("markerHeight", 6)
    marker.attr("orient", "auto")
    val path = marker.append("path")
    path.attr("d", "M 0 0 L 10 5 L 0 10 z")
    path.style("fill", "#333")
  }

  /** Formats a coordinate without a trailing `.0` for integral values. */
  private def fmtCoord(v: Double): String =
    if (v == v.toLong.toDouble) v.toLong.toString else v.toString
}
