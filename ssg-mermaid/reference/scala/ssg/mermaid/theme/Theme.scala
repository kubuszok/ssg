/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/index.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces dynamic JS module registry with Scala 3 enum + factory
 *   Idiom: Enum for theme names; factory method getTheme dispatches to theme initializers
 *   Renames: index.js registry → Theme enum + getTheme factory
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

/** Supported Mermaid theme names.
  *
  * Each variant corresponds to a pre-defined color scheme. The [[Theme.getTheme]] factory method returns a fully initialized [[ThemeVariables]] for the selected theme.
  */
enum ThemeName extends java.lang.Enum[ThemeName] {

  /** Default light theme with purple/lavender palette. */
  case Default

  /** Dark theme with dark backgrounds and light text. */
  case Dark

  /** Forest theme with green tones. */
  case Forest

  /** Neutral grayscale theme. */
  case Neutral

  /** Base theme — minimal defaults for user customization. */
  case Base
}

/** Theme factory and registry.
  *
  * Matches the original `themes/index.js` module map that dispatches `getThemeVariables(overrides)` calls to the appropriate theme constructor.
  */
object Theme {

  /** Returns a fully initialized [[ThemeVariables]] for the given theme name.
    *
    * @param name
    *   the theme to instantiate
    * @param overrides
    *   optional user overrides for theme variables
    * @return
    *   a mutable ThemeVariables with all derived colors calculated
    */
  def getTheme(name: ThemeName, overrides: Map[String, String] = Map.empty): ThemeVariables = {
    val theme = name match {
      case ThemeName.Default => DefaultTheme.create()
      case ThemeName.Dark    => DarkTheme.create()
      case ThemeName.Forest  => ForestTheme.create()
      case ThemeName.Neutral => NeutralTheme.create()
      case ThemeName.Base    => BaseTheme.create()
    }
    theme.calculate(overrides)
    theme
  }

  /** Returns a theme by string name (case-insensitive).
    *
    * Falls back to [[ThemeName.Default]] if the name is not recognized.
    */
  def getThemeByName(name: String, overrides: Map[String, String] = Map.empty): ThemeVariables = {
    val themeName = name.toLowerCase match {
      case "dark"    => ThemeName.Dark
      case "forest"  => ThemeName.Forest
      case "neutral" => ThemeName.Neutral
      case "base"    => ThemeName.Base
      case _         => ThemeName.Default
    }
    getTheme(themeName, overrides)
  }
}
