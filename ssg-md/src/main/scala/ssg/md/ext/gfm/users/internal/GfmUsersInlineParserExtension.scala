/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/internal/GfmUsersInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/internal/GfmUsersInlineParserExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gfm
package users
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }

import scala.language.implicitConversions
import java.util.regex.Pattern

class GfmUsersInlineParserExtension(inlineParser: LightInlineParser) extends InlineParserExtension {

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = {
    val index      = inlineParser.index
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
        val text       = matches(2)

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
  // Cross-platform: original Java regex used lookahead (?=[a-z\\d]) inside the
  // repetition to ensure each dash is followed by an alphanumeric character.
  // Lookaheads are unavailable on Scala.js and Scala Native. Rewritten to
  // [a-z\\d](?:[a-z\\d-]{0,37}[a-z\\d])? which prevents leading/trailing dashes
  // and limits total length to 39 chars. This is slightly more permissive than
  // the original (allows consecutive dashes) but matches GitHub's actual rules.
  // Original: "^(@)([a-z\\d](?:[a-z\\d]|-(?=[a-z\\d])){0,38})\\b"
  // Revert to original if/when Scala.js and Scala Native add full java.util.regex support.
  val GITHUB_USER: Pattern = Pattern.compile("^(@)([a-z\\d](?:[a-z\\d-]{0,37}[a-z\\d])?)\\b", Pattern.CASE_INSENSITIVE)

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "@"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension =
      new GfmUsersInlineParserExtension(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
