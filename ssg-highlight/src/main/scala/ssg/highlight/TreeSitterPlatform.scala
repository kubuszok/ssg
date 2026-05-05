/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

trait TreeSitterPlatform {

  def availableGrammars: Seq[String]

  def highlight(source: String, grammarName: String, highlightQuery: String): Seq[HighlightSpan]
}

object TreeSitterPlatform {
  export TreeSitterPlatformImpl.availableGrammars
  export TreeSitterPlatformImpl.highlight
}
