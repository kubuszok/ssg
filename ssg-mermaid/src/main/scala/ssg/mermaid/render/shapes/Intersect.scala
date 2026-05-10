/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/intersect/
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Individual intersect-*.js files consolidated into one utility object
 *   Idiom: Pure functions; uses dagre Point case class
 *   Renames: intersect-rect.js, intersect-ellipse.js, etc. → Intersect object methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.mermaid.layout.dagre.Point

/** Geometry intersection utilities for computing where an edge meets a shape boundary.
  *
  * Each method takes the shape's center (node x/y) and size, plus an external point, and returns the intersection point on the shape boundary. These are direct ports of the D3/dagre intersection
  * functions used by Mermaid.
  */
object Intersect {

  /** Computes the intersection of a line from an external point to the center of a rectangle.
    *
    * The rectangle is centered at (nodeX, nodeY) with given width and height. The external point is outside the rectangle. Returns the point on the rectangle boundary where the line crosses.
    *
    * Ported from intersect-rect.js.
    *
    * @param nodeX
    *   rectangle center x
    * @param nodeY
    *   rectangle center y
    * @param width
    *   rectangle width
    * @param height
    *   rectangle height
    * @param point
    *   external point
    * @return
    *   intersection point on the rectangle boundary
    */
  def rect(nodeX: Double, nodeY: Double, width: Double, height: Double, point: Point): Point = {
    val dx = point.x - nodeX
    val dy = point.y - nodeY
    val w  = width / 2.0
    val h  = height / 2.0

    if (dx == 0 && dy == 0) {
      // Point is at center — return top-center as a default
      Point(nodeX, nodeY - h)
    } else {
      // Determine which edge the line crosses
      val sx: Double = if (dy == 0) {
        // Horizontal line
        0.0
      } else {
        val slope = dx / dy
        if (math.abs(slope) * h > w) {
          // Crosses left or right edge
          if (dx > 0) w / dx else -w / -dx
        } else {
          // Crosses top or bottom edge
          if (dy > 0) h / dy else -h / -dy
        }
      }

      // For pure horizontal lines
      if (dy == 0) {
        if (dx > 0) {
          Point(nodeX + w, nodeY)
        } else {
          Point(nodeX - w, nodeY)
        }
      } else {
        Point(nodeX + sx * dx, nodeY + sx * dy)
      }
    }
  }

  /** Computes the intersection of a line from an external point to the center of an ellipse.
    *
    * The ellipse is centered at (cx, cy) with semi-axes rx and ry.
    *
    * Ported from intersect-ellipse.js.
    *
    * @param cx
    *   ellipse center x
    * @param cy
    *   ellipse center y
    * @param rx
    *   horizontal semi-axis
    * @param ry
    *   vertical semi-axis
    * @param point
    *   external point
    * @return
    *   intersection point on the ellipse boundary
    */
  def ellipse(cx: Double, cy: Double, rx: Double, ry: Double, point: Point): Point = {
    val px = point.x - cx
    val py = point.y - cy

    if (px == 0 && py == 0) {
      // Point is at center — return top as default
      Point(cx, cy - ry)
    } else if (px == 0) {
      // Vertical line
      if (py > 0) Point(cx, cy + ry) else Point(cx, cy - ry)
    } else if (py == 0) {
      // Horizontal line
      if (px > 0) Point(cx + rx, cy) else Point(cx - rx, cy)
    } else {
      // General case — parametric intersection
      // Line direction: (px, py), scaled to hit ellipse boundary
      // Ellipse: (x/rx)^2 + (y/ry)^2 = 1
      // Parametric: x = t*px, y = t*py → (t*px/rx)^2 + (t*py/ry)^2 = 1
      val t = 1.0 / math.sqrt((px * px) / (rx * rx) + (py * py) / (ry * ry))
      Point(cx + t * px, cy + t * py)
    }
  }

  /** Computes the intersection of a line from an external point to the center of a circle.
    *
    * Ported from intersect-circle.js (calls through to ellipse with rx = ry = r).
    *
    * @param cx
    *   circle center x
    * @param cy
    *   circle center y
    * @param r
    *   radius
    * @param point
    *   external point
    * @return
    *   intersection point on the circle boundary
    */
  def circle(cx: Double, cy: Double, r: Double, point: Point): Point =
    ellipse(cx, cy, r, r, point)

  /** Computes the intersection of a line from an external point to the center of a polygon.
    *
    * The polygon is defined by its vertices (absolute coordinates). This is used for diamonds, hexagons, trapezoids, and other irregular shapes.
    *
    * Ported from intersect-polygon.js.
    *
    * @param polyPoints
    *   polygon vertices in order (absolute coordinates)
    * @param point
    *   external point
    * @return
    *   intersection point on the polygon boundary
    */
  def polygon(polyPoints: Array[Point], point: Point): Point = {
    // Find the polygon center (average of all vertices)
    val cx = polyPoints.map(_.x).sum / polyPoints.length
    val cy = polyPoints.map(_.y).sum / polyPoints.length

    var bestDist  = Double.MaxValue
    var bestPoint = Point(cx, cy)

    // Check intersection with each edge of the polygon
    var i = 0
    while (i < polyPoints.length) {
      val p1 = polyPoints(i)
      val p2 = polyPoints((i + 1) % polyPoints.length)

      val inter = lineIntersection(Point(cx, cy), point, p1, p2)
      inter.foreach { pt =>
        val dist = distance(point, pt)
        if (dist < bestDist) {
          bestDist = dist
          bestPoint = pt
        }
      }

      i += 1
    }

    bestPoint
  }

  /** Computes the intersection point of two line segments, if any.
    *
    * Line 1 goes from p1 to p2. Line 2 goes from p3 to p4. Returns the intersection point if the segments cross, or None if they are parallel or do not intersect within both segments.
    *
    * Ported from intersect-line.js.
    *
    * @param p1
    *   start of line 1
    * @param p2
    *   end of line 1
    * @param p3
    *   start of line 2
    * @param p4
    *   end of line 2
    * @return
    *   intersection point, or None if no intersection
    */
  def lineIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Option[Point] = {
    // Using parametric form:
    // P = p1 + t*(p2-p1) for line 1
    // P = p3 + u*(p4-p3) for line 2
    val d1x = p2.x - p1.x
    val d1y = p2.y - p1.y
    val d2x = p4.x - p3.x
    val d2y = p4.y - p3.y

    val denom = d2y * d1x - d2x * d1y

    if (math.abs(denom) < 1e-10) {
      // Lines are parallel or coincident
      None
    } else {
      val ua = (d2x * (p1.y - p3.y) - d2y * (p1.x - p3.x)) / denom
      val ub = (d1x * (p1.y - p3.y) - d1y * (p1.x - p3.x)) / denom

      // Check if intersection is within both line segments
      // For the line from center to external point, we want ua >= 0 (in direction of point)
      // For the polygon edge, we want 0 <= ub <= 1
      if (ub >= 0.0 && ub <= 1.0 && ua >= 0.0) {
        val x = p1.x + ua * d1x
        val y = p1.y + ua * d1y
        Some(Point(x, y))
      } else {
        None
      }
    }
  }

  /** Computes the Euclidean distance between two points. */
  def distance(p1: Point, p2: Point): Double = {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    math.sqrt(dx * dx + dy * dy)
  }
}
