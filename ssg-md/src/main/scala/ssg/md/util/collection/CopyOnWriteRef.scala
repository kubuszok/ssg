/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/CopyOnWriteRef.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/CopyOnWriteRef.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

class CopyOnWriteRef[T](initialValue: Nullable[T], private val copyFunction: T => T) {

  private var _value:         Nullable[T] = initialValue
  private var referenceCount: Int         = 0

  def peek: Nullable[T] = _value

  def immutable: Nullable[T] = {
    if (_value.isDefined) referenceCount += 1
    _value
  }

  def mutable: Nullable[T] = {
    if (referenceCount > 0) {
      _value = Nullable(copyFunction(_value.get))
      referenceCount = 0
    }
    _value
  }

  def value: Nullable[T] = _value

  def value_=(v: Nullable[T]): Unit = {
    referenceCount = 0
    _value = Nullable(copyFunction(v.getOrElse(null.asInstanceOf[T]))) // @nowarn - Java interop: copyFunction must handle null (original Java always passes value including null)
  }

  def isMutable: Boolean = referenceCount == 0
}
