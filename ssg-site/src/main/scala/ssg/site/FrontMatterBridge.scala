/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Front-matter detection, splitting, and YAML-to-DataView bridge.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 5 for design.
 *
 * Uses ssg-md ONLY to detect/split the ---...--- block. The YAML block
 * is re-parsed with kindlings-yaml to recover real types (nested maps,
 * booleans, dates) into a faithful DataView (design Q5 DECIDED-default).
 */
package ssg
package site

import lowlevel.Nullable

import ssg.data.DataView

import scala.collection.immutable.VectorMap
import scala.util.matching.Regex

/** Bridge between raw file content (with optional YAML front matter) and the typed [[ssg.data.DataView]] that the Liquid template engine consumes as `page.*` variables.
  *
  * The split uses a regex matching Jekyll's front-matter convention: the file must begin with `---` followed by a newline, the YAML block ends at the next `---` on its own line, and the remainder is
  * the body. The YAML block is then re-parsed with kindlings-yaml (via [[YamlDataViewDecoder]]) to recover real types into DataView (design section 5, Q5 DECIDED-default).
  */
object FrontMatterBridge {

  /** Regex that matches a Jekyll-style YAML front-matter block at the start of a file.
    *
    * Format: the file begins with `---` (optionally followed by whitespace), then a newline, then the YAML body (captured in group 1), then a line starting with `---` (or `...`), optionally followed
    * by whitespace and a newline. The body after the closing delimiter is the page content.
    *
    * The `(?s)` (DOTALL) flag lets `.` match newlines inside the body.
    */
  private val FrontMatterRegex: Regex = """(?s)\A-{3}[ \t]*\r?\n((?:.*?\r?\n)?)-{3}[ \t]*\r?\n""".r

  /** The empty DataView mapping, used when no front matter is present or when the front-matter block is empty.
    */
  private val emptyMap: DataView = DataView.from(VectorMap.empty[String, DataView])

  /** Parses a raw file body, splitting it into a front-matter DataView and the remaining body text.
    *
    * If the file begins with a `---`...`---` front-matter block, the block is extracted and re-parsed with kindlings-yaml into a faithful DataView (preserving booleans, integers, nested maps, etc.).
    * The remainder of the file after the closing `---` is returned as the body.
    *
    * If no front-matter block is detected, the full input is returned as the body and the DataView is an empty mapping.
    *
    * @param rawBody
    *   the full file content (including any front-matter block)
    * @return
    *   a tuple of (DataView from front matter, body text after the block)
    */
  def parse(rawBody: String): (DataView, String) =
    FrontMatterRegex.findPrefixMatchOf(rawBody) match {
      case scala.None =>
        // No front matter detected; entire input is the body.
        (emptyMap, rawBody)
      case Some(m) =>
        val yamlBlock = m.group(1)
        val body      = rawBody.substring(m.group(0).length)
        val frontMatter: DataView =
          if (yamlBlock.trim.isEmpty) {
            // Empty front matter (just `---\n---`); return empty mapping.
            emptyMap
          } else {
            // Re-parse the YAML block with kindlings-yaml for faithful types.
            val parsed: Nullable[DataView] = YamlDataViewDecoder.parse(yamlBlock)
            parsed.fold(emptyMap) { dv =>
              // Ensure the result is a mapping; a non-mapping top-level value
              // (scalar or sequence) degrades to an empty mapping, mirroring
              // Jekyll's expectation that front matter is key-value pairs.
              if (dv.asMap.toOption.isDefined) dv else emptyMap
            }
          }
        (frontMatter, body)
    }

  /** Returns true if the raw file content begins with a YAML front-matter block (`---`...`---`).
    *
    * This is the Jekyll rule for determining whether a file is "processed" (has front matter) or "static" (copied verbatim).
    *
    * @param rawBody
    *   the full file content
    * @return
    *   true if the file has a front-matter block
    */
  def hasFrontMatter(rawBody: String): Boolean =
    FrontMatterRegex.findPrefixMatchOf(rawBody).isDefined
}
