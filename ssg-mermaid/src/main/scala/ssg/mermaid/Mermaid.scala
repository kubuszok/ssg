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
    // Mirrors upstream preprocess.ts: frontmatter is stripped and extracted
    // ONCE, before detection and before any parser sees the text, so the
    // parsers never receive the leading `---` delimiters (ISS-1056). The
    // extracted `title` is applied to each diagram db, mirroring
    // Diagram.ts:41-43 `if (metadata.title) { db.setDiagramTitle?.(metadata.title); }`.
    // The extracted `config` is surfaced by Preprocess but its application is
    // owned by ISS-1057 (init-directive/config-application).
    val pre   = Preprocess.processFrontmatter(input)
    val text  = pre.text
    val title = pre.title

    val diagramType = DetectType.detect(text)
    diagramType match {
      case DiagramType.Flowchart | DiagramType.FlowchartV2 | DiagramType.Graph =>
        FlowchartDiagram.render(text, config, title)
      case DiagramType.Sequence =>
        SequenceDiagram.render(text, config, title)
      case DiagramType.ClassDiagram | DiagramType.ClassDiagramV2 =>
        ClassDiagram.render(text, config, title)
      case DiagramType.StateDiagram | DiagramType.StateDiagramV2 =>
        StateDiagram.render(text, config, title)
      case DiagramType.ErDiagram =>
        ErDiagram.render(text, config, title)
      case DiagramType.Pie =>
        PieDiagram.render(text, config, title)
      case DiagramType.Gantt =>
        GanttDiagram.render(text, config, title)
      case DiagramType.Timeline =>
        TimelineDiagram.render(text, config, title)
      case DiagramType.Journey =>
        JourneyDiagram.render(text, config, title)
      case DiagramType.Mindmap =>
        MindmapDiagram.render(text, config, title)
      case DiagramType.GitGraph =>
        GitDiagram.render(text, config, title)
      case DiagramType.XyChart =>
        XyChartDiagram.render(text, config, title)
      case DiagramType.QuadrantChart =>
        QuadrantDiagram.render(text, config, title)
      case DiagramType.Requirement =>
        RequirementDiagram.render(text, config, title)
      case DiagramType.Sankey =>
        SankeyDiagram.render(text, config, title)
      case DiagramType.Block =>
        BlockDiagram.render(text, config, title)
      case DiagramType.Architecture =>
        ArchitectureDiagram.render(text, config, title)
      case DiagramType.Packet =>
        PacketDiagram.render(text, config, title)
      case DiagramType.Radar =>
        RadarDiagram.render(text, config, title)
      case DiagramType.Kanban =>
        KanbanDiagram.render(text, config, title)
      case DiagramType.Venn =>
        VennDiagram.render(text, config, title)
      case DiagramType.Ishikawa =>
        IshikawaDiagram.render(text, config, title)
      case DiagramType.C4Context | DiagramType.C4Container | DiagramType.C4Component | DiagramType.C4Deployment | DiagramType.C4Dynamic =>
        C4Diagram.render(text, config, title)
      case DiagramType.Cynefin =>
        CynefinDiagram.render(text, config, title)
      case DiagramType.EventModeling =>
        EventModelingDiagram.render(text, config, title)
      case DiagramType.TreeView =>
        TreeViewDiagram.render(text, config, title)
      case DiagramType.Treemap =>
        TreemapDiagram.render(text, config, title)
      case DiagramType.Wardley =>
        WardleyDiagram.render(text, config, title)
      case DiagramType.Info =>
        // InfoDb has no title field — mirrors Diagram.ts:42 optional-chaining
        // `db.setDiagramTitle?.(...)` being a no-op when absent.
        InfoDiagram.render(text, config)
      case DiagramType.Error =>
        // ErrorDb has no title field — mirrors Diagram.ts:42 optional-chaining
        // `db.setDiagramTitle?.(...)` being a no-op when absent.
        ErrorDiagram.render(text, config)
      case other =>
        s"<!-- Unsupported diagram type: ${other.keyword} -->"
    }
  }
}
