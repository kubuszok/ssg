/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/HtmlInnerVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/HtmlInnerVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

trait HtmlInnerVisitor {
  def visit(node: HtmlInnerBlock):        Unit
  def visit(node: HtmlInnerBlockComment): Unit
}
