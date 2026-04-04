/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacrosInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }

import scala.language.implicitConversions
import java.util.regex.Pattern

class MacrosInlineParserExtension(inlineParser: LightInlineParser) extends InlineParserExtension {

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = {
    val pattern  = if (inlineParser.parsing.intellijDummyIdentifier) MacrosInlineParserExtension.MACRO_REFERENCE_INTELLIJ else MacrosInlineParserExtension.MACRO_REFERENCE
    val matchOpt = inlineParser.`match`(pattern)

    if (matchOpt.isDefined) {
      val matched = matchOpt.get
      val name    = matched.midSequence(3, -3)
      val macro_  = new MacroReference(matched.subSequence(0, 3), name, matched.midSequence(-3))
      inlineParser.flushTextNode()
      inlineParser.block.appendChild(macro_)
      true
    } else {
      false
    }
  }
}

object MacrosInlineParserExtension {
  val MACRO_REFERENCE:          Pattern = Pattern.compile("<<<([\\w_-]+)>>>")
  val MACRO_REFERENCE_INTELLIJ: Pattern = Pattern.compile("<<<([\u001f\\w_-]+)>>>")

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "<"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension =
      new MacrosInlineParserExtension(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
