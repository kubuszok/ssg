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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/map.dart
 * Covenant-verified: 2026-04-26
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

  /** dart-sass serialize.dart visitMap(): maps are NOT valid CSS values. Attempting to serialize a map for CSS output throws a SassScriptException. Only `toString` (which uses the inspect form) is
    * allowed.
    */
  override def toCssString(quote: Boolean = true): String =
    throw SassScriptException(s"$this isn't a valid CSS value.")

  /** Inspect form of this map: `(k: v, ...)`. Used by `toString` and for debug output. This is NOT valid CSS.
    */
  override def toString: String = {
    val entries = contents
      .map { case (k, v) =>
        s"$k: $v"
      }
      .mkString(", ")
    s"($entries)"
  }
}

object SassMap {
  val empty: SassMap = new SassMap(ListMap.empty)

  def apply(contents: ListMap[Value, Value]): SassMap = new SassMap(contents)
}
