/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/UriContentResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.{ LinkResolverBasicContext, ResolvedContent }
import ssg.md.util.ast.Node

trait UriContentResolver {
  def resolveContent(node: Node, context: LinkResolverBasicContext, content: ResolvedContent): ResolvedContent
}

object UriContentResolver {
  val NULL: UriContentResolver = (_, _, content) => content
}
