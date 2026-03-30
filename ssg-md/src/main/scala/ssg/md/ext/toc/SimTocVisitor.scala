/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc

trait SimTocVisitor {
  def visit(node: SimTocBlock): Unit
  def visit(node: SimTocOptionList): Unit
  def visit(node: SimTocOption): Unit
  def visit(node: SimTocContent): Unit
}
