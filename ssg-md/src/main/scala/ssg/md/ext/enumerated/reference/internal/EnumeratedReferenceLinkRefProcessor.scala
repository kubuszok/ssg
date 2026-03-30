/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceLinkRefProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.parser.{LinkRefProcessor, LinkRefProcessorFactory}
import ssg.md.util.ast.{Document, Node}
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class EnumeratedReferenceLinkRefProcessor(document: Document) extends LinkRefProcessor {

  private val enumeratedReferenceRepository: EnumeratedReferenceRepository = EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(document)

  override def wantExclamationPrefix: Boolean = EnumeratedReferenceLinkRefProcessor.WANT_EXCLAMATION_PREFIX

  override def bracketNestingLevel: Int = EnumeratedReferenceLinkRefProcessor.BRACKET_NESTING_LEVEL

  override def isMatch(nodeChars: BasedSequence): Boolean = {
    nodeChars.length() >= 3 && nodeChars.charAt(0) == '[' && (nodeChars.charAt(1) == '@' || nodeChars.charAt(1) == '#') && nodeChars.endCharAt(1) == ']' && (nodeChars.length() == 3 || !Character.isDigit(nodeChars.charAt(2)))
  }

  override def createNode(nodeChars: BasedSequence): Node = {
    val enumeratedReferenceId = nodeChars.midSequence(2, -1).trim()
    val enumeratedReferenceBlock: EnumeratedReferenceBlock =
      if (enumeratedReferenceId.length() > 0) enumeratedReferenceRepository.get(enumeratedReferenceId.toString)
      else null // @nowarn - Java interop: repository.get may return null

    if (nodeChars.charAt(1) == '@') {
      // reference link
      val enumeratedReference = new EnumeratedReferenceLink(nodeChars.subSequence(0, 2), enumeratedReferenceId, nodeChars.endSequence(1))
      enumeratedReference.enumeratedReferenceBlock = enumeratedReferenceBlock
      enumeratedReference
    } else {
      // reference text
      val enumeratedReferenceText = new EnumeratedReferenceText(nodeChars.subSequence(0, 2), enumeratedReferenceId, nodeChars.endSequence(1))
      enumeratedReferenceText.enumeratedReferenceBlock = enumeratedReferenceBlock
      enumeratedReferenceText
    }
  }

  override def adjustInlineText(document: Document, node: Node): BasedSequence = {
    assert(node.isInstanceOf[EnumeratedReferenceBase])
    node.asInstanceOf[EnumeratedReferenceBase].text
  }

  override def allowDelimiters(chars: BasedSequence, document: Document, node: Node): Boolean = true

  override def updateNodeElements(document: Document, node: Node): Unit = {}
}

object EnumeratedReferenceLinkRefProcessor {

  val WANT_EXCLAMATION_PREFIX: Boolean = false
  val BRACKET_NESTING_LEVEL: Int = 0

  class Factory extends LinkRefProcessorFactory {

    override def apply(document: Document): LinkRefProcessor = new EnumeratedReferenceLinkRefProcessor(document)

    override def getWantExclamationPrefix(options: DataHolder): Boolean = WANT_EXCLAMATION_PREFIX

    override def getBracketNestingLevel(options: DataHolder): Int = BRACKET_NESTING_LEVEL
  }
}
