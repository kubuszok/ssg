/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/MutableAttributes.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package html

import ssg.md.Nullable

import scala.collection.mutable.LinkedHashMap

class MutableAttributes(attrs: Attributes) extends Attributes(attrs) {

  def this() = {
    this(new Attributes())
  }

  override def toMutable: MutableAttributes = this

  override def toImmutable: Attributes =
    new Attributes(this)

  protected def getAttributes: LinkedHashMap[String, Attribute] = {
    if (attributes.isEmpty) {
      attributes = Nullable(LinkedHashMap.empty[String, Attribute])
    }
    attributes.get
  }

  def replaceValue(attribute: Attribute): Attribute =
    replaceValue(attribute.name, attribute.value)

  /** Attribute dependent value replacement class and style append new values to existing ones others set it to the new value
    *
    * @param key
    *   attribute name
    * @param value
    *   new value
    * @return
    *   new attribute
    */
  def replaceValue(key: CharSequence, value: CharSequence): Attribute = {
    val useKey = String.valueOf(key)
    val attribute: Attribute =
      if (attributes.isEmpty) {
        AttributeImpl.of(useKey, value)
      } else {
        attributes.get.get(useKey) match {
          case Some(existing) => existing.replaceValue(value)
          case scala.None     => AttributeImpl.of(useKey, value)
        }
      }
    getAttributes.put(useKey, attribute)
    attribute
  }

  def addValue(attribute: Attribute): Attribute =
    addValue(attribute.name, attribute.value)

  def addValues(attrs: Attributes): MutableAttributes = {
    for (attribute <- attrs.values)
      addValue(attribute.name, attribute.value)
    this
  }

  def addValue(key: CharSequence, value: CharSequence): Attribute = {
    val useKey = String.valueOf(key)
    val attribute: Attribute =
      if (attributes.isEmpty) {
        AttributeImpl.of(key, value)
      } else {
        attributes.get.get(useKey) match {
          case Some(existing) => existing.setValue(value)
          case scala.None     => AttributeImpl.of(key, value)
        }
      }
    getAttributes.put(useKey, attribute)
    attribute
  }

  def removeValue(attribute: Attribute): Nullable[Attribute] =
    removeValue(attribute.name, attribute.value)

  def remove(attribute: Attribute): Nullable[Attribute] =
    remove(attribute.name)

  def removeValue(key: CharSequence, value: CharSequence): Nullable[Attribute] =
    if (attributes.isEmpty || key == null || key.length() == 0) Nullable.empty
    else {
      val useKey = String.valueOf(key)
      attributes.get.get(useKey) match {
        case Some(oldAttribute) =>
          val attribute = oldAttribute.removeValue(value)
          getAttributes.put(useKey, attribute)
          Nullable(attribute)
        case scala.None => Nullable.empty
      }
    }

  def clear(): Unit =
    attributes = Nullable.empty

  def remove(key: CharSequence): Nullable[Attribute] =
    if (attributes.isEmpty || key == null || key.length() == 0) Nullable.empty
    else {
      val useKey = String.valueOf(key)
      attributes.get.remove(useKey) match {
        case Some(attr) => Nullable(attr)
        case scala.None => Nullable.empty
      }
    }

  def replaceValues(other: MutableAttributes): Unit =
    if (attributes.isEmpty) {
      if (other.attributes.isDefined) {
        attributes = Nullable(LinkedHashMap.from(other.attributes.get))
      }
    } else {
      if (other.attributes.isDefined) {
        attributes.get ++= other.attributes.get
      }
    }

  override def toString: String = {
    val sb  = new StringBuilder
    var sep = ""
    for (attrName <- keySet) {
      sb.append(sep).append(attrName)
      val attribute = attributes.get(attrName)
      if (attribute.value.nonEmpty) {
        sb.append("=").append("\"").append(attribute.value.replace("\"", "\\\"")).append("\"")
      }
      sep = " "
    }
    s"MutableAttributes{${sb.toString()}}"
  }
}
