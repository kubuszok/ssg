/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionTerm.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package definition

import ssg.md.ast.{ ListItem, Paragraph }
import ssg.md.parser.ListOptions
import ssg.md.util.ast.Node
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/** A Definition block node
  */
class DefinitionTerm() extends ListItem {

  override def astExtra(out: StringBuilder): Unit = {}

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(node: Node) = {
    this()
    appendChild(node)
    this.setCharsFromContent()
  }

  override def isParagraphWrappingDisabled(node: Paragraph, listOptions: ListOptions, options: DataHolder): Boolean = true
}
