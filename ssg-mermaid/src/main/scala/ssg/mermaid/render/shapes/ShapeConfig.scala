/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid (shape rendering configuration)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Encapsulates shape rendering parameters passed from diagrams
 *   Idiom: Immutable case class with defaults; replaces ad-hoc JS config objects
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.graphs.commons.layout.dagre.Point

/** Configuration for rendering a single shape node.
  *
  * Carries the layout-computed position and size, plus visual style properties. This is populated from dagre node labels and diagram-specific configuration.
  *
  * @param id
  *   the unique node identifier (used for element IDs)
  * @param x
  *   center x coordinate (from dagre layout)
  * @param y
  *   center y coordinate (from dagre layout)
  * @param width
  *   node width (from dagre layout, including padding)
  * @param height
  *   node height (from dagre layout, including padding)
  * @param label
  *   text label to display inside the shape
  * @param rx
  *   corner radius for rounded rectangles (0 for sharp corners)
  * @param ry
  *   corner radius for rounded rectangles (0 for sharp corners)
  * @param cssClass
  *   CSS class to apply to the shape group
  * @param style
  *   inline CSS styles (fill, stroke, etc.)
  * @param padding
  *   internal padding between shape boundary and label
  * @param labelStyle
  *   inline CSS styles for the label text
  */
final case class ShapeConfig(
  id:         String = "",
  x:          Double = 0,
  y:          Double = 0,
  width:      Double = 0,
  height:     Double = 0,
  label:      String = "",
  rx:         Double = 0,
  ry:         Double = 0,
  cssClass:   String = "",
  style:      String = "",
  padding:    Double = 8,
  labelStyle: String = ""
)

/** Result of rendering a shape. Contains the SVG builder for the shape group and a function for computing edge intersections with the shape boundary.
  *
  * @param shapeGroup
  *   the SVG builder containing the rendered shape elements
  * @param intersectFn
  *   function that computes where a line from an external point meets the shape boundary
  */
final case class ShapeResult(
  shapeGroup:  ssg.graphs.commons.svg.SvgBuilder,
  intersectFn: Point => Point
)
