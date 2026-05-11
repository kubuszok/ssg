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
 *   Convention: Replaces browser-native SVGRect / getBBox() return type
 *   Idiom: Immutable case class instead of mutable DOM object
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package svg

/** Bounding box representing the spatial extent of an SVG element.
  *
  * Mirrors the browser-native `SVGRect` returned by `SVGGraphicsElement.getBBox()`. In a server-side rendering context, bounding boxes are estimated from element attributes and text metrics rather
  * than measured by a layout engine.
  */
final case class BBox(x: Double, y: Double, width: Double, height: Double) {

  /** The right edge of the bounding box (x + width). */
  def maxX: Double = x + width

  /** The bottom edge of the bounding box (y + height). */
  def maxY: Double = y + height

  /** Returns a new BBox that encompasses both this box and `other`. */
  def union(other: BBox): BBox = {
    val minX    = math.min(x, other.x)
    val minY    = math.min(y, other.y)
    val newMaxX = math.max(maxX, other.maxX)
    val newMaxY = math.max(maxY, other.maxY)
    BBox(minX, minY, newMaxX - minX, newMaxY - minY)
  }

  /** Returns true if this bounding box has zero area. */
  def isEmpty: Boolean = width == 0.0 && height == 0.0
}

object BBox {

  /** A zero-sized bounding box at the origin. */
  val Empty: BBox = BBox(0.0, 0.0, 0.0, 0.0)
}
