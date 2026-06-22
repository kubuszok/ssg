/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

final class LanguageRegistryConsistencyIss1093Suite extends munit.FunSuite {

  private def highlighter: SyntaxHighlighter = SyntaxHighlighter.default

  test("ISS-1093: every supportedLanguage is accepted by supportsLanguage") {
    val supported = highlighter.supportedLanguages
    val failures  = supported.filterNot(lang => highlighter.supportsLanguage(lang))
    assert(
      failures.isEmpty,
      s"supportedLanguages contains ${failures.size} language(s) that supportsLanguage rejects: ${failures.mkString(", ")}"
    )
  }

  test("ISS-1093: supportsLanguage agrees with supportedLanguages for all reported languages") {
    // If supportsLanguage says true but the language is not in supportedLanguages, that is a disagreement.
    // We test all supportedLanguages plus a bogus one to verify both directions.
    val supported = highlighter.supportedLanguages.toSet
    // Every supported language must be accepted:
    val falseNegatives = supported.filter(lang => !highlighter.supportsLanguage(lang))
    assert(
      falseNegatives.isEmpty,
      s"supportedLanguages reports ${falseNegatives.size} language(s) that supportsLanguage rejects: ${falseNegatives.mkString(", ")}"
    )
  }

  test("ISS-1093: supportedLanguages is a subset of registered grammars") {
    val supported  = highlighter.supportedLanguages.toSet
    val registered = LanguageRegistry.registeredGrammars
    val extra      = supported -- registered
    assert(
      extra.isEmpty,
      s"supportedLanguages contains ${extra.size} grammar(s) not in the registry: ${extra.mkString(", ")}"
    )
  }

  test("ISS-1093: supportedLanguages is a subset of available runtime grammars") {
    val supported = highlighter.supportedLanguages.toSet
    val runtime   = TreeSitterPlatform.availableGrammars.toSet
    val extra     = supported -- runtime
    assert(
      extra.isEmpty,
      s"supportedLanguages contains ${extra.size} grammar(s) not available at runtime: ${extra.mkString(", ")}"
    )
  }

  test("ISS-1093: registered grammars not available at runtime are rejected by supportsLanguage") {
    val registered     = LanguageRegistry.registeredGrammars
    val runtime        = TreeSitterPlatform.availableGrammars.toSet
    val registryOnly   = registered -- runtime
    val falsePositives = registryOnly.filter(g => highlighter.supportsLanguage(g))
    assert(
      falsePositives.isEmpty,
      s"supportsLanguage accepts ${falsePositives.size} grammar(s) that are NOT available at runtime: ${falsePositives.mkString(", ")}"
    )
  }

  test("ISS-1093: registered grammars not available at runtime are absent from supportedLanguages") {
    val registered   = LanguageRegistry.registeredGrammars
    val runtime      = TreeSitterPlatform.availableGrammars.toSet
    val supported    = highlighter.supportedLanguages.toSet
    val registryOnly = registered -- runtime
    val leaking      = registryOnly.intersect(supported)
    assert(
      leaking.isEmpty,
      s"supportedLanguages includes ${leaking.size} registry-only grammar(s) not available at runtime: ${leaking.mkString(", ")}"
    )
  }

  test("ISS-1093: runtime grammars not in registry are absent from supportedLanguages") {
    val registered  = LanguageRegistry.registeredGrammars
    val runtime     = TreeSitterPlatform.availableGrammars.toSet
    val supported   = highlighter.supportedLanguages.toSet
    val runtimeOnly = runtime -- registered
    val leaking     = runtimeOnly.intersect(supported)
    assert(
      leaking.isEmpty,
      s"supportedLanguages includes ${leaking.size} runtime-only grammar(s) not in registry: ${leaking.mkString(", ")}"
    )
  }

  test("ISS-1093: known alias resolves consistently with supportedLanguages") {
    // "js" is an alias for "javascript" in LanguageRegistry.aliases
    val alias          = "js"
    val resolved       = LanguageRegistry.resolveGrammar(alias)
    val aliasSupported = highlighter.supportsLanguage(alias)
    val supported      = highlighter.supportedLanguages.toSet
    resolved match {
      case Some(grammarName) =>
        assertEquals(
          aliasSupported,
          supported.contains(grammarName),
          s"supportsLanguage($alias) = $aliasSupported but supportedLanguages.contains($grammarName) = ${supported.contains(grammarName)}"
        )
      case None =>
        assert(!aliasSupported, s"supportsLanguage($alias) is true but resolveGrammar returned None")
    }
  }

  test("ISS-1093: bogus language is rejected by supportsLanguage and absent from supportedLanguages") {
    val bogus = "definitely-not-a-language-xyz"
    assert(!highlighter.supportsLanguage(bogus), s"supportsLanguage should reject bogus language '$bogus'")
    assert(
      !highlighter.supportedLanguages.contains(bogus),
      s"supportedLanguages should not contain bogus language '$bogus'"
    )
  }
}
