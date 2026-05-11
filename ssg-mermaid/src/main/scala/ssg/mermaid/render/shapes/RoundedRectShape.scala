/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/roundedRect.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection chaining with SvgBuilder API
 *   Idiom: Delegates to RectShape with forced corner radius; pure function
 *   Renames: roundedRect() → RoundedRectShape.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.graphs.commons.svg.SvgBuilder

/** Renders a rounded rectangle shape for flowchart nodes.
  *
  * This is a convenience that delegates to [[RectShape]] with a default corner radius applied if none is specified. Mermaid's rounded rectangle uses rx=ry=5 by default.
  */
object RoundedRectShape {

  /** Default corner radius for rounded rectangles (matching Mermaid's default). */
  private val DefaultRadius: Double = 5.0

  /** Renders a rounded rectangle shape.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param config
    *   shape configuration with position, size, and style
    * @return
    *   shape result with the rendered group and an intersection function
    */
  def render(parent: SvgBuilder, config: ShapeConfig): ShapeResult = {
    // Apply default corner radius if not explicitly set
    val adjustedConfig = if (config.rx <= 0 && config.ry <= 0) {
      config.copy(rx = DefaultRadius, ry = DefaultRadius)
    } else {
      config
    }
    RectShape.render(parent, adjustedConfig)
  }
}
