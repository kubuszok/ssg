/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagram-api/frontmatter.ts
 *              mermaid/packages/mermaid/src/diagram-api/regexes.ts (frontMatterRegex)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: js-yaml's untyped `yaml.load()` is replaced by decoding the
 *     frontmatter body to the SSG-native untyped [[ssg.data.DataView]] ADT via
 *     kindlings-yaml-derivation (com.kubuszok %%% kindlings-yaml-derivation).
 *     kindlings-yaml-derivation wraps a cross-platform YAML engine (JVM, JS,
 *     Native) and lets us decode an OPEN-ENDED object to a generic value the
 *     same way js-yaml's `yaml.load` returns untyped `any`. A hand-written
 *     [[ssg.mermaid.YamlDataViewDecoder]] bridges the engine's node tree to
 *     DataView; the engine node type is touched only inside that decoder
 *     (an interop boundary), never in the frontmatter model. scala-yaml is
 *     explicitly NOT used as a project-level dependency (project rule:
 *     kindlings-yaml only).
 *   Idiom: FrontMatterMetadata / FrontMatterResult modelled as final case
 *     classes; optional fields use Nullable[A] instead of TS `?`.
 *   Renames: extractFrontMatter -> Frontmatter.extractFrontMatter
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

import scala.util.matching.Regex

/** Metadata extracted from a diagram's YAML frontmatter.
  *
  * Ports the `FrontMatterMetadata` interface from `frontmatter.ts`. Only the explicitly supported keys are modelled: `title`, `displayMode` and `config`. Unspecified keys are ignored (mirrors
  * `frontmatter.ts:45-54`).
  *
  * @param title
  *   the diagram title, applied via the diagram db's title mechanism
  * @param displayMode
  *   custom display mode (currently used for compact mode in gantt charts)
  * @param config
  *   raw configuration object extracted from the frontmatter. Application of this config is owned by ISS-1057 (the init-directive/config-application issue); the value is captured faithfully here
  *   as the parsed untyped [[ssg.data.DataView]] so that ISS-1057 can consume it without re-parsing.
  */
final case class FrontMatterMetadata(
  title:       Nullable[String] = Nullable.empty,
  displayMode: Nullable[String] = Nullable.empty,
  config:      Nullable[DataView] = Nullable.empty
)

/** Result of extracting frontmatter from a diagram's text.
  *
  * Ports the `FrontMatterResult` interface from `frontmatter.ts`.
  *
  * @param text
  *   the input text with the frontmatter block stripped out
  * @param metadata
  *   the extracted (and filtered) frontmatter metadata
  */
final case class FrontMatterResult(
  text:     String,
  metadata: FrontMatterMetadata
)

/** Extraction and parsing of YAML frontmatter from Mermaid diagram text.
  *
  * Ports `frontmatter.ts` together with the `frontMatterRegex` from `regexes.ts`.
  */
object Frontmatter {

  /** Matches Jekyll-style front matter blocks.
    *
    * Ports `frontMatterRegex` from `regexes.ts`: {{{/^-{3}\s*[\n\r](.*?)[\n\r]-{3}\s*[\n\r]+/s}}}
    *
    * The `(?s)` (DOTALL) flag corresponds to the trailing `/s` in the original so that `.` matches newlines inside the captured body. JS doesn't support the `\A` anchor; Scala's `^` is anchored to
    * the start of the input here (no `(?m)`), matching the original's single-line-anchor behaviour.
    *
    * Based on regex used by Jekyll: https://github.com/jekyll/jekyll/blob/6dd3cc21c40b98054851846425af06c64f9fb466/lib/jekyll/document.rb#L10
    */
  val FrontMatterRegex: Regex = """(?s)^-{3}\s*[\n\r](.*?)[\n\r]-{3}\s*[\n\r]+""".r

  /** Extract and parse frontmatter from text, if present.
    *
    * Ports `extractFrontMatter` (`frontmatter.ts:24-60`). Strips the matched block from the text (`text.slice(matches[0].length)`) and extracts ONLY the explicitly supported keys (`displayMode`,
    * `title`, `config`); unspecified keys are ignored.
    *
    * @param text
    *   the text that may have a YAML frontmatter
    * @return
    *   the text with frontmatter stripped, plus the extracted metadata
    */
  def extractFrontMatter(text: String): FrontMatterResult =
    FrontMatterRegex.findPrefixMatchOf(text) match {
      case None =>
        // if (!matches) { return { text, metadata: {} }; }
        FrontMatterResult(text = text, metadata = FrontMatterMetadata())

      case Some(matches) =>
        // let parsed: FrontMatterMetadata =
        //   yaml.load(matches[1], { schema: yaml.JSON_SCHEMA }) ?? {};
        //
        // YamlDataViewDecoder.parse produces the untyped DataView graph (the
        // analogue of js-yaml's untyped `any`). A parse failure degrades to an
        // empty map, mirroring the original's `?? {}`. The JSON_SCHEMA option
        // exists in js-yaml only so that `config` keys are parsed without
        // YAML-specific type coercions; the DataView decoder already keeps
        // scalars as their parsed forms, so there is no schema knob to thread.
        //
        // To handle runtime data type changes:
        //   parsed = typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
        // A non-mapping top-level node (scalar or sequence) therefore yields no
        // keys, which `asMap` already models by returning an empty lookup.
        val parsed: Nullable[DataView] = YamlDataViewDecoder.parse(matches.group(1))

        val metadata = FrontMatterMetadata(
          // Only add properties that are explicitly supported, if they exist.
          //
          // if (parsed.displayMode) { metadata.displayMode = parsed.displayMode.toString() }
          displayMode = scalarValue(parsed, "displayMode"),
          // if (parsed.title) { metadata.title = parsed.title.toString() }
          title = scalarValue(parsed, "title"),
          // if (parsed.config) { metadata.config = parsed.config }
          config = nodeValue(parsed, "config")
        )

        FrontMatterResult(
          // text: text.slice(matches[0].length)
          text = text.substring(matches.group(0).length),
          metadata = metadata
        )
    }

  /** Looks up a top-level scalar value by key and coerces it to a string.
    *
    * Mirrors the original's `parsed.<key>.toString()` coercion. Empty scalar values are treated as absent, mirroring JS's falsy `if (parsed.<key>)` guard (an empty string is falsy in JS).
    */
  private def scalarValue(parsed: Nullable[DataView], key: String): Nullable[String] =
    lookup(parsed, key).flatMap { dv =>
      // parsed.<key>.toString() — DataView.toString renders the scalar (and is
      // "" for an absent/null view), so an empty result mirrors a falsy value.
      val str = dv.toString
      if (str.nonEmpty) Nullable(str) else Nullable.empty
    }

  /** Looks up a top-level value node by key, returning it unparsed.
    *
    * Used for `config`, which the original keeps as the raw object (`metadata.config = parsed.config`). A `null`/absent value is dropped to mirror the JS falsy `if (parsed.config)` guard.
    */
  private def nodeValue(parsed: Nullable[DataView], key: String): Nullable[DataView] =
    lookup(parsed, key).flatMap { dv =>
      if (dv.isNull) Nullable.empty else Nullable(dv)
    }

  /** Looks up the value for a top-level string key in the parsed mapping.
    *
    * A non-mapping top-level value (the "runtime data type changes" guard, `frontmatter.ts:41`) yields no keys.
    */
  private def lookup(parsed: Nullable[DataView], key: String): Nullable[DataView] =
    parsed.flatMap(dv => Nullable.fromOption(dv.asMap.toOption.flatMap(_.get(key))))
}
