/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AnchorRefTargetBlockVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AnchorRefTargetBlockVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast
package util

import ssg.md.ast.AnchorRefTarget
import ssg.md.util.ast.Block
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitorBase

/** Abstract visitor that visits only children of blocks excluding Paragraphs
  *
  * Can be used to only process block nodes efficiently skipping text. If you override a method and want visiting to descend into children, call [[visitChildren]].
  */
abstract class AnchorRefTargetBlockVisitor extends NodeVisitorBase {

  protected def visit(node: AnchorRefTarget): Unit

  protected def preVisit(node: Node): Boolean = true

  def visit(node: Node): Unit = {
    node match {
      case art: AnchorRefTarget => visit(art: AnchorRefTarget)
      case _ => ()
    }

    if (preVisit(node) && node.isInstanceOf[Block]) {
      visitChildren(node)
    }
  }
}
