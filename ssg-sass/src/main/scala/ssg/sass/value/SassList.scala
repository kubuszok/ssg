/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/list.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: list.dart → SassList.scala
 *   Convention: ListSeparator in separate file
 *   Idiom: Not final — SassArgumentList extends this
 */
package ssg
package sass
package value

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.visitor.ValueVisitor

import scala.language.implicitConversions

/** A SassScript list value. */
class SassList(
  private val contents:   List[Value],
  override val separator: ListSeparator,
  brackets:               Boolean = false
) extends Value {

  override val hasBrackets: Boolean = brackets

  override def asList: List[Value] = contents

  override def lengthAsList: Int = contents.length

  override def isBlank: Boolean = !hasBrackets && contents.forall(_.isBlank)

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitList(this)

  override def assertMap(name: Nullable[String]): SassMap =
    if (contents.isEmpty) SassMap.empty
    else super.assertMap(name)

  override def tryMap(): Option[SassMap] =
    if (contents.isEmpty) Some(SassMap.empty)
    else None

  override def hashCode(): Int =
    if (contents.isEmpty) SassMap.empty.hashCode()
    else contents.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassList =>
      this.separator == that.separator &&
      this.hasBrackets == that.hasBrackets &&
      this.contents == that.contents
    case that: SassMap =>
      contents.isEmpty && that.contents.isEmpty
    case _ => false
  }

  override def toString: String = {
    val sep        = separator.separatorChar.getOrElse(" ")
    val inner      = contents.map(_.toString).mkString(sep + (if (sep == ",") " " else ""))
    val withParens =
      if (contents.length == 1 && separator == ListSeparator.Comma) s"($inner,)"
      else inner
    if (hasBrackets) s"[$withParens]"
    else withParens
  }
}

object SassList {
  val emptySpace: SassList = new SassList(Nil, ListSeparator.Undecided)
  val emptyComma: SassList = new SassList(Nil, ListSeparator.Comma)

  def apply(
    contents:  List[Value],
    separator: ListSeparator,
    brackets:  Boolean = false
  ): SassList = new SassList(contents, separator, brackets)

  def empty(separator: ListSeparator = ListSeparator.Undecided, brackets: Boolean = false): SassList =
    new SassList(Nil, separator, brackets)
}
