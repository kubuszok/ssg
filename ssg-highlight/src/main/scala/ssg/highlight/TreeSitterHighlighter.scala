/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

object TreeSitterHighlighter {

  private val queryCache = scala.collection.mutable.Map.empty[String, Option[String]]

  val instance: SyntaxHighlighter = new SyntaxHighlighter {

    /** Runtime grammars cached as a Set for O(1) membership checks. */
    private lazy val availableRuntimeGrammars: Set[String] =
      TreeSitterPlatform.availableGrammars.toSet

    override def highlight(source: String, language: String): Either[HighlightError, String] =
      for {
        grammarName <- LanguageRegistry.resolveGrammar(language).toRight(HighlightError.UnknownLanguage)
        queryDir <- LanguageRegistry.queryDir(grammarName).toRight(HighlightError.MissingQuery)
        querySource <- queryCache.getOrElseUpdate(queryDir, QueryLoader.loadHighlightQuery(queryDir)).toRight(HighlightError.QueryLoadFailed)
      } yield {
        val spans = TreeSitterPlatform.highlight(source, grammarName, querySource)
        HtmlHighlightRenderer.render(source, spans)
      }

    override def supportsLanguage(language: String): Boolean =
      LanguageRegistry.resolveGrammar(language).exists(g => availableRuntimeGrammars.contains(g))

    override def supportedLanguages: Seq[String] =
      LanguageRegistry.registeredGrammars.intersect(availableRuntimeGrammars).toSeq.sorted
  }
}
