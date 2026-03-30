/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/internal/GfmUsersInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package users
package internal

import ssg.md.Nullable
import ssg.md.parser.{InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser}

import scala.language.implicitConversions
import java.util.regex.Pattern

class GfmUsersInlineParserExtension(inlineParser: LightInlineParser) extends InlineParserExtension {

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = {
    val index = inlineParser.index
    var isPossible = index == 0
    if (!isPossible) {
      val c = inlineParser.input.charAt(index - 1)
      if (!Character.isUnicodeIdentifierPart(c) && c != '-' && c != '.') {
        isPossible = true
      }
    }
    if (isPossible) {
      val matchResult = inlineParser.matchWithGroups(GfmUsersInlineParserExtension.GITHUB_USER)
      if (matchResult.isDefined) {
        val matches = matchResult.get
        inlineParser.flushTextNode()

        val openMarker = matches(1)
        val text = matches(2)

        val gitHubUser = new GfmUser(openMarker, text)
        inlineParser.block.appendChild(gitHubUser)
        true
      } else {
        false
      }
    } else {
      false
    }
  }
}

object GfmUsersInlineParserExtension {
  val GITHUB_USER: Pattern = Pattern.compile("^(@)([a-z\\d](?:[a-z\\d]|-(?=[a-z\\d])){0,38})\\b", Pattern.CASE_INSENSITIVE)

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "@"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension = {
      new GfmUsersInlineParserExtension(lightInlineParser)
    }

    override def affectsGlobalScope: Boolean = false
  }
}
