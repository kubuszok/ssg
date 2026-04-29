/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/CustomBlockParserFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/CustomBlockParserFactory.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.util.data.DataHolder
import ssg.md.util.dependency.Dependent
import ssg.md.util.sequence.mappers.SpecialLeadInHandler

/** Custom block parser factory to create parser instance specific block parser factory.
  */
trait CustomBlockParserFactory extends (DataHolder => BlockParserFactory), Dependent {

  override def apply(options: DataHolder): BlockParserFactory

  /** @param options
    *   options for this parser session
    * @return
    *   special lead in character handler for the block parser elements
    */
  def getLeadInHandler(options: DataHolder): Nullable[SpecialLeadInHandler] =
    Nullable.empty
}
