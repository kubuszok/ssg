/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRendererFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRendererFactory.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.util.data.DataHolder

/** Factory for instantiating new node renderers when rendering is done.
  */
trait NodeRendererFactory extends (DataHolder => NodeRenderer) {

  /** Create a new node renderer for the specified rendering context.
    *
    * @param options
    *   the context for rendering (normally passed on to the node renderer)
    * @return
    *   a node renderer
    */
  override def apply(options: DataHolder): NodeRenderer
}
