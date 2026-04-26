/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package definition

trait DefinitionVisitor {
  def visit(node: DefinitionList): Unit
  def visit(node: DefinitionTerm): Unit
  def visit(node: DefinitionItem): Unit
}
