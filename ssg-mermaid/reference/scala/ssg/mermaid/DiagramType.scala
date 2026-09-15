/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid/packages/mermaid/src/diagram-api/detectType.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces string-based diagram type detection with enum
 *   Idiom: Scala 3 enum extending java.lang.Enum for cross-platform compatibility
 *   Renames: detectType string returns → DiagramType enum values
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

/** All diagram types supported by Mermaid.
  *
  * Each variant corresponds to a diagram type recognized by the `detect` method. The `keyword` field holds the canonical text keyword used in Mermaid source to introduce the diagram.
  */
enum DiagramType(val keyword: String) extends java.lang.Enum[DiagramType] {

  /** Flowchart (graph) diagram. */
  case Flowchart extends DiagramType("flowchart")

  /** Flowchart v2 (flowchart keyword). */
  case FlowchartV2 extends DiagramType("flowchart-v2")

  /** Graph (alias for flowchart). */
  case Graph extends DiagramType("graph")

  /** Sequence diagram. */
  case Sequence extends DiagramType("sequence")

  /** Gantt chart. */
  case Gantt extends DiagramType("gantt")

  /** Class diagram. */
  case ClassDiagram extends DiagramType("classDiagram")

  /** Class diagram v2. */
  case ClassDiagramV2 extends DiagramType("classDiagram-v2")

  /** State diagram. */
  case StateDiagram extends DiagramType("stateDiagram")

  /** State diagram v2. */
  case StateDiagramV2 extends DiagramType("stateDiagram-v2")

  /** Entity-relationship diagram. */
  case ErDiagram extends DiagramType("erDiagram")

  /** User journey diagram. */
  case Journey extends DiagramType("journey")

  /** Pie chart. */
  case Pie extends DiagramType("pie")

  /** Quadrant chart. */
  case QuadrantChart extends DiagramType("quadrantChart")

  /** Requirement diagram. */
  case Requirement extends DiagramType("requirementDiagram")

  /** Git graph. */
  case GitGraph extends DiagramType("gitGraph")

  /** Mindmap. */
  case Mindmap extends DiagramType("mindmap")

  /** Timeline. */
  case Timeline extends DiagramType("timeline")

  /** C4 context diagram. */
  case C4Context extends DiagramType("C4Context")

  /** C4 container diagram. */
  case C4Container extends DiagramType("C4Container")

  /** C4 component diagram. */
  case C4Component extends DiagramType("C4Component")

  /** C4 deployment diagram. */
  case C4Deployment extends DiagramType("C4Deployment")

  /** C4 dynamic diagram. */
  case C4Dynamic extends DiagramType("C4Dynamic")

  /** Sankey diagram. */
  case Sankey extends DiagramType("sankey-beta")

  /** XY chart (bar/line). */
  case XyChart extends DiagramType("xychart-beta")

  /** Block diagram. */
  case Block extends DiagramType("block-beta")

  /** Packet diagram. */
  case Packet extends DiagramType("packet-beta")

  /** Architecture diagram. */
  case Architecture extends DiagramType("architecture-beta")

  /** Kanban board. */
  case Kanban extends DiagramType("kanban")

  /** Radar chart. */
  case Radar extends DiagramType("radar-beta")

  /** Venn diagram. */
  case Venn extends DiagramType("venn-beta")

  /** Ishikawa (fishbone) diagram. */
  case Ishikawa extends DiagramType("ishikawa")

  /** Cynefin framework diagram. */
  case Cynefin extends DiagramType("cynefin")

  /** Event modeling diagram. */
  case EventModeling extends DiagramType("eventmodeling")

  /** Tree view diagram. */
  case TreeView extends DiagramType("treeView")

  /** Treemap diagram. */
  case Treemap extends DiagramType("treemap")

  /** Wardley map. */
  case Wardley extends DiagramType("wardley")

  /** Info diagram (for debug/version display). */
  case Info extends DiagramType("info")

  /** Error diagram (used for rendering parse/detect errors). */
  case Error extends DiagramType("error")

  /** Unknown diagram type. */
  case Unknown extends DiagramType("unknown")
}

object DiagramType {

  /** Detects the diagram type from the given Mermaid source text.
    *
    * Inspects the first non-directive, non-comment, non-frontmatter line to identify the diagram keyword.
    *
    * @param text
    *   Mermaid diagram source text
    * @return
    *   the detected diagram type, or [[DiagramType.Unknown]] if unrecognized
    */
  def detect(text: String): DiagramType =
    DetectType.detect(text)
}
