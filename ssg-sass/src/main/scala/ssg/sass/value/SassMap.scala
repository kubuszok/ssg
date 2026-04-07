/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/map.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: map.dart → SassMap.scala
 *   Convention: Uses ListMap for insertion order preservation
 */
package ssg
package sass
package value

import ssg.sass.Nullable
import ssg.sass.visitor.ValueVisitor

import scala.collection.immutable.ListMap

/** A SassScript map value. */
final class SassMap(val contents: ListMap[Value, Value]) extends Value {

  override def separator: ListSeparator =
    if (contents.isEmpty) ListSeparator.Undecided else ListSeparator.Comma

  override def asList: List[Value] =
    contents.map { case (k, v) =>
      SassList(List(k, v), ListSeparator.Space)
    }.toList

  override def lengthAsList: Int = contents.size

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitMap(this)

  override def assertMap(name: Nullable[String]): SassMap = this

  override def tryMap(): Option[SassMap] = Some(this)

  override def hashCode(): Int =
    if (contents.isEmpty) SassList.emptySpace.hashCode()
    else contents.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassMap  => this.contents == that.contents
    case that: SassList => contents.isEmpty && that.asList.isEmpty
    case _ => false
  }

  /** CSS representation of this map. Maps don't have a direct CSS literal; this matches dart-sass's inspect form `(k: v, ...)` and recurses via `toCssString` so nested values render consistently with
    * the serializer.
    */
  override def toCssString(quote: Boolean = true): String = {
    val entries = contents
      .map { case (k, v) =>
        s"${k.toCssString()}: ${v.toCssString()}"
      }
      .mkString(", ")
    s"($entries)"
  }

  override def toString: String = toCssString()
}

object SassMap {
  val empty: SassMap = new SassMap(ListMap.empty)

  def apply(contents: ListMap[Value, Value]): SassMap = new SassMap(contents)
}
