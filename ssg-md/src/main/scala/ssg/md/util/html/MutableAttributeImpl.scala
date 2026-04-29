/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/MutableAttributeImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/MutableAttributeImpl.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package html

import ssg.md.Nullable
import ssg.md.util.sequence.SequenceUtils

import scala.collection.mutable.LinkedHashMap
import scala.util.boundary
import scala.util.boundary.break

class MutableAttributeImpl private (
  val name:               String,
  val valueListDelimiter: Char,
  val valueNameDelimiter: Char,
  private var _value:     Nullable[String],
  private var values:     Nullable[LinkedHashMap[String, String]]
) extends MutableAttribute {

  private def this(name: String, value: String, valueListDelimiter: Char, valueNameDelimiter: Char) =
    this(name, valueListDelimiter, valueNameDelimiter, Nullable(if (value == null) "" else value), Nullable.empty)

  override def toImmutable: Attribute =
    AttributeImpl.of(this)

  override def toMutable: MutableAttribute = this

  override def copy(): MutableAttribute = MutableAttributeImpl.of(this)

  def value: String = {
    if (_value.isEmpty) {
      _value = Nullable(valueFromMap())
    }
    _value.get
  }

  def resetToValuesMap(): Unit = {
    if (values.isEmpty) throw new IllegalStateException("resetToValuesMap called when values is null")
    _value = Nullable.empty
  }

  protected def getValueMap: LinkedHashMap[String, String] = {
    if (values.isEmpty) {
      val map = LinkedHashMap.empty[String, String]
      if (valueListDelimiter != SequenceUtils.NUL) {
        val v = _value.getOrElse("")
        if (v.nonEmpty) {
          var lastPos = 0
          boundary {
            while (lastPos < v.length) {
              val pos    = v.indexOf(valueListDelimiter, lastPos)
              val endPos = if (pos == -1) v.length else pos
              if (lastPos < endPos) {
                val item    = v.substring(lastPos, endPos)
                val namePos = if (valueNameDelimiter != SequenceUtils.NUL) item.indexOf(valueNameDelimiter) else -1
                if (namePos == -1) {
                  map.put(item, "")
                } else {
                  map.put(item.substring(0, namePos), item.substring(namePos + 1))
                }
              }
              if (pos == -1) break()
              lastPos = endPos + 1
            }
          }
        }
      } else {
        map.put(_value.getOrElse(""), "")
      }
      values = Nullable(map)
    }
    values.get
  }

  /** Return the attribute value string by splicing the values of the map using valueListDelimiter and valueNameDelimiter with replacements of the given name/value if provided. If the name is not
    * empty and value is empty then this will be removed from the final string
    *
    * @return
    *   string for value of this attribute from map
    */
  protected def valueFromMap(): String =
    if (valueListDelimiter != SequenceUtils.NUL) {
      val sb = new StringBuilder
      if (valueNameDelimiter != SequenceUtils.NUL) {
        var sep = ""
        val del = valueListDelimiter.toString
        for ((k, v) <- values.get)
          if (k.nonEmpty /* && !entry.getValue().isEmpty() */ ) {
            sb.append(sep)
            sep = del
            sb.append(k).append(valueNameDelimiter).append(v)
          }
      } else {
        var sep = ""
        val del = valueListDelimiter.toString
        for ((k, _) <- values.get)
          if (k.nonEmpty) {
            sb.append(sep)
            sb.append(k)
            sep = del
          }
      }
      val result = sb.toString()
      _value = Nullable(result)
      result
    } else {
      val result = if (values.isEmpty || values.get.isEmpty) "" else values.get.keys.head
      _value = Nullable(result)
      result
    }

  override def isNonRendering: Boolean =
    name.indexOf(' ') != -1 || (value.isEmpty && Attribute.NON_RENDERING_WHEN_EMPTY.contains(name))

  override def replaceValue(value: CharSequence): MutableAttribute = {
    val useValue = if (value == null) "" else String.valueOf(value)
    if (_value.isEmpty || !_value.get.equals(useValue)) {
      _value = Nullable(useValue)
      values = Nullable.empty
    }
    this
  }

  override def setValue(value: CharSequence): MutableAttribute = {
    if (valueListDelimiter != SequenceUtils.NUL) {
      if (value != null && value.length() != 0) {
        val valueMap = getValueMap

        forEachValue(
          value,
          (itemName, itemValue) =>
            if (valueNameDelimiter != SequenceUtils.NUL && itemValue.isEmpty) {
              valueMap.remove(itemName)
            } else {
              valueMap.put(itemName, itemValue)
            }
        )

        _value = Nullable.empty
      }
    } else {
      val currentVal = _value.getOrElse("")
      if (value == null || currentVal != value.toString) {
        _value = Nullable(if (value == null) "" else String.valueOf(value))
        values = Nullable.empty
      }
    }
    this
  }

  private def forEachValue(value: CharSequence, consumer: (String, String) => Unit): Unit = {
    val useValue = if (value == null) "" else String.valueOf(value)
    var lastPos  = 0
    boundary {
      while (lastPos < useValue.length) {
        val pos    = useValue.indexOf(valueListDelimiter, lastPos)
        val endPos = if (pos == -1) useValue.length else pos
        if (lastPos < endPos) {
          val valueItem = useValue.substring(lastPos, endPos).trim
          if (valueItem.nonEmpty) {
            val namePos   = if (valueNameDelimiter == SequenceUtils.NUL) -1 else valueItem.indexOf(valueNameDelimiter)
            val itemName  = if (namePos == -1) valueItem else valueItem.substring(0, namePos)
            val itemValue = if (namePos == -1) "" else valueItem.substring(namePos + 1)
            consumer(itemName, itemValue)
          }
        }
        if (pos == -1) break()
        lastPos = endPos + 1
      }
    }
  }

  override def removeValue(value: CharSequence): MutableAttribute = {
    if (valueListDelimiter != SequenceUtils.NUL) {
      if (value != null && value.length() != 0) {
        val valueMap = getValueMap
        var removed  = false

        forEachValue(value,
                     (itemName, _) =>
                       if (valueMap.remove(itemName).isDefined) {
                         removed = true
                       }
        )

        if (removed) _value = Nullable.empty
      }
    } else {
      val currentVal = _value.getOrElse("")
      if (value == null || currentVal != value.toString) {
        _value = Nullable("")
        values = Nullable.empty
      }
    }
    this
  }

  override def containsValue(value: CharSequence): Boolean =
    AttributeImpl.indexOfValue(this.value, value, valueListDelimiter, valueNameDelimiter) != -1

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
    s"MutableAttributeImpl { name='$name', value='$value' }"
}

object MutableAttributeImpl {

  def of(other: Attribute): MutableAttributeImpl =
    of(other.name, other.value, other.valueListDelimiter, other.valueNameDelimiter)

  def of(attrName: CharSequence): MutableAttributeImpl =
    of(attrName, attrName, SequenceUtils.NUL, SequenceUtils.NUL)

  def of(attrName: CharSequence, value: CharSequence): MutableAttributeImpl =
    of(attrName, value, SequenceUtils.NUL, SequenceUtils.NUL)

  def of(attrName: CharSequence, value: CharSequence, valueListDelimiter: Char): MutableAttributeImpl =
    of(attrName, value, valueListDelimiter, SequenceUtils.NUL)

  def of(attrName: CharSequence, value: CharSequence, valueListDelimiter: Char, valueNameDelimiter: Char): MutableAttributeImpl = {
    val nameStr  = String.valueOf(attrName)
    val valueStr = if (value == null) "" else String.valueOf(value)
    if (nameStr == Attribute.CLASS_ATTR) {
      new MutableAttributeImpl(nameStr, valueStr, ' ', SequenceUtils.NUL)
    } else if (nameStr == Attribute.STYLE_ATTR) {
      new MutableAttributeImpl(nameStr, valueStr, ';', ':')
    } else {
      new MutableAttributeImpl(nameStr, valueStr, valueListDelimiter, valueNameDelimiter)
    }
  }
}
