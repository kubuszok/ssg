/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/AttributeImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package html

import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class AttributeImpl private (
  val name:               String,
  val valueListDelimiter: Char,
  val valueNameDelimiter: Char,
  val value:              String
) extends Attribute {

  override def toMutable: MutableAttribute =
    MutableAttributeImpl.of(this)

  override def isNonRendering: Boolean =
    name.indexOf(' ') != -1 || (value.isEmpty && Attribute.NON_RENDERING_WHEN_EMPTY.contains(name))

  override def containsValue(value: CharSequence): Boolean =
    AttributeImpl.indexOfValue(this.value, value, valueListDelimiter, valueNameDelimiter) != -1

  override def replaceValue(value: CharSequence): Attribute =
    if (value.toString == this.value) this
    else AttributeImpl.of(name, value, valueListDelimiter, valueNameDelimiter)

  override def setValue(value: CharSequence): Attribute = {
    val mutable = toMutable.setValue(value)
    if (mutable == this) this else mutable.toImmutable
  }

  override def removeValue(value: CharSequence): Attribute = {
    val mutable = toMutable.removeValue(value)
    if (mutable == this) this else mutable.toImmutable
  }

  override def equals(o: Any): Boolean =
    o match {
      case attr: Attribute =>
        (this eq attr.asInstanceOf[AnyRef]) || (name == attr.name && value == attr.value)
      case _ => false
    }

  override def hashCode(): Int = {
    var result = name.hashCode
    result = 31 * result + value.hashCode
    result
  }

  override def toString: String =
    s"AttributeImpl { name='$name', value='$value' }"
}

object AttributeImpl {

  def indexOfValue(value: CharSequence, valueName: CharSequence, valueListDelimiter: Char, valueNameDelimiter: Char): Int =
    if (valueName.length() == 0 || value.length() == 0) {
      -1
    } else if (valueListDelimiter == SequenceUtils.NUL) {
      if (value.toString == valueName.toString) 0 else -1
    } else {
      var lastPos = 0
      val subSeq  = BasedSequence.of(value)
      boundary[Int] {
        while (lastPos < value.length()) {
          val pos = subSeq.indexOf(valueName, lastPos)
          if (pos == -1) break(-1)
          // see if it is 0 or preceded by a space, or at the end or followed by a space
          val endPos = pos + valueName.length()
          if (
            (pos == 0
              || value.charAt(pos - 1) == valueListDelimiter
              || (valueNameDelimiter != SequenceUtils.NUL && value.charAt(pos - 1) == valueNameDelimiter))
            && (endPos >= value.length()
              || value.charAt(endPos) == valueListDelimiter
              || (valueNameDelimiter != SequenceUtils.NUL && value.charAt(endPos) == valueNameDelimiter))
          ) {
            break(pos)
          }
          lastPos = endPos + 1
        }
        -1
      }
    }

  def of(other: Attribute): AttributeImpl =
    of(other.name, other.value, other.valueListDelimiter, other.valueNameDelimiter)

  def of(attrName: CharSequence): AttributeImpl =
    of(attrName, attrName, SequenceUtils.NUL, SequenceUtils.NUL)

  def of(attrName: CharSequence, value: CharSequence): AttributeImpl =
    of(attrName, value, SequenceUtils.NUL, SequenceUtils.NUL)

  def of(attrName: CharSequence, value: CharSequence, valueListDelimiter: Char): AttributeImpl =
    of(attrName, value, valueListDelimiter, SequenceUtils.NUL)

  def of(attrName: CharSequence, value: CharSequence, valueListDelimiter: Char, valueNameDelimiter: Char): AttributeImpl = {
    val nameStr = String.valueOf(attrName)
    if (nameStr == Attribute.CLASS_ATTR) {
      new AttributeImpl(nameStr, ' ', SequenceUtils.NUL, if (value == null) "" else String.valueOf(value))
    } else if (nameStr == Attribute.STYLE_ATTR) {
      new AttributeImpl(nameStr, ';', ':', if (value == null) "" else String.valueOf(value))
    } else {
      new AttributeImpl(nameStr, valueListDelimiter, valueNameDelimiter, if (value == null) "" else String.valueOf(value))
    }
  }
}
