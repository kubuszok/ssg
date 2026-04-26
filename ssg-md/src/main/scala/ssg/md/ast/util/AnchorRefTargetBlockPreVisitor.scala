/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AnchorRefTargetBlockPreVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AnchorRefTargetBlockPreVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.Node

trait AnchorRefTargetBlockPreVisitor {

  /** Test if node needs to have its children visited
    *
    * @param node
    *   node
    * @param anchorRefTargetBlockVisitor
    *   anchor ref target visitor, can be used to visit anchor ref targets
    * @return
    *   true, if children of block node need to be visited
    */
  def preVisit(node: Node, anchorRefTargetBlockVisitor: AnchorRefTargetBlockVisitor): Boolean
}
