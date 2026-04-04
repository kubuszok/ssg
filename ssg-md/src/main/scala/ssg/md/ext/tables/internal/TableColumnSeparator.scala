/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableColumnSeparator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables
package internal

import ssg.md.util.ast.{ DoNotDecorate, Node }
import ssg.md.util.sequence.BasedSequence
import scala.language.implicitConversions

/** Table cell separator only used during parsing, not part of the AST, use the [[ssg.md.ext.tables.TableCell#openingMarker]] and [[ssg.md.ext.tables.TableCell#closingMarker]]
  */
private[tables] class TableColumnSeparator() extends Node, DoNotDecorate {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: String) = {
    this()
    this.chars = BasedSequence.of(chars)
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit =
    astExtraChars(out)

  override protected def toStringAttributes: String = "text=" + chars
}
