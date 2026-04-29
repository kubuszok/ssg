/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableTextCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableTextCollectingVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package tables

import ssg.md.ast.util.TextCollectingVisitor

/** @deprecated use [[ssg.md.util.ast.TextCollectingVisitor]] from the utils library */
@deprecated("use ssg.md.util.ast.TextCollectingVisitor from the utils library", "")
class TableTextCollectingVisitor(lineBreakNodes: Class[?]*)
    extends TextCollectingVisitor(
      TableTextCollectingVisitor.mergedClasses(lineBreakNodes*)*
    )

object TableTextCollectingVisitor {

  val TABLE_LINE_BREAK_CLASSES: Array[Class[?]] = Array(classOf[TableBlock], classOf[TableRow], classOf[TableCaption])

  private[tables] def mergedClasses(extra: Class[?]*): Array[Class[?]] =
    if (extra.isEmpty) TABLE_LINE_BREAK_CLASSES
    else TABLE_LINE_BREAK_CLASSES ++ extra
}
