/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/config.type.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Subset of the full 1500-line TypeScript config — ports fields actually used by renderers
 *   Idiom: Immutable case classes with defaults; expand as diagram implementations need more fields
 *   Renames: MermaidConfig interface → MermaidConfig case class
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import ssg.commons.Nullable

/** Top-level Mermaid configuration.
  *
  * This is a subset of the full Mermaid config type. Fields are added here as diagram renderers need them. The original TypeScript config has ~200 fields across all diagram types.
  *
  * @param theme
  *   theme name: "default", "dark", "forest", "neutral", "base"
  * @param themeVariables
  *   custom theme variable overrides (color names → CSS color values)
  * @param themeCSS
  *   additional CSS to inject into the SVG
  * @param look
  *   diagram look: "classic" or "handDrawn"
  * @param fontFamily
  *   CSS font-family for rendered text
  * @param fontSize
  *   base font size in pixels
  * @param securityLevel
  *   trust level for parsed diagrams: "strict", "loose", "antiscript", "sandbox"
  * @param startOnLoad
  *   whether to render diagrams on page load (relevant for browser usage)
  * @param logLevel
  *   logging verbosity: 0=trace, 1=debug, 2=info, 3=warn, 4=error, 5=fatal
  * @param darkMode
  *   whether dark mode is enabled
  * @param htmlLabels
  *   whether to use HTML labels (foreignObject) in diagrams
  * @param wrap
  *   whether to wrap text in labels
  * @param maxTextSize
  *   maximum allowed size of user text diagrams
  * @param maxEdges
  *   maximum number of edges that can be drawn
  * @param deterministicIds
  *   whether generated SVG IDs should be deterministic
  * @param deterministicIDSeed
  *   seed for deterministic ID generation
  * @param arrowMarkerAbsolute
  *   whether arrow markers use absolute paths
  * @param markdownAutoWrap
  *   whether to auto-wrap markdown text
  * @param flowchart
  *   flowchart-specific configuration
  * @param sequence
  *   sequence diagram-specific configuration
  * @param gantt
  *   gantt chart-specific configuration
  * @param pie
  *   pie chart-specific configuration
  * @param er
  *   ER diagram-specific configuration
  * @param gitGraph
  *   git graph-specific configuration
  * @param mindmap
  *   mindmap-specific configuration
  * @param timeline
  *   timeline-specific configuration
  */
final case class MermaidConfig(
  theme:               String = "default",
  themeVariables:      Map[String, String] = Map.empty,
  themeCSS:            String = "",
  look:                String = "classic",
  fontFamily:          String = "\"trebuchet ms\", verdana, arial, sans-serif",
  fontSize:            Int = 16,
  securityLevel:       String = "strict",
  startOnLoad:         Boolean = true,
  logLevel:            Int = 2,
  darkMode:            Boolean = false,
  htmlLabels:          Boolean = true,
  wrap:                Boolean = false,
  maxTextSize:         Int = 50000,
  maxEdges:            Int = 500,
  deterministicIds:    Boolean = false,
  deterministicIDSeed: Nullable[String] = Nullable.empty,
  arrowMarkerAbsolute: Boolean = false,
  markdownAutoWrap:    Boolean = true,
  flowchart:           FlowchartConfig = FlowchartConfig(),
  sequence:            SequenceConfig = SequenceConfig(),
  gantt:               GanttConfig = GanttConfig(),
  pie:                 PieConfig = PieConfig(),
  er:                  ErConfig = ErConfig(),
  gitGraph:            GitGraphConfig = GitGraphConfig(),
  mindmap:             MindmapConfig = MindmapConfig(),
  timeline:            TimelineConfig = TimelineConfig()
)

/** Flowchart-specific configuration.
  *
  * @param diagramPadding
  *   padding around the flowchart diagram
  * @param htmlLabels
  *   whether to use HTML labels
  * @param nodeSpacing
  *   spacing between nodes
  * @param rankSpacing
  *   spacing between ranks (rows/columns)
  * @param curve
  *   curve type for edges (e.g. "basis", "linear", "cardinal")
  * @param padding
  *   padding inside node boxes
  * @param defaultRenderer
  *   rendering engine: "dagre-d3", "dagre-wrapper", "elk"
  * @param wrappingWidth
  *   maximum width before text wrapping
  */
final case class FlowchartConfig(
  diagramPadding:  Int = 8,
  htmlLabels:      Boolean = true,
  nodeSpacing:     Int = 50,
  rankSpacing:     Int = 50,
  curve:           String = "basis",
  padding:         Int = 15,
  defaultRenderer: String = "dagre-wrapper",
  wrappingWidth:   Int = 200
)

/** Sequence diagram-specific configuration.
  *
  * @param diagramMarginX
  *   horizontal margin around the sequence diagram
  * @param diagramMarginY
  *   vertical margin around the sequence diagram
  * @param actorMargin
  *   margin between actors
  * @param width
  *   width of actor boxes
  * @param height
  *   height of actor boxes
  * @param boxMargin
  *   margin around activation boxes
  * @param boxTextMargin
  *   text margin within boxes
  * @param noteMargin
  *   margin around notes
  * @param messageMargin
  *   margin between messages
  * @param mirrorActors
  *   whether to mirror actors at the bottom
  * @param bottomMarginAdj
  *   bottom margin adjustment
  * @param showSequenceNumbers
  *   whether to show sequence numbers
  * @param useMaxWidth
  *   whether to use maximum width
  * @param rightAngles
  *   whether to use right angles for self-referencing messages
  * @param wrap
  *   whether to wrap message text
  */
final case class SequenceConfig(
  diagramMarginX:      Int = 50,
  diagramMarginY:      Int = 10,
  actorMargin:         Int = 50,
  width:               Int = 150,
  height:              Int = 65,
  boxMargin:           Int = 10,
  boxTextMargin:       Int = 5,
  noteMargin:          Int = 10,
  messageMargin:       Int = 35,
  mirrorActors:        Boolean = true,
  bottomMarginAdj:     Int = 1,
  showSequenceNumbers: Boolean = false,
  useMaxWidth:         Boolean = true,
  rightAngles:         Boolean = false,
  wrap:                Boolean = false
)

/** Gantt chart-specific configuration.
  *
  * @param titleTopMargin
  *   margin above the title
  * @param barHeight
  *   height of task bars
  * @param barGap
  *   gap between task bars
  * @param topPadding
  *   padding above the chart
  * @param rightPadding
  *   padding to the right
  * @param leftPadding
  *   padding to the left
  * @param gridLineStartPadding
  *   padding before grid lines start
  * @param fontSize
  *   font size for labels
  * @param sectionFontSize
  *   font size for section labels
  * @param numberSectionStyles
  *   number of alternating section styles
  * @param axisFormat
  *   date format for the time axis
  * @param topAxis
  *   whether to show the time axis on top
  * @param displayMode
  *   display mode for tasks
  * @param weekday
  *   first day of the week
  */
final case class GanttConfig(
  titleTopMargin:       Int = 25,
  barHeight:            Int = 20,
  barGap:               Int = 4,
  topPadding:           Int = 50,
  rightPadding:         Int = 75,
  leftPadding:          Int = 75,
  gridLineStartPadding: Int = 35,
  fontSize:             Int = 11,
  sectionFontSize:      Int = 11,
  numberSectionStyles:  Int = 4,
  axisFormat:           String = "%Y-%m-%d",
  topAxis:              Boolean = false,
  displayMode:          String = "",
  weekday:              String = "sunday"
)

/** Pie chart-specific configuration.
  *
  * @param textPosition
  *   radial position of labels (0 to 1, where 0 is center and 1 is edge)
  * @param useMaxWidth
  *   whether to use the full available width
  * @param useWidth
  *   fixed width override (0 means auto)
  */
final case class PieConfig(
  textPosition: Double = 0.75,
  useMaxWidth:  Boolean = true,
  useWidth:     Int = 0
)

/** ER diagram-specific configuration.
  *
  * @param diagramPadding
  *   padding around the ER diagram
  * @param layoutDirection
  *   layout direction: "TB" (top-bottom) or "LR" (left-right)
  * @param minEntityWidth
  *   minimum width for entity boxes
  * @param minEntityHeight
  *   minimum height for entity boxes
  * @param entityPadding
  *   padding inside entity boxes
  * @param stroke
  *   stroke color for borders
  * @param fill
  *   fill color for entities
  * @param fontSize
  *   font size for labels
  * @param useMaxWidth
  *   whether to use full available width
  */
final case class ErConfig(
  diagramPadding:  Int = 20,
  layoutDirection: String = "TB",
  minEntityWidth:  Int = 100,
  minEntityHeight: Int = 75,
  entityPadding:   Int = 15,
  stroke:          String = "gray",
  fill:            String = "honeydew",
  fontSize:        Int = 12,
  useMaxWidth:     Boolean = true
)

/** Git graph-specific configuration.
  *
  * @param diagramPadding
  *   padding around the diagram
  * @param nodeSpacing
  *   spacing between commit nodes
  * @param nodeLabel
  *   label configuration for commit nodes
  * @param mainBranchName
  *   name of the main branch
  * @param mainBranchOrder
  *   display order of the main branch
  * @param showCommitLabel
  *   whether to show commit labels
  * @param showBranches
  *   whether to show branch labels
  * @param rotateCommitLabel
  *   whether to rotate commit labels
  */
final case class GitGraphConfig(
  diagramPadding:    Int = 8,
  nodeSpacing:       Int = 150,
  nodeLabel:         String = "",
  mainBranchName:    String = "main",
  mainBranchOrder:   Int = 0,
  showCommitLabel:   Boolean = true,
  showBranches:      Boolean = true,
  rotateCommitLabel: Boolean = true
)

/** Mindmap-specific configuration.
  *
  * @param padding
  *   padding around nodes
  * @param maxNodeWidth
  *   maximum width for nodes before text wrapping
  * @param useMaxWidth
  *   whether to use the full available width
  */
final case class MindmapConfig(
  padding:      Int = 10,
  maxNodeWidth: Int = 200,
  useMaxWidth:  Boolean = true
)

/** Timeline-specific configuration.
  *
  * @param diagramMarginX
  *   horizontal margin
  * @param diagramMarginY
  *   vertical margin
  * @param leftMargin
  *   left margin for the timeline axis
  * @param width
  *   width of event boxes
  * @param height
  *   height of event boxes
  * @param padding
  *   padding inside event boxes
  * @param boxMargin
  *   margin between event boxes
  * @param boxTextMargin
  *   text margin within boxes
  * @param noteMargin
  *   margin around notes
  * @param messageMargin
  *   margin between messages
  * @param useMaxWidth
  *   whether to use the full available width
  */
final case class TimelineConfig(
  diagramMarginX: Int = 50,
  diagramMarginY: Int = 10,
  leftMargin:     Int = 150,
  width:          Int = 150,
  height:         Int = 50,
  padding:        Int = 8,
  boxMargin:      Int = 10,
  boxTextMargin:  Int = 5,
  noteMargin:     Int = 10,
  messageMargin:  Int = 35,
  useMaxWidth:    Boolean = true
)

object MermaidConfig {

  /** Default configuration with all default values. */
  val Default: MermaidConfig = MermaidConfig()

  /** Available theme names. */
  val Themes: Set[String] = Set("default", "dark", "forest", "neutral", "base")

  /** Available security levels. */
  val SecurityLevels: Set[String] = Set("strict", "loose", "antiscript", "sandbox")
}
