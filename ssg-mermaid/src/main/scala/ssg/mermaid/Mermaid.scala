/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid/packages/mermaid/src/mermaid.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces browser-centric mermaid.initialize() + mermaid.render() with pure function
 *   Idiom: Object with render() entry point; diagram type dispatch via pattern match
 *   Renames: mermaid.render() → Mermaid.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import ssg.mermaid.diagrams.architecture.ArchitectureDiagram
import ssg.mermaid.diagrams.block.BlockDiagram
import ssg.mermaid.diagrams.c4.C4Diagram
import ssg.mermaid.diagrams.class_.ClassDiagram
import ssg.mermaid.diagrams.cynefin.CynefinDiagram
import ssg.mermaid.diagrams.er.ErDiagram
import ssg.mermaid.diagrams.error_.ErrorDiagram
import ssg.mermaid.diagrams.eventmodeling.EventModelingDiagram
import ssg.mermaid.diagrams.flowchart.FlowchartDiagram
import ssg.mermaid.diagrams.gantt.GanttDiagram
import ssg.mermaid.diagrams.git.GitDiagram
import ssg.mermaid.diagrams.info.InfoDiagram
import ssg.mermaid.diagrams.ishikawa.IshikawaDiagram
import ssg.mermaid.diagrams.journey.JourneyDiagram
import ssg.mermaid.diagrams.kanban.KanbanDiagram
import ssg.mermaid.diagrams.mindmap.MindmapDiagram
import ssg.mermaid.diagrams.packet.PacketDiagram
import ssg.mermaid.diagrams.pie.PieDiagram
import ssg.mermaid.diagrams.quadrant.QuadrantDiagram
import ssg.mermaid.diagrams.radar.RadarDiagram
import ssg.mermaid.diagrams.requirement.RequirementDiagram
import ssg.mermaid.diagrams.sankey.SankeyDiagram
import ssg.mermaid.diagrams.sequence.SequenceDiagram
import ssg.mermaid.diagrams.state.StateDiagram
import ssg.mermaid.diagrams.timeline.TimelineDiagram
import ssg.mermaid.diagrams.treemap.TreemapDiagram
import ssg.mermaid.diagrams.treeview.TreeViewDiagram
import ssg.mermaid.diagrams.venn.VennDiagram
import ssg.mermaid.diagrams.wardley.WardleyDiagram
import ssg.mermaid.diagrams.xychart.XyChartDiagram

/** Top-level entry point for Mermaid diagram rendering.
  *
  * Detects the diagram type from the input text and dispatches to the appropriate diagram renderer. Supports all 31 diagram types.
  *
  * Usage:
  * {{{
  * val svg = Mermaid.render("graph TD\n    A-->B")
  * }}}
  */
object Mermaid {

  /** Renders a Mermaid diagram to SVG.
    *
    * Detects the diagram type from the input text and dispatches to the appropriate renderer.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param config
    *   optional configuration overrides
    * @return
    *   SVG markup string, or an HTML comment indicating an unsupported diagram type
    */
  def render(input: String, config: MermaidConfig = MermaidConfig()): String = {
    val diagramType = DetectType.detect(input)
    diagramType match {
      case DiagramType.Flowchart | DiagramType.FlowchartV2 | DiagramType.Graph =>
        FlowchartDiagram.render(input, config)
      case DiagramType.Sequence =>
        SequenceDiagram.render(input, config)
      case DiagramType.ClassDiagram | DiagramType.ClassDiagramV2 =>
        ClassDiagram.render(input, config)
      case DiagramType.StateDiagram | DiagramType.StateDiagramV2 =>
        StateDiagram.render(input, config)
      case DiagramType.ErDiagram =>
        ErDiagram.render(input, config)
      case DiagramType.Pie =>
        PieDiagram.render(input, config)
      case DiagramType.Gantt =>
        GanttDiagram.render(input, config)
      case DiagramType.Timeline =>
        TimelineDiagram.render(input, config)
      case DiagramType.Journey =>
        JourneyDiagram.render(input, config)
      case DiagramType.Mindmap =>
        MindmapDiagram.render(input, config)
      case DiagramType.GitGraph =>
        GitDiagram.render(input, config)
      case DiagramType.XyChart =>
        XyChartDiagram.render(input, config)
      case DiagramType.QuadrantChart =>
        QuadrantDiagram.render(input, config)
      case DiagramType.Requirement =>
        RequirementDiagram.render(input, config)
      case DiagramType.Sankey =>
        SankeyDiagram.render(input, config)
      case DiagramType.Block =>
        BlockDiagram.render(input, config)
      case DiagramType.Architecture =>
        ArchitectureDiagram.render(input, config)
      case DiagramType.Packet =>
        PacketDiagram.render(input, config)
      case DiagramType.Radar =>
        RadarDiagram.render(input, config)
      case DiagramType.Kanban =>
        KanbanDiagram.render(input, config)
      case DiagramType.Venn =>
        VennDiagram.render(input, config)
      case DiagramType.Ishikawa =>
        IshikawaDiagram.render(input, config)
      case DiagramType.C4Context | DiagramType.C4Container | DiagramType.C4Component | DiagramType.C4Deployment | DiagramType.C4Dynamic =>
        C4Diagram.render(input, config)
      case DiagramType.Cynefin =>
        CynefinDiagram.render(input, config)
      case DiagramType.EventModeling =>
        EventModelingDiagram.render(input, config)
      case DiagramType.TreeView =>
        TreeViewDiagram.render(input, config)
      case DiagramType.Treemap =>
        TreemapDiagram.render(input, config)
      case DiagramType.Wardley =>
        WardleyDiagram.render(input, config)
      case DiagramType.Info =>
        InfoDiagram.render(input, config)
      case DiagramType.Error =>
        ErrorDiagram.render(input, config)
      case other =>
        s"<!-- Unsupported diagram type: ${other.keyword} -->"
    }
  }
}
