/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabInlineParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabInlineParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gitlab
package internal

import ssg.md.Nullable
import ssg.md.ast.Text
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }
import ssg.md.util.sequence.BasedSequence

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

class GitLabInlineParser(inlineParser: LightInlineParser) extends InlineParserExtension {

  private val openInlines: ArrayBuffer[GitLabInline] = ArrayBuffer.empty
  private val options:     GitLabOptions             = new GitLabOptions(inlineParser.document)

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {
    // convert any unclosed ones to text
    var j = openInlines.size
    while (j > 0) {
      j -= 1
      val gitLabInline = openInlines(j)
      val textNode     = new Text(gitLabInline.chars)
      gitLabInline.insertBefore(textNode)
      gitLabInline.unlink()
    }
    openInlines.clear()
  }

  override def parse(inlineParser: LightInlineParser): Boolean = {
    val firstChar  = inlineParser.peek()
    val secondChar = inlineParser.peek(1)
    if ((firstChar == '{' || firstChar == '[') && (options.insParser && secondChar == '+' || options.delParser && secondChar == '-')) {
      // possible open, if matched close
      val input = inlineParser.input.subSequence(inlineParser.index)

      val open: GitLabInline = if (secondChar == '+') new GitLabIns(input.subSequence(0, 2)) else new GitLabDel(input.subSequence(0, 2))
      inlineParser.flushTextNode()
      inlineParser.block.appendChild(open)
      openInlines += open
      inlineParser.index = inlineParser.index + 2
      true
    } else if ((options.insParser && firstChar == '+' || options.delParser && firstChar == '-') && (secondChar == ']' || secondChar == '}')) {
      // possible closed, if matches open
      val input = inlineParser.input.subSequence(inlineParser.index)
      val matchOpen: CharSequence = if (secondChar == ']') if (firstChar == '+') "[+" else "[-" else if (firstChar == '+') "{+" else "{-"
      val matchOpenSeq = BasedSequence.of(matchOpen)

      var i     = openInlines.size
      var found = false
      while (i > 0 && !found) {
        i -= 1
        val open       = openInlines(i)
        val openMarker = open.chars
        if (openMarker.equals(matchOpenSeq)) {
          // this one is now closed, we remove all intervening ones since they did not match
          inlineParser.index = inlineParser.index + 2
          val closingMarker = input.subSequence(0, 2)
          open.openingMarker = openMarker
          open.closingMarker = closingMarker
          open.text = openMarker.baseSubSequence(openMarker.endOffset, closingMarker.startOffset)

          inlineParser.flushTextNode()
          val last = inlineParser.block.lastChild
          last.foreach(inlineParser.moveNodes(open, _))

          if (i == 0) {
            openInlines.clear()
          } else {
            openInlines.remove(i, openInlines.size - i)
          }
          found = true
        }
      }
      found
    } else {
      false
    }
  }
}

object GitLabInlineParser {

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "{[-+"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension = new GitLabInlineParser(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
