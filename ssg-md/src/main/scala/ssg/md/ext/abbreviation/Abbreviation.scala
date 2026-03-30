/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/Abbreviation.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation

import ssg.md.Nullable
import ssg.md.ext.abbreviation.internal.AbbreviationRepository
import ssg.md.util.ast.{DoNotDecorate, DoNotLinkDecorate, Document, Node, ReferencingNode}
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/**
 * A node containing the abbreviated text that will be rendered as an abbr tag or a link with title attribute
 */
class Abbreviation(chars: BasedSequence, val abbreviation: BasedSequence)
    extends Node(chars) with DoNotDecorate with DoNotLinkDecorate with ReferencingNode[AbbreviationRepository, AbbreviationBlock] {

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit = {
    astExtraChars(out)
  }

  override protected def toStringAttributes: String = "text=" + this.chars

  override def isDefined: Boolean = true

  override def reference: BasedSequence = abbreviation

  override def getReferenceNode(document: Document): AbbreviationBlock = {
    getReferenceNode(AbbreviationExtension.ABBREVIATIONS.get(document))
  }

  override def getReferenceNode(repository: AbbreviationRepository): AbbreviationBlock = {
    repository.get(this.chars.toString)
  }
}
