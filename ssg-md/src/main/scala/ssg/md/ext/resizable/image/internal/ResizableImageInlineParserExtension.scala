/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/internal/ResizableImageInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/internal/ResizableImageInlineParserExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package resizable
package image
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }

import scala.language.implicitConversions
import java.util.regex.Pattern

class ResizableImageInlineParserExtension(inlineParser: LightInlineParser) extends InlineParserExtension {

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = {
    val index = inlineParser.index
    val c     = inlineParser.input.safeCharAt(index + 1)
    if (c == '[') {
      val matchesOpt = inlineParser.matchWithGroups(ResizableImageInlineParserExtension.IMAGE_PATTERN)
      if (matchesOpt.isDefined) {
        val matches = matchesOpt.get
        inlineParser.flushTextNode()

        val text   = matches(1)
        val source = matches(2)
        val width  = matches(3)
        val height = matches(4)

        val image = new ResizableImage(text, source, width, height)
        inlineParser.block.appendChild(image)
        true
      } else {
        false
      }
    } else {
      false
    }
  }
}

object ResizableImageInlineParserExtension {
  val IMAGE_PATTERN: Pattern = Pattern.compile(
    "\\!\\[([^\\s\\]]*)]\\(([^\\s\\]]+)\\s*=*(\\d*)x*(\\d*)\\)",
    Pattern.CASE_INSENSITIVE
  )

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "!"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension =
      new ResizableImageInlineParserExtension(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
