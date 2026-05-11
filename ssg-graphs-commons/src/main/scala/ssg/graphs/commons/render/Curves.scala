/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Ported from: d3-shape/src/curve/ (curveBasis, curveLinear, curveCardinal, curveStep)
 * Original: Copyright (c) Mike Bostock
 * Original license: ISC
 *
 * Migration notes:
 *   Convention: Replaces D3 curve factory/context pattern with pure functions returning PathData
 *   Idiom: Each curve type is a pure function: Array[Point] => PathData
 *   Renames: d3.curveBasis -> Curves.basis, d3.curveLinear -> Curves.linear, etc.
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package render

import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.svg.PathData

/** Curve interpolation functions for edge rendering.
  *
  * Ports the subset of D3's curve functions used by Mermaid for drawing edges between nodes. Each function takes an array of control points and returns a PathData builder containing the interpolated
  * SVG path.
  *
  * These correspond to the D3 curve types: `d3.curveBasis`, `d3.curveLinear`, `d3.curveCardinal`, and `d3.curveStep`.
  */
object Curves {

  /** Linear interpolation — straight line segments between points.
    *
    * Connects each point to the next with a straight line. This produces a polyline path.
    *
    * Port of `d3.curveLinear`.
    *
    * @param points
    *   array of points to connect
    * @return
    *   path data with linear segments
    */
  def linear(points: Array[Point]): PathData = {
    val path = PathData()
    if (points.isEmpty) {
      path
    } else {
      path.moveTo(points(0).x, points(0).y)
      var i = 1
      while (i < points.length) {
        path.lineTo(points(i).x, points(i).y)
        i += 1
      }
      path
    }
  }

  /** Basis spline interpolation — smooth cubic B-spline through control points.
    *
    * Produces a smooth curve that passes near (but not necessarily through) the control points. The curve starts at the first point and ends at the last point.
    *
    * Port of `d3.curveBasis`.
    *
    * @param points
    *   array of control points (minimum 2)
    * @return
    *   path data with cubic bezier segments approximating a B-spline
    */
  def basis(points: Array[Point]): PathData = {
    val path = PathData()
    if (points.length < 2) {
      if (points.length == 1) {
        path.moveTo(points(0).x, points(0).y)
      }
      path
    } else if (points.length == 2) {
      path.moveTo(points(0).x, points(0).y)
      path.lineTo(points(1).x, points(1).y)
      path
    } else {
      // B-spline basis functions
      // The curve starts at the first point
      path.moveTo(points(0).x, points(0).y)

      // For B-spline, we generate cubic bezier segments from overlapping groups of 4 points
      // First, duplicate the first and last points for endpoint interpolation
      val p0 = points(0)
      val p1 = points(0)
      val p2 = points(1)

      // First segment
      basisPoint(path, p0, p1, p2)

      var i = 2
      while (i < points.length) {
        basisPoint(path, points(i - 2), points(i - 1), points(i))
        i += 1
      }

      // Final segment — duplicate last point
      val pn = points(points.length - 1)
      basisPoint(path, points(points.length - 2), pn, pn)

      path
    }
  }

  /** Helper for basis spline: adds a cubic bezier segment for three consecutive control points.
    *
    * Uses the B-spline basis function to compute the bezier control points.
    */
  private def basisPoint(path: PathData, p0: Point, p1: Point, p2: Point): Unit = {
    val x1 = (2.0 * p0.x + p1.x) / 3.0
    val y1 = (2.0 * p0.y + p1.y) / 3.0
    val x2 = (p0.x + 2.0 * p1.x) / 3.0
    val y2 = (p0.y + 2.0 * p1.y) / 3.0
    val x  = (p0.x + 4.0 * p1.x + p2.x) / 6.0
    val y  = (p0.y + 4.0 * p1.y + p2.y) / 6.0
    path.curveTo(x1, y1, x2, y2, x, y)
  }

  /** Cardinal spline interpolation — smooth curve that passes through control points.
    *
    * The tension parameter controls how "tight" the curve is. A tension of 0 produces Catmull-Rom splines; a tension of 1 produces straight lines.
    *
    * Port of `d3.curveCardinal`.
    *
    * @param points
    *   array of points to interpolate (minimum 2)
    * @param tension
    *   tension parameter in [0, 1] (default 0, i.e. Catmull-Rom)
    * @return
    *   path data with smooth curve through the points
    */
  def cardinal(points: Array[Point], tension: Double = 0.0): PathData = {
    val path = PathData()
    if (points.length < 2) {
      if (points.length == 1) {
        path.moveTo(points(0).x, points(0).y)
      }
      path
    } else if (points.length == 2) {
      path.moveTo(points(0).x, points(0).y)
      path.lineTo(points(1).x, points(1).y)
      path
    } else {
      path.moveTo(points(0).x, points(0).y)

      // Scale factor from tension
      val k = (1.0 - tension) / 6.0

      // First segment: use first point as phantom predecessor
      var i = 1
      while (i < points.length) {
        val p0 = if (i == 1) points(0) else points(i - 2)
        val p1 = points(i - 1)
        val p2 = points(i)
        val p3 = if (i + 1 < points.length) points(i + 1) else points(i)

        val cp1x = p1.x + k * (p2.x - p0.x)
        val cp1y = p1.y + k * (p2.y - p0.y)
        val cp2x = p2.x - k * (p3.x - p1.x)
        val cp2y = p2.y - k * (p3.y - p1.y)

        path.curveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        i += 1
      }

      path
    }
  }

  /** Step interpolation — produces a staircase pattern.
    *
    * Connects points with horizontal and vertical segments, creating a step function. The step occurs at the midpoint between consecutive x coordinates.
    *
    * Port of `d3.curveStep`.
    *
    * @param points
    *   array of points to connect
    * @return
    *   path data with step segments
    */
  def step(points: Array[Point]): PathData = {
    val path = PathData()
    if (points.isEmpty) {
      path
    } else {
      path.moveTo(points(0).x, points(0).y)

      var i = 1
      while (i < points.length) {
        val prev = points(i - 1)
        val curr = points(i)

        // Step at midpoint between x coordinates
        val midX = (prev.x + curr.x) / 2.0

        path.lineTo(midX, prev.y)
        path.lineTo(midX, curr.y)
        path.lineTo(curr.x, curr.y)

        i += 1
      }

      path
    }
  }

  /** Step-before interpolation — step occurs before each point.
    *
    * Connects points with vertical-then-horizontal segments. The vertical step happens at the x coordinate of the current point.
    *
    * Port of `d3.curveStepBefore`.
    *
    * @param points
    *   array of points to connect
    * @return
    *   path data with step-before segments
    */
  def stepBefore(points: Array[Point]): PathData = {
    val path = PathData()
    if (points.isEmpty) {
      path
    } else {
      path.moveTo(points(0).x, points(0).y)

      var i = 1
      while (i < points.length) {
        val curr = points(i)
        val prev = points(i - 1)

        path.lineTo(curr.x, prev.y)
        path.lineTo(curr.x, curr.y)

        i += 1
      }

      path
    }
  }

  /** Step-after interpolation — step occurs after each point.
    *
    * Connects points with horizontal-then-vertical segments. The horizontal segment extends to the x coordinate of the next point before stepping vertically.
    *
    * Port of `d3.curveStepAfter`.
    *
    * @param points
    *   array of points to connect
    * @return
    *   path data with step-after segments
    */
  def stepAfter(points: Array[Point]): PathData = {
    val path = PathData()
    if (points.isEmpty) {
      path
    } else {
      path.moveTo(points(0).x, points(0).y)

      var i = 1
      while (i < points.length) {
        val curr = points(i)
        val prev = points(i - 1)

        path.lineTo(prev.x, curr.y)
        path.lineTo(curr.x, curr.y)

        i += 1
      }

      path
    }
  }

  /** Monotone x interpolation — smooth curve that preserves monotonicity in x.
    *
    * Produces a smooth curve that passes through all points while preserving monotonicity relative to the x axis. This prevents the curve from overshooting and creating loops.
    *
    * Port of `d3.curveMonotoneX`.
    *
    * @param points
    *   array of points (should be sorted by x)
    * @return
    *   path data with monotone cubic hermite segments
    */
  def monotoneX(points: Array[Point]): PathData = {
    val path = PathData()
    if (points.length < 2) {
      if (points.length == 1) {
        path.moveTo(points(0).x, points(0).y)
      }
      path
    } else if (points.length == 2) {
      path.moveTo(points(0).x, points(0).y)
      path.lineTo(points(1).x, points(1).y)
      path
    } else {
      path.moveTo(points(0).x, points(0).y)

      // Compute tangent slopes
      val n      = points.length
      val slopes = new Array[Double](n)

      // Compute secant slopes
      val deltas = new Array[Double](n - 1)
      val dxs    = new Array[Double](n - 1)
      var i      = 0
      while (i < n - 1) {
        dxs(i) = points(i + 1).x - points(i).x
        deltas(i) = if (dxs(i) != 0) (points(i + 1).y - points(i).y) / dxs(i) else 0
        i += 1
      }

      // Initial tangent estimates
      slopes(0) = deltas(0)
      slopes(n - 1) = deltas(n - 2)
      i = 1
      while (i < n - 1) {
        if (deltas(i - 1) * deltas(i) <= 0) {
          slopes(i) = 0
        } else {
          slopes(i) = (deltas(i - 1) + deltas(i)) / 2.0
        }
        i += 1
      }

      // Enforce monotonicity
      i = 0
      while (i < n - 1) {
        if (deltas(i) == 0) {
          slopes(i) = 0
          slopes(i + 1) = 0
        } else {
          val alpha = slopes(i) / deltas(i)
          val beta  = slopes(i + 1) / deltas(i)
          val s     = alpha * alpha + beta * beta
          if (s > 9) {
            val t = 3.0 / math.sqrt(s)
            slopes(i) = t * alpha * deltas(i)
            slopes(i + 1) = t * beta * deltas(i)
          }
        }
        i += 1
      }

      // Generate cubic bezier segments
      i = 0
      while (i < n - 1) {
        val dx   = dxs(i) / 3.0
        val cp1x = points(i).x + dx
        val cp1y = points(i).y + slopes(i) * dx
        val cp2x = points(i + 1).x - dx
        val cp2y = points(i + 1).y - slopes(i + 1) * dx
        path.curveTo(cp1x, cp1y, cp2x, cp2y, points(i + 1).x, points(i + 1).y)
        i += 1
      }

      path
    }
  }
}
