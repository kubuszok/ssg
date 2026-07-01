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
  * @param htmlLabels
  *   when true, the node label is rendered as an HTML `<foreignObject>` (ISS-1205) instead of an SVG `<text>`. Resolved per `node.useHtmlLabels || evaluate(flowchart.htmlLabels)`
  *   (dagre-wrapper/shapes/util.js:9). Defaults to false so the SVG-text geometry is unchanged.
  * @param securityLevel
  *   the resolved `MermaidConfig.securityLevel`; gates HTML-label sanitization (diagrams/common/common.ts:66-94). Only consulted on the HTML-label path.
  * @param look
  *   the resolved `MermaidConfig.look` ("classic" or "handDrawn"); upstream `flowDb.ts:882/918` copies `config.look` onto every node so each shape renderer can branch `node.look === "handDrawn"` into
  *   `rough.svg(...)`. Threaded here so shape renderers can select the hand-drawn path (ISS-1204). Defaults to "classic" so classic geometry is unchanged.
  * @param handDrawnSeed
  *   the resolved `MermaidConfig.handDrawnSeed`; seeds the rough.js PRNG on the hand-drawn path so sketch output is reproducible. Defaults to 0 (the "random seed" sentinel).
  */
final case class ShapeConfig(
  id:            String = "",
  x:             Double = 0,
  y:             Double = 0,
  width:         Double = 0,
  height:        Double = 0,
  label:         String = "",
  rx:            Double = 0,
  ry:            Double = 0,
  cssClass:      String = "",
  style:         String = "",
  padding:       Double = 8,
  labelStyle:    String = "",
  htmlLabels:    Boolean = false,
  securityLevel: String = "strict",
  look:          String = "classic",
  handDrawnSeed: Int = 0
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
