/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributeNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

import ssg.md.util.ast.{DoNotDecorate, Node}
import ssg.md.util.html.Attribute
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/** An Attribute node representing a single attribute name and value in attributes node */
class AttributeNode() extends Node with DoNotDecorate {

  var name: BasedSequence = BasedSequence.NULL
  var attributeSeparator: BasedSequence = BasedSequence.NULL
  var openingMarker: BasedSequence = BasedSequence.NULL
  var value: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = { this(); this.chars = chars }

  def this(name: BasedSequence, attributeSeparator: BasedSequence, openingMarker: BasedSequence, value: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = Node.spanningChars(name, attributeSeparator, openingMarker, value, closingMarker)
    this.name = if (name != null) name else BasedSequence.NULL // @nowarn - Java interop: may be null
    this.attributeSeparator = if (attributeSeparator != null) attributeSeparator else BasedSequence.NULL // @nowarn
    this.openingMarker = if (openingMarker != null) openingMarker else BasedSequence.NULL // @nowarn
    this.value = if (value != null) value else BasedSequence.NULL // @nowarn
    this.closingMarker = if (closingMarker != null) closingMarker else BasedSequence.NULL // @nowarn
  }

  override def segments: Array[BasedSequence] = Array(name, attributeSeparator, openingMarker, value, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, name, "name")
    Node.segmentSpanChars(out, attributeSeparator, "sep")
    Node.delimitedSegmentSpanChars(out, openingMarker, value, closingMarker, "value")
    if (isImplicitName) out.append(" isImplicit")
    if (isClass) out.append(" isClass")
    if (isId) out.append(" isId")
  }

  def isImplicitName: Boolean = value.isNotNull && attributeSeparator.isNull && name.isNotNull

  def isClass: Boolean = (isImplicitName && name.equals(".")) || (!isImplicitName && name.equals(Attribute.CLASS_ATTR))

  def isId: Boolean = (isImplicitName && name.equals("#")) || (!isImplicitName && name.equals(Attribute.ID_ATTR))
}

object AttributeNode {
  def isImplicitName(text: CharSequence): Boolean = text.length() > 0 && (text.charAt(0) == '.' || text.charAt(0) == '#')
}
