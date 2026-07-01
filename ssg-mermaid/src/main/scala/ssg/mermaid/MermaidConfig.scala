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

import lowlevel.Nullable

import ssg.data.AsDataView
import ssg.data.DataView
import ssg.data.FromDataView

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
  * @param handDrawnSeed
  *   seed used for the hand-drawn look (config.schema.yaml:84). Feeds the rough.js PRNG so `look="handDrawn"` output is reproducible; the default 0 upstream means "give a random seed" (the
  *   [[ssg.graphs.commons.rough.Random]] `seed==0` Math.random fallback path), so tests pin a non-zero seed for deterministic output.
  * @param fontFamily
  *   CSS font-family for rendered text
  * @param fontSize
  *   base font size in pixels
  * @param securityLevel
  *   trust level for parsed diagrams: "strict", "loose", "antiscript", "sandbox". Consulted by [[ssg.mermaid.util.Utils.formatUrl]] when a clickable node link is added (flowDb.ts:349): under any
  *   level other than "loose" the link URL is run through [[ssg.mermaid.util.Utils.sanitizeUrl]], while "loose" passes the author URL through verbatim. "sandbox" additionally forces the link target
  *   to "_top" (FlowchartRenderer, nodes.js:68-76).
  * @param startOnLoad
  *   whether to render diagrams on page load. Browser bootstrap flag (mermaid.ts:144-146 `mermaidAPI.updateSiteConfig({ startOnLoad })`): an environment/bootstrap flag with no effect in SSG's
  *   pure-render model, which has no DOMContentLoaded event. Kept for full-fidelity schema parity.
  * @param logLevel
  *   logging verbosity: 0=trace, 1=debug, 2=info, 3=warn, 4=error, 5=fatal. Upstream `setLogLevel` controls console verbosity of the `log.*` calls; SSG's pure render emits no log output, so this is
  *   an environment/host-config flag consumed by the host's logger injection ([[ssg.commons.Logger]]) rather than by the renderer.
  * @param darkMode
  *   whether dark mode is enabled. Upstream `darkMode` is a theme-internal flag (a property of the Theme instance, theme-base.js:33-65); top-level `config.darkMode` is never copied onto the active
  *   theme by `getThemeVariables` (mermaidAPI.ts:492-499), so it has no faithful effect distinct from selecting `theme="dark"` or passing `themeVariables.darkMode`. The theme's own darkMode (driven
  *   by theme selection / [[ssg.mermaid.theme.ThemeVariables]] `darkMode` key) remains the single source of truth.
  * @param htmlLabels
  *   whether to use HTML labels (foreignObject) in diagrams
  * @param wrap
  *   whether to wrap text in labels. Fed as the global auto-wrap default into sequence diagrams (sequenceDiagram.ts:13-14 `init: ({ wrap }) => db.setWrap(wrap)`): every message/actor/note whose own
  *   wrap is unset inherits this via [[ssg.mermaid.diagrams.sequence.SequenceDb.autoWrap]]. Also the fold target of the `%%{wrap}%%` directive (preprocess.ts:35-39).
  * @param maxTextSize
  *   maximum allowed size of user text diagrams. Guarded at the top of [[Mermaid.render]] (mermaidAPI.ts:319-322): input longer than this is replaced by [[Mermaid.MaxTextLengthExceededMsg]]. A secure
  *   key (not overridable by author markup), so the caller/site value always wins.
  * @param maxEdges
  *   maximum number of edges that can be drawn. Enforced by [[ssg.mermaid.diagrams.flowchart.FlowchartDb.addEdge]] (flowDb.ts:148-155): adding the (maxEdges+1)-th edge throws.
  * @param deterministicIds
  *   environment id-disambiguation flag. Upstream feeds this pair into `new utils.InitIDGenerator(conf.deterministicIds, conf.deterministicIDSeed)` (mermaid.ts:150), whose `next()` (utils.ts:752-761)
  *   switches between a seeded incrementing counter (`() => count++`, deterministic) and a wall-clock timestamp (`() => Date.now()`, non-deterministic) to disambiguate the `mermaid-${next()}` root id
  *   (mermaid.ts:165) when multiple diagrams share one browser page. SSG renders one diagram per call and its root/node ids are unconditionally deterministic (`mermaid-<diagramType>` /
  *   `flowchart-<id>`, see [[ssg.mermaid.Accessibility.applyTo]] Accessibility.scala:126-139) — i.e. it already behaves as `deterministicIds=true`. The `Date.now()` timestamp branch is a browser
  *   multi-element-on-page artifact with no SSG analogue, so this flag has no distinct effect in pure render. Kept for full-fidelity schema parity.
  * @param deterministicIDSeed
  *   seed companion to [[deterministicIds]]. Upstream uses it only for its length (`count = seed ? seed.length : 0`, utils.ts:758) to offset the deterministic counter's start value. SSG ids are
  *   unconditionally deterministic ([[ssg.mermaid.Accessibility.applyTo]]) and carry no counter to offset, so this has no distinct effect in pure render. Kept for full-fidelity schema parity.
  * @param arrowMarkerAbsolute
  *   whether arrow markers use absolute paths. Upstream prefixes the marker `url(...)` reference with `window.location` when true (common.ts getUrl:153-167) and the post-render `cleanUpSvgCode`
  *   (mermaidAPI.ts:183-204) strips that prefix back to a bare `url(#…)` fragment when false. In SSG's pure-render model there is no document location, so `getUrl` yields the empty prefix for both
  *   values and the marker reference is the bare `url(#…)` fragment either way (see [[ssg.mermaid.render.edges.ArrowMarkers.markerUrl]]). The flag therefore has no faithful distinct render effect
  *   here; kept for full-fidelity schema parity.
  * @param markdownAutoWrap
  *   whether to auto-wrap markdown text. Upstream consumes this only in the markdown text processor (handle-markdown-text.ts:18-20,74-76): when false, soft-wrap spaces become `&nbsp;` and newlines
  *   become explicit `<br/>`. SSG's label path strips markdown markers ([[ssg.mermaid.render.text.TextUtils.stripMarkdown]]) but has no `markdownToLines`/`preprocessMarkdown` port, so the field has
  *   no consumer yet (awaits the markdown-string label render path). Kept for full-fidelity schema parity.
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
  handDrawnSeed:       Int = 0,
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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

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
) derives AsDataView,
      FromDataView

object MermaidConfig {

  /** Applies a config `overlay` (frontmatter + init-directive, already merged via [[Directives.cleanAndMerge]]) onto a base [[MermaidConfig]], with the overlay's present keys winning.
    *
    * This is the round-trip the user approved for ISS-1057: the base config is projected to a [[ssg.data.DataView]] via `AsDataView`, the overlay is deep-merged on top via
    * [[ssg.data.DataView.deepMerge]] (present-keys-win), and the merged view is reconstructed into a `MermaidConfig` via `FromDataView`. Keys absent from the overlay keep the base's value; keys not
    * modelled by `MermaidConfig` are ignored by `FromDataView`.
    *
    * If reconstruction somehow fails (it should not for any overlay subset of the config shape), the base config is returned unchanged.
    *
    * @param base
    *   the base config (defaults < caller config param)
    * @param overlay
    *   the author-supplied config overlay (frontmatter < init directive)
    * @return
    *   the effective config with the overlay applied
    */
  def applyOverlay(base: MermaidConfig, overlay: DataView): MermaidConfig = {
    val baseView: DataView = summon[AsDataView[MermaidConfig]].asDataView(base)
    val merged:   DataView = DataView.deepMerge(baseView, overlay)
    summon[FromDataView[MermaidConfig]].fromDataView(merged).getOrElse(base)
  }

  /** Default configuration with all default values. */
  val Default: MermaidConfig = MermaidConfig()

  /** Available theme names. */
  val Themes: Set[String] = Set("default", "dark", "forest", "neutral", "base")

  /** Available security levels. */
  val SecurityLevels: Set[String] = Set("strict", "loose", "antiscript", "sandbox")
}
