/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionList.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionList.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package definition

import ssg.md.ast.ListBlock
import ssg.md.util.ast.{ BlockContent, Node }
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }
import scala.language.implicitConversions

/** A DefinitionList block node
  */
class DefinitionList() extends ListBlock {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.contentLines = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
}
