/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/LinkRefProcessorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder

/** Processing of elements which are based on a link ref: `[]` or `![]`. This includes footnote references `[^...]` and wiki links `[[...]]`.
  */
trait LinkRefProcessorFactory extends (Document => LinkRefProcessor) {

  /** Whether the image ref is desired, if not then `!` will be stripped off the prefix and treated as plain text.
    *
    * @param options
    *   options
    * @return
    *   true if `!` is part of the desired element, false otherwise
    */
  def getWantExclamationPrefix(options: DataHolder): Boolean

  /** Whether the element consists of nested `[]` inside the link ref.
    *
    * @param options
    *   options
    * @return
    *   nesting level for references, `>0` for nesting
    */
  def getBracketNestingLevel(options: DataHolder): Int

  /** Create a link ref processor for the document.
    *
    * @param document
    *   on which the processor will work
    * @return
    *   link ref processor
    */
  override def apply(document: Document): LinkRefProcessor
}
