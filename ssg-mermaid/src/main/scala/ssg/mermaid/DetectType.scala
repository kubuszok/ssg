/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagram-api/detectType.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces dynamic detector registry with static keyword matching
 *   Idiom: Pattern matching on stripped text; boundary/break for early return
 *   Renames: detectType → DetectType.detect
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import scala.util.boundary
import scala.util.boundary.break
import scala.util.matching.Regex

/** Diagram type detection from Mermaid source text.
  *
  * Ports the logic from `detectType.ts` and `regexes.ts`. The original uses a dynamic detector registry; this port uses static keyword matching since all diagram types are known at compile time.
  *
  * The detection process:
  *   1. Strip YAML front matter (`---...---`)
  *   1. Strip `%%{...}%%` directives
  *   1. Strip `%%` comments
  *   1. Match the first significant token against known diagram keywords
  */
object DetectType {

  // --- Regexes from regexes.ts ---

  /** Matches Jekyll-style front matter blocks.
    *
    * Based on regex used by Jekyll: https://github.com/jekyll/jekyll/blob/6dd3cc21c40b98054851846425af06c64f9fb466/lib/jekyll/document.rb#L10
    */
  private val FrontMatterRegex: Regex = """(?s)^-{3}\s*[\n\r](.*?)[\n\r]-{3}\s*[\n\r]+""".r

  /** Matches `%%{...}%%` directives. */
  private val DirectiveRegex: Regex =
    """(?i)%%\{\s*(?:(\w+)\s*:|(\w+))\s*(?:(\w+)|([\s\S]*?))\s*(?:\}%%|$)""".r

  /** Matches `%% comment` lines. */
  private val AnyCommentRegex: Regex = """\s*%%.*\n""".r

  /** Detects the diagram type from Mermaid source text.
    *
    * Takes into consideration the possible existence of an `%%init` directive.
    *
    * @param text
    *   the text defining the graph
    * @return
    *   the detected [[DiagramType]], or [[DiagramType.Unknown]] if no match
    */
  def detect(text: String): DiagramType = {
    // Strip front matter, directives, and comments
    var cleaned = FrontMatterRegex.replaceFirstIn(text, "")
    cleaned = DirectiveRegex.replaceAllIn(cleaned, "")
    cleaned = AnyCommentRegex.replaceAllIn(cleaned, "\n")
    cleaned = cleaned.trim

    matchKeyword(cleaned)
  }

  /** Parses a `%%{init: ...}%%` directive from the given text, extracting the JSON body.
    *
    * @param text
    *   Mermaid source text
    * @return
    *   the directive body content if found, or empty string if not
    */
  def extractDirectiveBody(text: String): String = {
    val initPattern = """(?i)%%\{\s*(?:init|initialize)\s*:\s*([\s\S]*?)\s*\}%%""".r
    initPattern.findFirstMatchIn(text) match {
      case Some(m) => m.group(1).trim
      case None    => ""
    }
  }

  /** Removes all `%%{...}%%` directives from the text.
    *
    * Mirrors the `removeDirectives` function in the original.
    */
  def removeDirectives(text: String): String =
    DirectiveRegex.replaceAllIn(text, "")

  /** Removes all `%%` comment lines from the text. */
  def removeComments(text: String): String =
    AnyCommentRegex.replaceAllIn(text, "\n")

  /** Removes YAML front matter from the text. */
  def removeFrontMatter(text: String): String =
    FrontMatterRegex.replaceFirstIn(text, "")

  /** Matches the first token of the cleaned text against known diagram keywords.
    *
    * The order matters: more specific keywords (like "flowchart-v2") must be tested before less specific ones (like "flowchart"). Keywords starting with the same prefix are ordered longest-first.
    */
  private def matchKeyword(text: String): DiagramType = {
    boundary[DiagramType] {
      // Get the first line for matching
      val firstLine = text.split("[\n\r]", 2)(0).trim.toLowerCase

      // Try each diagram type keyword, checking if the first line starts with it
      // Order: more specific before less specific

      // Flowchart variants
      if (firstLine.startsWith("flowchart-v2")) break(DiagramType.FlowchartV2)
      if (firstLine.startsWith("flowchart")) break(DiagramType.FlowchartV2) // modern flowchart keyword implies v2
      if (firstLine.startsWith("graph")) break(DiagramType.Flowchart)

      // Sequence
      if (firstLine.startsWith("sequencediagram") || firstLine.startsWith("sequence-diagram")) {
        break(DiagramType.Sequence)
      }
      if (firstLine.startsWith("sequencediagram")) break(DiagramType.Sequence)

      // Gantt
      if (firstLine.startsWith("gantt")) break(DiagramType.Gantt)

      // Class diagram variants
      if (firstLine.startsWith("classdiagram-v2")) break(DiagramType.ClassDiagramV2)
      if (firstLine.startsWith("classdiagram")) break(DiagramType.ClassDiagram)

      // State diagram variants
      if (firstLine.startsWith("statediagram-v2")) break(DiagramType.StateDiagramV2)
      if (firstLine.startsWith("statediagram")) break(DiagramType.StateDiagram)

      // ER diagram
      if (firstLine.startsWith("erdiagram")) break(DiagramType.ErDiagram)

      // Journey
      if (firstLine.startsWith("journey")) break(DiagramType.Journey)

      // Pie
      if (firstLine.startsWith("pie")) break(DiagramType.Pie)

      // Quadrant chart
      if (firstLine.startsWith("quadrantchart")) break(DiagramType.QuadrantChart)

      // Requirement
      if (firstLine.startsWith("requirementdiagram")) break(DiagramType.Requirement)

      // Git graph
      if (firstLine.startsWith("gitgraph")) break(DiagramType.GitGraph)

      // Mindmap
      if (firstLine.startsWith("mindmap")) break(DiagramType.Mindmap)

      // Timeline
      if (firstLine.startsWith("timeline")) break(DiagramType.Timeline)

      // C4 diagrams (case-insensitive, but original uses exact casing)
      if (firstLine.startsWith("c4context")) break(DiagramType.C4Context)
      if (firstLine.startsWith("c4container")) break(DiagramType.C4Container)
      if (firstLine.startsWith("c4component")) break(DiagramType.C4Component)
      if (firstLine.startsWith("c4deployment")) break(DiagramType.C4Deployment)
      if (firstLine.startsWith("c4dynamic")) break(DiagramType.C4Dynamic)

      // Beta diagrams
      if (firstLine.startsWith("sankey-beta")) break(DiagramType.Sankey)
      if (firstLine.startsWith("xychart-beta")) break(DiagramType.XyChart)
      if (firstLine.startsWith("block-beta")) break(DiagramType.Block)
      if (firstLine.startsWith("packet-beta")) break(DiagramType.Packet)
      if (firstLine.startsWith("architecture-beta")) break(DiagramType.Architecture)

      // Radar
      if (firstLine.startsWith("radar-beta")) break(DiagramType.Radar)

      // Venn
      if (firstLine.startsWith("venn-beta")) break(DiagramType.Venn)

      // Kanban
      if (firstLine.startsWith("kanban")) break(DiagramType.Kanban)

      // Ishikawa
      if (firstLine.startsWith("ishikawa")) break(DiagramType.Ishikawa)

      // Cynefin
      if (firstLine.startsWith("cynefin")) break(DiagramType.Cynefin)

      // Event modeling
      if (firstLine.startsWith("eventmodeling")) break(DiagramType.EventModeling)

      // Tree view
      if (firstLine.startsWith("treeview")) break(DiagramType.TreeView)

      // Treemap
      if (firstLine.startsWith("treemap")) break(DiagramType.Treemap)

      // Wardley
      if (firstLine.startsWith("wardley")) break(DiagramType.Wardley)

      // Info
      if (firstLine.startsWith("info")) break(DiagramType.Info)

      // Error
      if (firstLine.startsWith("error")) break(DiagramType.Error)

      // No match
      DiagramType.Unknown
    }
  }
}
