/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/preprocess.ts (processFrontmatter)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: only the frontmatter stage of preprocessDiagram is ported here;
 *     directive/comment stripping already lives in DetectType and the per-diagram
 *     parsers. The result mirrors processFrontmatter's { title, config, text }.
 *   Idiom: PreprocessResult is a final case class; optional fields use Nullable.
 *     The open-ended config object is captured as the untyped ssg.data.DataView.
 *   Renames: processFrontmatter -> Preprocess.processFrontmatter
 *
 * Covenant: full-port
 * Covenant-verified: 2026-06-17
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import lowlevel.Nullable

import ssg.data.DataView

import scala.collection.immutable.VectorMap

/** Result of the frontmatter preprocessing stage.
  *
  * Mirrors the object returned by `processFrontmatter` in `preprocess.ts`: `{ title, config, text }`.
  *
  * @param text
  *   the diagram text with the frontmatter block stripped out, ready for the diagram parser
  * @param title
  *   the extracted frontmatter title, to be applied to the diagram db via its title mechanism (mirrors `Diagram.ts:42` `db.setDiagramTitle?.(metadata.title)`)
  * @param config
  *   the raw frontmatter config object, if present. Application of this config is owned by ISS-1057 (init-directive/config-application); the value is surfaced here so ISS-1057 can consume it without
  *   re-parsing.
  */
final case class PreprocessResult(
  text:   String,
  title:  Nullable[String] = Nullable.empty,
  config: Nullable[DataView] = Nullable.empty
)

/** Frontmatter preprocessing for Mermaid diagram text.
  *
  * Ports `processFrontmatter` from `preprocess.ts`. In upstream `preprocessDiagram` this stage runs BEFORE directive processing and before the parser sees the text, so the parser never receives the
  * `---` delimiters.
  */
object Preprocess {

  /** Strips and extracts the YAML frontmatter block.
    *
    * Ports `processFrontmatter` (`preprocess.ts:19-30`):
    * {{{
    * const { text, metadata } = extractFrontMatter(code);
    * const { displayMode, title, config = {} } = metadata;
    * if (displayMode) {
    *   if (!config.gantt) { config.gantt = {}; }
    *   config.gantt.displayMode = displayMode;
    * }
    * return { title, config, text };
    * }}}
    *
    * The `displayMode` -> `config.gantt.displayMode` legacy merge (preprocess.ts:22-27) is reproduced here by overlaying `{ gantt: { displayMode } }` onto the frontmatter config (via
    * [[ssg.data.DataView.deepMerge]]), so the value is carried into [[PreprocessResult.config]] and flows through the config-application channel (ISS-1057).
    *
    * @param code
    *   the (already CRLF-normalised) diagram text
    * @return
    *   the stripped text plus the extracted title and config
    */
  def processFrontmatter(code: String): PreprocessResult = {
    val result   = Frontmatter.extractFrontMatter(code)
    val metadata = result.metadata

    // const { displayMode, title, config = {} } = metadata;
    // if (displayMode) {
    //   if (!config.gantt) { config.gantt = {}; }
    //   config.gantt.displayMode = displayMode;
    // }
    //
    // The displayMode -> config.gantt.displayMode legacy merge mutates the raw
    // frontmatter config object before it is handed to cleanAndMerge. Reproduce
    // it here by overlaying `{ gantt: { displayMode } }` onto the config so the
    // value flows through the config-application channel (ISS-1057). A `config`
    // default of `{}` is modelled by starting from an empty map when absent.
    val config: Nullable[DataView] = metadata.displayMode.fold(metadata.config) { displayMode =>
      val base         = metadata.config.getOrElse(DataView.from(VectorMap.empty[String, DataView]))
      val ganttOverlay = DataView.from(
        VectorMap[String, DataView](
          "gantt" -> DataView.from(VectorMap[String, DataView]("displayMode" -> DataView.from(displayMode)))
        )
      )
      Nullable(DataView.deepMerge(base, ganttOverlay))
    }

    PreprocessResult(
      text = result.text,
      title = metadata.title,
      config = config
    )
  }
}
