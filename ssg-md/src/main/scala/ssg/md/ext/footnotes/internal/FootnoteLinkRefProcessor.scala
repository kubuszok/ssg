/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteLinkRefProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.Nullable
import ssg.md.parser.{LinkRefProcessor, LinkRefProcessorFactory}
import ssg.md.util.ast.{Document, Node}
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class FootnoteLinkRefProcessor(document: Document) extends LinkRefProcessor {

  private val footnoteRepository: FootnoteRepository = FootnoteExtension.FOOTNOTES.get(document)

  override def wantExclamationPrefix: Boolean = FootnoteLinkRefProcessor.WANT_EXCLAMATION_PREFIX

  override def bracketNestingLevel: Int = FootnoteLinkRefProcessor.BRACKET_NESTING_LEVEL

  override def isMatch(nodeChars: BasedSequence): Boolean = {
    nodeChars.length() >= 3 && nodeChars.charAt(0) == '[' && nodeChars.charAt(1) == '^' && nodeChars.endCharAt(1) == ']'
  }

  override def createNode(nodeChars: BasedSequence): Node = {
    val footnoteId = nodeChars.midSequence(2, -1).trim()
    val footnoteBlock: Nullable[FootnoteBlock] =
      if (footnoteId.length() > 0) Nullable(footnoteRepository.get(footnoteId.toString))
      else Nullable.empty

    val footnote = new Footnote(nodeChars.subSequence(0, 2), footnoteId, nodeChars.endSequence(1))
    footnote.footnoteBlock = footnoteBlock

    footnoteBlock.foreach { fb =>
      footnoteRepository.addFootnoteReference(fb, footnote)
    }
    footnote
  }

  override def adjustInlineText(document: Document, node: Node): BasedSequence = {
    assert(node.isInstanceOf[Footnote])
    node.asInstanceOf[Footnote].text
  }

  override def allowDelimiters(chars: BasedSequence, document: Document, node: Node): Boolean = true

  override def updateNodeElements(document: Document, node: Node): Unit = {}
}

object FootnoteLinkRefProcessor {

  val WANT_EXCLAMATION_PREFIX: Boolean = false
  val BRACKET_NESTING_LEVEL: Int = 0

  class Factory extends LinkRefProcessorFactory {

    override def apply(document: Document): LinkRefProcessor = new FootnoteLinkRefProcessor(document)

    override def getWantExclamationPrefix(options: DataHolder): Boolean = WANT_EXCLAMATION_PREFIX

    override def getBracketNestingLevel(options: DataHolder): Int = BRACKET_NESTING_LEVEL
  }
}
