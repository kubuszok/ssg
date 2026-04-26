/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagInlineParserExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package jekyll
package tag
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }

import scala.language.implicitConversions

class JekyllTagInlineParserExtension(lightInlineParser: LightInlineParser) extends InlineParserExtension {

  private val parsing:          JekyllTagParsing = new JekyllTagParsing(lightInlineParser.parsing)
  private val listIncludesOnly: Boolean          = JekyllTagExtension.LIST_INCLUDES_ONLY.get(lightInlineParser.document)

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean =
    if (inlineParser.peek(1) == '%' && (inlineParser.peek(2) == ' ' || inlineParser.peek(2) == '\t')) {
      val input      = inlineParser.input
      val matcherOpt = inlineParser.matcher(parsing.MACRO_TAG)
      if (matcherOpt.isDefined) {
        val matcher    = matcherOpt.get
        val tagSeq     = input.subSequence(matcher.start(), matcher.end())
        val tagName    = input.subSequence(matcher.start(1), matcher.end(1))
        val parameters = input.subSequence(matcher.end(1), matcher.end() - 2).trim()
        val macro_     = new JekyllTag(tagSeq.subSequence(0, 2), tagName, parameters, tagSeq.endSequence(2))
        macro_.setCharsFromContent()

        if (!listIncludesOnly || tagName.equals(JekyllTagBlockParser.INCLUDE_TAG)) {
          val tagList = JekyllTagExtension.TAG_LIST.get(inlineParser.document)
          tagList.add(macro_)
        }

        inlineParser.flushTextNode()
        inlineParser.block.appendChild(macro_)
        true
      } else {
        false
      }
    } else {
      false
    }
}

object JekyllTagInlineParserExtension {

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "{"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension =
      new JekyllTagInlineParserExtension(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
