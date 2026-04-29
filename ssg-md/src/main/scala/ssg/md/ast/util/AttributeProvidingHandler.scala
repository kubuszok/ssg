/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AttributeProvidingHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AttributeProvidingHandler.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast
package util

import ssg.md.html.renderer.AttributablePart
import ssg.md.util.ast.Node
import ssg.md.util.html.MutableAttributes
import ssg.md.util.visitor.AstAction
import ssg.md.util.visitor.AstHandler

class AttributeProvidingHandler[N <: Node](aClass: Class[N], adapter: AttributeProvidingHandler.AttributeProvidingVisitor[N])
    extends AstHandler[N, AttributeProvidingHandler.AttributeProvidingVisitor[N]](aClass, adapter) {

  def setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes): Unit =
    adapter.setAttributes(node.asInstanceOf[N], part, attributes)
}

object AttributeProvidingHandler {

  trait AttributeProvidingVisitor[N <: Node] extends AstAction[N] {
    def setAttributes(node: N, part: AttributablePart, attributes: MutableAttributes): Unit
  }
}
