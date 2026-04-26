/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitHandler.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

/** Interface to visit variations on specific nodes: visit() visiting node and if no handler defined then visit node's children visitNodeOnly() visit node and if no handler then do not process
  * children visitChildren() visit node's children
  */
trait NodeVisitHandler extends Visitor[Node] {
  def visitNodeOnly(node:   Node): Unit
  def visitChildren(parent: Node): Unit
}
