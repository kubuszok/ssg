/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Original source: mermaid
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces d3-path for SVG path `d` attribute construction
 *   Idiom: Mutable builder with toString for final output
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package svg

import scala.collection.mutable.ArrayBuffer

/** Builder for SVG path `d` attribute values.
  *
  * Replaces `d3-path` with a server-side builder that produces SVG path command strings. Coordinates are rounded to 2 decimal places to match Mermaid's output precision.
  *
  * Usage:
  * {{{
  * val path = PathData()
  * path.moveTo(0, 0)
  * path.lineTo(100, 0)
  * path.curveTo(100, 0, 100, 50, 50, 50)
  * path.close()
  * val d: String = path.toString // "M0,0 L100,0 C100,0,100,50,50,50 Z"
  * }}}
  */
final class PathData {

  private val commands: ArrayBuffer[String] = ArrayBuffer.empty

  /** Tracks the current point for relative path calculations. */
  private var currentX: Double = 0.0
  private var currentY: Double = 0.0

  /** Tracks the start of the current subpath (for close). */
  private var subpathStartX: Double = 0.0
  private var subpathStartY: Double = 0.0

  /** M command — move to absolute position. */
  def moveTo(x: Double, y: Double): PathData = {
    commands += s"M${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    subpathStartX = x
    subpathStartY = y
    this
  }

  /** L command — line to absolute position. */
  def lineTo(x: Double, y: Double): PathData = {
    commands += s"L${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    this
  }

  /** H command — horizontal line to absolute x. */
  def horizontalTo(x: Double): PathData = {
    commands += s"H${fmt(x)}"
    currentX = x
    this
  }

  /** V command — vertical line to absolute y. */
  def verticalTo(y: Double): PathData = {
    commands += s"V${fmt(y)}"
    currentY = y
    this
  }

  /** C command — cubic bezier curve.
    *
    * @param x1
    *   first control point x
    * @param y1
    *   first control point y
    * @param x2
    *   second control point x
    * @param y2
    *   second control point y
    * @param x
    *   end point x
    * @param y
    *   end point y
    */
  def curveTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double): PathData = {
    commands += s"C${fmt(x1)},${fmt(y1)},${fmt(x2)},${fmt(y2)},${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    this
  }

  /** S command — smooth cubic bezier curve.
    *
    * @param x2
    *   second control point x
    * @param y2
    *   second control point y
    * @param x
    *   end point x
    * @param y
    *   end point y
    */
  def smoothCurveTo(x2: Double, y2: Double, x: Double, y: Double): PathData = {
    commands += s"S${fmt(x2)},${fmt(y2)},${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    this
  }

  /** Q command — quadratic bezier curve.
    *
    * @param x1
    *   control point x
    * @param y1
    *   control point y
    * @param x
    *   end point x
    * @param y
    *   end point y
    */
  def quadTo(x1: Double, y1: Double, x: Double, y: Double): PathData = {
    commands += s"Q${fmt(x1)},${fmt(y1)},${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    this
  }

  /** T command — smooth quadratic bezier curve. */
  def smoothQuadTo(x: Double, y: Double): PathData = {
    commands += s"T${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    this
  }

  /** A command — elliptical arc.
    *
    * @param rx
    *   x-axis radius
    * @param ry
    *   y-axis radius
    * @param rotation
    *   x-axis rotation in degrees
    * @param largeArc
    *   large-arc-flag (true = 1, false = 0)
    * @param sweep
    *   sweep-flag (true = clockwise, false = counter-clockwise)
    * @param x
    *   end point x
    * @param y
    *   end point y
    */
  def arcTo(rx: Double, ry: Double, rotation: Double, largeArc: Boolean, sweep: Boolean, x: Double, y: Double): PathData = {
    val largeArcFlag = if (largeArc) "1" else "0"
    val sweepFlag    = if (sweep) "1" else "0"
    commands += s"A${fmt(rx)},${fmt(ry)},${fmt(rotation)},$largeArcFlag,$sweepFlag,${fmt(x)},${fmt(y)}"
    currentX = x
    currentY = y
    this
  }

  /** Z command — close the current subpath. */
  def close(): PathData = {
    commands += "Z"
    currentX = subpathStartX
    currentY = subpathStartY
    this
  }

  /** Returns true if no commands have been added. */
  def isEmpty: Boolean = commands.isEmpty

  /** Returns the number of commands in the path. */
  def size: Int = commands.size

  /** Returns the current position as a tuple. */
  def currentPoint: (Double, Double) = (currentX, currentY)

  /** Produces the SVG path `d` attribute value string. */
  override def toString: String = commands.mkString(" ")

  /** Formats a coordinate value with up to 2 decimal places, stripping trailing zeros. */
  private def fmt(v: Double): String =
    // Use integer representation when there is no fractional part
    if (v == v.toLong.toDouble) {
      v.toLong.toString
    } else {
      // Round to 2 decimal places
      val rounded = math.round(v * 100.0) / 100.0
      if (rounded == rounded.toLong.toDouble) {
        rounded.toLong.toString
      } else {
        // Format with up to 2 decimal places, strip trailing zeros
        val s = f"$rounded%.2f"
        // Remove trailing zeros after decimal point
        if (s.contains('.')) {
          val trimmed = s.replaceAll("0+$", "")
          if (trimmed.endsWith(".")) trimmed.init else trimmed
        } else {
          s
        }
      }
    }
}

object PathData {

  /** Creates an empty PathData builder. */
  def apply(): PathData = new PathData()
}
