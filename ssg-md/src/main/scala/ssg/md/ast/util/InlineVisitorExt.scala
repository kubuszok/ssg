/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/InlineVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/InlineVisitorExt.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.VisitHandler

object InlineVisitorExt {

  def VISIT_HANDLERS[V <: InlineVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler(classOf[AutoLink], (node: AutoLink) => visitor.visit(node)),
      new VisitHandler(classOf[Code], (node: Code) => visitor.visit(node)),
      new VisitHandler(classOf[Emphasis], (node: Emphasis) => visitor.visit(node)),
      new VisitHandler(classOf[HardLineBreak], (node: HardLineBreak) => visitor.visit(node)),
      new VisitHandler(classOf[HtmlEntity], (node: HtmlEntity) => visitor.visit(node)),
      new VisitHandler(classOf[HtmlInline], (node: HtmlInline) => visitor.visit(node)),
      new VisitHandler(classOf[HtmlInlineComment], (node: HtmlInlineComment) => visitor.visit(node)),
      new VisitHandler(classOf[Image], (node: Image) => visitor.visit(node)),
      new VisitHandler(classOf[ImageRef], (node: ImageRef) => visitor.visit(node)),
      new VisitHandler(classOf[Link], (node: Link) => visitor.visit(node)),
      new VisitHandler(classOf[LinkRef], (node: LinkRef) => visitor.visit(node)),
      new VisitHandler(classOf[MailLink], (node: MailLink) => visitor.visit(node)),
      new VisitHandler(classOf[SoftLineBreak], (node: SoftLineBreak) => visitor.visit(node)),
      new VisitHandler(classOf[StrongEmphasis], (node: StrongEmphasis) => visitor.visit(node)),
      new VisitHandler(classOf[Text], (node: Text) => visitor.visit(node))
    )
}
