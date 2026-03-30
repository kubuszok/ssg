/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/Pair.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

import ssg.md.Nullable

import java.util.{ Map, Objects }

final class Pair[K, V](val first: Nullable[K], val second: Nullable[V]) extends Paired[Nullable[K], Nullable[V]] {

  override def getKey: Nullable[K] = first

  override def getValue: Nullable[V] = second

  override def setValue(value: Nullable[V]): Nullable[V] =
    throw new IllegalStateException("setValue not supported")

  override def toString: String = {
    val out = new StringBuilder()
    out.append('(')

    if (first.isEmpty) out.append("null")
    else out.append(first)

    out.append(", ")

    if (second.isEmpty) out.append("null")
    else out.append(second)

    out.append(')')
    out.toString()
  }

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) true
    else
      o match {
        case pair: Map.Entry[?, ?] =>
          Objects.equals(first, pair.getKey) && Objects.equals(second, pair.getValue)
        case _ => false
      }

  override def hashCode(): Int = {
    var result = if (first.isEmpty) 0 else first.hashCode()
    result = 31 * result + (if (second.isEmpty) 0 else second.hashCode())
    result
  }
}

object Pair {
  def of[K1, V1](first: Nullable[K1], second: Nullable[V1]): Pair[K1, V1] =
    new Pair(first, second)
}
