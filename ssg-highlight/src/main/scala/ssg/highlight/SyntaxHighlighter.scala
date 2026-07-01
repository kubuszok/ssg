/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import ssg.commons.{ DiagResult, Diagnostic }

trait SyntaxHighlighter {

  def highlight(source: String, language: String): Either[HighlightError, String]

  def supportsLanguage(language: String): Boolean

  def supportedLanguages: Seq[String]
}

object SyntaxHighlighter {

  def default: SyntaxHighlighter = TreeSitterHighlighter.instance

  /** Error-contract facade (ISS-1381; docs/architecture/error-contracts.md §2.9): wraps the existing `Either[HighlightError, String]` contract in the shared `DiagResult` envelope. `Right` becomes a
    * clean success; `Left` a failure carrying one `Severity.Error` diagnostic with `component = "ssg-highlight"`, the enum's stable name as the machine-readable `code`, and a human sentence as the
    * message. Additive only -- the `highlight` contract and the zero-captures-as-`Right` behavior are unchanged (ISS-1096).
    */
  extension (highlighter: SyntaxHighlighter) {
    def highlightResult(source: String, language: String): DiagResult[String] =
      DiagResult.fromEither(highlighter.highlight(source, language))(err => Diagnostic.error("ssg-highlight", messageFor(err), code = Some(err.name)))
  }

  /** A short human sentence for each [[HighlightError]] case. `position` stays `None` at the call site: the only position-bearing native type, `HighlightSpan`'s UTF-8 byte offsets, describes
    * successful captures, and today's `HighlightError` cases carry no position (a parser/FFI failure signal is tracked by ISS-1371).
    */
  private def messageFor(error: HighlightError): String =
    error match {
      case HighlightError.UnknownLanguage => "No registered grammar matched the requested language."
      case HighlightError.MissingQuery    => "The grammar is registered but has no highlight query directory mapped."
      case HighlightError.QueryLoadFailed => "Loading the highlight query for the grammar failed."
    }
}
