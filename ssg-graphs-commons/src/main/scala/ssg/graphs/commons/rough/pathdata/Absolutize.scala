/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SVG path relative->absolute command translation — Scala 3 port
 *
 * Original source: path-data-parser (src/absolutize.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 93d3fa8
 *
 * Migration notes:
 *   Renames: module-level `absolutize` function -> `object Absolutize.absolutize`.
 *   Convention: `number[]` -> immutable `Vector[Double]`; defensive `[...data]`
 *     spreads collapse to direct reuse of the immutable `Vector`.
 *   Idiom: the relative-coordinate fold
 *     `data.map((d, i) => (i % 2) ? (d + cy) : (d + cx))` — odd indices (truthy
 *     `i % 2`) take `+ cy`, even indices `+ cx` — is ported via `zipWithIndex` with
 *     `if (i % 2 != 0)`, preserving the index-parity semantics exactly.
 */
package ssg
package graphs
package commons
package rough
package pathdata

import scala.collection.mutable.ArrayBuffer

/** Translate relative SVG path commands to absolute commands (port of `absolutize.ts`). */
object Absolutize {

  // Translate relative commands to absolute commands
  def absolutize(segments: Vector[Segment]): Vector[Segment] = {
    var cx: Double               = 0
    var cy: Double               = 0
    var subx: Double             = 0
    var suby: Double             = 0
    val out: ArrayBuffer[Segment] = ArrayBuffer.empty
    for (segment <- segments) {
      val key: String          = segment.key
      val data: Vector[Double] = segment.data
      key match {
        case "M" =>
          out += Segment("M", data)
          cx = data(0)
          cy = data(1)
          subx = data(0)
          suby = data(1)
        case "m" =>
          cx += data(0)
          cy += data(1)
          out += Segment("M", Vector(cx, cy))
          subx = cx
          suby = cy
        case "L" =>
          out += Segment("L", data)
          cx = data(0)
          cy = data(1)
        case "l" =>
          cx += data(0)
          cy += data(1)
          out += Segment("L", Vector(cx, cy))
        case "C" =>
          out += Segment("C", data)
          cx = data(4)
          cy = data(5)
        case "c" =>
          val newdata: Vector[Double] = data.zipWithIndex.map { case (d, i) => if (i % 2 != 0) d + cy else d + cx }
          out += Segment("C", newdata)
          cx = newdata(4)
          cy = newdata(5)
        case "Q" =>
          out += Segment("Q", data)
          cx = data(2)
          cy = data(3)
        case "q" =>
          val newdata: Vector[Double] = data.zipWithIndex.map { case (d, i) => if (i % 2 != 0) d + cy else d + cx }
          out += Segment("Q", newdata)
          cx = newdata(2)
          cy = newdata(3)
        case "A" =>
          out += Segment("A", data)
          cx = data(5)
          cy = data(6)
        case "a" =>
          cx += data(5)
          cy += data(6)
          out += Segment("A", Vector(data(0), data(1), data(2), data(3), data(4), cx, cy))
        case "H" =>
          out += Segment("H", data)
          cx = data(0)
        case "h" =>
          cx += data(0)
          out += Segment("H", Vector(cx))
        case "V" =>
          out += Segment("V", data)
          cy = data(0)
        case "v" =>
          cy += data(0)
          out += Segment("V", Vector(cy))
        case "S" =>
          out += Segment("S", data)
          cx = data(2)
          cy = data(3)
        case "s" =>
          val newdata: Vector[Double] = data.zipWithIndex.map { case (d, i) => if (i % 2 != 0) d + cy else d + cx }
          out += Segment("S", newdata)
          cx = newdata(2)
          cy = newdata(3)
        case "T" =>
          out += Segment("T", data)
          cx = data(0)
          cy = data(1)
        case "t" =>
          cx += data(0)
          cy += data(1)
          out += Segment("T", Vector(cx, cy))
        case "Z" | "z" =>
          out += Segment("Z", Vector.empty)
          cx = subx
          cy = suby
        case _ =>
          ()
      }
    }
    out.toVector
  }
}
