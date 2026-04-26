/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/AbbreviationVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/AbbreviationVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package abbreviation

trait AbbreviationVisitor {
  def visit(node: AbbreviationBlock): Unit
  def visit(node: Abbreviation):      Unit
}
