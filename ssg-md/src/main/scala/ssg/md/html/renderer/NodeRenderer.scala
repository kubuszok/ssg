/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html
package renderer

/** A renderer for a set of node types.
  */
trait NodeRenderer {

  /** @return
    *   the mapping of nodes this renderer handles to rendering function
    */
  def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]]
}
