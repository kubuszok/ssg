/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/theme-base.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Constructor assignments translated to create() factory setting mutable fields
 *   Idiom: Factory method returns ThemeVariables with minimal base values; user-customizable
 *   Renames: Theme constructor → BaseTheme.create()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

/** Base theme — minimal defaults intended for full user customization.
  *
  * Ports the constructor of `theme-base.js`. Uses a warm yellow-tinted primary color (`#fff4dd`) and derives all other values during the updateColors phase, using `||` fallbacks so that user
  * overrides take precedence.
  */
object BaseTheme {

  /** Creates a new [[ThemeVariables]] with base theme values. */
  def create(): ThemeVariables = {
    val t = new ThemeVariables

    t.background = "#f4f4f4"
    t.primaryColor = "#fff4dd"
    t.noteBkgColor = "#fff5ad"
    t.noteTextColor = "#333"
    t.THEME_COLOR_LIMIT = 12
    t.fontFamily = "\"trebuchet ms\", verdana, arial, sans-serif"
    t.fontSize = "16px"

    t
  }
}
