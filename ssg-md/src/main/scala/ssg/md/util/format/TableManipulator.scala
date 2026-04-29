/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableManipulator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableManipulator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

// Forward reference: Node will be available when util-ast is ported
import ssg.md.util.ast.Node

trait TableManipulator {

  def apply(table: MarkdownTable, tableNode: Node): Unit
}

object TableManipulator {

  val NULL: TableManipulator = new TableManipulator {
    override def apply(table: MarkdownTable, tableNode: Node): Unit = {}
  }
}
