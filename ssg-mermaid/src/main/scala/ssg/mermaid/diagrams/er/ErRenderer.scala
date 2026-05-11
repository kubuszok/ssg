/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/er/erRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from ErDb + config -> SVG string; custom entity box layout
 *   Renames: erRenderer draw() -> ErRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package er

import ssg.mermaid.MermaidConfig
import ssg.mermaid.render.text.TextMetrics
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders an ER diagram to SVG.
  *
  * Takes a populated [[ErDb]] and produces a complete SVG string. Uses a simple grid layout: entities are arranged in rows with relationships drawn as lines between them.
  */
object ErRenderer {

  private val DiagramPadding:         Double = 20.0
  private val EntityPadding:          Double = 15.0
  private val AttributeRowHeight:     Double = 20.0
  private val EntityGap:              Double = 60.0
  private val RelationshipLineMargin: Double = 10.0

  /** Renders an ER diagram to an SVG string.
    *
    * @param db
    *   the populated ER database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: ErDb, config: MermaidConfig): String = {
    val erConfig = config.er

    // Compute entity dimensions
    val entityDims = computeEntityDimensions(db, erConfig)

    // Layout entities in a grid
    val entityPositions = layoutEntities(db, entityDims, erConfig)

    // Compute total SVG dimensions
    var maxX = 0.0
    var maxY = 0.0
    for ((name, pos) <- entityPositions) {
      val dim    = entityDims(name)
      val right  = pos._1 + dim._1
      val bottom = pos._2 + dim._2
      if (right > maxX) maxX = right
      if (bottom > maxY) maxY = bottom
    }

    val svgWidth  = maxX + DiagramPadding * 2
    val svgHeight = maxY + DiagramPadding * 2

    // Create SVG
    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Add defs with markers and styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = ErStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    // Add cardinality markers
    addMarkers(defs)

    // Main group
    val mainGroup = svg.append("g")
    mainGroup.attr("transform", s"translate($DiagramPadding, $DiagramPadding)")

    // Render entities
    for ((name, entity) <- db.entities) {
      val pos = entityPositions(name)
      val dim = entityDims(name)
      renderEntity(mainGroup, entity, pos._1, pos._2, dim._1, dim._2, erConfig)
    }

    // Render relationships
    for (rel <- db.relationships) {
      val posA = entityPositions(rel.entityA)
      val dimA = entityDims(rel.entityA)
      val posB = entityPositions(rel.entityB)
      val dimB = entityDims(rel.entityB)
      renderRelationship(mainGroup, rel, posA, dimA, posB, dimB)
    }

    // Render title if present
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", maxX / 2.0)
      titleText.attr("y", -5)
      titleText.attr("text-anchor", "middle")
      titleText.classed("er-title", true)
      titleText.text(db.title)
    }

    svg.build().toMarkup()
  }

  /** Computes the width and height for each entity box. */
  private def computeEntityDimensions(
    db:     ErDb,
    config: ErConfig
  ): mutable.LinkedHashMap[String, (Double, Double)] = {
    val result = mutable.LinkedHashMap.empty[String, (Double, Double)]

    for ((name, entity) <- db.entities) {
      val nameBBox     = TextMetrics.measureText(name, config.fontSize.toDouble, "sans-serif", "bold")
      var maxWidth     = nameBBox.width + EntityPadding * 2
      val headerHeight = nameBBox.height + EntityPadding

      var totalHeight = headerHeight

      for (attr <- entity.attributes) {
        val attrText  = formatAttribute(attr)
        val attrBBox  = TextMetrics.measureText(attrText, config.fontSize.toDouble, "sans-serif")
        val attrWidth = attrBBox.width + EntityPadding * 2
        if (attrWidth > maxWidth) maxWidth = attrWidth
        totalHeight += AttributeRowHeight
      }

      if (entity.attributes.isEmpty) {
        totalHeight += EntityPadding
      }

      maxWidth = math.max(maxWidth, config.minEntityWidth.toDouble)
      totalHeight = math.max(totalHeight, config.minEntityHeight.toDouble)

      result(name) = (maxWidth, totalHeight)
    }

    result
  }

  /** Lays out entities in a simple grid. */
  private def layoutEntities(
    db:     ErDb,
    dims:   mutable.LinkedHashMap[String, (Double, Double)],
    config: ErConfig
  ): mutable.LinkedHashMap[String, (Double, Double)] = {
    val positions = mutable.LinkedHashMap.empty[String, (Double, Double)]
    val names     = db.entities.keys.toArray
    val cols      = math.max(1, math.ceil(math.sqrt(names.length.toDouble)).toInt)

    var x            = 0.0
    var y            = 0.0
    var rowMaxHeight = 0.0
    var col          = 0

    for (name <- names) {
      val dim = dims(name)
      positions(name) = (x, y)

      if (dim._2 > rowMaxHeight) rowMaxHeight = dim._2
      x += dim._1 + EntityGap
      col += 1

      if (col >= cols) {
        col = 0
        x = 0.0
        y += rowMaxHeight + EntityGap
        rowMaxHeight = 0.0
      }
    }

    positions
  }

  /** Renders a single entity box. */
  private def renderEntity(
    parent: SvgBuilder,
    entity: ErEntity,
    x:      Double,
    y:      Double,
    width:  Double,
    height: Double,
    config: ErConfig
  ): Unit = {
    val g = parent.append("g")
    g.classed("er", true)
    g.classed("entityBox", true)
    g.attr("transform", s"translate($x, $y)")

    // Background rect
    val rect = g.append("rect")
    rect.attr("width", width)
    rect.attr("height", height)
    rect.attr("rx", 0)
    rect.attr("ry", 0)
    rect.classed("er", true)
    rect.classed("entityBox", true)
    rect.style("fill", config.fill)
    rect.style("stroke", config.stroke)

    // Entity name (header)
    val headerHeight = TextMetrics.measureText(entity.name, config.fontSize.toDouble, "sans-serif", "bold").height + EntityPadding
    val nameText     = g.append("text")
    nameText.attr("x", width / 2.0)
    nameText.attr("y", headerHeight / 2.0 + config.fontSize / 3.0)
    nameText.attr("text-anchor", "middle")
    nameText.attr("font-weight", "bold")
    nameText.classed("er", true)
    nameText.classed("entityLabel", true)
    nameText.text(entity.name)

    // Separator line
    val sep = g.append("line")
    sep.attr("x1", 0)
    sep.attr("y1", headerHeight)
    sep.attr("x2", width)
    sep.attr("y2", headerHeight)
    sep.style("stroke", config.stroke)

    // Attributes
    var attrY = headerHeight + AttributeRowHeight * 0.75
    for ((attr, idx) <- entity.attributes.zipWithIndex) {
      // Alternating background
      val bgColor = if (idx % 2 == 0) "attributeBackgroundColorEven" else "attributeBackgroundColorOdd"
      val attrBg  = g.append("rect")
      attrBg.attr("x", 0)
      attrBg.attr("y", headerHeight + idx * AttributeRowHeight)
      attrBg.attr("width", width)
      attrBg.attr("height", AttributeRowHeight)
      attrBg.classed(bgColor, true)
      attrBg.style("fill", if (idx % 2 == 0) "#f2f2f2" else "#ffffff")

      val attrText = g.append("text")
      attrText.attr("x", EntityPadding)
      attrText.attr("y", attrY)
      attrText.classed("er", true)
      attrText.classed("entityLabel", true)
      attrText.text(formatAttribute(attr))

      attrY += AttributeRowHeight
    }
  }

  /** Renders a relationship line between two entities. */
  private def renderRelationship(
    parent: SvgBuilder,
    rel:    ErRelationship,
    posA:   (Double, Double),
    dimA:   (Double, Double),
    posB:   (Double, Double),
    dimB:   (Double, Double)
  ): Unit = {
    // Connect from center-right of A to center-left of B (simple approach)
    val ax = posA._1 + dimA._1
    val ay = posA._2 + dimA._2 / 2.0
    val bx = posB._1
    val by = posB._2 + dimB._2 / 2.0

    val g = parent.append("g")
    g.classed("er", true)
    g.classed("relationshipLine", true)

    val line = g.append("path")
    val midX = (ax + bx) / 2.0
    line.attr("d", s"M $ax $ay C $midX $ay $midX $by $bx $by")
    line.classed("er", true)
    line.classed("relationshipLine", true)
    line.style("stroke", "#333")
    line.style("stroke-width", "1")
    line.style("fill", "none")

    // Add cardinality markers as text labels near endpoints
    renderCardinalityLabel(g, rel.roleA, ax + RelationshipLineMargin, ay - 10)
    renderCardinalityLabel(g, rel.roleB, bx - RelationshipLineMargin - 20, by - 10)

    // Render relationship label at midpoint
    if (rel.label.nonEmpty) {
      val labelX    = midX
      val labelY    = (ay + by) / 2.0 - 5
      val labelBg   = g.append("rect")
      val labelBBox = TextMetrics.measureText(rel.label, 12, "sans-serif")
      labelBg.attr("x", labelX - labelBBox.width / 2.0 - 3)
      labelBg.attr("y", labelY - labelBBox.height + 2)
      labelBg.attr("width", labelBBox.width + 6)
      labelBg.attr("height", labelBBox.height + 4)
      labelBg.style("fill", "white")
      labelBg.style("stroke", "none")

      val labelText = g.append("text")
      labelText.attr("x", labelX)
      labelText.attr("y", labelY)
      labelText.attr("text-anchor", "middle")
      labelText.attr("font-size", "12")
      labelText.classed("er", true)
      labelText.classed("relationshipLabel", true)
      labelText.text(rel.label)
    }
  }

  /** Renders a cardinality marker symbol near a relationship endpoint.
    *
    * Draws actual SVG shapes matching the ER notation:
    *   - ONE_ONLY (ExactlyOne): single vertical line `||`
    *   - ZERO_OR_ONE (ZeroOrOne): circle + vertical line `|o`
    *   - ONE_OR_MORE (OneOrMore): crow's foot `|{`
    *   - ZERO_OR_MORE (ZeroOrMore): circle + crow's foot `o{`
    */
  private def renderCardinalityLabel(parent: SvgBuilder, card: Cardinality, x: Double, y: Double): Unit = {
    val g = parent.append("g")
    g.attr("transform", s"translate($x, $y)")
    g.classed("er", true)
    g.classed("cardinality", true)

    card match {
      case Cardinality.ExactlyOne =>
        // Two vertical lines ||
        drawOneLine(g, 0)
        drawOneLine(g, 6)

      case Cardinality.ZeroOrOne =>
        // Circle + vertical line |o
        drawOneLine(g, 0)
        drawZeroCircle(g, 10)

      case Cardinality.OneOrMore =>
        // Vertical line + crow's foot |{
        drawOneLine(g, 0)
        drawCrowsFoot(g, 6)

      case Cardinality.ZeroOrMore =>
        // Circle + crow's foot o{
        drawZeroCircle(g, 0)
        drawCrowsFoot(g, 12)

      case Cardinality.MdParent =>
        // Same as ExactlyOne for md-parent notation
        drawOneLine(g, 0)
        drawOneLine(g, 6)
    }
  }

  /** Draws a single vertical line (ONE marker). */
  private def drawOneLine(parent: SvgBuilder, xOff: Double): Unit = {
    val line = parent.append("line")
    line.attr("x1", xOff)
    line.attr("y1", -6)
    line.attr("x2", xOff)
    line.attr("y2", 6)
    line.style("stroke", "#333")
    line.style("stroke-width", "1.5")
  }

  /** Draws a circle (ZERO marker). */
  private def drawZeroCircle(parent: SvgBuilder, xOff: Double): Unit = {
    val circle = parent.append("circle")
    circle.attr("cx", xOff + 4)
    circle.attr("cy", 0)
    circle.attr("r", 4)
    circle.style("stroke", "#333")
    circle.style("stroke-width", "1.5")
    circle.style("fill", "white")
  }

  /** Draws a crow's foot (MANY marker). */
  private def drawCrowsFoot(parent: SvgBuilder, xOff: Double): Unit = {
    // Three lines from a point fanning out
    val line1 = parent.append("line")
    line1.attr("x1", xOff)
    line1.attr("y1", 0)
    line1.attr("x2", xOff + 10)
    line1.attr("y2", -6)
    line1.style("stroke", "#333")
    line1.style("stroke-width", "1.5")

    val line2 = parent.append("line")
    line2.attr("x1", xOff)
    line2.attr("y1", 0)
    line2.attr("x2", xOff + 10)
    line2.attr("y2", 0)
    line2.style("stroke", "#333")
    line2.style("stroke-width", "1.5")

    val line3 = parent.append("line")
    line3.attr("x1", xOff)
    line3.attr("y1", 0)
    line3.attr("x2", xOff + 10)
    line3.attr("y2", 6)
    line3.style("stroke", "#333")
    line3.style("stroke-width", "1.5")
  }

  /** Formats an attribute for display. */
  private def formatAttribute(attr: ErAttribute): String = {
    val sb = new StringBuilder()
    sb.append(attr.attributeType)
    sb.append(' ')
    sb.append(attr.attributeName)
    if (attr.attributeKeyType.nonEmpty) {
      sb.append(' ')
      sb.append(attr.attributeKeyType)
    }
    if (attr.attributeComment.nonEmpty) {
      sb.append(" \"")
      sb.append(attr.attributeComment)
      sb.append('"')
    }
    sb.toString
  }

  /** Adds SVG marker definitions for relationship cardinality symbols. */
  private def addMarkers(defs: SvgBuilder): Unit = {
    // One-to-one marker (line)
    val oneMarker = defs.append("marker")
    oneMarker.attr("id", "er-one")
    oneMarker.attr("viewBox", "0 0 12 12")
    oneMarker.attr("refX", 9)
    oneMarker.attr("refY", 6)
    oneMarker.attr("markerWidth", 12)
    oneMarker.attr("markerHeight", 12)
    oneMarker.attr("orient", "auto")
    val oneLine = oneMarker.append("path")
    oneLine.attr("d", "M9,0 L9,12")
    oneLine.style("stroke", "#333")
    oneLine.style("stroke-width", "1")

    // Many marker (crow's foot)
    val manyMarker = defs.append("marker")
    manyMarker.attr("id", "er-many")
    manyMarker.attr("viewBox", "0 0 12 12")
    manyMarker.attr("refX", 9)
    manyMarker.attr("refY", 6)
    manyMarker.attr("markerWidth", 12)
    manyMarker.attr("markerHeight", 12)
    manyMarker.attr("orient", "auto")
    val manyPath = manyMarker.append("path")
    manyPath.attr("d", "M0,6 L9,0 M0,6 L9,12")
    manyPath.style("stroke", "#333")
    manyPath.style("stroke-width", "1")
    manyPath.style("fill", "none")

    // Zero marker (circle)
    val zeroMarker = defs.append("marker")
    zeroMarker.attr("id", "er-zero")
    zeroMarker.attr("viewBox", "0 0 12 12")
    zeroMarker.attr("refX", 3)
    zeroMarker.attr("refY", 6)
    zeroMarker.attr("markerWidth", 12)
    zeroMarker.attr("markerHeight", 12)
    zeroMarker.attr("orient", "auto")
    val zeroCircle = zeroMarker.append("circle")
    zeroCircle.attr("cx", 6)
    zeroCircle.attr("cy", 6)
    zeroCircle.attr("r", 4)
    zeroCircle.style("stroke", "#333")
    zeroCircle.style("fill", "white")
  }
}
