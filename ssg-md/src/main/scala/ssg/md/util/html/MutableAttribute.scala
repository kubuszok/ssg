/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/MutableAttribute.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package html

import ssg.md.util.misc.Mutable

trait MutableAttribute extends Attribute with Mutable[MutableAttribute, Attribute] {
  def copy(): MutableAttribute

  override def containsValue(value: CharSequence): Boolean
  override def replaceValue(value:  CharSequence): MutableAttribute
  override def setValue(value:      CharSequence): MutableAttribute
  override def removeValue(value:   CharSequence): MutableAttribute
}
