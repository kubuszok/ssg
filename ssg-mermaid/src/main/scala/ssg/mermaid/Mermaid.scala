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

import lowlevel.Nullable

import ssg.data.DataView

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
import ssg.mermaid.parse.ParseException

/** Top-level entry point for Mermaid diagram rendering.
  *
  * Detects the diagram type from the input text and dispatches to the appropriate diagram renderer. Supports 30 diagram types: 24 ported from upstream Mermaid (flowchart, sequence, class, state, er,
  * pie, gantt, timeline, journey, mindmap, git, xychart, quadrant, requirement, sankey, block, architecture, packet, radar, kanban, c4, treemap, info, error) and 6 SSG-native types not present in
  * upstream Mermaid (cynefin, eventmodeling, ishikawa, treeview, venn, wardley).
  *
  * Usage:
  * {{{
  * val svg = Mermaid.render("graph TD\n    A-->B")
  * }}}
  */
object Mermaid {

  /** Replacement diagram source emitted when the input exceeds `config.maxTextSize`.
    *
    * Mirrors `MAX_TEXTLENGTH_EXCEEDED_MSG` (mermaidAPI.ts:31-32): a self-contained flowchart whose single node carries the over-limit message.
    */
  val MaxTextLengthExceededMsg: String =
    "graph TB;a[Maximum text size in diagram exceeded];style a fill:#faa"

  /** Renders a Mermaid diagram to SVG.
    *
    * Detects the diagram type from the input text and dispatches to the appropriate renderer.
    *
    * '''Failure contract''' (unified to upstream `mermaidAPI.ts:393-401`, ISS-1068):
    *   - '''Parse failure''' — when a per-type renderer throws [[ssg.mermaid.parse.ParseException]] on malformed input, the ERROR diagram is rendered instead of propagating the exception
    *     ([[ssg.mermaid.diagrams.error_.ErrorDiagram.renderError]] carrying the parse message), mirroring upstream `diag = Diagram.fromText('error'); parseEncounteredException = error`.
    *   - '''suppressErrorRendering''' — when [[MermaidConfig.suppressErrorRendering]] is set, the `ParseException` is rethrown instead of rendering the error diagram, mirroring upstream `if
    *     (config.suppressErrorRendering) { throw error }` (mermaidAPI.ts:395-397).
    *   - '''Unknown diagram type''' — an undetected type ([[DiagramType.Unknown]]) is a parse failure in upstream: `detectType` throws `UnknownDiagramError` inside `Diagram.fromText`
    *     (detectType.ts:48), caught at mermaidAPI.ts:393-401 and routed to the error diagram (or rethrown under `suppressErrorRendering`). SSG matches this: the unknown case renders the error diagram
    *     with the `No diagram type detected matching given configuration for text: …` message rather than emitting an in-band HTML comment.
    *
    * Faithful deviation: upstream catches broadly (`catch (error)`), while SSG catches only the specific `ParseException` so that genuine renderer bugs (any other exception type) still surface rather
    * than being masked as a syntax error.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param config
    *   optional configuration overrides
    * @return
    *   SVG markup string; on a parse failure (or an undetected diagram type) the error-diagram SVG, unless `suppressErrorRendering` is set (then the [[ssg.mermaid.parse.ParseException]] propagates)
    */
  def render(input: String, config: MermaidConfig = MermaidConfig()): String = {
    // Mirrors upstream preprocess.ts: frontmatter is stripped and extracted
    // ONCE, before detection and before any parser sees the text, so the
    // parsers never receive the leading `---` delimiters (ISS-1056). The
    // extracted `title` is applied to each diagram db, mirroring
    // Diagram.ts:41-43 `if (metadata.title) { db.setDiagramTitle?.(metadata.title); }`.
    val pre   = Preprocess.processFrontmatter(input)
    val title = pre.title

    // ISS-1058: maximum allowed text size guard. Mirrors mermaidAPI.ts:319-322:
    //   // Check the maximum allowed text size
    //   if (text.length > (config?.maxTextSize ?? MAX_TEXTLENGTH)) {
    //     text = MAX_TEXTLENGTH_EXCEEDED_MSG;
    //   }
    // where MAX_TEXTLENGTH = 50_000 (mermaidAPI.ts:30) and
    // MAX_TEXTLENGTH_EXCEEDED_MSG =
    //   'graph TB;a[Maximum text size in diagram exceeded];style a fill:#faa'
    // (mermaidAPI.ts:31-32). The replacement text is itself a flowchart that
    // renders the over-limit message, so detection runs over it normally.
    val text =
      if (pre.text.length > config.maxTextSize) {
        Mermaid.MaxTextLengthExceededMsg
      } else {
        pre.text
      }

    val diagramType = DetectType.detect(text)

    // ISS-1057: apply the frontmatter `config` and the `%%{init: {...}}%%`
    // directive. Mirrors preprocess.ts:processDirectives + cleanAndMerge:
    //   const directiveResult = processDirectives(frontMatterResult.text);
    //   const config = cleanAndMerge(frontMatterResult.config, directiveResult.directive);
    //
    // detectInit collects the init/initialize directives from the
    // frontmatter-stripped text and applies the config-key remapping using the
    // detected diagram type. The `wrap` directive
    // (processDirectives, preprocess.ts:35-39) folds `wrap: true` into the init
    // overlay. cleanAndMerge then merges the frontmatter config (lower
    // precedence) with the init directive (higher precedence, directive wins).
    val initDirective: Nullable[DataView] = Directives.detectInit(text, diagramType)
    val wrapDirectives = Directives.detectDirective(text, Nullable("wrap"))
    val wrapIsSet      = wrapDirectives.exists(_.`type`.contains("wrap"))
    val initWithWrap: Nullable[DataView] =
      if (wrapIsSet) {
        val base = initDirective.getOrElse(DataView.from(scala.collection.immutable.VectorMap.empty[String, DataView]))
        Nullable(
          DataView.deepMerge(base, DataView.from(scala.collection.immutable.VectorMap[String, DataView]("wrap" -> DataView.from(true))))
        )
      } else {
        initDirective
      }

    // cleanAndMerge(frontmatterConfig, directive) — directive wins.
    val mergedOverlay: DataView = Directives.cleanAndMerge(pre.config, initWithWrap)

    // Sanitize the MERGED overlay ONCE, mirroring mermaidAPI.ts:55-57:
    //   configApi.reset(); configApi.addDirective(processed.config ?? {});
    // addDirective -> sanitizeDirective + updateCurrentConfig -> config.ts
    // sanitize() (config.ts:146-181) runs over the whole merged
    // (frontmatter + directive) config `d` (config.ts:22-25). So the frontmatter
    // `config:` block gets the same secure-key drop / proto-pollution / XSS
    // string filtering as an init directive does.
    val overlay: DataView = Directives.sanitizeConfig(mergedOverlay)

    // Precedence (faithful to upstream): defaults < caller `config` param <
    // frontmatter.config < init directive. The caller's `config` plays
    // upstream's `siteConfig` role, so the author markup (frontmatter + init
    // directive), assembled in `overlay`, OVERRIDES it.
    val effectiveConfig: MermaidConfig = MermaidConfig.applyOverlay(config, overlay)

    // ISS-1068: unified failure contract, faithful to mermaidAPI.ts:393-401.
    // A parse failure in any per-type renderer surfaces as a `ParseException`;
    // upstream catches it and renders the ERROR diagram
    // (`diag = Diagram.fromText('error')`) instead of propagating, recording the
    // exception — UNLESS `config.suppressErrorRendering` is set, in which case
    // the exception is rethrown (mermaidAPI.ts:395-397). SSG catches the
    // SPECIFIC `ParseException` (not a blanket `Exception`/`Throwable`) so any
    // OTHER exception type — a genuine renderer bug — still propagates rather
    // than being masked as a syntax error (faithful deviation from upstream's
    // broad catch; see the render scaladoc).
    try
      diagramType match {
        case DiagramType.Flowchart | DiagramType.FlowchartV2 | DiagramType.Graph =>
          FlowchartDiagram.render(text, effectiveConfig, title)
        case DiagramType.Sequence =>
          SequenceDiagram.render(text, effectiveConfig, title)
        case DiagramType.ClassDiagram | DiagramType.ClassDiagramV2 =>
          ClassDiagram.render(text, effectiveConfig, title)
        case DiagramType.StateDiagram | DiagramType.StateDiagramV2 =>
          StateDiagram.render(text, effectiveConfig, title)
        case DiagramType.ErDiagram =>
          ErDiagram.render(text, effectiveConfig, title)
        case DiagramType.Pie =>
          PieDiagram.render(text, effectiveConfig, title)
        case DiagramType.Gantt =>
          GanttDiagram.render(text, effectiveConfig, title)
        case DiagramType.Timeline =>
          TimelineDiagram.render(text, effectiveConfig, title)
        case DiagramType.Journey =>
          JourneyDiagram.render(text, effectiveConfig, title)
        case DiagramType.Mindmap =>
          MindmapDiagram.render(text, effectiveConfig, title)
        case DiagramType.GitGraph =>
          GitDiagram.render(text, effectiveConfig, title)
        case DiagramType.XyChart =>
          XyChartDiagram.render(text, effectiveConfig, title)
        case DiagramType.QuadrantChart =>
          QuadrantDiagram.render(text, effectiveConfig, title)
        case DiagramType.Requirement =>
          RequirementDiagram.render(text, effectiveConfig, title)
        case DiagramType.Sankey =>
          SankeyDiagram.render(text, effectiveConfig, title)
        case DiagramType.Block =>
          BlockDiagram.render(text, effectiveConfig, title)
        case DiagramType.Architecture =>
          ArchitectureDiagram.render(text, effectiveConfig, title)
        case DiagramType.Packet =>
          PacketDiagram.render(text, effectiveConfig, title)
        case DiagramType.Radar =>
          RadarDiagram.render(text, effectiveConfig, title)
        case DiagramType.Kanban =>
          KanbanDiagram.render(text, effectiveConfig, title)
        case DiagramType.Venn =>
          VennDiagram.render(text, effectiveConfig, title)
        case DiagramType.Ishikawa =>
          IshikawaDiagram.render(text, effectiveConfig, title)
        case DiagramType.C4Context | DiagramType.C4Container | DiagramType.C4Component | DiagramType.C4Deployment | DiagramType.C4Dynamic =>
          C4Diagram.render(text, effectiveConfig, title)
        case DiagramType.Cynefin =>
          CynefinDiagram.render(text, effectiveConfig, title)
        case DiagramType.EventModeling =>
          EventModelingDiagram.render(text, effectiveConfig, title)
        case DiagramType.TreeView =>
          TreeViewDiagram.render(text, effectiveConfig, title)
        case DiagramType.Treemap =>
          TreemapDiagram.render(text, effectiveConfig, title)
        case DiagramType.Wardley =>
          WardleyDiagram.render(text, effectiveConfig, title)
        case DiagramType.Info =>
          // InfoDb has no title field — mirrors Diagram.ts:42 optional-chaining
          // `db.setDiagramTitle?.(...)` being a no-op when absent.
          InfoDiagram.render(text, effectiveConfig)
        case DiagramType.Error =>
          // ErrorDb has no title field — mirrors Diagram.ts:42 optional-chaining
          // `db.setDiagramTitle?.(...)` being a no-op when absent.
          ErrorDiagram.render(text, effectiveConfig)
        case _ =>
          // Unknown/undetected diagram type. In upstream `detectType` throws
          // `UnknownDiagramError` inside `Diagram.fromText` (detectType.ts:48),
          // which is caught at mermaidAPI.ts:393-401 and routed to the error
          // diagram (or rethrown under `suppressErrorRendering`). We unify the
          // contract by treating it as a parse failure with the same message,
          // rather than emitting an in-band HTML comment.
          val message = s"No diagram type detected matching given configuration for text: $text"
          if (effectiveConfig.suppressErrorRendering) {
            throw new ParseException(message, 0, 0)
          } else {
            ErrorDiagram.renderError(message, effectiveConfig)
          }
      }
    catch {
      // mermaidAPI.ts:395-401 — a parse failure records the exception and
      // renders the error diagram; `suppressErrorRendering` rethrows instead.
      case e: ParseException =>
        if (effectiveConfig.suppressErrorRendering) {
          throw e
        } else {
          ErrorDiagram.renderError(e.getMessage, effectiveConfig)
        }
    }
  }
}
