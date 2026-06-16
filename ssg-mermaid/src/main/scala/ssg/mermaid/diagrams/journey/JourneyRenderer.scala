/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/user-journey/journeyRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from JourneyDb + config -> SVG string; horizontal swim lanes with colored task boxes
 *   Renames: journeyRenderer draw() -> JourneyRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package journey

import ssg.mermaid.MermaidConfig
import ssg.mermaid.Accessibility
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a user journey diagram to SVG.
  *
  * Uses horizontal swim lanes with task boxes colored by satisfaction score.
  */
object JourneyRenderer {

  private val DiagramPadding:    Double = 20.0
  private val TaskBoxWidth:      Double = 150.0
  private val TaskBoxHeight:     Double = 50.0
  private val TaskGap:           Double = 20.0
  private val SectionHeight:     Double = 70.0
  private val SectionLabelWidth: Double = 150.0
  private val ActorLabelHeight:  Double = 30.0

  /** Renders a user journey diagram to an SVG string.
    *
    * @param db
    *   the populated journey database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: JourneyDb, config: MermaidConfig): String = {
    // Calculate dimensions
    val maxTasksInSection = if (db.sections.nonEmpty) {
      db.sections.map(_.tasks.length).max
    } else {
      db.tasks.length
    }

    val chartWidth  = SectionLabelWidth + maxTasksInSection * (TaskBoxWidth + TaskGap) + DiagramPadding * 2
    val numSections = math.max(db.sections.length, 1)
    val chartHeight = DiagramPadding + (if (db.title.nonEmpty) 40 else 0) +
      ActorLabelHeight + numSections * SectionHeight + DiagramPadding

    val viewBox = s"0 0 $chartWidth $chartHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "journey", db.accTitle, db.accDescription)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = JourneyStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    // Main group
    val mainGroup = svg.append("g")

    var y = DiagramPadding

    // Title
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", chartWidth / 2.0)
      titleText.attr("y", y + 20)
      titleText.attr("text-anchor", "middle")
      titleText.classed("journeyTitle", true)
      titleText.text(db.title)
      y += 40
    }

    // Render sections with tasks
    if (db.sections.nonEmpty) {
      for ((section, sectionIdx) <- db.sections.zipWithIndex) {
        // Section background
        val sectionBg = mainGroup.append("rect")
        sectionBg.attr("x", 0)
        sectionBg.attr("y", y)
        sectionBg.attr("width", chartWidth)
        sectionBg.attr("height", SectionHeight)
        sectionBg.classed(s"section-${sectionIdx % 2}", true)

        // Section label
        val sectionLabel = mainGroup.append("text")
        sectionLabel.attr("x", 10)
        sectionLabel.attr("y", y + SectionHeight / 2.0 + 5)
        sectionLabel.classed("journeySection", true)
        sectionLabel.text(section.name)

        // Tasks
        var taskX = SectionLabelWidth
        for (task <- section.tasks) {
          renderTask(mainGroup, task, taskX, y + 10, themeVars)
          taskX += TaskBoxWidth + TaskGap
        }

        y += SectionHeight
      }
    } else {
      // No sections — render tasks directly
      var taskX = SectionLabelWidth
      for (task <- db.tasks) {
        renderTask(mainGroup, task, taskX, y + 10, themeVars)
        taskX += TaskBoxWidth + TaskGap
      }
    }

    svg.build().toMarkup()
  }

  /** Renders a single task box. */
  private def renderTask(parent: SvgBuilder, task: JourneyTask, x: Double, y: Double, themeVars: ssg.mermaid.theme.ThemeVariables): Unit = {
    val fillColor = scoreColor(task.score, themeVars)

    val taskGroup = parent.append("g")
    taskGroup.attr("transform", s"translate($x, $y)")

    // Task box
    val rect = taskGroup.append("rect")
    rect.attr("width", TaskBoxWidth)
    rect.attr("height", TaskBoxHeight)
    rect.attr("rx", 5)
    rect.attr("ry", 5)
    rect.classed("journeyTask", true)
    rect.style("fill", fillColor)

    // Task name
    val nameText = taskGroup.append("text")
    nameText.attr("x", TaskBoxWidth / 2.0)
    nameText.attr("y", 20)
    nameText.attr("text-anchor", "middle")
    nameText.classed("journeyTaskText", true)
    nameText.text(task.name)

    // Score indicator
    val scoreText = taskGroup.append("text")
    scoreText.attr("x", TaskBoxWidth / 2.0)
    scoreText.attr("y", 40)
    scoreText.attr("text-anchor", "middle")
    scoreText.attr("font-size", "12")
    scoreText.classed("journeyScore", true)
    scoreText.text(s"${task.score}/5")

    // Actor labels below the box
    if (task.actors.nonEmpty) {
      val actorText = taskGroup.append("text")
      actorText.attr("x", TaskBoxWidth / 2.0)
      actorText.attr("y", TaskBoxHeight + 15)
      actorText.attr("text-anchor", "middle")
      actorText.attr("font-size", "10")
      actorText.classed("journeyActor", true)
      actorText.text(task.actors.mkString(", "))
    }
  }

  /** Returns a color based on the satisfaction score (1-5). */
  private def scoreColor(score: Int, themeVars: ssg.mermaid.theme.ThemeVariables): String = {
    val idx       = (score - 1).max(0).min(7)
    val fillColor = idx match {
      case 0 => themeVars.fillType0
      case 1 => themeVars.fillType1
      case 2 => themeVars.fillType2
      case 3 => themeVars.fillType3
      case 4 => themeVars.fillType4
      case _ => themeVars.fillType5
    }
    if (fillColor.nonEmpty) fillColor
    else {
      // Fallback gradient from red (1) to green (5)
      score match {
        case 1 => "#ff6b6b"
        case 2 => "#ffa07a"
        case 3 => "#ffd700"
        case 4 => "#90ee90"
        case 5 => "#5cb85c"
        case _ => "#ffd700"
      }
    }
  }
}
