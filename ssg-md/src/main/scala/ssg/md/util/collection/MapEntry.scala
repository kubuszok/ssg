/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/MapEntry.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/MapEntry.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection

import java.util.Objects

// Value is stored as raw V (which may be null at Java interop level)
final class MapEntry[K, V](private val key: K, private val _value: V) extends java.util.Map.Entry[K, V] {

  override def getKey: K = key

  override def getValue: V = _value

  override def setValue(v: V): V =
    throw new UnsupportedOperationException()

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) true
    else {
      o match {
        case entry: MapEntry[?, ?] =>
          Objects.equals(key, entry.key) && Objects.equals(_value, entry._value)
        case _ => false
      }
    }

  override def hashCode(): Int = {
    var result = key.hashCode()
    result = 31 * result + (if (_value != null) _value.hashCode() else 0) // Java interop — value may be null
    result
  }
}
