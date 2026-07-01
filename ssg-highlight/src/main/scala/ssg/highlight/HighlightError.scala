/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Distinguishable failure modes for `SyntaxHighlighter.highlight`.
  *
  * ISS-1096: the original `Option[String]` return type conflated four
  * different failure conditions into `None`.  This enum makes each
  * distinguishable so callers can tell misconfiguration apart from
  * valid-but-unhighlighted input.
  *
  *   - `UnknownLanguage`  — the language string did not resolve to any
  *     registered grammar (typo, unsupported language).
  *   - `MissingQuery`     — the grammar is registered but has no query
  *     directory mapped (configuration gap).
  *   - `QueryLoadFailed`  — the query directory exists in the registry
  *     but loading the highlight query file from it failed (I/O error,
  *     missing file, permission issue).
  *
  * Note: a FOURTH condition (zero captures from a valid grammar/query)
  * is NOT an error — it is a successful highlight with no spans.  The
  * `TreeSitterPlatform.highlight` layer currently cannot distinguish
  * zero captures from a parser/FFI failure (both return an empty array);
  * separating those would require changes to the platform FFI boundary
  * and is tracked separately.
  */
enum HighlightError extends java.lang.Enum[HighlightError] {
  case UnknownLanguage
  case MissingQuery
  case QueryLoadFailed
}
