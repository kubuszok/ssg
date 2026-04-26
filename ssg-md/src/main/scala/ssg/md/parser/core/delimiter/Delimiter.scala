/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/Delimiter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/Delimiter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package core
package delimiter

import ssg.md.ast.Text
import ssg.md.parser.delimiter.DelimiterRun
import ssg.md.util.ast.{ DelimitedNode, Node }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class Delimiter(
  val node:              Text,
  val input:             BasedSequence,
  val delimiterChar:     Char,
  private val _canOpen:  Boolean,
  private val _canClose: Boolean,
  private var _previous: Nullable[Delimiter],
  private var _index:    Int
) extends DelimiterRun {

  /** Skip this delimiter when looking for a link/image opener because it was already matched.
    */
  var matched: Boolean = false

  private var _next: Nullable[Delimiter] = Nullable.empty

  var numDelims: Int = 1

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  override def previous:                      Delimiter           = _previous.getOrElse(null.asInstanceOf[Delimiter]) // @nowarn - Java interop: may be null
  def previousNullable:                       Nullable[Delimiter] = _previous
  def previous_=(value: Nullable[Delimiter]): Unit                = _previous = value

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  override def next:                      Delimiter           = _next.getOrElse(null.asInstanceOf[Delimiter]) // @nowarn - Java interop: may be null
  def nextNullable:                       Nullable[Delimiter] = _next
  def next_=(value: Nullable[Delimiter]): Unit                = _next = value

  def startIndex: Int = _index

  def endIndex: Int = _index + numDelims

  def getIndex:             Int  = _index
  def setIndex(value: Int): Unit = _index = value

  def tailChars(delimiterUse: Int): BasedSequence =
    input.subSequence(endIndex - delimiterUse, endIndex)

  def leadChars(delimiterUse: Int): BasedSequence =
    input.subSequence(startIndex, startIndex + delimiterUse)

  def previousNonDelimiterTextNode: Nullable[Text] = {
    val previousNode = node.previous
    previousNode.fold[Nullable[Text]](Nullable.empty) { prev =>
      prev match {
        case t: Text if _previous.isEmpty || _previous.get.node.ne(t) => Nullable(t)
        case _ => Nullable.empty
      }
    }
  }

  def nextNonDelimiterTextNode: Nullable[Text] = {
    val nextNode = node.next
    nextNode.fold[Nullable[Text]](Nullable.empty) { n =>
      n match {
        case t: Text if _next.isEmpty || _next.get.node.ne(t) => Nullable(t)
        case _ => Nullable.empty
      }
    }
  }

  def moveNodesBetweenDelimitersTo(delimitedNode: DelimitedNode, closer: Delimiter): Unit = {
    var tmp: Nullable[Node] = node.next
    while (tmp.isDefined && !tmp.contains(closer.node)) {
      val nextTmp = tmp.get.next
      delimitedNode.asInstanceOf[Node].appendChild(tmp.get)
      tmp = nextTmp
    }

    delimitedNode.text = input.subSequence(endIndex, closer.startIndex)
    node.insertAfter(delimitedNode.asInstanceOf[Node])
  }

  def convertDelimitersToText(delimitersUsed: Int, closer: Delimiter): Unit = {
    val openerText = Text()
    openerText.chars = tailChars(delimitersUsed)
    val closerText = Text()
    closerText.chars = closer.leadChars(delimitersUsed)

    node.insertAfter(openerText)
    closer.node.insertBefore(closerText)
  }

  override def canOpen: Boolean = _canOpen

  override def canClose: Boolean = _canClose

  override def length: Int = numDelims
}
