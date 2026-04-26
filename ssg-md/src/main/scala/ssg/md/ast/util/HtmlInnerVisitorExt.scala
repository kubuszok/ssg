/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/HtmlInnerVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/HtmlInnerVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.VisitHandler

object HtmlInnerVisitorExt {

  def VISIT_HANDLERS[V <: HtmlInnerVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler(classOf[HtmlInnerBlock], (node: HtmlInnerBlock) => visitor.visit(node)),
      new VisitHandler(classOf[HtmlInnerBlockComment], (node: HtmlInnerBlockComment) => visitor.visit(node))
    )
}
