/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main entry point for the Graphviz DOT renderer.
 */
package ssg
package graphviz

import ssg.graphviz.parse.{DotGraph, DotParser, DotScanner}
import ssg.graphviz.render.DotRenderer

object Graphviz {

  /** Parses a DOT language string into an AST. */
  def parse(input: String): DotGraph = {
    val tokens = DotScanner(input).scan()
    DotParser(tokens).parse()
  }

  /** Parses and renders a DOT language string to an SVG string. */
  def render(input: String, config: GraphvizConfig = GraphvizConfig()): String = {
    val ast = parse(input)
    DotRenderer.render(ast, config)
  }
}
