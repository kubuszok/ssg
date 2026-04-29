/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/Attribute.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/Attribute.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package html

import ssg.md.util.misc.Immutable
import ssg.md.util.sequence.SequenceUtils

trait Attribute extends Immutable[Attribute, MutableAttribute] {
  def name:               String
  def value:              String
  def valueListDelimiter: Char
  def valueNameDelimiter: Char
  def isNonRendering:     Boolean

  /** See if the attribute contains the value (if attribute has list delimiter set) or is equal to the value if no list delimiter is set
    *
    * @param value
    *   name part of the attribute value list or the value if the attribute does not have a value list delimiter
    * @return
    *   true if the attribute contains the valueName
    */
  def containsValue(value: CharSequence): Boolean

  // create a new attribute, if needed
  /** Replace the complete value of this attribute by a new value
    *
    * @param value
    *   new value
    * @return
    *   new attribute or same attribute if nothing changed or attribute is mutable
    */
  def replaceValue(value: CharSequence): Attribute

  /** Add a new value or values depending on list and name delimiter settings and value content <p> If the attribute does not have a list delimiter then its value will be set to the given value. <p>
    * If the attribute has a list delimiter but not name delimiter then value will be split by list delimiter and all values will be added to the attribute's value list. New ones added at the end, old
    * ones left as is. <p> If the attribute has a list delimiter and a name delimiter then value will be split by list delimiter and the name portion of each value will be used to find duplicates
    * whose value will be replaced. New ones added at the end, old ones left where they are but with a new value.
    *
    * @param value
    *   value or list of values (if attribute has a list delimiter and name delimiter) to change
    * @return
    *   new attribute or same attribute if nothing changed or attribute is mutable
    */
  def setValue(value: CharSequence): Attribute

  /** Add a new value or values depending on list and name delimiter settings and value content. <p> If the attribute does not have a list delimiter and its value is equal to the given value then its
    * value is set to empty <p> If the attribute has a list delimiter but not name delimiter then value will be split by list delimiter and any values in attribute's value list will be removed <p> If
    * the attribute has a list delimiter and a name delimiter then value will be split by list delimiter and only the name portion of each value will be used for removal from the attribute's value
    * list
    *
    * @param value
    *   value or list of values (if attribute has a list delimiter and name delimiter) to remove
    * @return
    *   new attribute or same attribute if nothing changed or attribute is mutable
    */
  def removeValue(value: CharSequence): Attribute
}

object Attribute {
  val CLASS_ATTR:               String                = "class"
  val ID_ATTR:                  String                = "id"
  val LINK_STATUS_ATTR:         String                = "Link Status"
  val NAME_ATTR:                String                = "name"
  val STYLE_ATTR:               String                = "style"
  val TITLE_ATTR:               String                = "title"
  val TARGET_ATTR:              String                = "target"
  val NO_FOLLOW:                Attribute             = AttributeImpl.of("rel", "nofollow")
  val NON_RENDERING_WHEN_EMPTY: java.util.Set[String] = {
    val set = new java.util.HashSet[String]()
    set.add(CLASS_ATTR)
    set.add(ID_ATTR)
    set.add(NAME_ATTR)
    set.add(STYLE_ATTR)
    set
  }

  @deprecated("Use SequenceUtils.NUL", "")
  val NUL: Char = SequenceUtils.NUL
}
