/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/LightInlineParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/LightInlineParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

import ssg.md.ast.Text
import ssg.md.ast.util.Parsing
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.sequence.BasedSequence

import scala.collection.mutable.ArrayBuffer
import java.util.regex.{ Matcher, Pattern }

trait LightInlineParser {

  def currentText: ArrayBuffer[BasedSequence]

  def input:                         BasedSequence
  def input_=(value: BasedSequence): Unit

  def index:               Int
  def index_=(value: Int): Unit

  def block:                Node
  def block_=(value: Node): Unit

  def document:                    Document
  def document_=(value: Document): Unit

  def options: InlineParserOptions
  def parsing: Parsing

  def `match`(re:         Pattern): Nullable[BasedSequence]
  def matchWithGroups(re: Pattern): Nullable[Array[BasedSequence]]
  def matcher(re:         Pattern): Nullable[Matcher]

  def peek():           Char
  def peek(ahead: Int): Char

  def flushTextNode(): Boolean

  def appendText(text: BasedSequence):                                 Unit
  def appendText(text: BasedSequence, beginIndex: Int, endIndex: Int): Unit
  def appendNode(node: Node):                                          Unit

  // In some cases, we don't want the text to be appended to an existing node, we need it separate
  def appendSeparateText(text: BasedSequence): Text

  def moveNodes(fromNode: Node, toNode: Node): Unit

  def spnl():        Boolean
  def nonIndentSp(): Boolean
  def sp():          Boolean
  def spnlUrl():     Boolean
  def toEOL():       Nullable[BasedSequence]
}
