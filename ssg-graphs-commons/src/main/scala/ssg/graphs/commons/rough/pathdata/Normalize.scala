/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SVG path normalization to M/L/C/Z (incl. A->C arc conversion) — Scala 3 port
 *
 * Original source: path-data-parser (src/normalize.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 93d3fa8
 *
 * Migration notes:
 *   Renames: module-level `normalize` -> `object Normalize.normalize`; private
 *     helpers `degToRad`/`rotate`/`arcToCubicCurves` -> private members.
 *   Convention: `number[]` -> immutable `Vector[Double]`; `number[][]` (the curve
 *     accumulator) -> `Vector[Vector[Double]]`. `[...data]` spreads collapse to
 *     direct reuse of the immutable `Vector`. The original `switch` has no default
 *     and silently ignores unrecognized keys (only updating `lastType`); the port
 *     adds an explicit `case _ => ()` for match exhaustiveness, preserving the
 *     no-op-then-`lastType`-update behavior.
 *   Idiom: `rotate` returns a `(Double, Double)` tuple in place of the TS
 *     `[number, number]`. The optional `recursive?: number[]` parameter (truthiness-
 *     checked via `if (recursive)`) is ported to `Option[Vector[Double]] = None`
 *     (an absent value -> `None`, present -> `Some`), avoiding `null`. Inside the
 *     final rotation loop the original re-declares `const r1/r2` shadowing the radii
 *     params; the port renames these locals to `ra`/`rb`/`rc` to avoid shadowing the
 *     `r1`/`r2` vars (no semantic change — the radii are unused after the loop).
 *   Idiom: `parseFloat(x.toFixed(9))` -> `FormatUtil.toFixed(x, 9).toDouble`
 *     (`FormatUtil.toFixed` mirrors ECMA-262 `Number.prototype.toFixed`: HALF_UP, '.'
 *     separator, exactly 9 fraction digits) — the clamp guarding `Math.asin` against
 *     tiny floating-point overshoot of [-1, 1]. The trig (`sin`/`cos`/`asin`/`tan`/
 *     `sqrt`) is `java.lang.Math`, matching the IEEE-754 semantics of JS `Math` to
 *     within a few ULPs across JVM/JS/Native.
 */
package ssg
package graphs
package commons
package rough
package pathdata

import scala.collection.mutable.ArrayBuffer

import ssg.graphs.commons.util.FormatUtil

/** Normalize an SVG path to only M, L, C, and Z commands (port of `normalize.ts`). */
object Normalize {

  // Normalize path to include only M, L, C, and Z commands
  def normalize(segments: Vector[Segment]): Vector[Segment] = {
    val out: ArrayBuffer[Segment] = ArrayBuffer.empty

    var lastType: String = ""
    var cx:       Double = 0
    var cy:       Double = 0
    var subx:     Double = 0
    var suby:     Double = 0
    var lcx:      Double = 0
    var lcy:      Double = 0

    for (segment <- segments) {
      val key:  String         = segment.key
      val data: Vector[Double] = segment.data
      key match {
        case "M" =>
          out += Segment("M", data)
          cx = data(0)
          cy = data(1)
          subx = data(0)
          suby = data(1)
        case "C" =>
          out += Segment("C", data)
          cx = data(4)
          cy = data(5)
          lcx = data(2)
          lcy = data(3)
        case "L" =>
          out += Segment("L", data)
          cx = data(0)
          cy = data(1)
        case "H" =>
          cx = data(0)
          out += Segment("L", Vector(cx, cy))
        case "V" =>
          cy = data(0)
          out += Segment("L", Vector(cx, cy))
        case "S" =>
          var cx1: Double = 0
          var cy1: Double = 0
          if (lastType == "C" || lastType == "S") {
            cx1 = cx + (cx - lcx)
            cy1 = cy + (cy - lcy)
          } else {
            cx1 = cx
            cy1 = cy
          }
          out += Segment("C", Vector(cx1, cy1) ++ data)
          lcx = data(0)
          lcy = data(1)
          cx = data(2)
          cy = data(3)
        case "T" =>
          val x:  Double = data(0)
          val y:  Double = data(1)
          var x1: Double = 0
          var y1: Double = 0
          if (lastType == "Q" || lastType == "T") {
            x1 = cx + (cx - lcx)
            y1 = cy + (cy - lcy)
          } else {
            x1 = cx
            y1 = cy
          }
          val cx1: Double = cx + 2 * (x1 - cx) / 3
          val cy1: Double = cy + 2 * (y1 - cy) / 3
          val cx2: Double = x + 2 * (x1 - x) / 3
          val cy2: Double = y + 2 * (y1 - y) / 3
          out += Segment("C", Vector(cx1, cy1, cx2, cy2, x, y))
          lcx = x1
          lcy = y1
          cx = x
          cy = y
        case "Q" =>
          val x1:  Double = data(0)
          val y1:  Double = data(1)
          val x:   Double = data(2)
          val y:   Double = data(3)
          val cx1: Double = cx + 2 * (x1 - cx) / 3
          val cy1: Double = cy + 2 * (y1 - cy) / 3
          val cx2: Double = x + 2 * (x1 - x) / 3
          val cy2: Double = y + 2 * (y1 - y) / 3
          out += Segment("C", Vector(cx1, cy1, cx2, cy2, x, y))
          lcx = x1
          lcy = y1
          cx = x
          cy = y
        case "A" =>
          val r1:           Double = Math.abs(data(0))
          val r2:           Double = Math.abs(data(1))
          val angle:        Double = data(2)
          val largeArcFlag: Double = data(3)
          val sweepFlag:    Double = data(4)
          val x:            Double = data(5)
          val y:            Double = data(6)
          if (r1 == 0 || r2 == 0) {
            out += Segment("C", Vector(cx, cy, x, y, x, y))
            cx = x
            cy = y
          } else {
            if (cx != x || cy != y) {
              val curves: Vector[Vector[Double]] = arcToCubicCurves(cx, cy, x, y, r1, r2, angle, largeArcFlag, sweepFlag)
              curves.foreach(curve => out += Segment("C", curve))
              cx = x
              cy = y
            }
          }
        case "Z" =>
          out += Segment("Z", Vector.empty)
          cx = subx
          cy = suby
        case _ =>
          ()
      }
      lastType = key
    }
    out.toVector
  }

  private def degToRad(degrees: Double): Double =
    (Math.PI * degrees) / 180

  private def rotate(x: Double, y: Double, angleRad: Double): (Double, Double) = {
    val rotatedX: Double = x * Math.cos(angleRad) - y * Math.sin(angleRad)
    val rotatedY: Double = x * Math.sin(angleRad) + y * Math.cos(angleRad)
    (rotatedX, rotatedY)
  }

  private def arcToCubicCurves(
    x1in:         Double,
    y1in:         Double,
    x2in:         Double,
    y2in:         Double,
    r1in:         Double,
    r2in:         Double,
    angle:        Double,
    largeArcFlag: Double,
    sweepFlag:    Double,
    recursive:    Option[Vector[Double]] = None
  ): Vector[Vector[Double]] = {
    var x1: Double = x1in
    var y1: Double = y1in
    var x2: Double = x2in
    var y2: Double = y2in
    var r1: Double = r1in
    var r2: Double = r2in

    val angleRad: Double                 = degToRad(angle)
    var params:   Vector[Vector[Double]] = Vector.empty

    var f1: Double = 0
    var f2: Double = 0
    var cx: Double = 0
    var cy: Double = 0
    recursive match {
      case Some(rec) =>
        f1 = rec(0)
        f2 = rec(1)
        cx = rec(2)
        cy = rec(3)
      case None =>
        val rotated1: (Double, Double) = rotate(x1, y1, -angleRad)
        x1 = rotated1._1
        y1 = rotated1._2
        val rotated2: (Double, Double) = rotate(x2, y2, -angleRad)
        x2 = rotated2._1
        y2 = rotated2._2

        val x: Double = (x1 - x2) / 2
        val y: Double = (y1 - y2) / 2
        var h: Double = (x * x) / (r1 * r1) + (y * y) / (r2 * r2)
        if (h > 1) {
          h = Math.sqrt(h)
          r1 = h * r1
          r2 = h * r2
        }

        val sign: Double = if (largeArcFlag == sweepFlag) -1 else 1

        val r1Pow: Double = r1 * r1
        val r2Pow: Double = r2 * r2

        val left:  Double = r1Pow * r2Pow - r1Pow * y * y - r2Pow * x * x
        val right: Double = r1Pow * y * y + r2Pow * x * x

        val k: Double = sign * Math.sqrt(Math.abs(left / right))

        cx = k * r1 * y / r2 + (x1 + x2) / 2
        cy = k * -r2 * x / r1 + (y1 + y2) / 2

        f1 = Math.asin(FormatUtil.toFixed((y1 - cy) / r2, 9).toDouble)
        f2 = Math.asin(FormatUtil.toFixed((y2 - cy) / r2, 9).toDouble)

        if (x1 < cx) {
          f1 = Math.PI - f1
        }
        if (x2 < cx) {
          f2 = Math.PI - f2
        }

        if (f1 < 0) {
          f1 = Math.PI * 2 + f1
        }
        if (f2 < 0) {
          f2 = Math.PI * 2 + f2
        }

        if (sweepFlag != 0 && f1 > f2) {
          f1 = f1 - Math.PI * 2
        }
        if (sweepFlag == 0 && f2 > f1) {
          f2 = f2 - Math.PI * 2
        }
    }

    var df: Double = f2 - f1

    if (Math.abs(df) > (Math.PI * 120 / 180)) {
      val f2old: Double = f2
      val x2old: Double = x2
      val y2old: Double = y2

      if (sweepFlag != 0 && f2 > f1) {
        f2 = f1 + (Math.PI * 120 / 180) * 1
      } else {
        f2 = f1 + (Math.PI * 120 / 180) * -1
      }

      x2 = cx + r1 * Math.cos(f2)
      y2 = cy + r2 * Math.sin(f2)
      params = arcToCubicCurves(x2, y2, x2old, y2old, r1, r2, angle, 0, sweepFlag, Some(Vector(f2, f2old, cx, cy)))
    }

    df = f2 - f1

    val c1: Double = Math.cos(f1)
    val s1: Double = Math.sin(f1)
    val c2: Double = Math.cos(f2)
    val s2: Double = Math.sin(f2)
    val t:  Double = Math.tan(df / 4)
    val hx: Double = 4.0 / 3 * r1 * t
    val hy: Double = 4.0 / 3 * r2 * t

    val m1: Vector[Double] = Vector(x1, y1)
    val m2: Array[Double]  = Array(x1 + hx * s1, y1 - hy * c1)
    val m3: Vector[Double] = Vector(x2 + hx * s2, y2 - hy * c2)
    val m4: Vector[Double] = Vector(x2, y2)

    m2(0) = 2 * m1(0) - m2(0)
    m2(1) = 2 * m1(1) - m2(1)

    if (recursive.isDefined) {
      Vector(m2.toVector, m3, m4) ++ params
    } else {
      params = Vector(m2.toVector, m3, m4) ++ params
      val curves: ArrayBuffer[Vector[Double]] = ArrayBuffer.empty
      var i:      Int                         = 0
      while (i < params.length) {
        val ra: (Double, Double) = rotate(params(i)(0), params(i)(1), angleRad)
        val rb: (Double, Double) = rotate(params(i + 1)(0), params(i + 1)(1), angleRad)
        val rc: (Double, Double) = rotate(params(i + 2)(0), params(i + 2)(1), angleRad)
        curves += Vector(ra._1, ra._2, rb._1, rb._2, rc._1, rc._2)
        i += 3
      }
      curves.toVector
    }
  }
}
