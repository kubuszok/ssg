/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceParagraphPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.parser.block.{ ParagraphPreProcessor, ParagraphPreProcessorFactory, ParserState }
import ssg.md.parser.core.ReferencePreProcessorFactory
import ssg.md.util.data.DataHolder

import java.util.regex.Pattern
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

// NOT USED, Parsing is done by EnumeratedReferenceBlockParser,
// otherwise Reference definitions take priority if preceded by reference definition
// because parser processor keeps going after first match
// to get around this need to add ReferenceParagraphPreProcessor set of leading characters
// which make it not a reference id
class EnumeratedReferenceParagraphPreProcessor(options: DataHolder) extends ParagraphPreProcessor {

  @annotation.nowarn("msg=unused private member") // stub: will be used when full processing is complete
  private val options_             = new EnumeratedReferenceOptions(options)
  private val enumeratedReferences = EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(options)

  override def preProcessBlock(block: Paragraph, state: ParserState): Int = {
    val trySequence = block.chars
    val matcher     = EnumeratedReferenceParagraphPreProcessor.ENUM_REF_DEF_PARAGRAPH_PATTERN.matcher(trySequence)
    var lastFound   = 0
    boundary {
      while (matcher.find()) {
        if (matcher.start() != lastFound) {
          break()
        }
        lastFound = matcher.end()

        val openingStart  = matcher.start(1)
        val openingEnd    = matcher.end(1)
        val openingMarker = trySequence.subSequence(openingStart, openingStart + 2)
        val text          = trySequence.subSequence(openingStart + 2, openingEnd - 2).trim()
        val closingMarker = trySequence.subSequence(openingEnd - 2, openingEnd)

        val enumeratedReferenceBlock = new EnumeratedReferenceBlock()
        enumeratedReferenceBlock.openingMarker = openingMarker
        enumeratedReferenceBlock.text = text
        enumeratedReferenceBlock.closingMarker = closingMarker

        val enumeratedReference = trySequence.subSequence(matcher.start(3), matcher.end(3))
        enumeratedReferenceBlock.enumeratedReference = enumeratedReference
        val paragraph = new Paragraph(enumeratedReference)
        enumeratedReferenceBlock.appendChild(paragraph)
        enumeratedReferenceBlock.setCharsFromContent()

        block.insertBefore(enumeratedReferenceBlock)
        state.blockAdded(enumeratedReferenceBlock)

        enumeratedReferences.put(enumeratedReferenceBlock.text.toString, enumeratedReferenceBlock)
      }
    }
    lastFound
  }
}

object EnumeratedReferenceParagraphPreProcessor {
  val ENUM_REF_ID:                    String  = "(?:[^0-9].*)?";
  val ENUM_REF_DEF_PARAGRAPH_PATTERN: Pattern = Pattern.compile("\\s{0,3}(\\[[\\@]\\s*(" + ENUM_REF_ID + ")\\s*\\]:)\\s+(.*\n)")

  def Factory(): ParagraphPreProcessorFactory = new ParagraphPreProcessorFactory {

    override def affectsGlobalScope: Boolean = true

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] =
      Nullable(Set[Class[?]](classOf[ReferencePreProcessorFactory]))

    override def apply(state: ParserState): ParagraphPreProcessor =
      new EnumeratedReferenceParagraphPreProcessor(state.properties)
  }
}
