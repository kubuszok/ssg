/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/class/classRenderer-v2.ts
 *              mermaid/packages/mermaid/src/diagrams/class/svgDraw.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + dagre-d3 rendering with SvgBuilder + dagre layout
 *   Idiom: Pure function from ClassDb + config → SVG string; dagre for layout
 *   Renames: classRenderer-v2 draw() → ClassRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package class_

import lowlevel.Nullable
import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.layout.dagre.{ DagreLayout, EdgeLabel, GraphLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.Graph
import ssg.mermaid.render.labels.HtmlLabelHelper
import ssg.mermaid.render.text.{ TextMetrics, TextUtils }
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme, ThemeVariables }

import scala.collection.mutable

/** Renders a class diagram to SVG.
  *
  * Takes a populated [[ClassDb]] and produces a complete SVG string. Uses dagre for layout with custom node rendering (3-compartment class boxes).
  *
  * Rendering pipeline:
  *   1. Build dagre graph from class nodes and relations
  *   1. Compute node dimensions (name + members + methods compartments)
  *   1. Run dagre layout
  *   1. Render each class node as a 3-compartment box
  *   1. Render each relation as an edge with appropriate markers
  *   1. Render namespaces as cluster backgrounds
  *   1. Render notes
  *   1. Add styles and return SVG
  */
object ClassRenderer {

  /** Padding around the diagram. */
  private val DiagramPadding: Double = 8.0

  /** Padding inside class compartments. */
  private val CompartmentPadding: Double = 5.0

  /** Height of a single member line. */
  private val MemberLineHeight: Double = 16.0

  /** Minimum class box width. */
  private val MinClassWidth: Double = 100.0

  /** Renders a class diagram to an SVG string.
    *
    * @param db
    *   the populated class database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: ClassDb, config: MermaidConfig): String = {
    val padding = config.flowchart.padding.toDouble

    // 1. Build dagre graph
    val g = buildDagreGraph(db, padding)

    // 2. Run layout
    DagreLayout.layout(g)

    // 3. Get computed dimensions
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
    Accessibility.applyTo(svg, "classDiagram", db.accTitle, db.accDescription)

    // 5. Add defs with markers and styles
    val defs = svg.append("defs")
    addClassMarkers(defs)

    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = ClassStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    // 6. Create main group
    val mainGroup = svg.append("g")
    mainGroup.attr("transform", s"translate($DiagramPadding, $DiagramPadding)")

    // 7. Render namespaces (clusters) — behind other elements
    renderNamespaces(mainGroup, db, g, themeVars)

    // 8. Render relations (edges)
    renderRelations(mainGroup, db, g, config)

    // 9. Render class nodes
    renderClasses(mainGroup, db, g, padding, config)

    // 10. Render notes
    renderNotes(mainGroup, db, g, themeVars)

    // 11. Title
    if (db.title.nonEmpty) {
      val titleGroup = mainGroup.append("g")
      val text       = titleGroup.append("text")
      text.attr("x", svgWidth / 2)
      text.attr("y", 20)
      text.attr("text-anchor", "middle")
      text.classed("classTitleText", true)
      text.text(db.title)
    }

    svg.build().toMarkup()
  }

  /** Builds a dagre graph from the class database. */
  private def buildDagreGraph(db: ClassDb, padding: Double): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = true, isCompound = true)

    val gl = new GraphLabel
    gl.rankdir = db.direction
    gl.nodesep = 50
    gl.ranksep = 50
    gl.marginx = DiagramPadding
    gl.marginy = DiagramPadding
    g.setGraph(gl)

    // Track namespace containment
    val classToNamespace = mutable.Map.empty[String, String]

    // Add namespace nodes
    for ((nsId, ns) <- db.namespaces) {
      val label = new NodeLabel
      label.label = nsId
      label.width = 100
      label.height = 50
      g.setNode(nsId, label)

      for ((className, _) <- ns.classes)
        classToNamespace(className) = nsId
    }

    // Add class nodes
    for ((id, classNode) <- db.classes) {
      val label = new NodeLabel

      // Calculate class box dimensions
      val (boxWidth, boxHeight) = calculateClassBoxDimensions(classNode, padding)
      label.width = boxWidth
      label.height = boxHeight
      label.label = classNode.label

      g.setNode(id, label)

      // Set parent for namespace containment
      classToNamespace.get(id).foreach { nsId =>
        g.setParent(id, nsId)
      }
    }

    // Add relation edges
    for ((relation, idx) <- db.relations.zipWithIndex) {
      val label = new EdgeLabel
      label.minlen = 1
      label.weight = 1

      // Label with cardinality/title
      if (relation.title.nonEmpty) {
        val labelBBox = TextMetrics.measureText(relation.title, 10, "sans-serif")
        label.width = labelBBox.width + 10
        label.height = labelBBox.height + 10
        label.labelpos = "c"
        label.labeloffset = 10
      }

      val edgeName = s"rel-${relation.id1}-${relation.id2}-$idx"
      g.setEdge(relation.id1, relation.id2, label, edgeName)
    }

    g
  }

  /** Calculates the dimensions for a class box based on its contents. */
  private def calculateClassBoxDimensions(
    classNode: ClassNode,
    padding:   Double
  ): (Double, Double) = {
    // Title width
    val titleBBox = TextMetrics.measureText(classNode.label, 12, "sans-serif", "bold")
    var maxWidth  = titleBBox.width + padding * 2

    // Annotations width
    for (ann <- classNode.annotations) {
      val annBBox = TextMetrics.measureText(s"<<$ann>>", 10, "sans-serif")
      maxWidth = math.max(maxWidth, annBBox.width + padding * 2)
    }

    // Members width
    for (member <- classNode.members) {
      val (displayText, _) = member.displayDetails
      val memberBBox       = TextMetrics.measureText(displayText, 10, "sans-serif")
      maxWidth = math.max(maxWidth, memberBBox.width + padding * 2)
    }

    // Methods width
    for (method <- classNode.methods) {
      val (displayText, _) = method.displayDetails
      val methodBBox       = TextMetrics.measureText(displayText, 10, "sans-serif")
      maxWidth = math.max(maxWidth, methodBBox.width + padding * 2)
    }

    maxWidth = math.max(maxWidth, MinClassWidth)

    // Height: title + annotations + divider + members + divider + methods
    val annotationHeight = classNode.annotations.size * MemberLineHeight
    val titleHeight      = 20.0 + annotationHeight
    val membersHeight    = math.max(classNode.members.size * MemberLineHeight, MemberLineHeight)
    val methodsHeight    = math.max(classNode.methods.size * MemberLineHeight, MemberLineHeight)
    val totalHeight      = titleHeight + CompartmentPadding * 2 +
      membersHeight + CompartmentPadding +
      methodsHeight + CompartmentPadding * 2

    (maxWidth, totalHeight)
  }

  /** Renders all class nodes. */
  private def renderClasses(
    parent:  SvgBuilder,
    db:      ClassDb,
    g:       Graph[NodeLabel, EdgeLabel],
    padding: Double,
    config:  MermaidConfig
  ): Unit =
    for ((id, classNode) <- db.classes)
      g.nodeOpt(id).foreach { nl =>
        renderClassBox(parent, classNode, nl, padding, config)
      }

  /** Renders a single class as a 3-compartment box. */
  private def renderClassBox(
    parent:    SvgBuilder,
    classNode: ClassNode,
    nodeLabel: NodeLabel,
    padding:   Double,
    config:    MermaidConfig
  ): Unit = {
    val group = parent.append("g")
    group.classed("classGroup", true)
    group.attr("id", classNode.domId)
    group.attr("transform", s"translate(${nodeLabel.x - nodeLabel.width / 2}, ${nodeLabel.y - nodeLabel.height / 2})")

    val w = nodeLabel.width
    val h = nodeLabel.height

    // Main rectangle
    val rect = group.append("rect")
    rect.attr("x", 0)
    rect.attr("y", 0)
    rect.attr("width", w)
    rect.attr("height", h)
    rect.attr("rx", 0)
    rect.attr("ry", 0)
    rect.classed("classGroup", true)

    // CSS classes
    classNode.cssClasses.foreach(cls => group.classed(cls, true))
    if (classNode.styles.nonEmpty) {
      rect.attr("style", classNode.styles.mkString("; "))
    }

    var yOffset: Double = CompartmentPadding

    // Annotations
    for (ann <- classNode.annotations) {
      yOffset += MemberLineHeight
      val annText = group.append("text")
      annText.attr("x", w / 2)
      annText.attr("y", yOffset)
      annText.attr("text-anchor", "middle")
      annText.attr("font-size", "10")
      annText.text(s"<<$ann>>")
    }

    // Title — htmlLabels resolution: classRenderer-v2.ts:263 `flowchart?.htmlLabels ?? htmlLabels`.
    // SSG flowchart.htmlLabels is always present, so it takes precedence.
    yOffset += MemberLineHeight + 2
    val htmlLabels = TextUtils.evaluate(config.flowchart.htmlLabels)
    if (htmlLabels) {
      // HTML label (ISS-1205): foreignObject for the class title (the node label).
      val labelGroup = group.append("g")
      labelGroup.classed("label", true)
      labelGroup.classed("classTitle", true)
      labelGroup.attr("transform", s"translate(${fmtCoord(w / 2)},${fmtCoord(yOffset)})")
      val sanitized = TextUtils.sanitizeTextHtml(classNode.label, config.securityLevel, htmlLabels)
      HtmlLabelHelper.createText(
        el = labelGroup,
        text = sanitized,
        useHtmlLabels = true,
        isNode = true,
        classes = "",
        width = w,
        style = "font-weight:bolder",
        addBackground = false
      )
      ()
    } else {
      val title = group.append("text")
      title.attr("x", w / 2)
      title.attr("y", yOffset)
      title.attr("text-anchor", "middle")
      title.classed("classTitle", true)
      title.attr("font-weight", "bolder")
      title.text(classNode.label)
    }

    yOffset += CompartmentPadding

    // Divider line (between title and members)
    val divider1 = group.append("line")
    divider1.attr("x1", 0)
    divider1.attr("y1", yOffset)
    divider1.attr("x2", w)
    divider1.attr("y2", yOffset)
    divider1.classed("divider", true)

    yOffset += CompartmentPadding

    // Members (attributes)
    if (classNode.members.isEmpty) {
      yOffset += MemberLineHeight
    } else {
      for (member <- classNode.members) {
        yOffset += MemberLineHeight
        val (displayText, cssStyle) = member.displayDetails
        renderMemberRow(group, displayText, cssStyle, yOffset, htmlLabels, w, config)
      }
    }

    yOffset += CompartmentPadding

    // Divider line (between members and methods)
    val divider2 = group.append("line")
    divider2.attr("x1", 0)
    divider2.attr("y1", yOffset)
    divider2.attr("x2", w)
    divider2.attr("y2", yOffset)
    divider2.classed("divider", true)

    yOffset += CompartmentPadding

    // Methods
    if (classNode.methods.isEmpty) {
      yOffset += MemberLineHeight
    } else {
      for (method <- classNode.methods) {
        yOffset += MemberLineHeight
        val (displayText, cssStyle) = method.displayDetails
        renderMemberRow(group, displayText, cssStyle, yOffset, htmlLabels, w, config)
      }
    }
  }

  /** Renders one class-body row (a member attribute or a method).
    *
    * Faithful port of the per-row `createLabel(parsedText, …, isTitle=true, isNode=true)` calls in `class_box` (nodes.js:953-982 for members, :987-1015 for methods). Under `htmlLabels` each row
    * becomes its own `<foreignObject>` carrying a `<span class="nodeLabel">` (isNode=true, createText.ts:28), with the displayed text's `<`/`>` pre-escaped to `&lt;`/`&gt;` (nodes.js:957/:991). When
    * `htmlLabels` is off the row stays an SVG `<text>` (byte-identical to the legacy inline block — default path / non-regression).
    *
    * @param group
    *   the class group `<g>`
    * @param displayText
    *   the member/method display text (`getDisplayDetails().displayText`)
    * @param cssStyle
    *   the per-member CSS style (`getDisplayDetails().cssStyle`), empty when absent
    * @param yOffset
    *   the row baseline y
    * @param htmlLabels
    *   resolved `evaluate(config.flowchart.htmlLabels)`
    * @param w
    *   the class box width (foreignObject wrap width)
    * @param config
    *   the Mermaid configuration (for the security gate)
    */
  private def renderMemberRow(
    group:       SvgBuilder,
    displayText: String,
    cssStyle:    String,
    yOffset:     Double,
    htmlLabels:  Boolean,
    w:           Double,
    config:      MermaidConfig
  ): Unit =
    if (htmlLabels) {
      // nodes.js:957/:991 — `parsedText.replace(/</g, '&lt;').replace(/>/g, '&gt;')` before
      // createLabel; createLabel then decodeEntities()-es it back inside the HTML span.
      val escaped    = displayText.replace("<", "&lt;").replace(">", "&gt;")
      val sanitized  = TextUtils.sanitizeTextHtml(escaped, config.securityLevel, htmlLabels)
      val labelGroup = group.append("g")
      labelGroup.classed("label", true)
      labelGroup.attr("transform", s"translate(${fmtCoord(CompartmentPadding)},${fmtCoord(yOffset)})")
      HtmlLabelHelper.createText(
        el = labelGroup,
        text = sanitized,
        useHtmlLabels = true,
        isNode = true,
        classes = "",
        width = w,
        // createLabel(parsedInfo.cssStyle ? parsedInfo.cssStyle : node.labelStyle) (nodes.js:964/:998).
        style = cssStyle,
        addBackground = false
      )
      ()
    } else {
      val memberText = group.append("text")
      memberText.attr("x", CompartmentPadding)
      memberText.attr("y", yOffset)
      memberText.attr("font-size", "10")
      if (cssStyle.nonEmpty) memberText.attr("style", cssStyle)
      memberText.text(displayText)
      ()
    }

  /** Renders all relations (edges). */
  private def renderRelations(
    parent: SvgBuilder,
    db:     ClassDb,
    g:      Graph[NodeLabel, EdgeLabel],
    config: MermaidConfig
  ): Unit = {
    val graphEdges = g.edges()

    for ((relation, idx) <- db.relations.zipWithIndex) {
      val edgeName  = s"rel-${relation.id1}-${relation.id2}-$idx"
      val dagreEdge = graphEdges.find { e =>
        e.v == relation.id1 && e.w == relation.id2 && e.name.exists(_ == edgeName)
      }

      dagreEdge.foreach { de =>
        val edgeLabel = g.edge(de)
        val points    = if (edgeLabel.points.nonEmpty) {
          edgeLabel.points
        } else {
          val srcNode = g.node(relation.id1)
          val dstNode = g.node(relation.id2)
          Array(Point(srcNode.x, srcNode.y), Point(dstNode.x, dstNode.y))
        }

        drawRelation(parent, points, relation, edgeLabel, config)
      }
    }
  }

  /** Draws a single relation edge. */
  private def drawRelation(
    parent:    SvgBuilder,
    points:    Array[Point],
    relation:  ClassRelation,
    edgeLabel: EdgeLabel,
    config:    MermaidConfig
  ): Unit = {
    val group = parent.append("g")
    group.classed("relation", true)

    // Build path from points
    if (points.length >= 2) {
      val pathData = new StringBuilder()
      pathData.append(s"M ${points(0).x},${points(0).y}")
      for (i <- 1 until points.length)
        pathData.append(s" L ${points(i).x},${points(i).y}")

      val path = group.append("path")
      path.attr("d", pathData.toString)
      path.classed("relation", true)
      path.attr("fill", "none")

      if (relation.relationType.lineType == ClassLineType.DottedLine) {
        path.attr("stroke-dasharray", "3, 3")
        path.classed("dashed-line", true)
      }

      // Markers based on relation types
      val endMarkerId   = resolveMarkerId(relation.relationType.type2)
      val startMarkerId = resolveMarkerId(relation.relationType.type1)

      if (endMarkerId.nonEmpty) {
        path.attr("marker-end", s"url(#${endMarkerId}End)")
      }
      if (startMarkerId.nonEmpty) {
        path.attr("marker-start", s"url(#${startMarkerId}Start)")
      }
    }

    // Cardinality labels
    if (relation.relationTitle1 != "none" && relation.relationTitle1.nonEmpty && points.length >= 2) {
      val cardText = group.append("text")
      cardText.attr("x", points(0).x + 10)
      cardText.attr("y", points(0).y - 10)
      cardText.attr("font-size", "11")
      cardText.classed("edgeTerminals", true)
      cardText.text(relation.relationTitle1)
    }

    if (relation.relationTitle2 != "none" && relation.relationTitle2.nonEmpty && points.length >= 2) {
      val lastPoint = points(points.length - 1)
      val cardText  = group.append("text")
      cardText.attr("x", lastPoint.x - 10)
      cardText.attr("y", lastPoint.y - 10)
      cardText.attr("font-size", "11")
      cardText.classed("edgeTerminals", true)
      cardText.text(relation.relationTitle2)
    }

    // Edge label (title)
    if (relation.title.nonEmpty) {
      // htmlLabels resolution: classRenderer-v2.ts:263 `flowchart?.htmlLabels ?? htmlLabels`.
      // SSG flowchart.htmlLabels is always present, so it takes precedence (same as the title path).
      val htmlLabels = TextUtils.evaluate(config.flowchart.htmlLabels)
      if (htmlLabels) {
        // HTML edge label (ISS-1205, classRenderer-v2.ts:263-266 `edgeData.label =
        // '<span class="edgeLabel">' + edge.text + '</span>'`): emitted as a `<foreignObject>`
        // with an inner `<span class="edgeLabel">` (isNode=false), inside the
        // `<g class="edgeLabel"> > <g class="label">` wrapper (edges.js:39-43), centred at the
        // dagre label point.
        val edgeLabelGroup = group.append("g")
        edgeLabelGroup.classed("edgeLabel", true)
        val labelGroup = edgeLabelGroup.append("g")
        labelGroup.classed("label", true)
        labelGroup.attr("transform", s"translate(${fmtCoord(edgeLabel.x)},${fmtCoord(edgeLabel.y)})")
        val sanitized = TextUtils.sanitizeTextHtml(relation.title, config.securityLevel, htmlLabels)
        HtmlLabelHelper.createText(
          el = labelGroup,
          text = sanitized,
          useHtmlLabels = true,
          isNode = false,
          classes = "",
          width = 200.0,
          style = "",
          addBackground = false
        )
        ()
      } else {
        val labelText = group.append("text")
        labelText.attr("x", edgeLabel.x)
        labelText.attr("y", edgeLabel.y)
        labelText.attr("text-anchor", "middle")
        labelText.attr("font-size", "10")
        labelText.classed("edgeLabel", true)

        // Background rect for label
        val labelBBox = TextMetrics.measureText(relation.title, 10, "sans-serif")
        val labelRect = group.append("rect")
        labelRect.attr("x", edgeLabel.x - labelBBox.width / 2 - 5)
        labelRect.attr("y", edgeLabel.y - labelBBox.height / 2 - 5)
        labelRect.attr("width", labelBBox.width + 10)
        labelRect.attr("height", labelBBox.height + 10)
        labelRect.classed("classLabel", true)
        labelRect.classed("box", true)

        labelText.text(relation.title)
      }
    }
  }

  /** Resolves a relation type to a marker ID prefix. */
  private def resolveMarkerId(relType: Int): String =
    relType match {
      case ClassRelationType.Extension   => "extension"
      case ClassRelationType.Composition => "composition"
      case ClassRelationType.Aggregation => "aggregation"
      case ClassRelationType.Dependency  => "dependency"
      case ClassRelationType.Lollipop    => "lollipop"
      case _                             => ""
    }

  /** Renders namespace clusters. */
  private def renderNamespaces(
    parent:    SvgBuilder,
    db:        ClassDb,
    g:         Graph[NodeLabel, EdgeLabel],
    themeVars: ThemeVariables
  ): Unit =
    for ((nsId, _) <- db.namespaces)
      g.nodeOpt(nsId).foreach { nl =>
        val group = parent.append("g")
        group.classed("classGroup", true)

        val rect = group.append("rect")
        rect.attr("x", nl.x - nl.width / 2)
        rect.attr("y", nl.y - nl.height / 2)
        rect.attr("width", nl.width)
        rect.attr("height", nl.height)
        rect.attr("fill", "none")
        rect.attr("stroke", themeVars.nodeBorder)
        rect.attr("stroke-width", "1")

        val label = group.append("text")
        label.attr("x", nl.x)
        label.attr("y", nl.y - nl.height / 2 + 15)
        label.attr("text-anchor", "middle")
        label.attr("font-size", "12")
        label.attr("font-weight", "bold")
        label.text(nsId)
      }

  /** Renders notes. */
  private def renderNotes(
    parent:    SvgBuilder,
    db:        ClassDb,
    g:         Graph[NodeLabel, EdgeLabel],
    themeVars: ThemeVariables
  ): Unit =
    for (note <- db.notes) {
      // Position note near its class if specified
      val (noteX, noteY) = if (note.className.nonEmpty) {
        g.nodeOpt(note.className)
          .map { nl =>
            (nl.x + nl.width / 2 + 20, nl.y)
          }
          .getOrElse((50.0, 50.0))
      } else {
        (50.0, 50.0)
      }

      val bbox       = TextMetrics.measureText(note.text, 10, "sans-serif")
      val noteWidth  = bbox.width + 20
      val noteHeight = bbox.height + 20

      val group = parent.append("g")

      val rect = group.append("rect")
      rect.attr("x", noteX)
      rect.attr("y", noteY)
      rect.attr("width", noteWidth)
      rect.attr("height", noteHeight)
      rect.attr("fill", themeVars.noteBkgColor)
      rect.attr("stroke", themeVars.noteBorderColor)

      val text = group.append("text")
      text.attr("x", noteX + noteWidth / 2)
      text.attr("y", noteY + noteHeight / 2)
      text.attr("text-anchor", "middle")
      text.attr("dominant-baseline", "central")
      text.attr("font-size", "10")
      text.text(note.text)
    }

  /** Adds class diagram-specific markers to defs. */
  private def addClassMarkers(defs: SvgBuilder): Unit = {
    // Extension markers (open triangle)
    addTriangleMarker(defs, "extensionStart", "transparent")
    addTriangleMarker(defs, "extensionEnd", "transparent")

    // Composition markers (filled diamond)
    addDiamondMarker(defs, "compositionStart", filled = true)
    addDiamondMarker(defs, "compositionEnd", filled = true)

    // Aggregation markers (open diamond)
    addDiamondMarker(defs, "aggregationStart", filled = false)
    addDiamondMarker(defs, "aggregationEnd", filled = false)

    // Dependency markers (open arrow)
    addArrowMarker(defs, "dependencyStart")
    addArrowMarker(defs, "dependencyEnd")

    // Lollipop markers (circle)
    addCircleMarker(defs, "lollipopStart")
    addCircleMarker(defs, "lollipopEnd")
  }

  /** Adds a triangle (extension) marker. */
  private def addTriangleMarker(defs: SvgBuilder, id: String, fill: String): Unit = {
    val marker = defs.append("marker")
    marker.attr("id", id)
    marker.attr("markerWidth", "20")
    marker.attr("markerHeight", "20")
    marker.attr("refX", "18")
    marker.attr("refY", "7")
    marker.attr("orient", "auto")
    val path = marker.append("path")
    path.attr("d", "M 1,1 L 18,7 L 1,13 z")
    path.attr("fill", fill)
    path.classed("extension", true)
  }

  /** Adds a diamond (composition/aggregation) marker. */
  private def addDiamondMarker(defs: SvgBuilder, id: String, filled: Boolean): Unit = {
    val marker = defs.append("marker")
    marker.attr("id", id)
    marker.attr("markerWidth", "20")
    marker.attr("markerHeight", "20")
    marker.attr("refX", "18")
    marker.attr("refY", "7")
    marker.attr("orient", "auto")
    val path = marker.append("path")
    path.attr("d", "M 1,7 L 9,13 L 18,7 L 9,1 z")
    path.attr("fill", if (filled) "currentColor" else "transparent")
    path.classed(if (filled) "composition" else "aggregation", true)
  }

  /** Adds an arrow (dependency) marker. */
  private def addArrowMarker(defs: SvgBuilder, id: String): Unit = {
    val marker = defs.append("marker")
    marker.attr("id", id)
    marker.attr("markerWidth", "20")
    marker.attr("markerHeight", "20")
    marker.attr("refX", "18")
    marker.attr("refY", "7")
    marker.attr("orient", "auto")
    val path = marker.append("path")
    path.attr("d", "M 1,1 L 18,7 L 1,13")
    path.attr("fill", "none")
    path.classed("dependency", true)
  }

  /** Adds a circle (lollipop) marker. */
  private def addCircleMarker(defs: SvgBuilder, id: String): Unit = {
    val marker = defs.append("marker")
    marker.attr("id", id)
    marker.attr("markerWidth", "20")
    marker.attr("markerHeight", "20")
    marker.attr("refX", "10")
    marker.attr("refY", "10")
    marker.attr("orient", "auto")
    val circle = marker.append("circle")
    circle.attr("cx", "10")
    circle.attr("cy", "10")
    circle.attr("r", "5")
    circle.classed("lollipop", true)
  }

  /** Formats a coordinate without a trailing `.0` for integral values. */
  private def fmtCoord(v: Double): String =
    if (v == v.toLong.toDouble) v.toLong.toString else v.toString
}
