/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.Nullable
import ssg.md.ast.{Text, TextBase}
import ssg.md.ext.autolink.internal.AutolinkNodePostProcessor
import ssg.md.parser.block.{NodePostProcessor, NodePostProcessorFactory}
import ssg.md.util.ast.{DoNotDecorate, DoNotLinkDecorate, Document, Node, NodeTracker}
import ssg.md.util.sequence.{BasedSequence, Escaping, ReplacedTextMapper}

import java.{util => ju}
import java.util.regex.Pattern
import scala.language.implicitConversions

class AbbreviationNodePostProcessor private (document: Document) extends NodePostProcessor {

  private var abbreviations: Nullable[Pattern] = Nullable.empty
  private var abbreviationMap: Nullable[ju.HashMap[String, BasedSequence]] = Nullable.empty

  computeAbbreviations(document)

  private def computeAbbreviations(document: Document): Unit = {
    val abbrRepository = AbbreviationExtension.ABBREVIATIONS.get(document)

    if (!abbrRepository.isEmpty) {
      val abbrMap = new ju.HashMap[String, BasedSequence]()
      val sb = new StringBuilder()

      // sort reverse alphabetical order so longer ones match first. for sdk7
      val abbreviationsList = new ju.ArrayList[String](abbrRepository.keySet())
      abbreviationsList.sort(ju.Comparator.reverseOrder())

      val iter = abbreviationsList.iterator()
      while (iter.hasNext) {
        val abbr = iter.next()
        // Issue #198, test for empty abbr
        if (!abbr.isEmpty) {
          val abbreviationBlock = abbrRepository.get(abbr)
          if (abbreviationBlock != null) { // @nowarn - Java interop: map get may return null
            val abbreviation = abbreviationBlock.abbreviation
            if (!abbreviation.isEmpty) {
              abbrMap.put(abbr, abbreviation)

              if (sb.nonEmpty) sb.append("|")

              if (Character.isLetterOrDigit(abbr.charAt(0))) sb.append("\\b")
              sb.append("\\Q").append(abbr).append("\\E")
              if (Character.isLetterOrDigit(abbr.charAt(abbr.length - 1))) sb.append("\\b")
            }
          }
        }
      }

      if (sb.nonEmpty) {
        abbreviations = Nullable(Pattern.compile(sb.toString, Pattern.UNICODE_CHARACTER_CLASS))
        abbreviationMap = Nullable(abbrMap)
      }
    }
  }

  override def process(state: NodeTracker, node: Node): Unit = {
    abbreviations.foreach { abbrPattern =>
      abbreviationMap.foreach { abbrMap =>
        val original = node.chars
        val textMapper = new ReplacedTextMapper(original)
        val literal = Escaping.unescape(original, textMapper)

        val m = abbrPattern.matcher(literal)
        var lastEscaped = 0
        var wrapInTextBase = !node.parent.exists(_.isInstanceOf[TextBase])
        var textBase: Nullable[TextBase] = if (wrapInTextBase) Nullable.empty else node.parent.map(_.asInstanceOf[TextBase])

        while (m.find()) {
          val abbreviation = abbrMap.get(m.group(0))
          if (abbreviation != null) { // @nowarn - Java interop: map get may return null
            val startOffset = textMapper.originalOffset(m.start(0))
            val endOffset = textMapper.originalOffset(m.end(0))

            if (wrapInTextBase) {
              wrapInTextBase = false
              val tb = new TextBase(original)
              node.insertBefore(tb)
              state.nodeAdded(tb)
              textBase = Nullable(tb)
            }

            if (startOffset != lastEscaped) {
              val escapedChars = original.subSequence(lastEscaped, startOffset)
              val node1 = new Text(escapedChars)
              textBase.foreach(_.appendChild(node1))
              state.nodeAdded(node1)
            }

            val origToDecorateText = original.subSequence(startOffset, endOffset)
            val decorationNode = new Abbreviation(origToDecorateText, abbreviation)
            textBase.foreach(_.appendChild(decorationNode))
            state.nodeAdded(decorationNode)

            lastEscaped = endOffset
          }
        }

        if (lastEscaped > 0) {
          if (lastEscaped != original.length()) {
            val escapedChars = original.subSequence(lastEscaped, original.length())
            val node1 = new Text(escapedChars)
            textBase.foreach(_.appendChild(node1))
            state.nodeAdded(node1)
          }

          node.unlink()
          state.nodeRemoved(node)
        }
      }
    }
  }
}

object AbbreviationNodePostProcessor {

  class Factory extends NodePostProcessorFactory(false) {
    addNodeWithExclusions(classOf[Text], classOf[DoNotDecorate], classOf[DoNotLinkDecorate])

    override def afterDependents: Nullable[Set[Class[?]]] = {
      Nullable(Set[Class[?]](classOf[AutolinkNodePostProcessor.Factory]))
    }

    override def apply(document: Document): NodePostProcessor = new AbbreviationNodePostProcessor(document)
  }
}
