/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/LinkRefProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.sequence.BasedSequence

/** Processing of elements which are based on a link ref: `[]` or `![]`. This includes footnote references `[^...]` and wiki links `[[...]]`.
  */
trait LinkRefProcessor {

  /** Whether the image ref is desired, if not then `!` will be stripped off the prefix and treated as plain text.
    *
    * @return
    *   true if `!` is part of the desired element, false otherwise
    */
  def wantExclamationPrefix: Boolean

  /** Whether the element consists of nested `[]` inside the link ref. For example Wiki link `[[]]` processor would return 1. Only immediately nested `[]` are considered. `[[  ]]` is nesting 1,
    * `[ [ ]]` is not considered.
    *
    * When `>0` then preview of next characters is used and if they will match then inner reference will not be created to allow outer one to match the desired element.
    *
    * @return
    *   desired nesting level for references, `>0` for nested, 0 for not nested
    */
  def bracketNestingLevel: Int

  /** Test whether the element matches desired one.
    *
    * @param nodeChars
    *   text to match, including `[]` or `![]`
    * @return
    *   true if it is a match
    */
  def isMatch(nodeChars: BasedSequence): Boolean

  /** Create the desired element that was previously matched with isMatch.
    *
    * @param nodeChars
    *   char sequence from which to create the node
    * @return
    *   Node element to be inserted into the tree
    */
  def createNode(nodeChars: BasedSequence): Node

  /** Adjust child nodes' text as needed when some of the link ref text was used in the opening or closing sequence of the node or if the children are not desired then remove them.
    *
    * @param document
    *   document, can be used to get parsing options
    * @param node
    *   node whose inline text is to be adjusted
    * @return
    *   adjusted sequence to use for this node's child text
    */
  def adjustInlineText(document: Document, node: Node): BasedSequence

  /** Allows the delimiter processor to allow/disallow other delimiters in its inline text.
    *
    * @param chars
    *   character sub-sequence to test
    * @param document
    *   document, can be used to get options
    * @param node
    *   delimited node created by this processor
    * @return
    *   true if delimiters are allowed in this part of the node's text
    */
  def allowDelimiters(chars: BasedSequence, document: Document, node: Node): Boolean

  /** Allows the processor to adjust nodes' elements after all delimiters have been processed inside the inlined text.
    *
    * @param document
    *   document, can be used to get parsing options
    * @param node
    *   node whose elements can be adjusted
    */
  def updateNodeElements(document: Document, node: Node): Unit
}
