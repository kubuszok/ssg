/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesInlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesInlineParserExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.Nullable
import ssg.md.parser.{ InlineParser, InlineParserExtension, InlineParserExtensionFactory, LightInlineParser }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class AttributesInlineParserExtension(inlineParser: LightInlineParser) extends InlineParserExtension {

  private val parsing: AttributeParsing = new AttributeParsing(inlineParser.parsing)

  override def finalizeDocument(inlineParser: InlineParser): Unit = {}

  override def finalizeBlock(inlineParser: InlineParser): Unit = {}

  override def parse(inlineParser: LightInlineParser): Boolean = boundary {
    if (inlineParser.peek(1) != '{') {
      val index       = inlineParser.index
      val input       = inlineParser.input
      val matchResult = inlineParser.matcher(parsing.ATTRIBUTES_TAG)
      if (matchResult.isDefined) {
        val matcher        = matchResult.get
        val attributesOpen = input.subSequence(matcher.start(), matcher.end())

        // see what we have
        // open, see if open/close
        val attributesText = input.subSequence(matcher.start(1), matcher.end(1))
        val attributes: AttributesNode =
          if (attributesText.equals("#") || attributesText.equals(".")) {
            new AttributesDelimiter(attributesOpen.subSequence(0, 1), attributesText, attributesOpen.endSequence(1))
          } else {
            new AttributesNode(attributesOpen.subSequence(0, 1), attributesText, attributesOpen.endSequence(1))
          }

        attributes.setCharsFromContent()

        inlineParser.flushTextNode()
        inlineParser.block.appendChild(attributes)

        val attributeText = attributesText.trim()
        if (!attributeText.isEmpty) {
          // have some attribute text
          // parse attributes
          val attributeMatcher = parsing.ATTRIBUTE.matcher(attributeText)
          while (attributeMatcher.find()) {
            val attributeName = attributeText.subSequence(attributeMatcher.start(1), attributeMatcher.end(1))
            val attributeSeparator: BasedSequence =
              if (attributeMatcher.groupCount() == 1 || attributeMatcher.start(2) == -1) BasedSequence.NULL
              else attributeText.subSequence(attributeMatcher.end(1), attributeMatcher.start(2)).trim()
            val attributeValueRaw: BasedSequence =
              if (attributeMatcher.groupCount() == 1 || attributeMatcher.start(2) == -1) BasedSequence.NULL
              else attributeText.subSequence(attributeMatcher.start(2), attributeMatcher.end(2))
            val isQuoted = attributeValueRaw.length() >= 2 && (
              (attributeValueRaw.charAt(0) == '"' && attributeValueRaw.endCharAt(1) == '"') ||
                (attributeValueRaw.charAt(0) == '\'' && attributeValueRaw.endCharAt(1) == '\'')
            )
            val attributeOpen:  BasedSequence = if (!isQuoted) BasedSequence.NULL else attributeValueRaw.subSequence(0, 1)
            val attributeClose: BasedSequence = if (!isQuoted) BasedSequence.NULL else attributeValueRaw.endSequence(1, 0)

            val attributeValue: BasedSequence = if (isQuoted) attributeValueRaw.midSequence(1, -1) else attributeValueRaw

            val attribute: AttributeNode =
              if ((attributeSeparator eq BasedSequence.NULL) && (attributeValue eq BasedSequence.NULL) && AttributeNode.isImplicitName(attributeName)) {
                new AttributeNode(attributeName.subSequence(0, 1), attributeSeparator, attributeOpen, attributeName.subSequence(1), attributeClose)
              } else {
                new AttributeNode(attributeName, attributeSeparator, attributeOpen, attributeValue, attributeClose)
              }
            attributes.appendChild(attribute)
          }

          break(true)
        }

        // did not process, reset to where we started
        inlineParser.index = index
      }
    }
    false
  }
}

object AttributesInlineParserExtension {

  class Factory extends InlineParserExtensionFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getCharacters: CharSequence = "{"

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(lightInlineParser: LightInlineParser): InlineParserExtension = new AttributesInlineParserExtension(lightInlineParser)

    override def affectsGlobalScope: Boolean = false
  }
}
