/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkLinkRefProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkLinkRefProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package wikilink
package internal

import ssg.md.parser.{ LinkRefProcessor, LinkRefProcessorFactory }
import ssg.md.util.ast.{ Document, Node, TextCollectingVisitor, TextContainer }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class WikiLinkLinkRefProcessor(document: Document) extends LinkRefProcessor {

  private val options: WikiLinkOptions = new WikiLinkOptions(document)

  override def wantExclamationPrefix: Boolean = options.imageLinks

  override def bracketNestingLevel: Int = WikiLinkLinkRefProcessor.BRACKET_NESTING_LEVEL

  override def isMatch(nodeChars: BasedSequence): Boolean = {
    val length = nodeChars.length()
    if (options.imageLinks) {
      if (length >= 5 && nodeChars.charAt(0) == '!') {
        nodeChars.charAt(1) == '[' && nodeChars.charAt(2) == '[' && nodeChars.endCharAt(1) == ']' && nodeChars.endCharAt(2) == ']'
      } else if (length >= 4) {
        nodeChars.charAt(0) == '[' && nodeChars.charAt(1) == '[' && nodeChars.endCharAt(1) == ']' && nodeChars.endCharAt(2) == ']'
      } else false
    } else if (length >= 4) {
      nodeChars.charAt(0) == '[' && nodeChars.charAt(1) == '[' && nodeChars.endCharAt(1) == ']' && nodeChars.endCharAt(2) == ']'
    } else false
  }

  override def createNode(nodeChars: BasedSequence): Node =
    if (nodeChars.firstChar() == '!') new WikiImage(nodeChars, options.linkFirstSyntax, options.allowPipeEscape)
    else new WikiLink(nodeChars, options.linkFirstSyntax, options.allowAnchors, options.allowPipeEscape, options.allowAnchorEscape)

  override def adjustInlineText(document: Document, node: Node): BasedSequence = {
    assert(node.isInstanceOf[WikiNode])
    val wikiNode = node.asInstanceOf[WikiNode]
    wikiNode.text.ifNull(wikiNode.link)
  }

  override def allowDelimiters(chars: BasedSequence, document: Document, node: Node): Boolean = {
    assert(node.isInstanceOf[WikiNode])
    val wikiNode = node.asInstanceOf[WikiNode]
    node.isInstanceOf[WikiLink] && WikiLinkExtension.ALLOW_INLINES.get(document) && wikiNode.text.ifNull(wikiNode.link).containsAllOf(chars)
  }

  override def updateNodeElements(document: Document, node: Node): Unit = {
    assert(node.isInstanceOf[WikiNode])
    val wikiNode = node.asInstanceOf[WikiNode]
    if (node.isInstanceOf[WikiLink] && WikiLinkExtension.ALLOW_INLINES.get(document)) {
      // need to update link and pageRef with plain text versions
      if (wikiNode.text.isNull) {
        val link = new TextCollectingVisitor().collectAndGetSequence(node, TextContainer.F_NODE_TEXT)
        wikiNode.setLink(link, WikiLinkExtension.ALLOW_ANCHORS.get(document), WikiLinkExtension.ALLOW_ANCHOR_ESCAPE.get(document))
      }
    }
  }
}

object WikiLinkLinkRefProcessor {

  val BRACKET_NESTING_LEVEL: Int = 1

  class Factory extends LinkRefProcessorFactory {

    override def apply(document: Document): LinkRefProcessor = new WikiLinkLinkRefProcessor(document)

    override def getWantExclamationPrefix(options: DataHolder): Boolean = WikiLinkExtension.IMAGE_LINKS.get(options)

    override def getBracketNestingLevel(options: DataHolder): Int = BRACKET_NESTING_LEVEL
  }
}
