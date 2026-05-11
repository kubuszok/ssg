/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Converts a DotGraph AST to a Graph[NodeLabel, EdgeLabel] for dagre layout.
 */
package ssg
package graphviz
package render

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.graphs.commons.layout.dagre.{ EdgeLabel, GraphLabel, NodeLabel }
import ssg.graphs.commons.layout.graph.Graph

import ssg.graphviz.parse._

object GraphBuilder {

  private val DefaultFontSize:   Double = 14.0
  private val DefaultCharWidth:  Double = 8.0
  private val DefaultNodeWidth:  Double = 60.0
  private val DefaultNodeHeight: Double = 40.0
  private val LabelPaddingX:     Double = 20.0
  private val LabelPaddingY:     Double = 10.0

  def build(dot: DotGraph): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](
      isDirected = dot.isDirected,
      isMultigraph = false,
      isCompound = true
    )
    val graphLabel = new GraphLabel()
    val ctx = new BuildContext(g, graphLabel)

    dot.stmts.foreach(stmt => processStmt(stmt, ctx, None))

    applyGraphAttrs(ctx.graphAttrs, graphLabel)
    g.setGraph(graphLabel)
    g
  }

  private final class BuildContext(
    val graph:            Graph[NodeLabel, EdgeLabel],
    val graphLabel:       GraphLabel,
    val defaultNodeAttrs: mutable.Map[String, String] = mutable.LinkedHashMap.empty,
    val defaultEdgeAttrs: mutable.Map[String, String] = mutable.LinkedHashMap.empty,
    val graphAttrs:       mutable.Map[String, String] = mutable.LinkedHashMap.empty
  )

  private def processStmt(stmt: DotStmt, ctx: BuildContext, parentSubgraph: Option[String]): Unit = {
    stmt match {
      case DotNodeStmt(id, attrs) =>
        ensureNode(id.id, ctx, parentSubgraph)
        applyNodeAttrs(id.id, attrs, ctx)

      case DotEdgeStmt(nodes, attrs) =>
        nodes.foreach(n => ensureNode(n.id, ctx, parentSubgraph))
        nodes.sliding(2).foreach { pair =>
          if (pair.size == 2) {
            val edgeLabel = new EdgeLabel()
            applyEdgeAttrs(edgeLabel, attrs, ctx)
            ctx.graph.setEdge(pair(0).id, pair(1).id, edgeLabel)
          }
        }

      case DotAttrStmt(target, attrs) =>
        target match {
          case DotAttrTarget.Graph => attrs.foreach(a => ctx.graphAttrs(a.key) = a.value)
          case DotAttrTarget.Node  => attrs.foreach(a => ctx.defaultNodeAttrs(a.key) = a.value)
          case DotAttrTarget.Edge  => attrs.foreach(a => ctx.defaultEdgeAttrs(a.key) = a.value)
        }

      case DotSubgraphStmt(id, stmts) =>
        val subId = id.getOrElse(s"cluster_${ctx.graph.nodeCount}")
        stmts.foreach(s => processStmt(s, ctx, Some(subId)))

      case DotAssignStmt(key, value) =>
        ctx.graphAttrs(key) = value
    }
  }

  private def ensureNode(id: String, ctx: BuildContext, parentSubgraph: Option[String]): Unit = {
    if (!ctx.graph.hasNode(id)) {
      val label = new NodeLabel()
      label.label = id
      val estimatedWidth = estimateTextWidth(id)
      label.width = math.max(estimatedWidth + LabelPaddingX, DefaultNodeWidth)
      label.height = DefaultNodeHeight + LabelPaddingY
      ctx.defaultNodeAttrs.foreach { (k, v) =>
        applySingleNodeAttr(label, k, v)
      }
      ctx.graph.setNode(id, label)
    }
    parentSubgraph.foreach { parent =>
      if (!ctx.graph.hasNode(parent)) {
        val parentLabel = new NodeLabel()
        parentLabel.label = parent
        parentLabel.width = 0
        parentLabel.height = 0
        ctx.graph.setNode(parent, parentLabel)
      }
      ctx.graph.setParent(id, parent)
    }
  }

  private def applyNodeAttrs(id: String, attrs: Seq[DotAttr], ctx: BuildContext): Unit = {
    if (attrs.nonEmpty) {
      val label = ctx.graph.node(id)
      attrs.foreach(a => applySingleNodeAttr(label, a.key, a.value))
      // Recalculate width from label text if label was changed
      attrs.find(_.key == "label").foreach { a =>
        val textWidth = estimateTextWidth(a.value)
        label.width = math.max(textWidth + LabelPaddingX, DefaultNodeWidth)
      }
      attrs.find(_.key == "width").foreach { a =>
        parseDoubleSafe(a.value).foreach(v => label.width = v * 72.0)
      }
      attrs.find(_.key == "height").foreach { a =>
        parseDoubleSafe(a.value).foreach(v => label.height = v * 72.0)
      }
    }
  }

  private def applySingleNodeAttr(label: NodeLabel, key: String, value: String): Unit = {
    key match {
      case "label"   => label.label = value
      case "width"   => parseDoubleSafe(value).foreach(v => label.width = v * 72.0)
      case "height"  => parseDoubleSafe(value).foreach(v => label.height = v * 72.0)
      case "padding" => parseDoubleSafe(value).foreach(v => label.padding = v)
      case _         => () // Unhandled attributes are silently ignored
    }
  }

  private def applyEdgeAttrs(edgeLabel: EdgeLabel, attrs: Seq[DotAttr], ctx: BuildContext): Unit = {
    ctx.defaultEdgeAttrs.foreach { (k, v) =>
      applySingleEdgeAttr(edgeLabel, k, v)
    }
    attrs.foreach(a => applySingleEdgeAttr(edgeLabel, a.key, a.value))
  }

  private def applySingleEdgeAttr(label: EdgeLabel, key: String, value: String): Unit = {
    key match {
      case "weight"  => parseDoubleSafe(value).foreach(v => label.weight = v)
      case "minlen"  => parseIntSafe(value).foreach(v => label.minlen = v)
      case "label"   =>
        val textWidth = estimateTextWidth(value)
        label.width = textWidth
        label.height = DefaultFontSize + LabelPaddingY
      case "labelpos" => label.labelpos = value
      case _          => () // Unhandled attributes are silently ignored
    }
  }

  private def applyGraphAttrs(attrs: mutable.Map[String, String], graphLabel: GraphLabel): Unit = {
    attrs.get("rankdir").foreach(v => graphLabel.rankdir = v.toUpperCase)
    attrs.get("nodesep").foreach(v => parseDoubleSafe(v).foreach(d => graphLabel.nodesep = d))
    attrs.get("ranksep").foreach(v => parseDoubleSafe(v).foreach(d => graphLabel.ranksep = d))
    attrs.get("marginx").orElse(attrs.get("margin")).foreach { v =>
      parseDoubleSafe(v).foreach(d => graphLabel.marginx = d)
    }
    attrs.get("marginy").orElse(attrs.get("margin")).foreach { v =>
      parseDoubleSafe(v).foreach(d => graphLabel.marginy = d)
    }
    attrs.get("ranker").foreach(v => graphLabel.ranker = v)
    attrs.get("acyclicer").foreach(v => graphLabel.acyclicer = v)
  }

  private def estimateTextWidth(text: String): Double = {
    // Strip HTML tags for width estimation
    val stripped = boundary {
      if (!text.contains('<')) { break(text) }
      val sb = new StringBuilder
      var inTag = false
      text.foreach { ch =>
        if (ch == '<') { inTag = true }
        else if (ch == '>') { inTag = false }
        else if (!inTag) { sb.append(ch) }
      }
      sb.toString
    }
    stripped.length * DefaultCharWidth
  }

  private def parseDoubleSafe(s: String): Option[Double] = {
    try { Some(s.toDouble) }
    catch { case _: NumberFormatException => None }
  }

  private def parseIntSafe(s: String): Option[Int] = {
    try { Some(s.toInt) }
    catch { case _: NumberFormatException => None }
  }
}
