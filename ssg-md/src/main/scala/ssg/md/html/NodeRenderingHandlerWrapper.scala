/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/NodeRenderingHandlerWrapper.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.NodeRenderingHandler

private[html] class NodeRenderingHandlerWrapper(
  val myRenderingHandler:         NodeRenderingHandler[?],
  val myPreviousRenderingHandler: Nullable[NodeRenderingHandlerWrapper]
)
