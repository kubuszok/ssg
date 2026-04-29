/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/UriContentResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/UriContentResolver.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
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
