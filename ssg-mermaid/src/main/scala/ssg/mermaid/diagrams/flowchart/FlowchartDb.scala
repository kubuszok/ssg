/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/flowchart/flowDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: flowDb module functions → FlowchartDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package flowchart

import ssg.commons.Nullable

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Data model types for flowchart diagrams.
  *
  * Ports the interfaces from `types.ts` in the original.
  */

/** A flowchart node (vertex) definition.
  *
  * @param id
  *   unique node identifier
  * @param text
  *   display text inside the node
  * @param shape
  *   shape type name (e.g. "square", "round", "circle", "diamond")
  * @param styles
  *   inline CSS styles applied to this node
  * @param cssClasses
  *   CSS classes applied to this node
  * @param dir
  *   direction override for this node (within a subgraph)
  * @param domId
  *   DOM element ID for this node
  * @param labelType
  *   type of label: "text", "string", or "markdown"
  * @param link
  *   URL link for clickable nodes
  * @param linkTarget
  *   link target attribute (_self, _blank, etc.)
  * @param haveCallback
  *   whether this node has a click callback
  */
final case class FlowNode(
  id:               String,
  var text:         String,
  var shape:        String = "square",
  styles:           mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  cssClasses:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var dir:          Nullable[String] = Nullable.empty,
  domId:            String = "",
  var labelType:    String = "text",
  var link:         Nullable[String] = Nullable.empty,
  var linkTarget:   Nullable[String] = Nullable.empty,
  var haveCallback: Boolean = false,
  var props:        Map[String, String] = Map.empty
)

/** A flowchart edge (link) definition.
  *
  * @param src
  *   source node ID
  * @param dst
  *   destination node ID
  * @param edgeType
  *   arrow type (arrow_point, arrow_cross, arrow_circle, double_arrow_point, etc.)
  * @param stroke
  *   stroke style: "normal", "thick", "dotted", "invisible"
  * @param label
  *   text label on the edge
  * @param labelType
  *   type of label: "text", "string", or "markdown"
  * @param length
  *   edge length (for rank separation)
  * @param style
  *   inline CSS styles
  * @param interpolate
  *   curve interpolation type
  */
final case class FlowEdge(
  src:             String,
  dst:             String,
  var edgeType:    Nullable[String] = Nullable.empty,
  var stroke:      String = "normal",
  var label:       String = "",
  var labelType:   String = "text",
  var length:      Int = 1,
  var style:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var interpolate: Nullable[String] = Nullable.empty
)

/** A flowchart subgraph definition.
  *
  * @param id
  *   unique subgraph identifier
  * @param title
  *   subgraph display title
  * @param nodeIds
  *   IDs of nodes contained in this subgraph
  * @param cssClasses
  *   CSS classes
  * @param dir
  *   direction override for this subgraph
  * @param labelType
  *   type of label
  */
final case class FlowSubgraph(
  id:            String,
  var title:     String,
  nodeIds:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  cssClasses:    mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var dir:       Nullable[String] = Nullable.empty,
  var labelType: String = "text"
)

/** A class definition for styling nodes.
  *
  * @param id
  *   class name
  * @param styles
  *   CSS styles
  * @param textStyles
  *   text-specific CSS styles (color-related)
  */
final case class FlowClass(
  id:         String,
  styles:     mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  textStyles: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
)

/** Result of destructuring an edge link string. */
final case class FlowLinkInfo(
  var edgeType: String = "arrow_open",
  var stroke:   String = "normal",
  var length:   Int = 1
)

/** Mutable database for flowchart diagram data.
  *
  * Accumulates nodes, edges, subgraphs, classes, and styling during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `flowDb.ts`.
  */
final class FlowchartDb {

  private val MERMAID_DOM_ID_PREFIX = "flowchart-"
  private var vertexCounter: Int = 0

  val nodes:                  mutable.LinkedHashMap[String, FlowNode]     = mutable.LinkedHashMap.empty
  val edges:                  mutable.ArrayBuffer[FlowEdge]               = mutable.ArrayBuffer.empty
  val classes:                mutable.LinkedHashMap[String, FlowClass]    = mutable.LinkedHashMap.empty
  val subgraphs:              mutable.ArrayBuffer[FlowSubgraph]           = mutable.ArrayBuffer.empty
  private val subgraphLookup: mutable.LinkedHashMap[String, FlowSubgraph] = mutable.LinkedHashMap.empty
  private val tooltips:       mutable.LinkedHashMap[String, String]       = mutable.LinkedHashMap.empty

  var defaultEdgeInterpolate: Nullable[String]            = Nullable.empty
  var defaultEdgeStyle:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty

  var direction:        String = "TB"
  var title:            String = ""
  var accTitle:         String = ""
  var accDescription:   String = ""
  private var subCount: Int    = 0
  private val maxEdges: Int    = 500

  /** Function to lookup domId from id in the graph definition. */
  def lookUpDomId(id: String): String =
    boundary[String] {
      for (vertex <- nodes.values)
        if (vertex.id == id) {
          break(vertex.domId)
        }
      id
    }

  /** Adds a node (vertex) to the flowchart.
    *
    * If the node already exists, updates its text, shape, styles, and classes. Ports `addVertex()` from `flowDb.ts`.
    */
  def addNode(
    id:         String,
    text:       Nullable[String] = Nullable.empty,
    textType:   String = "text",
    shape:      Nullable[String] = Nullable.empty,
    style:      Nullable[Array[String]] = Nullable.empty,
    cssClasses: Nullable[Array[String]] = Nullable.empty,
    dir:        Nullable[String] = Nullable.empty,
    props:      Map[String, String] = Map.empty
  ): Unit =
    if (id.nonEmpty && id.trim.nonEmpty) {
      // noop for empty IDs, matching original
      val node = nodes.getOrElseUpdate(id,
                                       FlowNode(
                                         id = id,
                                         text = id,
                                         domId = MERMAID_DOM_ID_PREFIX + id + "-" + vertexCounter
                                       )
      )
      vertexCounter += 1

      text.foreach { rawText =>
        var txt = sanitizeText(rawText.trim)
        // strip quotes if string starts and ends with a quote
        if (txt.startsWith("\"") && txt.endsWith("\"")) {
          txt = txt.substring(1, txt.length - 1)
        }
        node.text = txt
        node.labelType = textType
      }

      shape.foreach { s =>
        node.shape = s
      }

      style.foreach { styles =>
        styles.foreach(s => node.styles += s)
      }

      cssClasses.foreach { classes =>
        classes.foreach(s => node.cssClasses += s)
      }

      dir.foreach { d =>
        node.dir = Nullable(d)
      }

      if (props.nonEmpty) {
        node.props = node.props ++ props
      }
    }

  /** Adds a single edge between two nodes.
    *
    * Ports `addSingleLink()` from `flowDb.ts`.
    */
  def addEdge(
    src:       String,
    dst:       String,
    edgeType:  Nullable[String] = Nullable.empty,
    stroke:    String = "normal",
    label:     String = "",
    labelType: String = "text",
    length:    Int = 1
  ): Unit = {
    if (edges.length >= maxEdges) {
      throw new IllegalStateException(
        s"Edge limit exceeded. ${edges.length} edges found, but the limit is $maxEdges."
      )
    }

    val edge = FlowEdge(
      src = src,
      dst = dst,
      edgeType = edgeType,
      stroke = stroke,
      label = sanitizeText(label),
      labelType = labelType,
      length = math.min(length, 10)
    )
    edges += edge
  }

  /** Adds edges from all source nodes to all destination nodes (cartesian product).
    *
    * Ports `addLink()` from `flowDb.ts`.
    */
  def addLink(starts: Array[String], ends: Array[String], linkInfo: FlowLinkInfo, labelText: String = "", labelType: String = "text"): Unit =
    for {
      src <- starts
      dst <- ends
    }
      addEdge(
        src = src,
        dst = dst,
        edgeType = Nullable(linkInfo.edgeType),
        stroke = linkInfo.stroke,
        label = labelText,
        labelType = labelType,
        length = linkInfo.length
      )

  /** Updates edge interpolation for given edge positions.
    *
    * Ports `updateLinkInterpolate()` from `flowDb.ts`.
    */
  def updateLinkInterpolate(positions: Array[Int], interpolate: String): Unit =
    for (pos <- positions)
      if (pos == -1) {
        // -1 signals "default"
        defaultEdgeInterpolate = Nullable(interpolate)
      } else if (pos >= 0 && pos < edges.length) {
        edges(pos).interpolate = Nullable(interpolate)
      }

  /** Updates edge styles for given edge positions.
    *
    * Ports `updateLink()` from `flowDb.ts`.
    */
  def updateLink(positions: Array[Int], style: Array[String]): Unit =
    for (pos <- positions)
      if (pos == -1) {
        // -1 signals "default"
        defaultEdgeStyle = mutable.ArrayBuffer.from(style)
      } else if (pos >= 0 && pos < edges.length) {
        edges(pos).style = mutable.ArrayBuffer.from(style)
        // If style has items but none start with "fill", add fill:none
        if (edges(pos).style.nonEmpty && !edges(pos).style.exists(_.startsWith("fill"))) {
          edges(pos).style += "fill:none"
        }
      } else {
        throw new IndexOutOfBoundsException(
          s"The index $pos for linkStyle is out of bounds. Valid indices are 0 to ${edges.length - 1}."
        )
      }

  /** Adds a CSS class definition.
    *
    * Ports `addClass()` from `flowDb.ts`.
    */
  def addClass(ids: String, style: Array[String]): Unit =
    for (id <- ids.split(",")) {
      val trimId    = id.trim
      val classNode = classes.getOrElseUpdate(trimId, FlowClass(id = trimId))
      for (s <- style) {
        if (s.contains("color")) {
          val newStyle = s.replace("fill", "bgFill")
          classNode.textStyles += newStyle
        }
        classNode.styles += s
      }
    }

  /** Sets the graph direction.
    *
    * Ports `setDirection()` from `flowDb.ts`.
    */
  def setDirection(dir: String): Unit = {
    direction = dir
    if (direction.contains("<")) direction = "RL"
    if (direction.contains("^")) direction = "BT"
    if (direction.contains(">")) direction = "LR"
    if (direction.contains("v")) direction = "TB"
    if (direction == "TD") direction = "TB"
  }

  /** Sets CSS class on nodes and subgraphs.
    *
    * Ports `setClass()` from `flowDb.ts`.
    */
  def setClass(ids: String, className: String): Unit =
    for (id <- ids.split(",")) {
      val trimId = id.trim
      nodes.get(trimId).foreach(_.cssClasses += className)
      subgraphLookup.get(trimId).foreach(_.cssClasses += className)
    }

  /** Sets inline styles on a node.
    *
    * Ports the `styleStatement` production which calls `addVertex(id, undefined, undefined, styles)`.
    */
  def setStyle(ids: String, style: Array[String]): Unit =
    for (id <- ids.split(",")) {
      val trimId = id.trim
      addNode(trimId, style = Nullable(style))
    }

  /** Adds a subgraph.
    *
    * Ports `addSubGraph()` from `flowDb.ts`.
    *
    * @return
    *   the subgraph ID
    */
  def addSubgraph(
    id:        Nullable[String],
    nodeList:  Array[String],
    titleText: String,
    titleType: String = "text"
  ): String = {
    val resolvedId     = id.filter(_.trim.nonEmpty).getOrElse("subGraph" + subCount)
    val sanitizedTitle = sanitizeText(titleText).trim
    subCount += 1

    // Filter unique nodes
    val uniqueNodes = mutable.ArrayBuffer.empty[String]
    val seen        = mutable.Set.empty[String]
    for (n <- nodeList) {
      val trimmed = n.trim
      if (trimmed.nonEmpty && !seen.contains(trimmed)) {
        seen += trimmed
        uniqueNodes += trimmed
      }
    }

    // Remove nodes that already belong to another subgraph
    val filtered = uniqueNodes.filter { nid =>
      !subgraphs.exists(_.nodeIds.contains(nid))
    }

    val sg = FlowSubgraph(
      id = resolvedId,
      title = sanitizedTitle,
      nodeIds = mutable.ArrayBuffer.from(filtered),
      labelType = titleType
    )
    subgraphs += sg
    subgraphLookup(resolvedId) = sg
    resolvedId
  }

  /** Destructures an edge link string into type, stroke, and length.
    *
    * Ports `destructLink()` and helpers from `flowDb.ts`.
    */
  def destructLink(endStr: String, startStr: Nullable[String] = Nullable.empty): FlowLinkInfo = {
    val endInfo = destructEndLink(endStr.trim)

    startStr match {
      case s if s.isDefined =>
        val startInfo = destructStartLink(s.get.trim)
        if (startInfo.stroke != endInfo.stroke) {
          FlowLinkInfo(edgeType = "INVALID", stroke = "INVALID")
        } else if (startInfo.edgeType == "arrow_open") {
          startInfo.edgeType = endInfo.edgeType
          startInfo.length = endInfo.length
          startInfo
        } else if (startInfo.edgeType != endInfo.edgeType) {
          FlowLinkInfo(edgeType = "INVALID", stroke = "INVALID")
        } else {
          startInfo.edgeType = "double_" + startInfo.edgeType
          if (startInfo.edgeType == "double_arrow") {
            startInfo.edgeType = "double_arrow_point"
          }
          startInfo.length = endInfo.length
          startInfo
        }
      case _ =>
        endInfo
    }
  }

  /** Destructures the start of an edge link. */
  private def destructStartLink(str: String): FlowLinkInfo = {
    var s        = str
    var edgeType = "arrow_open"

    if (s.nonEmpty) {
      s(0) match {
        case '<' => edgeType = "arrow_point"; s = s.substring(1)
        case 'x' => edgeType = "arrow_cross"; s = s.substring(1)
        case 'o' => edgeType = "arrow_circle"; s = s.substring(1)
        case _   => ()
      }
    }

    var stroke = "normal"
    if (s.contains('=')) stroke = "thick"
    if (s.contains('.')) stroke = "dotted"

    FlowLinkInfo(edgeType = edgeType, stroke = stroke)
  }

  /** Destructures the end of an edge link. */
  private def destructEndLink(str: String): FlowLinkInfo = {
    var line     = str.substring(0, str.length - 1)
    var edgeType = "arrow_open"

    if (str.nonEmpty) {
      str.last match {
        case 'x' =>
          edgeType = "arrow_cross"
          if (str.startsWith("x")) { edgeType = "double_arrow_cross"; line = line.substring(1) }
        case '>' =>
          edgeType = "arrow_point"
          if (str.startsWith("<")) { edgeType = "double_arrow_point"; line = line.substring(1) }
        case 'o' =>
          edgeType = "arrow_circle"
          if (str.startsWith("o")) { edgeType = "double_arrow_circle"; line = line.substring(1) }
        case _ => ()
      }
    }

    var stroke = "normal"
    var length = line.length - 1

    if (line.startsWith("=")) stroke = "thick"
    if (line.startsWith("~")) stroke = "invisible"

    val dots = countChar('.', line)
    if (dots > 0) {
      stroke = "dotted"
      length = dots
    }

    FlowLinkInfo(edgeType = edgeType, stroke = stroke, length = length)
  }

  /** Counts occurrences of a character in a string. */
  private def countChar(ch: Char, s: String): Int = {
    var count = 0
    var i     = 0
    while (i < s.length) {
      if (s.charAt(i) == ch) count += 1
      i += 1
    }
    count
  }

  /** Clears all state for parsing a new diagram.
    *
    * Ports `clear()` from `flowDb.ts`.
    */
  def clear(): Unit = {
    nodes.clear()
    edges.clear()
    classes.clear()
    subgraphs.clear()
    subgraphLookup.clear()
    tooltips.clear()
    vertexCounter = 0
    subCount = 0
    direction = "TB"
    title = ""
    accTitle = ""
    accDescription = ""
    defaultEdgeInterpolate = Nullable.empty
    defaultEdgeStyle = mutable.ArrayBuffer.empty
  }

  /** Simple sanitizer matching `common.sanitizeText()`. */
  private def sanitizeText(txt: String): String =
    txt.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")

  /** Sets a tooltip on a node.
    *
    * Ports `setTooltip()` from `flowDb.ts`.
    */
  def setTooltip(ids: String, tooltip: String): Unit =
    for (id <- ids.split(",")) {
      val trimId = id.trim
      if (trimId.nonEmpty) {
        tooltips(trimId) = sanitizeText(tooltip)
      }
    }

  /** Gets the tooltip for a node.
    *
    * Ports `getTooltip()` from `flowDb.ts`.
    */
  def getTooltip(id: String): Nullable[String] =
    tooltips.get(id) match {
      case Some(tip) => Nullable(tip)
      case _         => Nullable.empty
    }

  /** Sets a link on a node (clickable URL).
    *
    * Ports `setLink()` from `flowDb.ts`.
    */
  def setLink(ids: String, linkStr: String, target: String = "_blank"): Unit = {
    for (id <- ids.split(",")) {
      val trimId = id.trim
      nodes.get(trimId).foreach { node =>
        node.link = Nullable(linkStr)
        node.linkTarget = Nullable(sanitizeText(target))
      }
    }
    setClass(ids, "clickable")
  }

  /** Sets a click event on a node (not functional server-side).
    *
    * Ports `setClickEvent()` from `flowDb.ts`.
    */
  def setClickEvent(ids: String, functionName: String, functionArgs: String = ""): Unit = {
    for (id <- ids.split(",")) {
      val trimId = id.trim
      nodes.get(trimId).foreach { node =>
        node.haveCallback = true
      }
    }
    setClass(ids, "clickable")
  }

  /** Returns the compiled CSS styles for a given node, merging the node's inline styles with all applied CSS class styles.
    *
    * Ports `getCompiledStyles()` from `flowDb.ts`.
    */
  def getCompiledStyles(id: String): mutable.ArrayBuffer[String] = {
    val result = mutable.ArrayBuffer.empty[String]
    nodes.get(id).foreach { node =>
      // Add class-defined styles first
      for (className <- node.cssClasses)
        classes.get(className).foreach { cls =>
          result ++= cls.styles
        }
      // Then add inline styles (which override class styles)
      result ++= node.styles
    }
    result
  }

  /** Indexes nodes by mapping subgraph nodes to their DOM IDs.
    *
    * Ports `indexNodes()` from `flowDb.ts`. This is used by the renderer to resolve node references in edges and subgraphs to actual DOM element IDs.
    */
  def indexNodes(): mutable.LinkedHashMap[String, String] = {
    val index = mutable.LinkedHashMap.empty[String, String]
    for ((id, node) <- nodes)
      index(id) = node.domId
    // Add subgraph IDs as well
    for (sg <- subgraphs)
      index(sg.id) = sg.id
    index
  }

  /** Default style string for nodes. */
  def defaultStyle: String =
    "fill:#ffa;stroke: #f66; stroke-width: 3px; stroke-dasharray: 5, 5;fill:#ffa;stroke: #666;"
}
