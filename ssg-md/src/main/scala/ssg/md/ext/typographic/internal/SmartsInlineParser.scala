/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/SmartsInlineParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/SmartsInlineParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package typographic
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class SmartsInlineParser(inlineParser: LightInlineParser) extends InlineParserExtension {

  private val parsing: SmartsParsing = new SmartsParsing(inlineParser.parsing)

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = {
    // hard coding implementation because pattern matching can be very slow for large files
    val input = inlineParser.input
    var typographicSmarts: Nullable[String]        = Nullable.empty
    var matched:           Nullable[BasedSequence] = Nullable.empty

    val i = inlineParser.index
    val c = input.charAt(i)

    if (c == '.') {
      if (input.matchChars(parsing.ELIPSIS, i)) {
        matched = Nullable(input.subSequence(i, i + parsing.ELIPSIS.length))
        typographicSmarts = Nullable("&hellip;")
      } else if (input.matchChars(parsing.ELIPSIS_SPACED, i)) {
        matched = Nullable(input.subSequence(i, i + parsing.ELIPSIS_SPACED.length))
        typographicSmarts = Nullable("&hellip;")
      }
    } else if (c == '-') {
      if (input.matchChars(parsing.EM_DASH, i)) {
        matched = Nullable(input.subSequence(i, i + parsing.EM_DASH.length))
        typographicSmarts = Nullable("&mdash;")
      } else if (input.matchChars(parsing.EN_DASH, i)) {
        matched = Nullable(input.subSequence(i, i + parsing.EN_DASH.length))
        typographicSmarts = Nullable("&ndash;")
      }
    }

    matched.fold(false) { m =>
      inlineParser.flushTextNode()
      inlineParser.index = i + m.length()
      val smarts = new TypographicSmarts(m, typographicSmarts.get)
      inlineParser.block.appendChild(smarts)
      true
    }
  }
}

object SmartsInlineParser {

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = ".-"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension =
      new SmartsInlineParser(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
