/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/ParagraphItemContainer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.data.DataHolder

// NOTE: ListOptions is in the parser package, which may not be ported yet.
// Using a forward reference type alias or trait here for now.
trait ParagraphItemContainer {
  def isParagraphInTightListItem(node:  Paragraph):                                        Boolean
  def isItemParagraph(node:             Paragraph):                                        Boolean
  def isParagraphWrappingDisabled(node: Paragraph, listOptions: Any, options: DataHolder): Boolean
}
