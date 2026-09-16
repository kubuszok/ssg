/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/theme-dark.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Constructor assignments translated to create() factory setting mutable fields
 *   Idiom: Factory method returns ThemeVariables with all base values pre-set
 *   Renames: Theme constructor → DarkTheme.create()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

import ssg.mermaid.color.{ AdjustOptions, ColorOps }

/** Dark theme with dark backgrounds and light text.
  *
  * Ports the constructor of `theme-dark.js`: primary = `#1f2020`, background = `#333`.
  */
object DarkTheme {

  /** Creates a new [[ThemeVariables]] with dark theme base values. */
  def create(): ThemeVariables = {
    val t = new ThemeVariables

    t.darkMode = true
    t.background = "#333"
    t.primaryColor = "#1f2020"
    t.secondaryColor = ColorOps.lighten(t.primaryColor, 16)
    t.tertiaryColor = ColorOps.adjust(t.primaryColor, AdjustOptions(h = -160))
    t.primaryBorderColor = ColorOps.invert(t.background)
    t.secondaryBorderColor = "" // calculated via mkBorder
    t.tertiaryBorderColor = "" // calculated via mkBorder
    t.primaryTextColor = ColorOps.invert(t.primaryColor)
    t.secondaryTextColor = ColorOps.invert(t.secondaryColor)
    t.tertiaryTextColor = ColorOps.invert(t.tertiaryColor)
    t.lineColor = ColorOps.invert(t.background)
    t.textColor = ColorOps.invert(t.background)

    t.mainBkg = "#1f2020"
    t.secondBkg = "" // calculated: lighten(mainBkg, 16)
    t.mainContrastColor = "lightgrey"
    t.darkTextColor = ColorOps.lighten(ColorOps.invert("#323D47"), 10)
    t.lineColor = "" // calculated
    t.border1 = "#ccc"
    t.border2 = "rgba(255,255,255,0.25)"
    t.arrowheadColor = "" // calculated
    t.fontFamily = "\"trebuchet ms\", verdana, arial, sans-serif"
    t.fontSize = "16px"
    t.labelBackground = "#181818"
    t.textColor = "#ccc"
    t.THEME_COLOR_LIMIT = 12

    /* Flowchart */
    t.nodeBkg = ""
    t.nodeBorder = ""
    t.clusterBkg = ""
    t.clusterBorder = ""
    t.defaultLinkColor = ""
    t.titleColor = "#F9FFFE"
    t.edgeLabelBackground = ""

    /* Sequence Diagram */
    t.actorBorder = ""
    t.actorBkg = ""
    t.actorTextColor = ""
    t.actorLineColor = ""
    t.signalColor = ""
    t.signalTextColor = ""
    t.labelBoxBkgColor = ""
    t.labelBoxBorderColor = ""
    t.labelTextColor = ""
    t.loopTextColor = ""
    t.noteBorderColor = ""
    t.noteBkgColor = "#fff5ad"
    t.noteTextColor = ""
    t.activationBorderColor = ""
    t.activationBkgColor = ""
    t.sequenceNumberColor = "black"

    /* Gantt chart */
    t.sectionBkgColor = ColorOps.darken("#EAE8D9", 30)
    t.altSectionBkgColor = ""
    t.sectionBkgColor2 = "#EAE8D9"
    t.excludeBkgColor = ColorOps.darken(t.sectionBkgColor, 10)
    t.taskBorderColor = "rgba(255,255,255,0.27)"
    t.taskBkgColor = ""
    t.taskTextColor = ""
    t.taskTextLightColor = ""
    t.taskTextOutsideColor = ""
    t.taskTextClickableColor = "#003163"
    t.activeTaskBorderColor = "rgba(255,255,255,0.20)"
    t.activeTaskBkgColor = "#81B1DB"
    t.gridColor = ""
    t.doneTaskBkgColor = ""
    t.doneTaskBorderColor = "grey"
    t.critBorderColor = "#E83737"
    t.critBkgColor = "#E83737"
    t.taskTextDarkColor = ""
    t.todayLineColor = "#DB5757"

    /* C4 */
    t.personBorder = ""
    t.personBkg = ""

    /* State colors */
    t.labelColor = ""

    t.errorBkgColor = "#a44141"
    t.errorTextColor = "#ddd"

    t
  }
}
