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

import ssg.commons.{ DiagResult, Diagnostic, Severity, SourcePosition }
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
    // The frontmatter extraction, text-size guard, type detection, and
    // effective-config assembly are shared verbatim with `renderResult` via
    // `prepare`, so both entry points see byte-identical inputs before dispatch.
    val Prepared(text, effectiveConfig, diagramType, title) = prepare(input, config)

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
      dispatchKnown(text, effectiveConfig, diagramType, title).toOption match {
        case Some(svg) => svg
        case None      =>
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

  /** Renders a Mermaid diagram to SVG, returning a diagnostics envelope (ISS-1379).
    *
    * Additive facade over [[render]] per docs/architecture/error-contracts.md section 2.7: it shares [[render]]'s exact effective-config computation (via [[prepare]]) and replicates the dispatch
    * `try` (via [[dispatchKnown]]), wrapping the outcome in the shared [[ssg.commons.DiagResult]] envelope instead of the legacy throw-or-error-diagram contract. [[render]]'s ISS-1068 contract
    * (test-locked by `MermaidIss1068Suite`), the `ParseException` fields, the `ErrorDiagram` markup, and `suppressErrorRendering` semantics are all unchanged.
    *
    *   - A caught `ParseException` becomes a `Severity.Error` [[ssg.commons.Diagnostic]] (component `"ssg-mermaid"`, `code = "parse-error"`, the native exception preserved as `cause`, position mapped
    *     from the exception's 1-based `line`/`col` verbatim per the section 1.3 mermaid row — NO `+1`). Under `suppressErrorRendering` (consulted on the EFFECTIVE config, after frontmatter/init
    *     merging) no substitute output is requested, so the result is [[ssg.commons.DiagResult.failure]]; otherwise it is [[ssg.commons.DiagResult.degraded]] carrying
    *     `ErrorDiagram.renderError(e.getMessage, effectiveConfig)` — byte-equal to [[render]]'s error SVG (the adapter invariant). The catch is SPECIFIC to the module-native `ParseException` (section
    *     1.2 rule 3): draw-phase exceptions (`IllegalStateException`, `IndexOutOfBoundsException`, …) stay uncaught, exactly as in [[render]].
    *   - An undetected [[DiagramType.Unknown]] becomes [[ssg.commons.DiagResult.degraded]] carrying the unknown-type error SVG plus a direct `Diagnostic.error` coded `"unknown-diagram-type"` (no
    *     native exception behind it, so no `cause` and no `position`). This mirrors the ISS-1068 unknown-type path routing to the error diagram.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param config
    *   optional configuration overrides
    * @return
    *   a clean success carrying the SVG, a degraded result carrying the error-diagram SVG plus its diagnostic, or (only under `suppressErrorRendering` on a parse failure) a failure carrying the
    *   `"parse-error"` diagnostic
    */
  def renderResult(input: String, config: MermaidConfig = MermaidConfig()): DiagResult[String] = {
    val Prepared(text, effectiveConfig, diagramType, title) = prepare(input, config)

    try
      dispatchKnown(text, effectiveConfig, diagramType, title).toOption match {
        case Some(svg) => DiagResult.success(svg)
        case None      =>
          // Unknown/undetected diagram type — the section 2.7 unknown-type path:
          // a degraded result carrying the error-diagram SVG plus a direct
          // `"unknown-diagram-type"` diagnostic (no ParseException behind it, so
          // no cause and no position).
          val message = s"No diagram type detected matching given configuration for text: $text"
          DiagResult.degraded(
            ErrorDiagram.renderError(message, effectiveConfig),
            Diagnostic.error("ssg-mermaid", message, code = Some("unknown-diagram-type"))
          )
      }
    catch {
      // section 2.7 — the caught ParseException becomes one Severity.Error
      // diagnostic; `suppressErrorRendering` means no substitute output was
      // requested (failure), otherwise the error diagram rides along (degraded).
      case e: ParseException =>
        val diag =
          Diagnostic.fromThrowable(Severity.Error, "ssg-mermaid", e, position = Some(SourcePosition.lineColumn(e.line, e.col)), code = Some("parse-error"))
        if (effectiveConfig.suppressErrorRendering) {
          DiagResult.failure(diag)
        } else {
          DiagResult.degraded(ErrorDiagram.renderError(e.getMessage, effectiveConfig), diag)
        }
    }
  }

  /** The shared pre-dispatch computation of [[render]] and [[renderResult]]: strips and extracts frontmatter, applies the max-text-size guard, detects the diagram type, and assembles the effective
    * config from the frontmatter/init overlay. Extracted so both entry points see byte-identical inputs before dispatch (the byte-parity the ISS-1379 facade asserts).
    */
  private def prepare(input: String, config: MermaidConfig): Prepared = {
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

    Prepared(text, effectiveConfig, diagramType, title)
  }

  /** Dispatches to the per-type renderer for a KNOWN diagram type, or [[lowlevel.Nullable.empty]] for an undetected type ([[DiagramType.Unknown]]). Shared by [[render]] and [[renderResult]] so the
    * two entry points cannot diverge on which SVG a given diagram type produces (the byte-parity the ISS-1379 facade asserts). Any [[ssg.mermaid.parse.ParseException]] a per-type renderer throws
    * propagates to the caller's dispatch `try`, unchanged.
    */
  private def dispatchKnown(text: String, effectiveConfig: MermaidConfig, diagramType: DiagramType, title: Nullable[String]): Nullable[String] =
    diagramType match {
      case DiagramType.Flowchart | DiagramType.FlowchartV2 | DiagramType.Graph =>
        Nullable(FlowchartDiagram.render(text, effectiveConfig, title))
      case DiagramType.Sequence =>
        Nullable(SequenceDiagram.render(text, effectiveConfig, title))
      case DiagramType.ClassDiagram | DiagramType.ClassDiagramV2 =>
        Nullable(ClassDiagram.render(text, effectiveConfig, title))
      case DiagramType.StateDiagram | DiagramType.StateDiagramV2 =>
        Nullable(StateDiagram.render(text, effectiveConfig, title))
      case DiagramType.ErDiagram =>
        Nullable(ErDiagram.render(text, effectiveConfig, title))
      case DiagramType.Pie =>
        Nullable(PieDiagram.render(text, effectiveConfig, title))
      case DiagramType.Gantt =>
        Nullable(GanttDiagram.render(text, effectiveConfig, title))
      case DiagramType.Timeline =>
        Nullable(TimelineDiagram.render(text, effectiveConfig, title))
      case DiagramType.Journey =>
        Nullable(JourneyDiagram.render(text, effectiveConfig, title))
      case DiagramType.Mindmap =>
        Nullable(MindmapDiagram.render(text, effectiveConfig, title))
      case DiagramType.GitGraph =>
        Nullable(GitDiagram.render(text, effectiveConfig, title))
      case DiagramType.XyChart =>
        Nullable(XyChartDiagram.render(text, effectiveConfig, title))
      case DiagramType.QuadrantChart =>
        Nullable(QuadrantDiagram.render(text, effectiveConfig, title))
      case DiagramType.Requirement =>
        Nullable(RequirementDiagram.render(text, effectiveConfig, title))
      case DiagramType.Sankey =>
        Nullable(SankeyDiagram.render(text, effectiveConfig, title))
      case DiagramType.Block =>
        Nullable(BlockDiagram.render(text, effectiveConfig, title))
      case DiagramType.Architecture =>
        Nullable(ArchitectureDiagram.render(text, effectiveConfig, title))
      case DiagramType.Packet =>
        Nullable(PacketDiagram.render(text, effectiveConfig, title))
      case DiagramType.Radar =>
        Nullable(RadarDiagram.render(text, effectiveConfig, title))
      case DiagramType.Kanban =>
        Nullable(KanbanDiagram.render(text, effectiveConfig, title))
      case DiagramType.Venn =>
        Nullable(VennDiagram.render(text, effectiveConfig, title))
      case DiagramType.Ishikawa =>
        Nullable(IshikawaDiagram.render(text, effectiveConfig, title))
      case DiagramType.C4Context | DiagramType.C4Container | DiagramType.C4Component | DiagramType.C4Deployment | DiagramType.C4Dynamic =>
        Nullable(C4Diagram.render(text, effectiveConfig, title))
      case DiagramType.Cynefin =>
        Nullable(CynefinDiagram.render(text, effectiveConfig, title))
      case DiagramType.EventModeling =>
        Nullable(EventModelingDiagram.render(text, effectiveConfig, title))
      case DiagramType.TreeView =>
        Nullable(TreeViewDiagram.render(text, effectiveConfig, title))
      case DiagramType.Treemap =>
        Nullable(TreemapDiagram.render(text, effectiveConfig, title))
      case DiagramType.Wardley =>
        Nullable(WardleyDiagram.render(text, effectiveConfig, title))
      case DiagramType.Info =>
        // InfoDb has no title field — mirrors Diagram.ts:42 optional-chaining
        // `db.setDiagramTitle?.(...)` being a no-op when absent.
        Nullable(InfoDiagram.render(text, effectiveConfig))
      case DiagramType.Error =>
        // ErrorDb has no title field — mirrors Diagram.ts:42 optional-chaining
        // `db.setDiagramTitle?.(...)` being a no-op when absent.
        Nullable(ErrorDiagram.render(text, effectiveConfig))
      case _ =>
        // Undetected diagram type — the unknown-type path is handled by the
        // caller (an error diagram in `render`, a degraded result in
        // `renderResult`), so no per-type renderer applies here.
        Nullable.empty
    }

  /** The shared pre-dispatch outcome of [[prepare]]: the frontmatter-stripped (and possibly size-capped) `text`, the assembled `effectiveConfig`, the detected `diagramType`, and the extracted
    * `title`.
    */
  final private case class Prepared(text: String, effectiveConfig: MermaidConfig, diagramType: DiagramType, title: Nullable[String])
}
