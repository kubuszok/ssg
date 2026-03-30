/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/NodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.sequence.SequenceUtils

/** A renderer for a set of node types.
  */
trait NodeFormatter {

  /** @return
    *   the mapping of nodes this renderer handles to rendering function
    */
  def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]]

  /** Collect nodes of given type so that they can be quickly accessed without traversing the AST by all formatting extensions.
    *
    * @return
    *   the nodes of interest to this formatter during formatting.
    */
  def getNodeClasses: Nullable[Set[Class[?]]]

  /** Return character which compacts like block quote prefix
    *
    * @return
    *   character or NUL if none
    */
  def getBlockQuoteLikePrefixChar: Char = SequenceUtils.NUL
}
