/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/theme-forest.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Constructor assignments translated to create() factory setting mutable fields
 *   Idiom: Factory method returns ThemeVariables with all base values pre-set
 *   Renames: Theme constructor → ForestTheme.create()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

import ssg.mermaid.color.ColorOps

/** Forest theme with green tones.
  *
  * Ports the constructor of `theme-forest.js`: primary = `#cde498`, secondary = `#cdffb2`, line color = green.
  */
object ForestTheme {

  /** Creates a new [[ThemeVariables]] with forest theme base values. */
  def create(): ThemeVariables = {
    val t = new ThemeVariables

    /* Base values */
    t.background = "white"
    t.primaryColor = "#cde498"
    t.secondaryColor = "#cdffb2"
    t.mainBkg = "#cde498"
    t.secondBkg = "#cdffb2"
    t.lineColor = "green"
    t.border1 = "#13540c"
    t.border2 = "#6eaa49"
    t.arrowheadColor = "green"
    t.fontFamily = "\"trebuchet ms\", verdana, arial, sans-serif"
    t.fontSize = "16px"

    t.tertiaryColor = ColorOps.lighten("#cde498", 10)
    t.primaryBorderColor = "" // mkBorder
    t.secondaryBorderColor = "" // mkBorder
    t.tertiaryBorderColor = "" // mkBorder
    t.primaryTextColor = ColorOps.invert(t.primaryColor)
    t.secondaryTextColor = ColorOps.invert(t.secondaryColor)
    t.tertiaryTextColor = ColorOps.invert(t.primaryColor)
    t.lineColor = ColorOps.invert(t.background)
    t.textColor = ColorOps.invert(t.background)
    t.THEME_COLOR_LIMIT = 12

    /* Flowchart */
    t.nodeBkg = ""
    t.nodeBorder = ""
    t.clusterBkg = ""
    t.clusterBorder = ""
    t.defaultLinkColor = ""
    t.titleColor = "#333"
    t.edgeLabelBackground = "#e8e8e8"

    /* Sequence Diagram */
    t.actorBorder = ""
    t.actorBkg = ""
    t.actorTextColor = "black"
    t.actorLineColor = ""
    t.signalColor = "#333"
    t.signalTextColor = "#333"
    t.labelBoxBkgColor = ""
    t.labelBoxBorderColor = "#326932"
    t.labelTextColor = ""
    t.loopTextColor = ""
    t.noteBorderColor = ""
    t.noteBkgColor = "#fff5ad"
    t.noteTextColor = ""
    t.activationBorderColor = "#666"
    t.activationBkgColor = "#f4f4f4"
    t.sequenceNumberColor = "white"

    /* Gantt chart */
    t.sectionBkgColor = "#6eaa49"
    t.altSectionBkgColor = "white"
    t.sectionBkgColor2 = "#6eaa49"
    t.excludeBkgColor = "#eeeeee"
    t.taskBorderColor = ""
    t.taskBkgColor = "#487e3a"
    t.taskTextLightColor = "white"
    t.taskTextColor = ""
    t.taskTextDarkColor = "black"
    t.taskTextOutsideColor = ""
    t.taskTextClickableColor = "#003163"
    t.activeTaskBorderColor = ""
    t.activeTaskBkgColor = ""
    t.gridColor = "lightgrey"
    t.doneTaskBkgColor = "lightgrey"
    t.doneTaskBorderColor = "grey"
    t.critBorderColor = "#ff8888"
    t.critBkgColor = "red"
    t.todayLineColor = "red"

    /* C4 */
    t.personBorder = ""
    t.personBkg = ""

    /* State */
    t.labelColor = "black"

    t.errorBkgColor = "#552222"
    t.errorTextColor = "#552222"

    t
  }
}
