/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/BlockVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/BlockVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.Document
import ssg.md.util.ast.VisitHandler

object BlockVisitorExt {

  def VISIT_HANDLERS[V <: BlockVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler(classOf[BlockQuote], (node: BlockQuote) => visitor.visit(node)),
      new VisitHandler(classOf[BulletList], (node: BulletList) => visitor.visit(node)),
      new VisitHandler(classOf[Document], (node: Document) => visitor.visit(node)),
      new VisitHandler(classOf[FencedCodeBlock], (node: FencedCodeBlock) => visitor.visit(node)),
      new VisitHandler(classOf[Heading], (node: Heading) => visitor.visit(node)),
      new VisitHandler(classOf[HtmlBlock], (node: HtmlBlock) => visitor.visit(node)),
      new VisitHandler(classOf[HtmlCommentBlock], (node: HtmlCommentBlock) => visitor.visit(node)),
      new VisitHandler(classOf[IndentedCodeBlock], (node: IndentedCodeBlock) => visitor.visit(node)),
      new VisitHandler(classOf[BulletListItem], (node: BulletListItem) => visitor.visit(node)),
      new VisitHandler(classOf[OrderedListItem], (node: OrderedListItem) => visitor.visit(node)),
      new VisitHandler(classOf[OrderedList], (node: OrderedList) => visitor.visit(node)),
      new VisitHandler(classOf[Paragraph], (node: Paragraph) => visitor.visit(node)),
      new VisitHandler(classOf[Reference], (node: Reference) => visitor.visit(node)),
      new VisitHandler(classOf[ThematicBreak], (node: ThematicBreak) => visitor.visit(node))
    )
}
