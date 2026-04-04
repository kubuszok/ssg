/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/internal/EscapedCharacterNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package escaped
package character
package internal

import ssg.md.Nullable
import ssg.md.ast.{ Text, TextBase }
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ DoNotDecorate, Document, Node, NodeTracker }
import ssg.md.util.sequence.{ Escaping, ReplacedTextMapper }

import scala.language.implicitConversions

class EscapedCharacterNodePostProcessor(document: Document) extends NodePostProcessor {

  override def process(state: NodeTracker, node: Node): Unit = {
    val original   = node.chars
    val textMapper = new ReplacedTextMapper(original)
    // NOTE: needed for its side-effects
    Escaping.unescape(original, textMapper)

    var lastEscaped    = 0
    val wrapInTextBase = !node.parent.exists(_.isInstanceOf[TextBase])
    var textBase: Nullable[TextBase] = if (wrapInTextBase) Nullable.empty else Nullable(node.parent.get.asInstanceOf[TextBase])

    val replacedRegions = textMapper.getRegions

    val iter = replacedRegions.iterator()
    while (iter.hasNext) {
      val region      = iter.next()
      val startOffset = region.originalRange.start
      val endOffset   = region.originalRange.end

      if (
        original.charAt(startOffset) == '\\' && region.replacedRange.span == 1
        // fix for #19, ArrayIndexOutOfBounds while parsing markdown with backslash as last character of text block
        && startOffset + 1 < original.length()
      ) {
        if (wrapInTextBase && textBase.isEmpty) {
          val tb = new TextBase(original)
          node.insertBefore(tb)
          state.nodeAdded(tb)
          textBase = Nullable(tb)
        }

        if (startOffset != lastEscaped) {
          val escapedChars = original.subSequence(lastEscaped, startOffset)
          val node1        = new Text(escapedChars)
          textBase.foreach(_.appendChild(node1))
          state.nodeAdded(node1)
        }

        val origToDecorateText = original.subSequence(startOffset, endOffset)
        val text               = origToDecorateText.subSequence(1)
        val decorationNode     = new EscapedCharacter(origToDecorateText.subSequence(0, 1), text)
        textBase.foreach(_.appendChild(decorationNode))
        state.nodeAdded(decorationNode)

        lastEscaped = endOffset
      }
    }

    if (lastEscaped > 0) {
      if (lastEscaped != original.length()) {
        val escapedChars = original.subSequence(lastEscaped, original.length())
        val node1        = new Text(escapedChars)
        textBase.foreach(_.appendChild(node1))
        state.nodeAdded(node1)
      }

      node.unlink()
      state.nodeRemoved(node)
    }
  }
}

object EscapedCharacterNodePostProcessor {

  class Factory extends NodePostProcessorFactory(false) {
    addNodeWithExclusions(classOf[Text], classOf[DoNotDecorate])

    override def apply(document: Document): NodePostProcessor =
      new EscapedCharacterNodePostProcessor(document)
  }
}
