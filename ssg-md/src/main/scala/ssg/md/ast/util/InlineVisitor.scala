/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/InlineVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/InlineVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast
package util

trait InlineVisitor {
  def visit(node: AutoLink):          Unit
  def visit(node: Code):              Unit
  def visit(node: Emphasis):          Unit
  def visit(node: HardLineBreak):     Unit
  def visit(node: HtmlEntity):        Unit
  def visit(node: HtmlInline):        Unit
  def visit(node: HtmlInlineComment): Unit
  def visit(node: Image):             Unit
  def visit(node: ImageRef):          Unit
  def visit(node: Link):              Unit
  def visit(node: LinkRef):           Unit
  def visit(node: MailLink):          Unit
  def visit(node: SoftLineBreak):     Unit
  def visit(node: StrongEmphasis):    Unit
  def visit(node: Text):              Unit
}
