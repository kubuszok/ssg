/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9g: note + subroutine handDrawn rendering (the last shape sub-chip).
 *
 *   - NoteShape.render with look="handDrawn" emits TWO rough elements — the body (the
 *     dog-eared rectangle outline) and the fold (the dog-ear triangle) — reusing SSG's OWN
 *     bodyPath/foldPath outlines, NOT the classic <path>s. Both carry the upstream
 *     note-SPECIFIC options (roughness 0.7, fillWeight 3, fill = theme noteBkgColor,
 *     stroke = theme noteBorderColor, seed = handDrawnSeed) — NOT userNodeOverrides. The
 *     emitted paths match the ported Rough oracle built with exactly those opts; mutating any
 *     of roughness/fillWeight/fill/stroke (or swapping in userNodeOverrides) makes them diverge.
 *   - SubroutineShape.render with look="handDrawn" emits THREE rough elements rough-sketching SSG's
 *     OWN classic subroutine geometry — the NOMINAL-width outer rectangle (left, top, w, h) plus two
 *     vertical inner lines INSET by BorderInset (left+8 / right-8) — each threaded through
 *     userNodeOverrides. This matches the classic subroutine (the SSG-analog rule, ISS-1363) so the
 *     drawn outline coincides with Intersect.rect(cx, cy, w, h); it is NOT upstream's widened
 *     x-8/w+16 + nominal-edge-line geometry. All three are grafted in body-first child order (rect,
 *     then left line, then right line), matching SSG's classic append order. The rect coords and the
 *     line x-positions are pinned to the ported Rough oracle; the upstream widened rect or nominal-
 *     edge lines diverge, and reversing the graft order fails the index assertions.
 *   - look="classic" (and the default) still emit the classic note <path>s / subroutine <rect>+lines
 *     (regression guard) for both shapes.
 *   - The seed threads through so distinct seeds produce distinct sketches.
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly in the test):
 * each shape must route the SAME coords/opts/seed into Rough, so any threading, coordinate, opts, or
 * branch mutation makes the emitted paths diverge from the independently-built oracle.
 */
package ssg
package mermaid
package render
package shapes

import munit.FunSuite

import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }
import ssg.mermaid.theme.ThemeVariables

final class NoteSubroutineHandDrawnIss1204Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // helpers
  // ──────────────────────────────────────────────────────────────────────────

  private def theme(noteBkg: String, noteBorder: String, nodeBorder: String = "#333333", mainBkg: String = "#ECECFF"): ThemeVariables = {
    val tv = new ThemeVariables
    tv.noteBkgColor = noteBkg
    tv.noteBorderColor = noteBorder
    tv.nodeBorder = nodeBorder
    tv.mainBkg = mainBkg
    tv
  }

  /** (d, stroke, fill, stroke-width) for every <path> in a subtree, in document order. */
  private def pathDescriptors(el: SvgElement): Vector[(String, String, String, String)] =
    el.findAllByTag("path").toVector.map { p =>
      (
        p.attr("d").getOrElse(""),
        p.attr("stroke").getOrElse(""),
        p.attr("fill").getOrElse(""),
        p.attr("stroke-width").getOrElse("")
      )
    }

  private def renderNote(config: ShapeConfig): SvgElement = {
    val parent = SvgBuilder.create("g")
    NoteShape.render(parent, config).shapeGroup.build()
  }

  private def renderSubroutine(config: ShapeConfig): SvgElement = {
    val parent = SvgBuilder.create("g")
    SubroutineShape.render(parent, config).shapeGroup.build()
  }

  /** The dog-ear fold size NoteShape uses (mirrors the private constant). */
  private val FoldSize: Double = 7.0

  /** The subroutine inner-line inset NoteShape uses (mirrors the private constant). */
  private val BorderInset: Double = 8.0

  /** Rebuild the exact note body outline NoteShape traces (for the oracle). */
  private def noteBodyPath(x: Double, y: Double, width: Double, height: Double): PathData = {
    val halfW  = width / 2.0
    val halfH  = height / 2.0
    val left   = x - halfW
    val right  = x + halfW
    val top    = y - halfH
    val bottom = y + halfH
    val p      = PathData()
    p.moveTo(left, top)
    p.lineTo(right - FoldSize, top)
    p.lineTo(right, top + FoldSize)
    p.lineTo(right, bottom)
    p.lineTo(left, bottom)
    p.close()
    p
  }

  /** Rebuild the exact note fold triangle NoteShape traces (for the oracle). */
  private def noteFoldPath(x: Double, y: Double, width: Double, height: Double): PathData = {
    val halfW = width / 2.0
    val halfH = height / 2.0
    val right = x + halfW
    val top   = y - halfH
    val p     = PathData()
    p.moveTo(right - FoldSize, top)
    p.lineTo(right - FoldSize, top + FoldSize)
    p.lineTo(right, top + FoldSize)
    p.close()
    p
  }

  /** The exact note-specific rough options NoteShape passes (roughness 0.7, fillWeight 3, fill/stroke from theme). */
  private def noteOpts(bkg: String, border: String, seed: Int): Options =
    Options(
      roughness = Some(0.7),
      fillWeight = Some(3),
      seed = Some(seed),
      fill = Some(bkg),
      stroke = Some(border)
    )

  private def noteBodyGroup(built: SvgElement): SvgElement =
    built.findAllByClass("note-shape").toVector.head

  private def noteFoldGroup(built: SvgElement): SvgElement =
    built.findAllByClass("note-fold").toVector.head

  // ──────────────────────────────────────────────────────────────────────────
  // Note handDrawn → TWO rough.svg().path (body + fold) with the note-specific opts
  // ──────────────────────────────────────────────────────────────────────────

  test("note handDrawn: emits rough <g> sketch groups (body + fold), NOT the classic note <path>s") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderNote(config)

    // Two grafted rough groups: body (note-shape) + fold (note-fold), both <g>.
    assertEquals(built.findAllByClass("note-shape").size, 1, "hand-drawn note must graft exactly one body (note-shape) group")
    assertEquals(built.findAllByClass("note-fold").size, 1, "hand-drawn note must graft exactly one fold (note-fold) group")
    assertEquals(noteBodyGroup(built).tagName, "g", "body must be a grafted rough <g>, not a plain <path>")
    assertEquals(noteFoldGroup(built).tagName, "g", "fold must be a grafted rough <g>, not a plain <path>")

    // The rough sketch paths carry bezier (C) segments; the classic note straight-line paths do not.
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn note must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(built.findAllByClass("node-shape").nonEmpty, "the body rough group must carry the node-shape class")
  }

  test("note handDrawn: body + fold match the Rough oracle built with the note-specific opts") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderNote(config)

    val bodyD = noteBodyPath(50, 40, 100, 60).toString
    val foldD = noteFoldPath(50, 40, 100, 60).toString
    val opts  = noteOpts("#fff5ad", "#aaaa33", seed)

    val bodyOracle = Rough.svg().path(bodyD, Some(opts))
    val foldOracle = Rough.svg().path(foldD, Some(opts))

    assertEquals(pathDescriptors(noteBodyGroup(built)), pathDescriptors(bodyOracle))
    assertEquals(pathDescriptors(noteFoldGroup(built)), pathDescriptors(foldOracle))
    assert(pathDescriptors(bodyOracle).nonEmpty, "sanity: body oracle must produce paths")
    assert(pathDescriptors(foldOracle).nonEmpty, "sanity: fold oracle must produce paths")
  }

  test("note handDrawn: opts are the note-specific ones (roughness 0.7, fillWeight 3, fill=noteBkg, stroke=noteBorder), NOT userNodeOverrides") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderNote(config)
    val bodyD  = noteBodyPath(50, 40, 100, 60).toString
    val actual = pathDescriptors(noteBodyGroup(built))

    // The correct oracle.
    val correct = pathDescriptors(Rough.svg().path(bodyD, Some(noteOpts("#fff5ad", "#aaaa33", seed))))
    assertEquals(actual, correct)

    // Mutation: wrong roughness (0.7 → 1.0) changes the jitter → different sketch d.
    val wrongRoughness = pathDescriptors(Rough.svg().path(bodyD, Some(noteOpts("#fff5ad", "#aaaa33", seed).copy(roughness = Some(1.0)))))
    assertNotEquals(actual, wrongRoughness, "roughness must be 0.7")

    // Mutation: wrong fillWeight (3 → 4) changes the fill path's stroke-width.
    val wrongFillWeight = pathDescriptors(Rough.svg().path(bodyD, Some(noteOpts("#fff5ad", "#aaaa33", seed).copy(fillWeight = Some(4)))))
    assertNotEquals(actual, wrongFillWeight, "fillWeight must be 3")

    // Mutation: fill and stroke swapped → the fill-sketch stroke and the outline stroke swap colors.
    val swappedColors = pathDescriptors(Rough.svg().path(bodyD, Some(noteOpts("#aaaa33", "#fff5ad", seed))))
    assertNotEquals(actual, swappedColors, "fill must be noteBkg and stroke must be noteBorder (not swapped)")

    // Mutation: userNodeOverrides opts (the WRONG helper) yield fill=mainBkg / stroke=nodeBorder / fillWeight 4.
    val wrongHelper = pathDescriptors(Rough.svg().path(bodyD, Some(HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed))))
    assertNotEquals(actual, wrongHelper, "note must use the note-specific opts, NOT userNodeOverrides")

    // The resolved fill / stroke are literally the theme note colors, not the generic node colors.
    val strokes = actual.map(_._2).toSet
    val fills   = actual.map(_._3).toSet
    assert(strokes.contains("#aaaa33"), s"expected theme noteBorderColor #aaaa33 as the outline stroke, got $strokes")
    assert(strokes.contains("#fff5ad"), s"expected theme noteBkgColor #fff5ad as the fill-sketch stroke, got $strokes")
    // The two sketch paths are painted as strokes (fill=none) even though the shape is 'filled' via hachure.
    assert(fills.forall(f => f == "none" || f.isEmpty), s"rough sketch paths paint via stroke (fill none), got $fills")
  }

  test("note handDrawn: config.style is applied to the body group only (matching classic)") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val built  = renderNote(config)
    assertEquals(noteBodyGroup(built).attr("style").getOrElse(""), "opacity: 0.5")
    assertEquals(noteFoldGroup(built).attr("style").getOrElse(""), "", "classic styles only the body; the fold carries no inline style")
  }

  test("note handDrawn: distinct seeds yield distinct sketches (seed threads to opts)") {
    val tv   = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderNote(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderNote(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("note classic look: still emits the classic body + fold <path>s, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderNote(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 2, "classic note must emit exactly two <path>s (body + fold)")
    assert(paths.forall(!_.attr("d").getOrElse("").contains("C")), "classic paths must not be rough beziers")
    assertEquals(built.findAllByClass("note-fold").size, 1)
    assert(built.findAllByClass("note-shape").toVector.head.tagName == "path", "classic body is a plain <path>")
  }

  test("note default look (classic): plain body + fold <path>s, no rough sketch groups") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderNote(config)
    assertEquals(built.findAllByTag("path").size, 2)
    assert(built.findAllByClass("node-shape").forall(_.tagName == "path"))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Subroutine handDrawn → THREE rough elements (wider rect + 2 lines) in body-first order
  // ──────────────────────────────────────────────────────────────────────────

  private def subRectGroup(built: SvgElement): SvgElement =
    built.children.toVector.filter(_.hasClass("node-shape")).head

  private def subLineGroups(built: SvgElement): Vector[SvgElement] =
    built.children.toVector.filter(_.hasClass("subroutine-border"))

  test("subroutine handDrawn: emits THREE rough groups (rect + 2 lines), NOT plain <rect>/<line>") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33", nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderSubroutine(config)

    // No plain <rect>/<line>: hand-drawn replaces them with rough <g> groups of <path>s.
    assert(built.findAllByTag("rect").isEmpty, "hand-drawn subroutine must not emit a plain <rect>")
    assert(built.findAllByTag("line").isEmpty, "hand-drawn subroutine must not emit plain <line>s")

    // Exactly one node-shape (rect) group + two subroutine-border (line) groups grafted.
    assertEquals(built.findAllByClass("node-shape").size, 1, "hand-drawn subroutine grafts exactly one rect (node-shape) group")
    assertEquals(built.findAllByClass("subroutine-border").size, 2, "hand-drawn subroutine grafts exactly two line (subroutine-border) groups")
    assertEquals(subRectGroup(built).tagName, "g")
    assert(subLineGroups(built).forall(_.tagName == "g"))

    // All three are rough sketches (bezier C segments).
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty && paths.exists(_.attr("d").getOrElse("").contains("C")), "must be rough sketches")
  }

  test("subroutine handDrawn: child order is rect, then left line, then right line (body-first)") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderSubroutine(config)

    val kids    = built.children.toVector
    val rectIdx = kids.indexWhere(_.hasClass("node-shape"))
    val borderIdxs = kids.zipWithIndex.collect { case (c, i) if c.hasClass("subroutine-border") => i }
    assert(rectIdx >= 0, s"rect group must be a direct child (rectIdx=$rectIdx)")
    assertEquals(borderIdxs.size, 2, s"two line groups must be direct children, got $borderIdxs")
    assert(rectIdx < borderIdxs.head, s"rect must be grafted before the first line: rect=$rectIdx line=${borderIdxs.head}")
    assert(borderIdxs.head < borderIdxs(1), s"left line must be grafted before right line: ${borderIdxs.head} < ${borderIdxs(1)}")
  }

  test("subroutine handDrawn: rect uses the nominal SSG-classic coords (NOT the upstream widened x-8/w+16)") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderSubroutine(config)

    val halfW = 100 / 2.0
    val halfH = 60 / 2.0
    val left  = 50 - halfW
    val top   = 40 - halfH
    val opts  = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)

    // Correct: SSG-analog nominal rect (left, top, config.width, config.height) — the exact classic
    // <rect> coords, so hand-drawn == classic and Intersect.rect(cx, cy, w, h) routes onto the border.
    val nominal = Rough.svg().rectangle(left, top, 100, 60, Some(opts))
    assertEquals(pathDescriptors(subRectGroup(built)), pathDescriptors(nominal))

    // Mutation guard: the upstream WIDENED rect (left-8 / width+16) must differ from the nominal one.
    val widened = Rough.svg().rectangle(left - BorderInset, top, 100 + 2 * BorderInset, 60, Some(opts))
    assertNotEquals(pathDescriptors(nominal), pathDescriptors(widened), "sanity: nominal vs upstream-widened rect must differ")
  }

  test("subroutine handDrawn: the two lines are INSET (left+8 / right-8), matching the classic bars") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderSubroutine(config)

    val halfW  = 100 / 2.0
    val halfH  = 60 / 2.0
    val left   = 50 - halfW
    val right  = 50 + halfW
    val top    = 40 - halfH
    val bottom = 40 + halfH
    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)

    val lines = subLineGroups(built)
    assertEquals(lines.size, 2)

    // Correct: SSG-analog inset bars — left line at left+BorderInset, right line at right-BorderInset,
    // the exact classic leftLine/rightLine positions.
    val leftOracle  = Rough.svg().line(left + BorderInset, top, left + BorderInset, bottom, Some(opts))
    val rightOracle = Rough.svg().line(right - BorderInset, top, right - BorderInset, bottom, Some(opts))
    assertEquals(pathDescriptors(lines(0)), pathDescriptors(leftOracle), "first line must sit at the classic left inset (left+8)")
    assertEquals(pathDescriptors(lines(1)), pathDescriptors(rightOracle), "second line must sit at the classic right inset (right-8)")

    // Mutation guard: the upstream lines at the NOMINAL edges (left / right) must differ from the inset bars.
    val edgeLeftOracle = Rough.svg().line(left, top, left, bottom, Some(opts))
    assertNotEquals(pathDescriptors(leftOracle), pathDescriptors(edgeLeftOracle), "sanity: inset bar vs upstream edge line must differ")
    assertNotEquals(pathDescriptors(lines(0)), pathDescriptors(rightOracle), "left inset bar must not equal right inset bar")
  }

  test("subroutine handDrawn: node theme + seed thread into the rough stroke/fill") {
    val tv      = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33", nodeBorder = "#abcdef", mainBkg = "#123456")
    val config  = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built   = renderSubroutine(config)
    val strokes = pathDescriptors(built).map(_._2).toSet
    assert(strokes.contains("#abcdef"), s"expected theme nodeBorder #abcdef as a stroke, got $strokes")
    assert(strokes.contains("#123456"), s"expected theme mainBkg #123456 as the rect fill-sketch stroke, got $strokes")

    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderSubroutine(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderSubroutine(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("subroutine handDrawn: config.style is applied to the rect group") {
    val tv     = theme(noteBkg = "#fff5ad", noteBorder = "#aaaa33")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val built  = renderSubroutine(config)
    val rect   = subRectGroup(built)
    assertEquals(rect.tagName, "g")
    assertEquals(rect.attr("style").getOrElse(""), "opacity: 0.5")
  }

  test("subroutine classic look: still emits the plain <rect> + two <line>s, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderSubroutine(config)
    assertEquals(built.findAllByTag("rect").size, 1, "classic subroutine must emit exactly one <rect>")
    assertEquals(built.findAllByTag("line").size, 2, "classic subroutine must emit exactly two <line>s")
    assert(built.findAllByTag("path").isEmpty, "classic subroutine must not emit rough <path>s")
    assertEquals(built.findAllByClass("subroutine-border").size, 2)
  }

  test("subroutine default look (classic): plain <rect> + two <line>s, no rough sketch groups") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderSubroutine(config)
    assertEquals(built.findAllByTag("rect").size, 1)
    assertEquals(built.findAllByTag("line").size, 2)
    assert(built.findAllByTag("path").isEmpty)
  }
}
