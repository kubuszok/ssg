/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mapping of Unicode accent characters to their LaTeX equivalent in text and
 * math mode (when they exist).
 *
 * Original source: katex src/unicodeAccents.js
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

import lowlevel.Nullable

/** Accent entry containing the text mode LaTeX command and optionally the math mode LaTeX command.
  */
final case class AccentEntry(text: String, math: Nullable[String])

/** Mapping of Unicode accent characters to their LaTeX equivalent in text and math mode (when they exist). This exports a CommonJS module, allowing to be required in unicodeSymbols without
  * transpiling.
  */
object UnicodeAccents {

  val unicodeAccents: Map[String, AccentEntry] = Map(
    "́" -> AccentEntry("\\'", Nullable("\\acute")),
    "̀" -> AccentEntry("\\`", Nullable("\\grave")),
    "̈" -> AccentEntry("\\\"", Nullable("\\ddot")),
    "̃" -> AccentEntry("\\~", Nullable("\\tilde")),
    "̄" -> AccentEntry("\\=", Nullable("\\bar")),
    "̆" -> AccentEntry("\\u", Nullable("\\breve")),
    "̌" -> AccentEntry("\\v", Nullable("\\check")),
    "̂" -> AccentEntry("\\^", Nullable("\\hat")),
    "̇" -> AccentEntry("\\.", Nullable("\\dot")),
    "̊" -> AccentEntry("\\r", Nullable("\\mathring")),
    "̋" -> AccentEntry("\\H", Nullable.Null),
    "̧" -> AccentEntry("\\c", Nullable.Null)
  )
}
