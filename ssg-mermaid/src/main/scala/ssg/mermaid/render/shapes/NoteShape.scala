/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/note.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Rectangle with dog-ear fold; Intersect.rect for edge routing
 *   Renames: note() → NoteShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.layout.dagre.Point
import ssg.mermaid.svg.{ PathData, SvgBuilder }

/** Renders a note shape for sequence diagram notes.
  *
  * A note is a rectangle with a folded corner (dog-ear) in the top-right. The fold is rendered as a triangle that gives the appearance of a turned-down page corner.
  */
object NoteShape {

  /** Size of the dog-ear fold, in pixels. */
  private val FoldSize: Double = 7.0

  /** Renders a note shape.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param config
    *   shape configuration with position, size, and style
    * @return
    *   shape result with the rendered group and an intersection function
    */
  def render(parent: SvgBuilder, config: ShapeConfig): ShapeResult = {
    val group = parent.append("g")

    if (config.cssClass.nonEmpty) {
      group.classed(config.cssClass, true)
    }
    if (config.id.nonEmpty) {
      group.attr("id", config.id)
    }

    val halfW = config.width / 2.0
    val halfH = config.height / 2.0
    val cx    = config.x
    val cy    = config.y

    val left   = cx - halfW
    val right  = cx + halfW
    val top    = cy - halfH
    val bottom = cy + halfH

    // Note body — rectangle with top-right corner cut off for the fold
    val bodyPath = PathData()
    bodyPath.moveTo(left, top)
    bodyPath.lineTo(right - FoldSize, top)
    bodyPath.lineTo(right, top + FoldSize)
    bodyPath.lineTo(right, bottom)
    bodyPath.lineTo(left, bottom)
    bodyPath.close()

    val body = group.append("path")
    body.attr("d", bodyPath.toString)
    body.classed("node-shape", true)
    body.classed("note-shape", true)

    if (config.style.nonEmpty) {
      body.attr("style", config.style)
    }

    // Dog-ear fold triangle
    val foldPath = PathData()
    foldPath.moveTo(right - FoldSize, top)
    foldPath.lineTo(right - FoldSize, top + FoldSize)
    foldPath.lineTo(right, top + FoldSize)
    foldPath.close()

    val fold = group.append("path")
    fold.attr("d", foldPath.toString)
    fold.classed("note-fold", true)

    // Add label
    if (config.label.nonEmpty) {
      val text = group.append("text")
      text.attr("x", cx)
      text.attr("y", cy)
      text.attr("dominant-baseline", "central")
      text.attr("text-anchor", "middle")
      text.classed("node-label", true)
      if (config.labelStyle.nonEmpty) {
        text.attr("style", config.labelStyle)
      }
      text.text(config.label)
    }

    val w = config.width
    val h = config.height

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.rect(cx, cy, w, h, point)
    )
  }
}
