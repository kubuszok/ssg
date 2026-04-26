/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/VisitHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/VisitHandler.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.util.visitor.AstHandler

/** Node visit handler for specific node type
  */
class VisitHandler[N <: Node](klass: Class[N], visitorAdapter: Visitor[N]) extends AstHandler[N, Visitor[N]](klass, visitorAdapter), Visitor[Node] {

  override def visit(node: Node): Unit =
    adapter.visit(node.asInstanceOf[N])
}
