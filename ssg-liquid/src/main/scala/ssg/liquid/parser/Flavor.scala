/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/parser/Flavor.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.parser → ssg.liquid.parser
 *   Convention: Java enum → Scala 3 enum extending java.lang.Enum
 *   Idiom: Extensible configuration bundle — each flavor defines defaults
 *          that can be overridden via TemplateParser.Builder
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/parser/Flavor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package parser

import ssg.liquid.filters.Filters

/** Liquid template flavor — a configuration bundle defining default behavior.
  *
  * Built-in flavors: LIQUID (Shopify standard), JEKYLL (Jekyll/GitHub Pages), LIQP (hybrid). Custom flavors can be created by composing filter sets, tag sets, and behavior flags via
  * TemplateParser.Builder, keeping the door open for Cobalt.rs-style, MkDocs-style, or Shopify-style configurations without touching the engine core.
  */
enum Flavor(
  val snippetsFolderName:     String,
  val errorMode:              TemplateParser.ErrorMode,
  val liquidStyleInclude:     Boolean,
  val liquidStyleWhere:       Boolean,
  val evaluateInOutputTag:    Boolean,
  val strictTypedExpressions: Boolean
) extends java.lang.Enum[Flavor] {

  case LIQUID
      extends Flavor(
        snippetsFolderName = "snippets",
        errorMode = TemplateParser.ErrorMode.STRICT,
        liquidStyleInclude = true,
        liquidStyleWhere = true,
        evaluateInOutputTag = false,
        strictTypedExpressions = true
      )

  case JEKYLL
      extends Flavor(
        snippetsFolderName = "_includes",
        errorMode = TemplateParser.ErrorMode.WARN,
        liquidStyleInclude = false,
        liquidStyleWhere = false,
        evaluateInOutputTag = false,
        strictTypedExpressions = true
      )

  case LIQP
      extends Flavor(
        snippetsFolderName = "snippets",
        errorMode = TemplateParser.ErrorMode.STRICT,
        liquidStyleInclude = true,
        liquidStyleWhere = true,
        evaluateInOutputTag = true,
        strictTypedExpressions = false
      )

  /** Returns the default Filters for this flavor. */
  def getFilters: Filters = this match {
    case LIQUID => Filters.DEFAULT_FILTERS
    case JEKYLL => Filters.JEKYLL_FILTERS
    case LIQP   => Filters.JEKYLL_FILTERS
  }

  /** Returns the default Insertions for this flavor. */
  def getInsertions: Insertions = this match {
    case LIQUID => Insertions.STANDARD_INSERTIONS
    case JEKYLL => Insertions.JEKYLL_INSERTIONS
    case LIQP   => Insertions.JEKYLL_INSERTIONS
  }

  /** Returns the default TemplateParser for this flavor. */
  def defaultParser(): TemplateParser =
    new TemplateParser.Builder().withFlavor(this).build()
}
