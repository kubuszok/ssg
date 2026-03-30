/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Visitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.util.visitor.AstAction

/** Node visitor interface
  *
  * @tparam N
  *   specific node type
  */
trait Visitor[N <: Node] extends AstAction[N] {
  def visit(node: N): Unit
}
