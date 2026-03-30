/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/Bracket.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core
package delimiter

import ssg.md.ast.Text
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** Opening bracket for links (`[`) or images (`![`).
  */
class Bracket private (
  val node:              Text,
  private val _index:    Int,
  val image:             Boolean,
  val previous:          Nullable[Bracket],
  val previousDelimiter: Nullable[Delimiter]
) {

  /** Whether this bracket is allowed to form a link/image (also known as "active"). */
  var allowed: Boolean = true

  /** Whether there is an unescaped bracket (opening or closing) anywhere after this opening bracket. Determined by next != null.
    */
  var bracketAfter: Boolean = false

  def startIndex: Int = _index

  def endIndex: Int = if (image) _index + 2 else _index + 1

  def isStraddling(nodeChars: BasedSequence): Boolean = {
    // first see if we have any closers in our span
    val startOffset = nodeChars.startOffset
    val endOffset   = nodeChars.endOffset
    var inner: Nullable[Delimiter] = previousDelimiter.flatMap(_.nextNullable)

    boundary {
      while (inner.isDefined) {
        val innerOffset = inner.get.endIndex
        if (innerOffset >= endOffset) break(false)
        if (innerOffset >= startOffset) {
          // inside our region, if unmatched then we are straddling the region
          if (!inner.get.matched) break(true)
        }
        inner = inner.get.nextNullable
      }
      false
    }
  }
}

object Bracket {

  def link(input: BasedSequence, node: Text, index: Int, previous: Nullable[Bracket], previousDelimiter: Nullable[Delimiter]): Bracket =
    Bracket(node, index, false, previous, previousDelimiter)

  def image(input: BasedSequence, node: Text, index: Int, previous: Nullable[Bracket], previousDelimiter: Nullable[Delimiter]): Bracket =
    Bracket(node, index, true, previous, previousDelimiter)

  private def apply(node: Text, index: Int, image: Boolean, previous: Nullable[Bracket], previousDelimiter: Nullable[Delimiter]): Bracket =
    new Bracket(node, index, image, previous, previousDelimiter)
}
