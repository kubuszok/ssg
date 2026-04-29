/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/internal/GfmIssuesInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/internal/GfmIssuesInlineParserExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gfm
package issues
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }

import scala.language.implicitConversions
import java.util.regex.Pattern

class GfmIssuesInlineParserExtension(inlineParser: LightInlineParser) extends InlineParserExtension {

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = {
    val matchResult = inlineParser.matchWithGroups(GfmIssuesInlineParserExtension.GITHUB_ISSUE)
    if (matchResult.isDefined) {
      val matches = matchResult.get
      inlineParser.flushTextNode()

      val openMarker = matches(1)
      val text       = matches(2)

      val gfmIssue = new GfmIssue(openMarker, text)
      inlineParser.block.appendChild(gfmIssue)
      true
    } else {
      false
    }
  }
}

object GfmIssuesInlineParserExtension {
  val GITHUB_ISSUE: Pattern = Pattern.compile("^(#)(\\d+)\\b")

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "#"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(inlineParser: LightInlineParser): InlineParserExtension =
      new GfmIssuesInlineParserExtension(inlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
