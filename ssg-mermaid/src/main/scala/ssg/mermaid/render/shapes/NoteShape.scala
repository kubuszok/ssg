/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/note.ts
 *   (classic path) + mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/note.ts
 *   (the `node.look === 'handDrawn'` branch — ISS-1204 Chip 9g)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Rectangle with dog-ear fold; Intersect.rect for edge routing
 *   Renames: note() → NoteShape.render()
 *   Hand-drawn (note.ts): upstream's hand-drawn branch emits ONE rough element — a PLAIN
 *     rectangle (`rc.rectangle(x, y, totalWidth, totalHeight, { roughness: 0.7, fill: noteBkgColor,
 *     fillWeight: 3, seed: handDrawnSeed, stroke: noteBorderColor })`), with NO fold. Unlike the
 *     other hand-drawn shapes, note.ts passes a SPECIFIC options object (roughness 0.7, fillWeight 3,
 *     fill = theme `noteBkgColor`, stroke = theme `noteBorderColor`), NOT `userNodeOverrides`. SSG's
 *     CLASSIC note, however, already draws its OWN geometry — a `bodyPath` (rectangle with the
 *     top-right corner cut for the fold) plus a `foldPath` (the dog-ear triangle). To keep hand-drawn
 *     CONSISTENT with SSG's own classic note (same body + fold, so the sketch outlines the SAME shape
 *     the classic path would), the hand-drawn branch rough-sketches SSG's OWN `bodyPath.toString` and
 *     `foldPath.toString` via `rough.svg().path(...)` (Chip 8 `RoughSVG.path`) rather than emitting
 *     upstream's plain fold-less rectangle. This is the exact analogue of the 9c/9d/9f decision
 *     (rough-sketch SSG's own classic outline instead of the upstream primitive). DIVERGENCE from
 *     upstream: upstream's hand-drawn note has no fold; SSG keeps the fold so hand-drawn and classic
 *     notes are the same dog-eared shape. Both paths carry the upstream note-specific `noteOpts`
 *     (roughness 0.7, fillWeight 3, fill = `themeVariables.noteBkgColor`, stroke =
 *     `themeVariables.noteBorderColor`, seed = `config.handDrawnSeed`) — the exact faithful note
 *     styling, preserved as-is. Both grafted in SSG's classic child order (body first, then fold) via
 *     `HandDrawnShapes.graftElement`, carrying the classic classes (`node-shape` + `note-shape` on the
 *     body, `note-fold` on the fold) + inline style on the body (matching classic, which styles only
 *     the body). Label + Intersect.rect are unchanged. Classic rendering is byte-identical. The `d`
 *     reused from `PathData.toString` is parseFloat input to rough (integral coords exact — the 9b
 *     number-formatting decision).
 *
 * upstream-commit: 2cfdd1620 (note.ts classic) / 56a2762 (note.ts hand-drawn, ISS-1204)
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.render.labels.ShapeLabel
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.render.Intersect
import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }

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

    // Dog-ear fold triangle
    val foldPath = PathData()
    foldPath.moveTo(right - FoldSize, top)
    foldPath.lineTo(right - FoldSize, top + FoldSize)
    foldPath.lineTo(right, top + FoldSize)
    foldPath.close()

    if (config.look == "handDrawn") {
      // note.ts: the `node.look === 'handDrawn'` branch. Upstream emits a plain rough rectangle with
      // the note-specific options; SSG rough-sketches its OWN bodyPath + foldPath (keeping the fold)
      // so hand-drawn and classic notes are the same dog-eared shape.
      // const { noteBorderColor, noteBkgColor } = themeVariables;
      val noteBkgColor    = config.themeVariables.noteBkgColor
      val noteBorderColor = config.themeVariables.noteBorderColor

      // rc.rectangle(x, y, totalWidth, totalHeight, {
      //   roughness: 0.7, fill: noteBkgColor, fillWeight: 3, seed: handDrawnSeed, stroke: noteBorderColor });
      // The upstream note-specific options object (NOT userNodeOverrides): roughness 0.7, fillWeight 3,
      // fill = noteBkgColor, stroke = noteBorderColor, seed = handDrawnSeed.
      val noteOpts = Options(
        roughness = Some(0.7),
        fillWeight = Some(3),
        seed = Some(config.handDrawnSeed),
        fill = Some(noteBkgColor),
        stroke = Some(noteBorderColor)
      )

      val roughBody: SvgElement = Rough.svg().path(bodyPath.toString, Some(noteOpts))
      val roughFold: SvgElement = Rough.svg().path(foldPath.toString, Some(noteOpts))

      // Graft in SSG's classic child order: body first, then fold (matching the classic append order
      // below). Body carries node-shape + note-shape and the inline style (as classic); fold carries
      // note-fold (as classic, which does not style the fold).
      val bodyGroup = HandDrawnShapes.graftElement(group, roughBody)
      bodyGroup.classed("node-shape", true)
      bodyGroup.classed("note-shape", true)
      if (config.style.nonEmpty) {
        bodyGroup.attr("style", config.style)
      }

      val foldGroup = HandDrawnShapes.graftElement(group, roughFold)
      foldGroup.classed("note-fold", true)
    } else {
      val body = group.append("path")
      body.attr("d", bodyPath.toString)
      body.classed("node-shape", true)
      body.classed("note-shape", true)

      if (config.style.nonEmpty) {
        body.attr("style", config.style)
      }

      val fold = group.append("path")
      fold.attr("d", foldPath.toString)
      fold.classed("note-fold", true)
    }

    // Add label (htmlLabels-aware shared chokepoint — ISS-1205)
    ShapeLabel.renderNodeLabel(group, config)

    val w = config.width
    val h = config.height

    ShapeResult(
      shapeGroup = group,
      intersectFn = (point: Point) => Intersect.rect(cx, cy, w, h, point)
    )
  }
}
