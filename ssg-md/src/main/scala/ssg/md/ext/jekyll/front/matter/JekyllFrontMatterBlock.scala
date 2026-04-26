/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/JekyllFrontMatterBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/JekyllFrontMatterBlock.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package jekyll
package front
package matter

import ssg.md.util.ast.{ Block, Node }
import ssg.md.util.sequence.BasedSequence

/** A JekyllFrontMatter block node */
class JekyllFrontMatterBlock() extends Block {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(node: Node) = {
    this()
    appendChild(node)
    setCharsFromContent()
  }

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpan(out, openingMarker, Nullable("open"))
    Node.segmentSpan(out, content, Nullable("content"))
    Node.segmentSpan(out, closingMarker, Nullable("close"))
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, closingMarker)

  def content: BasedSequence = contentChars

  def accept(visitor: JekyllFrontMatterVisitor): Unit =
    visitor.visit(this)
}
