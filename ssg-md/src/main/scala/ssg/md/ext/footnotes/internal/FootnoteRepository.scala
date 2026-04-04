/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.Nullable
import ssg.md.util.ast.{ Document, KeepType, Node, NodeRepository, NodeVisitor, VisitHandler }
import ssg.md.util.data.{ DataHolder, DataKey }

import java.{ util => ju }
import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

class FootnoteRepository(options: Nullable[DataHolder]) extends NodeRepository[FootnoteBlock](options.map(FootnoteExtension.FOOTNOTES_KEEP.get(_))) {

  private val referencedFootnoteBlocks: ArrayBuffer[FootnoteBlock] = ArrayBuffer.empty

  override def dataKey: DataKey[FootnoteRepository] = FootnoteExtension.FOOTNOTES

  override def keepDataKey: DataKey[KeepType] = FootnoteExtension.FOOTNOTES_KEEP

  override def getReferencedElements(parent: Node): ju.Set[FootnoteBlock] = {
    val references = new ju.HashSet[FootnoteBlock]()
    visitNodes(
      parent,
      value =>
        value match {
          case fn: Footnote =>
            val reference = fn.getReferenceNode(FootnoteRepository.this)
            if (reference != null) { // @nowarn - Java interop: getReferenceNode may return null
              references.add(reference)
            }
          case _ => ()
        },
      classOf[Footnote]
    )
    references
  }

  def addFootnoteReference(footnoteBlock: FootnoteBlock, footnote: Footnote): Unit = {
    if (!footnoteBlock.isReferenced) {
      referencedFootnoteBlocks += footnoteBlock
    }

    footnoteBlock.firstReferenceOffset = footnote.startOffset

    val referenceOrdinal = footnoteBlock.footnoteReferences
    footnoteBlock.footnoteReferences = referenceOrdinal + 1
    footnote.referenceOrdinal = referenceOrdinal
  }

  def resolveFootnoteOrdinals(): Unit = {
    // need to sort by first referenced offset then set each to its ordinal position in the array+1
    val sorted = referencedFootnoteBlocks.sortBy(_.firstReferenceOffset)
    referencedFootnoteBlocks.clear()
    referencedFootnoteBlocks ++= sorted

    var ordinal = 0
    for (footnoteBlock <- referencedFootnoteBlocks) {
      ordinal += 1
      footnoteBlock.footnoteOrdinal = ordinal
    }
  }

  def getReferencedFootnoteBlocks: ju.List[FootnoteBlock] = {
    val list = new ju.ArrayList[FootnoteBlock](referencedFootnoteBlocks.size)
    referencedFootnoteBlocks.foreach(list.add)
    list
  }
}

object FootnoteRepository {

  def resolveFootnotes(document: Document): Unit = {
    val footnoteRepository = FootnoteExtension.FOOTNOTES.get(document)

    var hadNewFootnotes = false
    val visitor         = new NodeVisitor(
      new VisitHandler[Footnote](
        classOf[Footnote],
        node =>
          if (!node.isDefined) {
            val footonoteBlock = node.getFootnoteBlock(footnoteRepository)

            if (footonoteBlock != null) { // @nowarn - Java interop: may be null
              footnoteRepository.addFootnoteReference(footonoteBlock, node)
              node.footnoteBlock = Nullable(footonoteBlock)
              hadNewFootnotes = true
            }
          }
      )
    )

    visitor.visit(document)
    if (hadNewFootnotes) {
      footnoteRepository.resolveFootnoteOrdinals()
    }
  }
}
