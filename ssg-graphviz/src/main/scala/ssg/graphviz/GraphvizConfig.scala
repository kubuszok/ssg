/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Configuration for the Graphviz DOT renderer.
 */
package ssg
package graphviz

enum LayoutEngine extends java.lang.Enum[LayoutEngine] {
  case Dot, Neato, Circo, Twopi
}

final case class GraphvizConfig(
  engine:            LayoutEngine = LayoutEngine.Dot,
  fontName:          String = "sans-serif",
  fontSize:          Double = 14.0,
  nodeSep:           Double = 50.0,
  rankSep:           Double = 50.0,
  marginX:           Double = 20.0,
  marginY:           Double = 20.0,
  defaultNodeShape:  String = "ellipse",
  defaultNodeWidth:  Double = 0.75,
  defaultNodeHeight: Double = 0.5
)
