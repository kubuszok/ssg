/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/InsVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/InsVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package ins

trait InsVisitor {
  def visit(node: Ins): Unit
}
