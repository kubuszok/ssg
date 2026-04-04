/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/MarkdownWriter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.ast.{ BlankLine, BlockQuoteLike, Document, Node }
import ssg.md.util.format.MarkdownWriterBase
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class MarkdownWriter(appendable: Nullable[Appendable], formatOptions: Int) extends MarkdownWriterBase[MarkdownWriter, Node, NodeFormatterContext](appendable, formatOptions) {

  def this() =
    this(Nullable.empty, 0)

  def this(formatOptions: Int) =
    this(Nullable.empty, formatOptions)

  override def getEmptyAppendable: MarkdownWriter =
    new MarkdownWriter(Nullable.empty, this.getOptions)

  def appendNonTranslating(csq: CharSequence): MarkdownWriter =
    appendNonTranslating(Nullable.empty, csq, Nullable.empty, Nullable.empty)

  def appendNonTranslating(prefix: CharSequence, csq: CharSequence, suffix: CharSequence): MarkdownWriter =
    appendNonTranslating(Nullable(prefix), csq, Nullable(suffix), Nullable.empty)

  def appendNonTranslating(prefix: CharSequence, csq: CharSequence, suffix: CharSequence, suffix2: CharSequence): MarkdownWriter =
    appendNonTranslating(Nullable(prefix), csq, Nullable(suffix), Nullable(suffix2))

  def appendNonTranslating(prefix: Nullable[CharSequence], csq: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): MarkdownWriter = {
    if (context.isTransformingText) {
      append(context.transformNonTranslating(prefix, csq, suffix, suffix2))
    } else {
      append(csq)
    }
    this
  }

  def appendTranslating(csq: CharSequence): MarkdownWriter =
    appendTranslating(Nullable.empty, csq, Nullable.empty, Nullable.empty)

  def appendTranslating(prefix: CharSequence, csq: CharSequence, suffix: CharSequence): MarkdownWriter =
    appendTranslating(Nullable(prefix), csq, Nullable(suffix), Nullable.empty)

  def appendTranslating(prefix: CharSequence, csq: CharSequence, suffix: CharSequence, suffix2: CharSequence): MarkdownWriter =
    appendTranslating(Nullable(prefix), csq, Nullable(suffix), Nullable(suffix2))

  def appendTranslating(prefix: Nullable[CharSequence], csq: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): MarkdownWriter = {
    if (context.isTransformingText) {
      append(context.transformTranslating(prefix, csq, suffix, suffix2))
    } else {
      prefix.foreach(p => append(p))
      append(csq)
      suffix.foreach(s => append(s))
      suffix2.foreach(s => append(s))
    }
    this
  }

  override def lastBlockQuoteChildPrefix(prefix: BasedSequence): BasedSequence = {
    var node: Nullable[Node] = Nullable(context.getCurrentNode)
    while (node.isDefined && node.get.nextAnyNot(classOf[BlankLine]).isEmpty) {
      val parent = node.get.parent
      if (parent.isEmpty || parent.exists(_.isInstanceOf[Document])) {
        // reached top
        node = Nullable.empty
      } else if (parent.exists(_.isInstanceOf[BlockQuoteLike])) {
        val pos = prefix.lastIndexOfAny(context.getBlockQuoteLikePrefixPredicate)
        if (pos >= 0) {
          prefix.replace(pos, pos + 1, " ") // TODO: use replaced value when nested block quotes are handled
          // keep going up in case there are nested block quotes
          node = parent
          // use recursive approach: return replaced as the result
          // For now, just return replaced
          // TODO: handle nested block quotes properly
        } else {
          node = Nullable.empty
        }
      } else {
        node = parent
      }
    }
    prefix
  }
}
