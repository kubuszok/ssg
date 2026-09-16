/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/themes/theme-default.js (+ dark/forest/neutral/base)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces per-theme JS classes with a single mutable variables container
 *   Idiom: Mutable fields matching original's this.xxx pattern; calculate() for derived values
 *   Renames: Theme class fields → ThemeVariables fields
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

import ssg.mermaid.color.{ AdjustOptions, ColorOps }

/** Holds all CSS variable values that a Mermaid theme provides.
  *
  * Each theme sets base variables in its constructor equivalent, then derives secondary variables via [[updateColors]]. Users can override any variable before or after derivation via [[calculate]].
  *
  * The fields here correspond to the `this.xxx = ...` assignments in the original JavaScript theme classes. They are mutable to match the original's two-pass override/calculate pattern.
  */
final class ThemeVariables {

  // --- Base variables ---
  var darkMode:             Boolean = false
  var background:           String  = "#f4f4f4"
  var primaryColor:         String  = "#ECECFF"
  var secondaryColor:       String  = "#ffffde"
  var tertiaryColor:        String  = ""
  var primaryBorderColor:   String  = ""
  var secondaryBorderColor: String  = ""
  var tertiaryBorderColor:  String  = ""
  var primaryTextColor:     String  = ""
  var secondaryTextColor:   String  = ""
  var tertiaryTextColor:    String  = ""
  var lineColor:            String  = "#333333"
  var textColor:            String  = "#333"
  var mainBkg:              String  = "#ECECFF"
  var secondBkg:            String  = "#ffffde"
  var border1:              String  = "#9370DB"
  var border2:              String  = "#aaaa33"
  var arrowheadColor:       String  = "#333333"
  var fontFamily:           String  = "\"trebuchet ms\", verdana, arial, sans-serif"
  var fontSize:             String  = "16px"
  var labelBackground:      String  = "rgba(232,232,232, 0.8)"
  var THEME_COLOR_LIMIT:    Int     = 12

  // --- Flowchart variables ---
  var nodeBkg:             String = ""
  var nodeBorder:          String = ""
  var clusterBkg:          String = ""
  var clusterBorder:       String = ""
  var defaultLinkColor:    String = ""
  var titleColor:          String = ""
  var edgeLabelBackground: String = ""
  var nodeTextColor:       String = ""

  // --- Sequence Diagram variables ---
  var actorBorder:           String = ""
  var actorBkg:              String = ""
  var actorTextColor:        String = "black"
  var actorLineColor:        String = ""
  var signalColor:           String = ""
  var signalTextColor:       String = ""
  var labelBoxBkgColor:      String = ""
  var labelBoxBorderColor:   String = ""
  var labelTextColor:        String = ""
  var loopTextColor:         String = ""
  var noteBorderColor:       String = ""
  var noteBkgColor:          String = "#fff5ad"
  var noteTextColor:         String = ""
  var activationBorderColor: String = "#666"
  var activationBkgColor:    String = "#f4f4f4"
  var sequenceNumberColor:   String = "white"

  // --- Gantt chart variables ---
  var sectionBkgColor:        String = ""
  var altSectionBkgColor:     String = "white"
  var sectionBkgColor2:       String = ""
  var excludeBkgColor:        String = "#eeeeee"
  var taskBorderColor:        String = ""
  var taskBkgColor:           String = ""
  var taskTextLightColor:     String = "white"
  var taskTextColor:          String = ""
  var taskTextDarkColor:      String = "black"
  var taskTextOutsideColor:   String = ""
  var taskTextClickableColor: String = "#003163"
  var activeTaskBorderColor:  String = ""
  var activeTaskBkgColor:     String = ""
  var gridColor:              String = "lightgrey"
  var doneTaskBkgColor:       String = "lightgrey"
  var doneTaskBorderColor:    String = "grey"
  var critBorderColor:        String = "#ff8888"
  var critBkgColor:           String = "red"
  var todayLineColor:         String = "red"

  // --- State colors ---
  var labelColor:     String = "black"
  var errorBkgColor:  String = "#552222"
  var errorTextColor: String = "#552222"

  // --- C4 Context Diagram variables ---
  var personBorder: String = ""
  var personBkg:    String = ""

  // --- State diagram ---
  var transitionColor:          String = ""
  var transitionLabelColor:     String = ""
  var stateLabelColor:          String = ""
  var stateBkg:                 String = ""
  var labelBackgroundColor:     String = ""
  var compositeBackground:      String = ""
  var altBackground:            String = ""
  var compositeTitleBackground: String = ""
  var compositeBorder:          String = ""
  var innerEndBackground:       String = ""
  var specialStateColor:        String = ""

  // --- Class diagram ---
  var classText: String = ""

  // --- Journey/fillType ---
  var fillType0: String = ""
  var fillType1: String = ""
  var fillType2: String = ""
  var fillType3: String = ""
  var fillType4: String = ""
  var fillType5: String = ""
  var fillType6: String = ""
  var fillType7: String = ""

  // --- Color scale (cScale0-11, cScalePeer, cScaleInv, cScaleLabel, surface) ---
  val cScale:          Array[String] = Array.fill(THEME_COLOR_LIMIT)("")
  val cScalePeer:      Array[String] = Array.fill(THEME_COLOR_LIMIT)("")
  val cScaleInv:       Array[String] = Array.fill(THEME_COLOR_LIMIT)("")
  val cScaleLabel:     Array[String] = Array.fill(THEME_COLOR_LIMIT)("")
  val surface:         Array[String] = Array.fill(5)("")
  val surfacePeer:     Array[String] = Array.fill(5)("")
  var scaleLabelColor: String        = ""

  // --- Pie ---
  val pie:                 Array[String] = Array.fill(13)("")
  var pieTitleTextSize:    String        = "25px"
  var pieTitleTextColor:   String        = ""
  var pieSectionTextSize:  String        = "17px"
  var pieSectionTextColor: String        = ""
  var pieLegendTextSize:   String        = "17px"
  var pieLegendTextColor:  String        = ""
  var pieStrokeColor:      String        = "black"
  var pieStrokeWidth:      String        = "2px"
  var pieOuterStrokeWidth: String        = "2px"
  var pieOuterStrokeColor: String        = "black"
  var pieOpacity:          String        = "0.7"

  // --- Git ---
  val git:                   Array[String] = Array.fill(8)("")
  val gitInv:                Array[String] = Array.fill(8)("")
  val gitBranchLabel:        Array[String] = Array.fill(8)("")
  var tagLabelColor:         String        = ""
  var tagLabelBackground:    String        = ""
  var tagLabelBorder:        String        = ""
  var tagLabelFontSize:      String        = "10px"
  var commitLabelColor:      String        = ""
  var commitLabelBackground: String        = ""
  var commitLabelFontSize:   String        = "10px"

  // --- ER diagram ---
  var attributeBackgroundColorOdd:  String = "#ffffff"
  var attributeBackgroundColorEven: String = "#f2f2f2"

  // --- Requirement diagram ---
  var requirementBackground:   String = ""
  var requirementBorderColor:  String = ""
  var requirementBorderSize:   String = "1"
  var requirementTextColor:    String = ""
  var relationColor:           String = ""
  var relationLabelBackground: String = ""
  var relationLabelColor:      String = ""

  // --- Extra dark theme fields ---
  var mainContrastColor: String = ""
  var darkTextColor:     String = ""

  // --- Neutral theme extras ---
  var contrast:         String = ""
  var note:             String = ""
  var text:             String = ""
  var critical:         String = ""
  var done:             String = ""
  var branchLabelColor: String = ""

  // --- Quadrant ---
  var quadrant1Fill:                    String = ""
  var quadrant2Fill:                    String = ""
  var quadrant3Fill:                    String = ""
  var quadrant4Fill:                    String = ""
  var quadrant1TextFill:                String = ""
  var quadrant2TextFill:                String = ""
  var quadrant3TextFill:                String = ""
  var quadrant4TextFill:                String = ""
  var quadrantPointFill:                String = ""
  var quadrantPointTextFill:            String = ""
  var quadrantXAxisTextFill:            String = ""
  var quadrantYAxisTextFill:            String = ""
  var quadrantInternalBorderStrokeFill: String = ""
  var quadrantExternalBorderStrokeFill: String = ""
  var quadrantTitleFill:                String = ""

  /** Helper: mkBorder produces a border color from a base color and dark mode flag.
    *
    * Matches `theme-helpers.js` mkBorder function.
    */
  private def mkBorder(col: String, dark: Boolean): String =
    if (dark) ColorOps.adjust(col, AdjustOptions(s = -40, l = 10))
    else ColorOps.adjust(col, AdjustOptions(s = -40, l = -10))

  /** Derives secondary/tertiary colors from primary ones.
    *
    * This is called after base colors are set (either from theme defaults or user overrides). Matches the original `updateColors()` method.
    */
  def updateColors(): Unit = {
    // Derive border colors
    if (primaryBorderColor.isEmpty) {
      primaryBorderColor = mkBorder(primaryColor, darkMode)
    }
    if (secondaryBorderColor.isEmpty) {
      secondaryBorderColor = mkBorder(secondaryColor, darkMode)
    }
    if (tertiaryColor.isEmpty) {
      tertiaryColor = ColorOps.adjust(primaryColor, AdjustOptions(h = -160))
    }
    if (tertiaryBorderColor.isEmpty) {
      tertiaryBorderColor = mkBorder(tertiaryColor, darkMode)
    }
    if (primaryTextColor.isEmpty) {
      primaryTextColor = ColorOps.invert(primaryColor)
    }
    if (secondaryTextColor.isEmpty) {
      secondaryTextColor = ColorOps.invert(secondaryColor)
    }
    if (tertiaryTextColor.isEmpty) {
      tertiaryTextColor = ColorOps.invert(tertiaryColor)
    }

    // Flowchart
    if (nodeBkg.isEmpty) nodeBkg = mainBkg
    if (nodeBorder.isEmpty) nodeBorder = border1
    if (clusterBkg.isEmpty) clusterBkg = secondBkg
    if (clusterBorder.isEmpty) clusterBorder = border2
    if (defaultLinkColor.isEmpty) defaultLinkColor = lineColor
    if (titleColor.isEmpty) titleColor = textColor
    if (edgeLabelBackground.isEmpty) edgeLabelBackground = labelBackground

    // Sequence
    if (actorBorder.isEmpty) actorBorder = ColorOps.lighten(border1, 23)
    if (actorBkg.isEmpty) actorBkg = mainBkg
    if (labelBoxBkgColor.isEmpty) labelBoxBkgColor = actorBkg
    if (signalColor.isEmpty) signalColor = textColor
    if (signalTextColor.isEmpty) signalTextColor = textColor
    if (labelBoxBorderColor.isEmpty) labelBoxBorderColor = actorBorder
    if (labelTextColor.isEmpty) labelTextColor = actorTextColor
    if (loopTextColor.isEmpty) loopTextColor = actorTextColor
    if (noteBorderColor.isEmpty) noteBorderColor = border2
    if (noteTextColor.isEmpty) noteTextColor = actorTextColor
    if (actorLineColor.isEmpty) actorLineColor = actorBorder

    // Gantt
    if (taskTextColor.isEmpty) taskTextColor = taskTextLightColor
    if (taskTextOutsideColor.isEmpty) taskTextOutsideColor = taskTextDarkColor

    // State
    if (transitionColor.isEmpty) transitionColor = lineColor
    if (transitionLabelColor.isEmpty) transitionLabelColor = textColor
    if (stateLabelColor.isEmpty) {
      stateLabelColor = if (stateBkg.nonEmpty) stateBkg else primaryTextColor
    }
    if (stateBkg.isEmpty) stateBkg = mainBkg
    if (labelBackgroundColor.isEmpty) labelBackgroundColor = stateBkg
    if (compositeBackground.isEmpty) {
      compositeBackground = if (background.nonEmpty) background else tertiaryColor
    }
    if (altBackground.isEmpty) altBackground = "#f0f0f0"
    if (compositeTitleBackground.isEmpty) compositeTitleBackground = mainBkg
    if (compositeBorder.isEmpty) compositeBorder = nodeBorder
    innerEndBackground = nodeBorder
    if (specialStateColor.isEmpty) specialStateColor = lineColor

    if (errorBkgColor.isEmpty) errorBkgColor = tertiaryColor
    if (errorTextColor.isEmpty) errorTextColor = tertiaryTextColor

    // Class
    if (classText.isEmpty) classText = primaryTextColor

    // Journey
    fillType0 = primaryColor
    fillType1 = secondaryColor
    fillType2 = ColorOps.adjust(primaryColor, AdjustOptions(h = 64))
    fillType3 = ColorOps.adjust(secondaryColor, AdjustOptions(h = 64))
    fillType4 = ColorOps.adjust(primaryColor, AdjustOptions(h = -64))
    fillType5 = ColorOps.adjust(secondaryColor, AdjustOptions(h = -64))
    fillType6 = ColorOps.adjust(primaryColor, AdjustOptions(h = 128))
    fillType7 = ColorOps.adjust(secondaryColor, AdjustOptions(h = 128))

    // Color scale
    if (cScale(0).isEmpty) cScale(0) = primaryColor
    if (cScale(1).isEmpty) cScale(1) = secondaryColor
    if (cScale(2).isEmpty) cScale(2) = tertiaryColor
    for (i <- 3 until THEME_COLOR_LIMIT)
      if (cScale(i).isEmpty) {
        cScale(i) = ColorOps.adjust(primaryColor, AdjustOptions(h = 30.0 * (i - 2)))
      }

    // cScaleInv
    for (i <- 0 until THEME_COLOR_LIMIT)
      if (cScaleInv(i).isEmpty) cScaleInv(i) = ColorOps.invert(cScale(i))

    // cScalePeer
    for (i <- 0 until THEME_COLOR_LIMIT)
      if (cScalePeer(i).isEmpty) cScalePeer(i) = ColorOps.darken(cScale(i), 25)

    // surface/surfacePeer
    for (i <- 0 until 5) {
      if (surface(i).isEmpty) {
        surface(i) = ColorOps.adjust(mainBkg, AdjustOptions(h = 30, l = -(5 + i * 5)))
      }
      if (surfacePeer(i).isEmpty) {
        surfacePeer(i) = ColorOps.adjust(mainBkg, AdjustOptions(h = 30, l = -(7 + i * 5)))
      }
    }

    // scale label
    if (scaleLabelColor.isEmpty) scaleLabelColor = labelTextColor
    for (i <- 0 until THEME_COLOR_LIMIT)
      if (cScaleLabel(i).isEmpty) cScaleLabel(i) = scaleLabelColor

    // Pie
    if (pie(0).isEmpty) pie(0) = primaryColor
    if (pie(1).isEmpty) pie(1) = secondaryColor
    if (pie(2).isEmpty) pie(2) = tertiaryColor
    for (i <- 3 until math.min(13, THEME_COLOR_LIMIT + 1))
      if (pie(i).isEmpty) pie(i) = cScale(math.min(i, THEME_COLOR_LIMIT - 1))
    if (pieTitleTextColor.isEmpty) pieTitleTextColor = taskTextDarkColor
    if (pieSectionTextColor.isEmpty) pieSectionTextColor = textColor
    if (pieLegendTextColor.isEmpty) pieLegendTextColor = taskTextDarkColor

    // Requirement
    if (requirementBackground.isEmpty) requirementBackground = primaryColor
    if (requirementBorderColor.isEmpty) requirementBorderColor = primaryBorderColor
    if (requirementTextColor.isEmpty) requirementTextColor = primaryTextColor
    if (relationColor.isEmpty) relationColor = lineColor
    if (relationLabelBackground.isEmpty) relationLabelBackground = labelBackground
    if (relationLabelColor.isEmpty) relationLabelColor = actorTextColor

    // Git
    if (git(0).isEmpty) git(0) = primaryColor
    if (git(1).isEmpty) git(1) = secondaryColor
    if (git(2).isEmpty) git(2) = tertiaryColor
    for (i <- 3 until 8) {
      val hueShift = i match {
        case 3 => -30.0
        case 4 => -60.0
        case 5 => -90.0
        case 6 => 60.0
        case 7 => 120.0
        case _ => 0.0
      }
      if (git(i).isEmpty) git(i) = ColorOps.adjust(primaryColor, AdjustOptions(h = hueShift))
    }
    if (darkMode) {
      for (i <- 0 until 8) git(i) = ColorOps.lighten(git(i), 25)
    } else {
      for (i <- 0 until 8) git(i) = ColorOps.darken(git(i), 25)
    }
    for (i <- 0 until 8)
      if (gitInv(i).isEmpty) gitInv(i) = ColorOps.invert(git(i))
    for (i <- 0 until 8)
      if (gitBranchLabel(i).isEmpty) {
        gitBranchLabel(i) = if (i == 0 || i == 3) ColorOps.invert(labelTextColor) else labelTextColor
      }

    if (tagLabelColor.isEmpty) tagLabelColor = primaryTextColor
    if (tagLabelBackground.isEmpty) tagLabelBackground = primaryColor
    if (tagLabelBorder.isEmpty) tagLabelBorder = primaryBorderColor
    if (commitLabelColor.isEmpty) commitLabelColor = secondaryTextColor
    if (commitLabelBackground.isEmpty) commitLabelBackground = secondaryColor

    // Quadrant
    if (quadrant1Fill.isEmpty) quadrant1Fill = primaryColor
    if (quadrant1TextFill.isEmpty) quadrant1TextFill = primaryTextColor
    if (quadrantPointTextFill.isEmpty) quadrantPointTextFill = primaryTextColor
    if (quadrantXAxisTextFill.isEmpty) quadrantXAxisTextFill = primaryTextColor
    if (quadrantYAxisTextFill.isEmpty) quadrantYAxisTextFill = primaryTextColor
    if (quadrantInternalBorderStrokeFill.isEmpty) quadrantInternalBorderStrokeFill = primaryBorderColor
    if (quadrantExternalBorderStrokeFill.isEmpty) quadrantExternalBorderStrokeFill = primaryBorderColor
    if (quadrantTitleFill.isEmpty) quadrantTitleFill = primaryTextColor

    // C4
    if (personBorder.isEmpty) personBorder = primaryBorderColor
    if (personBkg.isEmpty) personBkg = mainBkg
  }

  /** Applies user overrides and recalculates derived values.
    *
    * Matches the original `calculate(overrides)` pattern: set overrides, run updateColors, then re-apply overrides to ensure user-specified derived values take precedence.
    *
    * @param overrides
    *   a map of variable name → CSS color value
    */
  def calculate(overrides: Map[String, String]): Unit = {
    // Copy values from overrides (base colors)
    applyOverrides(overrides)
    // Calculate derived colors
    updateColors()
    // Re-apply overrides for derived value overrides
    applyOverrides(overrides)
  }

  /** Applies a map of overrides to the theme variables by name.
    *
    * Uses reflection-free field matching to map override keys to mutable fields.
    */
  private def applyOverrides(overrides: Map[String, String]): Unit =
    for ((key, value) <- overrides)
      setField(key, value)

  /** Sets a theme variable field by name.
    *
    * This is a large pattern match mirroring the dynamic `this[k] = overrides[k]` pattern in JS.
    */
  def setField(key: String, value: String): Unit = {
    key match {
      case "background"                   => background = value
      case "primaryColor"                 => primaryColor = value
      case "secondaryColor"               => secondaryColor = value
      case "tertiaryColor"                => tertiaryColor = value
      case "primaryBorderColor"           => primaryBorderColor = value
      case "secondaryBorderColor"         => secondaryBorderColor = value
      case "tertiaryBorderColor"          => tertiaryBorderColor = value
      case "primaryTextColor"             => primaryTextColor = value
      case "secondaryTextColor"           => secondaryTextColor = value
      case "tertiaryTextColor"            => tertiaryTextColor = value
      case "lineColor"                    => lineColor = value
      case "textColor"                    => textColor = value
      case "mainBkg"                      => mainBkg = value
      case "secondBkg"                    => secondBkg = value
      case "border1"                      => border1 = value
      case "border2"                      => border2 = value
      case "arrowheadColor"               => arrowheadColor = value
      case "fontFamily"                   => fontFamily = value
      case "fontSize"                     => fontSize = value
      case "labelBackground"              => labelBackground = value
      case "nodeBkg"                      => nodeBkg = value
      case "nodeBorder"                   => nodeBorder = value
      case "clusterBkg"                   => clusterBkg = value
      case "clusterBorder"                => clusterBorder = value
      case "defaultLinkColor"             => defaultLinkColor = value
      case "titleColor"                   => titleColor = value
      case "edgeLabelBackground"          => edgeLabelBackground = value
      case "nodeTextColor"                => nodeTextColor = value
      case "actorBorder"                  => actorBorder = value
      case "actorBkg"                     => actorBkg = value
      case "actorTextColor"               => actorTextColor = value
      case "actorLineColor"               => actorLineColor = value
      case "signalColor"                  => signalColor = value
      case "signalTextColor"              => signalTextColor = value
      case "labelBoxBkgColor"             => labelBoxBkgColor = value
      case "labelBoxBorderColor"          => labelBoxBorderColor = value
      case "labelTextColor"               => labelTextColor = value
      case "loopTextColor"                => loopTextColor = value
      case "noteBkgColor"                 => noteBkgColor = value
      case "noteTextColor"                => noteTextColor = value
      case "noteBorderColor"              => noteBorderColor = value
      case "activationBorderColor"        => activationBorderColor = value
      case "activationBkgColor"           => activationBkgColor = value
      case "sequenceNumberColor"          => sequenceNumberColor = value
      case "sectionBkgColor"              => sectionBkgColor = value
      case "altSectionBkgColor"           => altSectionBkgColor = value
      case "sectionBkgColor2"             => sectionBkgColor2 = value
      case "excludeBkgColor"              => excludeBkgColor = value
      case "taskBorderColor"              => taskBorderColor = value
      case "taskBkgColor"                 => taskBkgColor = value
      case "taskTextLightColor"           => taskTextLightColor = value
      case "taskTextColor"                => taskTextColor = value
      case "taskTextDarkColor"            => taskTextDarkColor = value
      case "taskTextOutsideColor"         => taskTextOutsideColor = value
      case "taskTextClickableColor"       => taskTextClickableColor = value
      case "activeTaskBorderColor"        => activeTaskBorderColor = value
      case "activeTaskBkgColor"           => activeTaskBkgColor = value
      case "gridColor"                    => gridColor = value
      case "doneTaskBkgColor"             => doneTaskBkgColor = value
      case "doneTaskBorderColor"          => doneTaskBorderColor = value
      case "critBorderColor"              => critBorderColor = value
      case "critBkgColor"                 => critBkgColor = value
      case "todayLineColor"               => todayLineColor = value
      case "labelColor"                   => labelColor = value
      case "errorBkgColor"                => errorBkgColor = value
      case "errorTextColor"               => errorTextColor = value
      case "transitionColor"              => transitionColor = value
      case "transitionLabelColor"         => transitionLabelColor = value
      case "stateLabelColor"              => stateLabelColor = value
      case "stateBkg"                     => stateBkg = value
      case "labelBackgroundColor"         => labelBackgroundColor = value
      case "compositeBackground"          => compositeBackground = value
      case "altBackground"                => altBackground = value
      case "compositeTitleBackground"     => compositeTitleBackground = value
      case "compositeBorder"              => compositeBorder = value
      case "innerEndBackground"           => innerEndBackground = value
      case "specialStateColor"            => specialStateColor = value
      case "classText"                    => classText = value
      case "personBorder"                 => personBorder = value
      case "personBkg"                    => personBkg = value
      case "attributeBackgroundColorOdd"  => attributeBackgroundColorOdd = value
      case "attributeBackgroundColorEven" => attributeBackgroundColorEven = value
      case "requirementBackground"        => requirementBackground = value
      case "requirementBorderColor"       => requirementBorderColor = value
      case "requirementBorderSize"        => requirementBorderSize = value
      case "requirementTextColor"         => requirementTextColor = value
      case "relationColor"                => relationColor = value
      case "relationLabelBackground"      => relationLabelBackground = value
      case "relationLabelColor"           => relationLabelColor = value
      case "pieTitleTextColor"            => pieTitleTextColor = value
      case "pieSectionTextColor"          => pieSectionTextColor = value
      case "pieLegendTextColor"           => pieLegendTextColor = value
      case "pieStrokeColor"               => pieStrokeColor = value
      case "pieStrokeWidth"               => pieStrokeWidth = value
      case "pieOpacity"                   => pieOpacity = value
      case "quadrant1Fill"                => quadrant1Fill = value
      case "quadrant2Fill"                => quadrant2Fill = value
      case "quadrant3Fill"                => quadrant3Fill = value
      case "quadrant4Fill"                => quadrant4Fill = value
      case "quadrant1TextFill"            => quadrant1TextFill = value
      case "quadrant2TextFill"            => quadrant2TextFill = value
      case "quadrant3TextFill"            => quadrant3TextFill = value
      case "quadrant4TextFill"            => quadrant4TextFill = value
      case "quadrantPointFill"            => quadrantPointFill = value
      case "quadrantPointTextFill"        => quadrantPointTextFill = value
      case "quadrantXAxisTextFill"        => quadrantXAxisTextFill = value
      case "quadrantYAxisTextFill"        => quadrantYAxisTextFill = value
      case "quadrantTitleFill"            => quadrantTitleFill = value
      case "tagLabelColor"                => tagLabelColor = value
      case "tagLabelBackground"           => tagLabelBackground = value
      case "tagLabelBorder"               => tagLabelBorder = value
      case "commitLabelColor"             => commitLabelColor = value
      case "commitLabelBackground"        => commitLabelBackground = value
      case "darkMode"                     => darkMode = value == "true"
      case _                              => () // unknown key — ignore
    }
  }
}
