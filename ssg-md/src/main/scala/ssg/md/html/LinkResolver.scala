/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/LinkResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/LinkResolver.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html

import ssg.md.html.renderer.{ LinkResolverBasicContext, ResolvedLink }
import ssg.md.util.ast.Node

trait LinkResolver {
  def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink
}

object LinkResolver {
  val NULL: LinkResolver = (_, _, link) => link
}
