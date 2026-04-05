/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/box.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: box.dart → Box.scala
 *   Convention: Reference equality via inner ModifiableBox identity
 */
package ssg
package sass
package util

/**
 * An unmodifiable reference to a value that may be mutated elsewhere.
 * Uses reference equality based on the underlying ModifiableBox.
 */
final class Box[T] private[util] (private val inner: ModifiableBox[T]) {
  def value: T = inner.value

  override def equals(other: Any): Boolean = other match {
    case that: Box[?] => this.inner eq that.inner
    case _ => false
  }

  override def hashCode(): Int = System.identityHashCode(inner)

  override def toString: String = s"<box: $value>"
}

/**
 * A mutable reference to a (presumably immutable) value.
 * Always uses reference equality.
 */
final class ModifiableBox[T](var value: T) {

  /** Returns an unmodifiable reference to this box. */
  def seal(): Box[T] = new Box[T](this)

  override def equals(other: Any): Boolean = this eq other.asInstanceOf[AnyRef]

  override def hashCode(): Int = System.identityHashCode(this)

  override def toString: String = s"<modifiable box: $value>"
}
