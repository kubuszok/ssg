/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/LinkResolvingHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/LinkResolvingHandler.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

import ssg.md.html.renderer.LinkResolverBasicContext
import ssg.md.html.renderer.ResolvedLink
import ssg.md.util.ast.Node
import ssg.md.util.visitor.AstAction
import ssg.md.util.visitor.AstHandler

class LinkResolvingHandler[N <: Node](aClass: Class[N], adapter: LinkResolvingHandler.LinkResolvingVisitor[N]) extends AstHandler[N, LinkResolvingHandler.LinkResolvingVisitor[N]](aClass, adapter) {

  def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink =
    adapter.resolveLink(node.asInstanceOf[N], context, link)
}

object LinkResolvingHandler {

  trait LinkResolvingVisitor[N <: Node] extends AstAction[N] {
    def resolveLink(node: N, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink
  }
}
