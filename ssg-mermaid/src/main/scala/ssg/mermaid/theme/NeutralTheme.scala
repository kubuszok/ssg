/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/theme-neutral.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Constructor assignments translated to create() factory setting mutable fields
 *   Idiom: Factory method returns ThemeVariables with all base values pre-set
 *   Renames: Theme constructor → NeutralTheme.create()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

import ssg.mermaid.color.{ AdjustOptions, ColorOps }

/** Neutral grayscale theme.
  *
  * Ports the constructor of `theme-neutral.js`: primary = `#eee`, contrast = `#707070`, grayscale palette.
  */
object NeutralTheme {

  /** Creates a new [[ThemeVariables]] with neutral theme base values. */
  def create(): ThemeVariables = {
    val t = new ThemeVariables

    t.primaryColor = "#eee"
    t.contrast = "#707070"
    t.secondaryColor = ColorOps.lighten(t.contrast, 55)
    t.background = "#ffffff"

    t.tertiaryColor = ColorOps.adjust(t.primaryColor, AdjustOptions(h = -160))
    t.primaryBorderColor = "" // mkBorder
    t.secondaryBorderColor = "" // mkBorder
    t.tertiaryBorderColor = "" // mkBorder

    t.primaryTextColor = ColorOps.invert(t.primaryColor)
    t.secondaryTextColor = ColorOps.invert(t.secondaryColor)
    t.tertiaryTextColor = ColorOps.invert(t.tertiaryColor)
    t.lineColor = ColorOps.invert(t.background)
    t.textColor = ColorOps.invert(t.background)

    t.mainBkg = "#eee"
    t.secondBkg = "" // calculated: lighten(contrast, 55)
    t.lineColor = "#666"
    t.border1 = "#999"
    t.border2 = "" // calculated: contrast
    t.note = "#ffa"
    t.text = "#333"
    t.critical = "#d42"
    t.done = "#bbb"
    t.arrowheadColor = "#333333"
    t.fontFamily = "\"trebuchet ms\", verdana, arial, sans-serif"
    t.fontSize = "16px"
    t.THEME_COLOR_LIMIT = 12

    /* Flowchart */
    t.nodeBkg = ""
    t.nodeBorder = ""
    t.clusterBkg = ""
    t.clusterBorder = ""
    t.defaultLinkColor = ""
    t.titleColor = ""
    t.edgeLabelBackground = "white"

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
    t.noteBkgColor = ""
    t.noteTextColor = ""
    t.activationBorderColor = "#666"
    t.activationBkgColor = "#f4f4f4"
    t.sequenceNumberColor = "white"

    /* Gantt chart */
    t.sectionBkgColor = ""
    t.altSectionBkgColor = "white"
    t.sectionBkgColor2 = ""
    t.excludeBkgColor = "#eeeeee"
    t.taskBorderColor = ""
    t.taskBkgColor = ""
    t.taskTextLightColor = "white"
    t.taskTextColor = ""
    t.taskTextDarkColor = ""
    t.taskTextOutsideColor = ""
    t.taskTextClickableColor = "#003163"
    t.activeTaskBorderColor = ""
    t.activeTaskBkgColor = ""
    t.gridColor = ""
    t.doneTaskBkgColor = ""
    t.doneTaskBorderColor = ""
    t.critBkgColor = ""
    t.critBorderColor = ""
    t.todayLineColor = ""

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
