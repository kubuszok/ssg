/*
 * Copyright (c) 2025-2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Borrowed from SGE (sge/src/main/scala/sge/utils/Nullable.scala).
 * Allocation-free Option alternative inspired by Kyo.
 *
 * NOTE: Pattern matches on NestedNone use explicit isInstanceOf checks
 * instead of case class extractors to avoid ClassCastException on
 * Scala Native (Native's Pattern class doesn't implement Serializable,
 * but NestedNone extends Product with Serializable as a case class).
 */
package ssg
package md

/** `Option` alternative that does not allocate memory for the `Some` case.
  *
  * Inspired by Kyo's allocation-free `Option` type.
  */
type Nullable[A] = Nullable.Impl[A]

object Nullable {
  opaque type Impl[A] = A | Nullable.NestedNone

  def apply[A](a: A): Nullable[A] =
    if (a == null) None
    else if (a.isInstanceOf[NestedNone]) NestedNone(a.asInstanceOf[NestedNone].value + 1)
    else a
  def empty[A]: Nullable[A] = NestedNone(0)

  def fromOption[A](option: Option[A]): Nullable[A] = option.fold(empty[A])(apply)

  // Use isInstanceOf for NestedNone checks to avoid Serializable cast on Scala Native
  private def isNone(v: Any): Boolean = (v.asInstanceOf[AnyRef] eq None) || v.isInstanceOf[NestedNone]

  extension [A](maybe: Nullable[A]) {

    def map[B](f: A => B): Nullable[B] =
      if (isNone(maybe)) None
      else apply(f(maybe.asInstanceOf[A]))

    def flatMap[B](f: A => Nullable[B]): Nullable[B] = map(f).flatten

    def foreach(f: A => Unit): Unit =
      if (!isNone(maybe)) f(maybe.asInstanceOf[A])

    def fold[B](onEmpty: => B)(onSome: A => B): B =
      if (isNone(maybe)) onEmpty
      else onSome(maybe.asInstanceOf[A])

    def getOrElse(onEmpty: => A): A =
      if (isNone(maybe)) onEmpty
      else maybe.asInstanceOf[A]

    /** Force-unwraps the value, throwing NullPointerException if empty. Named `get` to avoid shadowing Scala 3's built-in `.nn` extension on `T | Null`.
      */
    def get: A =
      if (isNone(maybe)) throw new NullPointerException("Nullable.get called on empty value")
      else maybe.asInstanceOf[A]

    /** Unwraps to the raw value or null. NOT actually deprecated — the annotation is used to trigger -Werror with -deprecation, forcing callers to add @nowarn("msg=deprecated") with an explicit
      * comment explaining why orNull is needed (e.g., passing to a Java API that expects null). For all other cases, use fold, foreach, getOrElse, map, isDefined, or isEmpty instead.
      */
    @deprecated("orNull should only be used at Java interop boundaries; use fold/foreach/getOrElse instead", "always")
    def orNull: A =
      if (isNone(maybe)) null.asInstanceOf[A]
      else maybe.asInstanceOf[A]

    def isDefined: Boolean = !isNone(maybe)

    def isEmpty: Boolean = isNone(maybe)

    def orElse(alternative: => Nullable[A]): Nullable[A] =
      if (isNone(maybe)) alternative
      else maybe

    def exists(p: A => Boolean): Boolean =
      if (isNone(maybe)) false
      else p(maybe.asInstanceOf[A])

    def forall(p: A => Boolean): Boolean =
      if (isNone(maybe)) true
      else p(maybe.asInstanceOf[A])

    def contains[A1 >: A](elem: A1): Boolean =
      if (isNone(maybe)) false
      else maybe.asInstanceOf[A] == elem

    def filter(p: A => Boolean): Nullable[A] =
      if (isNone(maybe)) None
      else if (p(maybe.asInstanceOf[A])) maybe
      else None
  }
  extension [A](maybe: Nullable[Nullable[A]]) {

    def flatten: Nullable[A] = {
      val m = maybe.asInstanceOf[AnyRef]
      if (m eq None) None
      else if (m.isInstanceOf[NestedNone]) NestedNone(m.asInstanceOf[NestedNone].value - 1)
      else maybe.asInstanceOf[A]
    }
  }

  /** Implicit conversion from non-null values to `Nullable`. */
  given [A]: Conversion[A, Nullable[A]] = nonEmptyConversion.asInstanceOf[Conversion[A, Nullable[A]]]

  /** Implicit conversion from `null` to `Nullable`. */
  given [A]: Conversion[Null, Nullable[A]] = emptyConversion.asInstanceOf[Conversion[Null, Nullable[A]]]

  private val nonEmptyConversion: Conversion[Any, Nullable[Any]] = new {
    def apply(a: Any): Nullable[Any] = Nullable(a)
  }
  private val emptyConversion: Conversion[Null, Nullable[Any]] = new {
    def apply(a: Null): Nullable[Any] = Nullable.empty
  }

  /** Non-nested `None`. For this value we should use an empty branch, since it cannot model Some(None), Some(Some(None)) and so on.
    */
  private val None = NestedNone(0)

  /** Nested `None`, that traces the amount of nesting. Cached to avoid allocation. NOT a case class — case class extends Product with Serializable, which causes ClassCastException on Scala Native
    * when the opaque union type `A | NestedNone` is erased (Native's Pattern class lacks Serializable).
    */
  final private class NestedNone(val value: Int) {

    assert(value >= 0, "None nesting level cannot be negative, got: " + value)

    override def equals(other: Any): Boolean = other match {
      case nn: NestedNone => nn.value == value
      case _ => false
    }
    override def hashCode(): Int    = value
    override def toString:   String = s"NestedNone($value)"
  }
  private object NestedNone {

    def apply(value: Int): NestedNone =
      if (value >= 0 && value < cache.length) cache(value)
      else new NestedNone(value)

    def unapply(a: Any): Option[Int] = a match {
      case nn: NestedNone => Some(nn.value)
      case _ => scala.None
    }

    private val cache = IArray.from((0 until 10).map(new NestedNone(_)))
  }
}
