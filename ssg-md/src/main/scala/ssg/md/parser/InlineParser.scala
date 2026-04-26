/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

import ssg.md.ast.Text
import ssg.md.parser.block.CharacterNodeFactory
import ssg.md.parser.core.delimiter.{ Bracket, Delimiter }
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.sequence.BasedSequence

import java.util.BitSet

/** Parser for inline content (text, links, emphasized text, etc).
  *
  * ''This interface is not intended to be implemented by clients.''
  */
trait InlineParser extends LightInlineParser {

  def initializeDocument(document: Document): Unit
  def finalizeDocument(document:   Document): Unit

  /** @param input
    *   the content to parse as inline
    * @param node
    *   the node to append resulting nodes to (as children)
    */
  def parse(input: BasedSequence, node: Node): Unit

  def lastDelimiter: Nullable[Delimiter]
  def lastBracket:   Nullable[Bracket]
  def parseCustom(
    input:            BasedSequence,
    node:             Node,
    customCharacters: BitSet,
    nodeFactoryMap:   Map[Char, CharacterNodeFactory]
  ):                                                                           Nullable[List[Node]]
  def mergeTextNodes(fromNode:        Nullable[Node], toNode: Nullable[Node]): Unit
  def mergeIfNeeded(first:            Nullable[Text], last:   Nullable[Text]): Unit
  override def toEOL():                                                        Nullable[BasedSequence]
  def parseNewline():                                                          Boolean
  def parseLinkDestination():                                                  Nullable[BasedSequence]
  def parseLinkTitle():                                                        Nullable[BasedSequence]
  def parseLinkLabel():                                                        Int
  def parseAutolink():                                                         Boolean
  def parseHtmlInline():                                                       Boolean
  def parseEntity():                                                           Boolean
  def processDelimiters(stackBottom:  Nullable[Delimiter]):                    Unit
  def removeDelimitersBetween(opener: Delimiter, closer:      Delimiter):      Unit
  def removeDelimiterAndNode(delim:   Delimiter):                              Unit
  def removeDelimiterKeepNode(delim:  Delimiter):                              Unit
  def removeDelimiter(delim:          Delimiter):                              Unit
}
