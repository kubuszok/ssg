/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/MacroReference.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package macros

import ssg.md.Nullable
import ssg.md.ext.macros.internal.MacroDefinitionRepository
import ssg.md.util.ast.{DelimitedNode, DoNotDecorate, Document, Node, ReferencingNode}
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/** A MacroReference node */
class MacroReference() extends Node, DelimitedNode, DoNotDecorate, ReferencingNode[MacroDefinitionRepository, MacroDefinitionBlock] {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL
  private var myMacroDefinitionBlock: Nullable[MacroDefinitionBlock] = Nullable.empty

  override def isDefined: Boolean = myMacroDefinitionBlock.isDefined

  override def reference: BasedSequence = text

  override def getReferenceNode(document: Document): MacroDefinitionBlock = {
    if (myMacroDefinitionBlock.isDefined || text.isEmpty) myMacroDefinitionBlock.getOrElse(null).asInstanceOf[MacroDefinitionBlock] // @nowarn - Java interop: may return null
    else {
      myMacroDefinitionBlock = Nullable(getMacroDefinitionBlock(MacrosExtension.MACRO_DEFINITIONS.get(document)))
      myMacroDefinitionBlock.getOrElse(null).asInstanceOf[MacroDefinitionBlock] // @nowarn - Java interop: may return null
    }
  }

  override def getReferenceNode(repository: MacroDefinitionRepository): MacroDefinitionBlock = {
    if (myMacroDefinitionBlock.isDefined || text.isEmpty) myMacroDefinitionBlock.getOrElse(null).asInstanceOf[MacroDefinitionBlock] // @nowarn - Java interop: may return null
    else {
      myMacroDefinitionBlock = Nullable(getMacroDefinitionBlock(repository))
      myMacroDefinitionBlock.getOrElse(null).asInstanceOf[MacroDefinitionBlock] // @nowarn - Java interop: may return null
    }
  }

  def getMacroDefinitionBlock(repository: MacroDefinitionRepository): MacroDefinitionBlock = {
    if (text.isEmpty) null.asInstanceOf[MacroDefinitionBlock] // @nowarn - Java interop boundary with NodeRepository.get
    else repository.get(text.toString)
  }

  def macroDefinitionBlock: Nullable[MacroDefinitionBlock] = myMacroDefinitionBlock

  def macroDefinitionBlock_=(block: MacroDefinitionBlock): Unit = {
    this.myMacroDefinitionBlock = Nullable(block)
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")
  }

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = text
    this.closingMarker = closingMarker
  }
}
