/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/theme-default.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Constructor assignments translated to create() factory setting mutable fields
 *   Idiom: Factory method returns ThemeVariables with all base values pre-set
 *   Renames: Theme constructor → DefaultTheme.create()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

import ssg.mermaid.color.{ AdjustOptions, ColorOps }

/** Default light theme with purple/lavender palette.
  *
  * Ports the constructor of `theme-default.js`: primary = `#ECECFF`, background = white, line color = `#333333`.
  */
object DefaultTheme {

  /** Creates a new [[ThemeVariables]] with default theme base values.
    *
    * All "calculated" fields are left empty so that [[ThemeVariables.updateColors]] fills them during the calculate pass.
    */
  def create(): ThemeVariables = {
    val t = new ThemeVariables

    /* Base variables */
    t.background = "#f4f4f4"
    t.primaryColor = "#ECECFF"

    t.secondaryColor = "#ffffde"
    t.tertiaryColor = ColorOps.adjust(t.primaryColor, AdjustOptions(h = -160))
    t.primaryBorderColor = "" // calculated
    t.secondaryBorderColor = "" // calculated
    t.tertiaryBorderColor = "" // calculated

    t.primaryTextColor = ColorOps.invert(t.primaryColor)
    t.secondaryTextColor = ColorOps.invert(t.secondaryColor)
    t.tertiaryTextColor = ColorOps.invert(t.tertiaryColor)
    t.lineColor = ColorOps.invert(t.background)
    t.textColor = ColorOps.invert(t.background)

    t.background = "white"
    t.mainBkg = "#ECECFF"
    t.secondBkg = "#ffffde"
    t.lineColor = "#333333"
    t.border1 = "#9370DB"
    t.border2 = "#aaaa33"
    t.arrowheadColor = "#333333"
    t.fontFamily = "\"trebuchet ms\", verdana, arial, sans-serif"
    t.fontSize = "16px"
    t.labelBackground = "rgba(232,232,232, 0.8)"
    t.textColor = "#333"
    t.THEME_COLOR_LIMIT = 12

    /* Flowchart — set to empty for calculation */
    t.nodeBkg = ""
    t.nodeBorder = ""
    t.clusterBkg = ""
    t.clusterBorder = ""
    t.defaultLinkColor = ""
    t.titleColor = ""
    t.edgeLabelBackground = ""

    /* Sequence Diagram */
    t.actorBorder = ""
    t.actorBkg = ""
    t.actorTextColor = "black"
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
    t.activationBorderColor = "#666"
    t.activationBkgColor = "#f4f4f4"
    t.sequenceNumberColor = "white"

    /* Gantt chart */
    t.sectionBkgColor = "" // calculated from rgba
    t.altSectionBkgColor = "white"
    t.sectionBkgColor2 = "#fff400"
    t.excludeBkgColor = "#eeeeee"
    t.taskBorderColor = "#534fbc"
    t.taskBkgColor = "#8a90dd"
    t.taskTextLightColor = "white"
    t.taskTextColor = ""
    t.taskTextDarkColor = "black"
    t.taskTextOutsideColor = ""
    t.taskTextClickableColor = "#003163"
    t.activeTaskBorderColor = "#534fbc"
    t.activeTaskBkgColor = "#bfc7ff"
    t.gridColor = "lightgrey"
    t.doneTaskBkgColor = "lightgrey"
    t.doneTaskBorderColor = "grey"
    t.critBorderColor = "#ff8888"
    t.critBkgColor = "red"
    t.todayLineColor = "red"

    /* C4 Context Diagram */
    t.personBorder = ""
    t.personBkg = ""

    /* State colors */
    t.labelColor = "black"
    t.errorBkgColor = "#552222"
    t.errorTextColor = "#552222"

    t
  }
}
