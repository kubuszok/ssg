/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/git/gitGraphRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from GitDb + config -> SVG string; custom horizontal commit graph layout
 *   Renames: gitGraphRenderer draw() -> GitRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package git

import ssg.mermaid.MermaidConfig
import ssg.mermaid.Accessibility
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a git graph diagram to SVG.
  *
  * Produces a horizontal or vertical commit graph with branches shown as parallel lanes.
  */
object GitRenderer {

  private val DiagramPadding: Double = 20.0
  private val CommitRadius:   Double = 10.0
  private val BranchSpacing:  Double = 30.0
  private val LabelOffset:    Double = 15.0

  /** Renders a git graph to an SVG string.
    *
    * @param db
    *   the populated git graph database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: GitDb, config: MermaidConfig): String = {
    val gitConfig    = config.gitGraph
    val isHorizontal = db.direction == "LR" || db.direction == "RL"

    // Build branch lane positions
    val branchLanes = mutable.LinkedHashMap.empty[String, Int]
    for ((branch, idx) <- db.orderedBranches.zipWithIndex)
      branchLanes(branch.name) = idx

    val orderedCommits = db.orderedCommits
    val numCommits     = orderedCommits.length
    val numBranches    = branchLanes.size

    // Compute dimensions
    val spacing               = gitConfig.nodeSpacing.toDouble
    val (svgWidth, svgHeight) = if (isHorizontal) {
      val w = DiagramPadding * 2 + numCommits * spacing + 100
      val h = DiagramPadding * 2 + numBranches * BranchSpacing + 60
      (w, h)
    } else {
      val w = DiagramPadding * 2 + numBranches * BranchSpacing + 100
      val h = DiagramPadding * 2 + numCommits * spacing + 60
      (w, h)
    }

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "gitGraph", db.accTitle, db.accDescription)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = GitStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    // Main group
    val mainGroup = svg.append("g")
    mainGroup.attr("transform", s"translate($DiagramPadding, $DiagramPadding)")

    // Compute commit positions
    val commitPositions = mutable.LinkedHashMap.empty[String, (Double, Double)]
    for ((commit, idx) <- orderedCommits.zipWithIndex) {
      val laneIdx = branchLanes.getOrElse(commit.branch, 0)
      val (x, y)  = if (isHorizontal) {
        (idx * spacing + 50, laneIdx * BranchSpacing + 30)
      } else {
        (laneIdx * BranchSpacing + 50, idx * spacing + 30)
      }
      commitPositions(commit.id) = (x, y)
    }

    // Draw branch lanes
    if (gitConfig.showBranches) {
      renderBranchLabels(mainGroup, db, branchLanes, isHorizontal, spacing, numCommits)
    }

    // Draw branch lines
    renderBranchLines(mainGroup, db, commitPositions, branchLanes, isHorizontal, spacing, numCommits, themeVars)

    // Draw edges (parent -> child connections)
    renderEdges(mainGroup, orderedCommits, commitPositions, themeVars)

    // Draw commits
    renderCommits(mainGroup, orderedCommits, commitPositions, gitConfig, themeVars)

    svg.build().toMarkup()
  }

  /** Renders branch labels. */
  private def renderBranchLabels(
    parent:       SvgBuilder,
    db:           GitDb,
    branchLanes:  mutable.LinkedHashMap[String, Int],
    isHorizontal: Boolean,
    spacing:      Double,
    numCommits:   Int
  ): Unit =
    for ((branch, laneIdx) <- branchLanes) {
      val label = parent.append("text")
      if (isHorizontal) {
        label.attr("x", 5)
        label.attr("y", laneIdx * BranchSpacing + 35)
      } else {
        label.attr("x", laneIdx * BranchSpacing + 50)
        label.attr("y", 15)
        label.attr("text-anchor", "middle")
      }
      label.classed("branch-label", true)
      label.attr("font-size", "12")
      label.text(branch)
    }

  /** Renders branch lane lines. */
  private def renderBranchLines(
    parent:          SvgBuilder,
    db:              GitDb,
    commitPositions: mutable.LinkedHashMap[String, (Double, Double)],
    branchLanes:     mutable.LinkedHashMap[String, Int],
    isHorizontal:    Boolean,
    spacing:         Double,
    numCommits:      Int,
    themeVars:       ssg.mermaid.theme.ThemeVariables
  ): Unit =
    // Group commits by branch to draw branch lines
    for ((branchName, laneIdx) <- branchLanes) {
      val branchCommits = db.orderedCommits.filter(_.branch == branchName)
      if (branchCommits.length >= 2) {
        for (i <- 0 until branchCommits.length - 1) {
          val from = commitPositions.get(branchCommits(i).id)
          val to   = commitPositions.get(branchCommits(i + 1).id)
          (from, to) match {
            case (Some((x1, y1)), Some((x2, y2))) =>
              val line = parent.append("line")
              line.attr("x1", x1)
              line.attr("y1", y1)
              line.attr("x2", x2)
              line.attr("y2", y2)
              line.classed("branch-line", true)
              val colorIdx = laneIdx % themeVars.git.length
              val color    = if (themeVars.git(colorIdx).nonEmpty) themeVars.git(colorIdx) else "#333"
              line.style("stroke", color)
              line.style("stroke-width", "2")
            case _ => ()
          }
        }
      }
    }

  /** Renders parent-child edges (for merges and branching). */
  private def renderEdges(
    parent:    SvgBuilder,
    commits:   Array[GitCommit],
    positions: mutable.LinkedHashMap[String, (Double, Double)],
    themeVars: ssg.mermaid.theme.ThemeVariables
  ): Unit =
    for (commit <- commits)
      // Draw edge from second parent (merge/cherry-pick line)
      commit.secondParent.foreach { spId =>
        val from = positions.get(spId)
        val to   = positions.get(commit.id)
        (from, to) match {
          case (Some((x1, y1)), Some((x2, y2))) =>
            val path = parent.append("path")
            val midX = (x1 + x2) / 2.0
            path.attr("d", s"M $x1 $y1 C $midX $y1 $midX $y2 $x2 $y2")
            path.classed("merge-line", true)
            path.style("fill", "none")
            path.style("stroke", "#888")
            path.style("stroke-width", "1.5")
            path.style("stroke-dasharray", "5,3")
          case _ => ()
        }
      }

  /** Renders commit nodes. */
  private def renderCommits(
    parent:    SvgBuilder,
    commits:   Array[GitCommit],
    positions: mutable.LinkedHashMap[String, (Double, Double)],
    gitConfig: GitGraphConfig,
    themeVars: ssg.mermaid.theme.ThemeVariables
  ): Unit =
    for (commit <- commits)
      positions.get(commit.id).foreach { case (x, y) =>
        val g = parent.append("g")
        g.classed("commit", true)
        g.attr("transform", s"translate($x, $y)")

        // Branch color
        val branchIdx = commits.indexWhere(_.branch == commit.branch)
        val colorIdx  = branchIdx % themeVars.git.length
        val fillColor = commit.commitType match {
          case CommitType.Reverse   => if (themeVars.gitInv(colorIdx).nonEmpty) themeVars.gitInv(colorIdx) else "#fff"
          case CommitType.Highlight => if (themeVars.git(colorIdx).nonEmpty) themeVars.git(colorIdx) else "#333"
          case _                    => if (themeVars.git(colorIdx).nonEmpty) themeVars.git(colorIdx) else "#333"
        }

        // Commit circle
        commit.commitType match {
          case CommitType.Merge =>
            // Merge commit: double circle
            val outer = g.append("circle")
            outer.attr("r", CommitRadius)
            outer.style("fill", fillColor)
            outer.style("stroke", "#333")
            outer.style("stroke-width", "2")
            val inner = g.append("circle")
            inner.attr("r", CommitRadius - 4)
            inner.style("fill", "#fff")
            inner.style("stroke", "none")

          case CommitType.CherryPick =>
            // Cherry-pick: diamond
            val d       = CommitRadius
            val diamond = g.append("polygon")
            diamond.attr("points", s"0,${-d} $d,0 0,$d ${-d},0")
            diamond.style("fill", fillColor)
            diamond.style("stroke", "#333")
            diamond.style("stroke-width", "1")

          case CommitType.Reverse =>
            // Reverse: cross through circle
            val circle = g.append("circle")
            circle.attr("r", CommitRadius)
            circle.style("fill", fillColor)
            circle.style("stroke", "#333")
            circle.style("stroke-width", "2")
            val line1 = g.append("line")
            line1.attr("x1", -5)
            line1.attr("y1", -5)
            line1.attr("x2", 5)
            line1.attr("y2", 5)
            line1.style("stroke", "#333")
            val line2 = g.append("line")
            line2.attr("x1", -5)
            line2.attr("y1", 5)
            line2.attr("x2", 5)
            line2.attr("y2", -5)
            line2.style("stroke", "#333")

          case _ =>
            // Normal/Highlight: filled circle
            val circle = g.append("circle")
            circle.attr("r", CommitRadius)
            circle.classed("commit-node", true)
            circle.style("fill", fillColor)
            circle.style("stroke", "#333")
            circle.style("stroke-width", "2")
        }

        // Commit label
        if (gitConfig.showCommitLabel) {
          val label = g.append("text")
          label.attr("x", 0)
          label.attr("y", CommitRadius + LabelOffset)
          label.attr("text-anchor", "middle")
          label.attr("font-size", themeVars.commitLabelFontSize)
          label.classed("commit-label", true)

          val labelText = if (commit.message.nonEmpty) commit.message else commit.id
          label.text(labelText)

          if (gitConfig.rotateCommitLabel) {
            label.attr("transform", s"rotate(-45, 0, ${CommitRadius + LabelOffset})")
            label.attr("text-anchor", "start")
          }
        }

        // Tag
        commit.tag.foreach { tagText =>
          val tagLabel = g.append("text")
          tagLabel.attr("x", 0)
          tagLabel.attr("y", -(CommitRadius + 5))
          tagLabel.attr("text-anchor", "middle")
          tagLabel.attr("font-size", themeVars.tagLabelFontSize)
          tagLabel.classed("tag-label", true)
          tagLabel.text(tagText)
        }
      }
}
