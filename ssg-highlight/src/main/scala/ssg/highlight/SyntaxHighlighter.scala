/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

trait SyntaxHighlighter {

  def highlight(source: String, language: String): Either[HighlightError, String]

  def supportsLanguage(language: String): Boolean

  def supportedLanguages: Seq[String]
}

object SyntaxHighlighter {

  def default: SyntaxHighlighter = TreeSitterHighlighter.instance
}
