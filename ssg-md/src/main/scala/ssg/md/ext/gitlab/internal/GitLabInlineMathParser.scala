/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabInlineMathParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }

import java.util.regex.Pattern
import scala.language.implicitConversions

class GitLabInlineMathParser(inlineParser: LightInlineParser) extends InlineParserExtension {

  private val MATH_PATTERN: Pattern = Pattern.compile("\\$`((?:.|\\n)*?)`\\$")

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean =
    if (inlineParser.peek(1) == '`') {
      val input         = inlineParser.input
      val matcherResult = inlineParser.matcher(MATH_PATTERN)
      if (matcherResult.isDefined) {
        val matcher = matcherResult.get
        inlineParser.flushTextNode()

        val mathOpen   = input.subSequence(matcher.start(), matcher.start(1))
        val mathClosed = input.subSequence(matcher.end(1), matcher.end())
        val inlineMath = new GitLabInlineMath(mathOpen, mathOpen.baseSubSequence(mathOpen.endOffset, mathClosed.startOffset), mathClosed)
        inlineParser.block.appendChild(inlineMath)
        true
      } else {
        false
      }
    } else {
      false
    }
}

object GitLabInlineMathParser {

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "$"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension = new GitLabInlineMathParser(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
