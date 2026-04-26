/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/ParagraphItemContainer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/ParagraphItemContainer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.parser.ListOptions
import ssg.md.util.data.DataHolder

trait ParagraphItemContainer {
  def isParagraphInTightListItem(node:  Paragraph):                                                Boolean
  def isItemParagraph(node:             Paragraph):                                                Boolean
  def isParagraphWrappingDisabled(node: Paragraph, listOptions: ListOptions, options: DataHolder): Boolean
}
