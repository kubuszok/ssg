/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/MatchedBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/MatchedBlockParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package block

import ssg.md.util.data.MutableDataHolder
import ssg.md.util.sequence.BasedSequence

/** Open block parser that was last matched during the continue phase. This is different from the currently active block parser, as an unmatched block is only closed when a new block is started.
  *
  * ''This interface is not intended to be implemented by clients.''
  */
trait MatchedBlockParser {

  /** @return
    *   current matched block parser instance
    */
  def blockParser: BlockParser

  /** Returns the current content of the paragraph if the matched block is a paragraph. The content can be multiple lines separated by `'\n'`.
    *
    * @return
    *   paragraph content or Nullable.empty
    */
  def paragraphContent: Nullable[BasedSequence]

  def paragraphLines: Nullable[List[BasedSequence]]

  def paragraphEolLengths: Nullable[List[Int]]

  def paragraphDataHolder: Nullable[MutableDataHolder]
}
