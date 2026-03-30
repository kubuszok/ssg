/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/AstCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.util.ast.{Node, NodeVisitorBase}

class AstCollectingVisitor extends NodeVisitorBase {

  val EOL: String = "\n"
  protected var output: StringBuilder = new StringBuilder()
  protected var indent: Int = 0
  protected var eolPending: Boolean = false

  def ast: String = output.toString()

  def clear(): Unit = {
    output = new StringBuilder()
    indent = 0
    eolPending = false
  }

  protected def appendIndent(): Unit = {
    var i = 0
    while (i < indent * 2) {
      output.append(' ')
      i += 1
    }
    eolPending = true
  }

  protected def appendEOL(): Unit = {
    output.append(EOL)
    eolPending = false
  }

  protected def appendPendingEOL(): Unit = {
    if (eolPending) appendEOL()
  }

  def collectAndGetAstText(node: Node): String = {
    visit(node)
    ast
  }

  def collect(node: Node): Unit = {
    visit(node)
  }

  override protected def visit(node: Node): Unit = {
    appendIndent()
    node.astString(output, true)
    output.append(EOL)
    indent += 1

    try {
      super.visitChildren(node)
    } finally {
      indent -= 1
    }
  }
}

object AstCollectingVisitor {

  val EOL: String = "\n"
}
