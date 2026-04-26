/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package tables

trait TableVisitor {
  def visit(node: TableBlock):     Unit
  def visit(node: TableHead):      Unit
  def visit(node: TableSeparator): Unit
  def visit(node: TableBody):      Unit
  def visit(node: TableRow):       Unit
  def visit(node: TableCell):      Unit
  def visit(node: TableCaption):   Unit
}
