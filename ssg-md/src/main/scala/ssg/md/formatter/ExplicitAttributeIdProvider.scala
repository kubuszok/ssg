/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/ExplicitAttributeIdProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.ast.Node

trait ExplicitAttributeIdProvider {

  /** Used by AttributesExtension to insert attributes for headings during merge
    *
    * @param node
    *   node
    * @param id
    *   explicit id
    * @param context
    *   context
    * @param markdown
    *   markdown writer
    */
  def addExplicitId(node: Node, id: Nullable[String], context: NodeFormatterContext, markdown: MarkdownWriter): Unit
}
