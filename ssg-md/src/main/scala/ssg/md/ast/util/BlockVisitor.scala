/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/BlockVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/BlockVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.Document

trait BlockVisitor {
  def visit(node: BlockQuote):        Unit
  def visit(node: BulletList):        Unit
  def visit(node: Document):          Unit
  def visit(node: FencedCodeBlock):   Unit
  def visit(node: Heading):           Unit
  def visit(node: HtmlBlock):         Unit
  def visit(node: HtmlCommentBlock):  Unit
  def visit(node: IndentedCodeBlock): Unit
  def visit(node: BulletListItem):    Unit
  def visit(node: OrderedListItem):   Unit
  def visit(node: OrderedList):       Unit
  def visit(node: Paragraph):         Unit
  def visit(node: Reference):         Unit
  def visit(node: ThematicBreak):     Unit
}
