/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/DelimitedBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

import scala.collection.mutable.Stack
import ssg.md.Nullable

final class DelimitedBuilder(private var delimiter: String, capacity: Int) {

  private var out: Nullable[StringBuilder] =
    if (capacity == 0) Nullable.empty else Nullable(new StringBuilder(capacity))
  private var pending:        Boolean                 = false
  private var lastLen:        Int                     = 0
  private var delimiterStack: Nullable[Stack[String]] = Nullable.empty

  def this() = this(",", 0)

  def this(delimiter: String) = this(delimiter, 0)

  override def toString: String = {
    if (delimiterStack.isDefined && delimiterStack.get.nonEmpty) {
      throw new IllegalStateException("Delimiter stack is not empty")
    }
    if (out.isEmpty) "" else out.get.toString
  }

  def isEmpty: Boolean =
    !pending && (out.isEmpty || out.get.length() == 0)

  def getAndClear(): String = {
    if (delimiterStack.isDefined && delimiterStack.get.nonEmpty) {
      throw new IllegalStateException("Delimiter stack is not empty")
    }
    val result = if (out.isEmpty) "" else out.get.toString
    clear()
    result
  }

  def clear(): DelimitedBuilder = {
    out = Nullable.empty
    unmark()
    this
  }

  def toStringOrNull: Nullable[String] = {
    if (delimiterStack.isDefined && delimiterStack.get.nonEmpty) {
      throw new IllegalStateException("Delimiter stack is not empty")
    }
    if (out.isEmpty) Nullable.empty else Nullable(out.get.toString)
  }

  def mark(): DelimitedBuilder = {
    val length = if (out.isDefined) out.get.length() else 0
    if (lastLen != length) pending = true
    lastLen = length
    this
  }

  def unmark(): DelimitedBuilder = {
    pending = false
    lastLen = if (out.isDefined) out.get.length() else 0
    this
  }

  def push(): DelimitedBuilder =
    push(delimiter)

  def push(delimiter: String): DelimitedBuilder = {
    unmark()
    if (delimiterStack.isEmpty) delimiterStack = Nullable(Stack[String]())
    delimiterStack.get.push(this.delimiter)
    this.delimiter = delimiter
    this
  }

  def pop(): DelimitedBuilder = {
    if (delimiterStack.isEmpty || delimiterStack.get.isEmpty) {
      throw new IllegalStateException("Nothing on the delimiter stack")
    }
    delimiter = delimiterStack.get.pop()
    this
  }

  private def doPending(): Unit = {
    if (out.isEmpty) out = Nullable(new StringBuilder())

    if (pending) {
      out.get.append(delimiter)
      pending = false
    }
  }

  def append(v: Char): DelimitedBuilder = {
    doPending()
    out.get.append(v)
    this
  }

  def append(v: Int): DelimitedBuilder = {
    doPending()
    out.get.append(v)
    this
  }

  def append(v: Boolean): DelimitedBuilder = {
    doPending()
    out.get.append(v)
    this
  }

  def append(v: Long): DelimitedBuilder = {
    doPending()
    out.get.append(v)
    this
  }

  def append(v: Float): DelimitedBuilder = {
    doPending()
    out.get.append(v)
    this
  }

  def append(v: Double): DelimitedBuilder = {
    doPending()
    out.get.append(v)
    this
  }

  def append(v: Nullable[String]): DelimitedBuilder = {
    if (v.isDefined && v.get.nonEmpty) {
      doPending()
      out.get.append(v.get)
    }
    this
  }

  def append(v: Nullable[String], start: Int, end: Int): DelimitedBuilder = {
    if (v.isDefined && start < end) {
      doPending()
      out.get.underlying.append(v.get.asInstanceOf[CharSequence], start, end)
    }
    this
  }

  def appendCharSeq(v: Nullable[CharSequence]): DelimitedBuilder = {
    if (v.isDefined && v.get.length() > 0) {
      doPending()
      out.get.append(v.get)
    }
    this
  }

  def appendCharSeq(v: Nullable[CharSequence], start: Int, end: Int): DelimitedBuilder = {
    if (v.isDefined && start < end) {
      doPending()
      out.get.underlying.append(v.get.asInstanceOf[CharSequence], start, end)
    }
    this
  }

  def append(v: Array[Char]): DelimitedBuilder = {
    if (v.length > 0) {
      doPending()
      out.get.appendAll(v)
    }
    this
  }

  def append(v: Array[Char], start: Int, end: Int): DelimitedBuilder = {
    if (start < end) {
      doPending()
      out.get.appendAll(v.slice(start, end))
    }
    this
  }

  def append(o: AnyRef): DelimitedBuilder =
    append(Nullable(o.toString))

  def appendCodePoint(codePoint: Int): DelimitedBuilder = {
    doPending()
    out.get.appendAll(Character.toChars(codePoint))
    this
  }

  def appendAll[V](v: Array[V]): DelimitedBuilder =
    appendAll(v, 0, v.length)

  def appendAll[V](v: Array[V], start: Int, end: Int): DelimitedBuilder = {
    var i = start
    while (i < end) {
      val item = v(i)
      append(Nullable(item.toString))
      mark()
      i += 1
    }
    this
  }

  def appendAll[V](delimiter: String, v: Array[V]): DelimitedBuilder =
    appendAll(delimiter, v, 0, v.length)

  def appendAll[V](delimiter: String, v: Array[V], start: Int, end: Int): DelimitedBuilder = {
    val lastLength = if (out.isDefined) out.get.length() else 0
    push(delimiter)
    appendAll(v, start, end)
    pop()

    if (lastLength != (if (out.isDefined) out.get.length() else 0)) mark()
    else unmark()

    this
  }

  def appendAll[V](v: Seq[? <: V]): DelimitedBuilder =
    appendAll(v, 0, v.size)

  def appendAll[V](v: Seq[? <: V], start: Int, end: Int): DelimitedBuilder = {
    var i = start
    while (i < end) {
      val item = v(i)
      append(Nullable(item.toString))
      mark()
      i += 1
    }
    this
  }

  def appendAll[V](delimiter: String, v: Seq[? <: V]): DelimitedBuilder =
    appendAll(delimiter, v, 0, v.size)

  def appendAll[V](delimiter: String, v: Seq[? <: V], start: Int, end: Int): DelimitedBuilder = {
    val lastLength = if (out.isDefined) out.get.length() else 0
    push(delimiter)
    appendAll(v, start, end)
    pop()

    if (lastLength != (if (out.isDefined) out.get.length() else 0)) mark()
    else unmark()

    this
  }
}
