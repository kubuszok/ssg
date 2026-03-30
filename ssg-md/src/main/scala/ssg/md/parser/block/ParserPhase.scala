/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/ParserPhase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package block

/** Current Parser Phase as the document is parsed.
  *
  * This enum is not visible by clients.
  */
enum ParserPhase extends java.lang.Enum[ParserPhase] {
  case NONE
  case STARTING
  case PARSE_BLOCKS
  case PRE_PROCESS_PARAGRAPHS
  case PRE_PROCESS_BLOCKS
  case PARSE_INLINES
  case DONE
}
