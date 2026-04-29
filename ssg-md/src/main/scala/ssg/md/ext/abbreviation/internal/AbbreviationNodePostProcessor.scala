/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationNodePostProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.Nullable
import ssg.md.ast.{ Text, TextBase }
import ssg.md.ext.autolink.internal.AutolinkNodePostProcessor
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ DoNotDecorate, DoNotLinkDecorate, Document, Node, NodeTracker }
import ssg.md.util.sequence.{ BasedSequence, Escaping, RegexCompat, ReplacedTextMapper }

import java.{ util => ju }
import java.util.regex.Pattern
import scala.language.implicitConversions

class AbbreviationNodePostProcessor private (document: Document) extends NodePostProcessor {

  private var abbreviations:   Nullable[Pattern]                           = Nullable.empty
  private var abbreviationMap: Nullable[ju.HashMap[String, BasedSequence]] = Nullable.empty

  computeAbbreviations(document)

  private def computeAbbreviations(document: Document): Unit = {
    val abbrRepository = AbbreviationExtension.ABBREVIATIONS.get(document)

    if (!abbrRepository.isEmpty) {
      val abbrMap = new ju.HashMap[String, BasedSequence]()
      val sb      = new StringBuilder()

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

              // Cross-platform: no \b, no \Q..\E, no lookaheads — boundary
              // checked programmatically in process() below.
              // Original: \b + Pattern.quote(abbr) + \b with UNICODE_CHARACTER_CLASS
              // Revert when scala-native#4810 ships with full java.util.regex support.
              sb.append(RegexCompat.regexEscape(abbr))
            }
          }
        }
      }

      if (sb.nonEmpty) {
        // Cross-platform: Pattern.UNICODE_CHARACTER_CLASS is not supported on Scala Native re2.
        // The flag makes \b use Unicode word boundaries. Without it, \b uses ASCII word boundaries
        // which is sufficient for typical abbreviation matching.
        // Original: Pattern.compile(sb.toString, Pattern.UNICODE_CHARACTER_CLASS)
        // Revert when scala-native#4810 ships with full java.util.regex support.
        abbreviations = Nullable(Pattern.compile(sb.toString))
        abbreviationMap = Nullable(abbrMap)
      }
    }
  }

  override def process(state: NodeTracker, node: Node): Unit =
    abbreviations.foreach { abbrPattern =>
      abbreviationMap.foreach { abbrMap =>
        val original   = node.chars
        val textMapper = new ReplacedTextMapper(original)
        val literal    = Escaping.unescape(original, textMapper)

        val m              = abbrPattern.matcher(literal)
        var lastEscaped    = 0
        var wrapInTextBase = !node.parent.exists(_.isInstanceOf[TextBase])
        var textBase: Nullable[TextBase] = if (wrapInTextBase) Nullable.empty else node.parent.map(_.asInstanceOf[TextBase])

        while (m.find()) {
          // Cross-platform word boundary check: since we can't use \b or lookaheads
          // in the regex (unavailable on Scala Native re2), check boundaries in code.
          // A word boundary exists if the char before/after the match is not a letter/digit.
          val matchStart        = m.start(0)
          val matchEnd          = m.end(0)
          val matched           = m.group(0)
          val needStartBoundary = matched.nonEmpty && Character.isLetterOrDigit(matched.charAt(0))
          val needEndBoundary   = matched.nonEmpty && Character.isLetterOrDigit(matched.charAt(matched.length - 1))
          val startOk           = !needStartBoundary || matchStart == 0 || !Character.isLetterOrDigit(literal.charAt(matchStart - 1))
          val endOk             = !needEndBoundary || matchEnd >= literal.length() || !Character.isLetterOrDigit(literal.charAt(matchEnd))
          if (!startOk || !endOk) {
            // Not at a word boundary — skip this match
          } else {
            val abbreviation = abbrMap.get(m.group(0))
            if (abbreviation != null) { // @nowarn - Java interop: map get may return null
              val startOffset = textMapper.originalOffset(m.start(0))
              val endOffset   = textMapper.originalOffset(m.end(0))

              if (wrapInTextBase) {
                wrapInTextBase = false
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
              val decorationNode     = new Abbreviation(origToDecorateText, abbreviation)
              textBase.foreach(_.appendChild(decorationNode))
              state.nodeAdded(decorationNode)

              lastEscaped = endOffset
            }
          } // else: boundary check passed
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
}

object AbbreviationNodePostProcessor {

  class Factory extends NodePostProcessorFactory(false) {
    addNodeWithExclusions(classOf[Text], classOf[DoNotDecorate], classOf[DoNotLinkDecorate])

    override def afterDependents: Nullable[Set[Class[?]]] =
      Nullable(Set[Class[?]](classOf[AutolinkNodePostProcessor.Factory]))

    override def apply(document: Document): NodePostProcessor = new AbbreviationNodePostProcessor(document)
  }
}
