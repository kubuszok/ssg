/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/Attributes.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package html

import ssg.md.Nullable

import scala.collection.mutable.LinkedHashMap

class Attributes(other: Nullable[Attributes]) {

  protected var attributes: Nullable[LinkedHashMap[String, Attribute]] =
    if (other.isDefined && other.get.attributes.isDefined) {
      Nullable(LinkedHashMap.from(other.get.attributes.get))
    } else {
      Nullable.empty
    }

  def this() =
    this(Nullable.empty[Attributes])

  def this(attrs: Attributes) =
    this(Nullable(attrs))

  def toMutable: MutableAttributes =
    new MutableAttributes(this)

  def toImmutable: Attributes = this

  def get(key: CharSequence): Nullable[Attribute] =
    if (attributes.isEmpty || key == null || key.length() == 0) Nullable.empty
    else {
      val useKey = String.valueOf(key)
      attributes.get.get(useKey) match {
        case Some(attr) => Nullable(attr)
        case scala.None => Nullable.empty
      }
    }

  def getValue(key: CharSequence): String =
    if (attributes.isEmpty || key == null || key.length() == 0) ""
    else {
      val useKey = String.valueOf(key)
      attributes.get.get(useKey) match {
        case Some(attr) => attr.value
        case scala.None => ""
      }
    }

  def contains(key: CharSequence): Boolean =
    if (attributes.isEmpty || key == null || key.length() == 0) false
    else {
      val useKey = String.valueOf(key)
      attributes.get.contains(useKey)
    }

  def containsValue(key: CharSequence, value: CharSequence): Boolean =
    if (attributes.isEmpty) false
    else {
      val useKey = String.valueOf(key)
      attributes.get.get(useKey) match {
        case Some(attr) => attr.containsValue(value)
        case scala.None => false
      }
    }

  def isEmpty: Boolean =
    attributes.isEmpty || attributes.get.isEmpty

  def keySet: Set[String] =
    if (attributes.isDefined) attributes.get.keySet.toSet else Set.empty

  def values: Iterable[Attribute] =
    if (attributes.isDefined) attributes.get.values else Iterable.empty

  def entrySet: Set[(String, Attribute)] =
    if (attributes.isDefined) attributes.get.toSet else Set.empty

  def forEach(action: (String, Attribute) => Unit): Unit =
    if (attributes.isDefined) {
      for ((k, v) <- attributes.get)
        action(k, v)
    }

  def size: Int =
    if (attributes.isEmpty) 0 else attributes.get.size

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
    s"Attributes{${sb.toString()}}"
  }
}

object Attributes {
  val EMPTY: Attributes = new Attributes()
}
