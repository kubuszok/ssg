/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/utils.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: utils.dart -> Utils.scala
 *   Convention: Top-level functions -> object methods
 *   Idiom: Some functions moved to CharCode/NumberUtil/MapUtil
 */
package ssg
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.{ CharCode, FileSpan, Frame }

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

object Utils {

  /** Converts iter into a sentence, separating each word with conjunction. */
  def toSentence(iter: Iterable[Any], conjunction: String = "and"): String = {
    val list = iter.toList
    if (list.size == 1) list.head.toString
    else list.init.mkString(", ") + s" $conjunction ${list.last}"
  }

  /** Returns string with every line indented by indentation spaces. */
  def indent(string: String, indentation: Int): String = {
    val prefix = " " * indentation
    val lines  = string.split("\n")
    val sb     = new StringBuilder()
    var i      = 0
    while (i < lines.length) {
      if (i > 0) sb.append("\n")
      sb.append(prefix)
      sb.append(lines(i))
      i += 1
    }
    sb.toString()
  }

  /** Returns name if number is 1, or the plural otherwise. */
  def pluralize(name: String, number: Int, plural: Nullable[String] = Nullable.Null): String =
    if (number == 1) name
    else plural.getOrElse(name + "s")

  /** Returns "a word" or "an word" depending on whether word starts with a vowel. */
  def a(word: String): String = {
    val vowels = Set('a', 'e', 'i', 'o', 'u')
    if (word.nonEmpty && vowels.contains(word.charAt(0).toLower)) s"an $word"
    else s"a $word"
  }

  /** Returns the number of times codeUnit appears in string. */
  def countOccurrences(string: String, codeUnit: Int): Int = {
    var count = 0
    var i     = 0
    while (i < string.length) {
      if (string.charAt(i).toInt == codeUnit) count += 1
      i += 1
    }
    count
  }

  /** Like String.trim, but only trims ASCII whitespace. */
  def trimAscii(string: String, excludeEscape: Boolean = false): String = {
    val start = firstNonWhitespace(string)
    if (start < 0) ""
    else string.substring(start, lastNonWhitespace(string, excludeEscape) + 1)
  }

  /** Like String.trimLeft, but only trims ASCII whitespace. */
  def trimAsciiLeft(string: String): String = {
    val start = firstNonWhitespace(string)
    if (start < 0) "" else string.substring(start)
  }

  /** Like String.trimRight, but only trims ASCII whitespace. */
  def trimAsciiRight(string: String, excludeEscape: Boolean = false): String = {
    val end = lastNonWhitespace(string, excludeEscape)
    if (end < 0) "" else string.substring(0, end + 1)
  }

  private def firstNonWhitespace(string: String): Int = boundary[Int] {
    var i = 0
    while (i < string.length) {
      if (!CharCode.isWhitespace(string.charAt(i).toInt)) break(i)
      i += 1
    }
    -1
  }

  private def lastNonWhitespace(string: String, excludeEscape: Boolean): Int = boundary[Int] {
    var i = string.length - 1
    while (i >= 0) {
      val c = string.charAt(i).toInt
      if (!CharCode.isWhitespace(c)) {
        if (excludeEscape && i != 0 && i != string.length - 1 && c == CharCode.$backslash) break(i + 1)
        else break(i)
      }
      i -= 1
    }
    -1
  }

  /** Whether member is a public Sass member name. */
  def isPublic(member: String): Boolean = {
    val start = member.charAt(0).toInt
    start != CharCode.$minus && start != CharCode.$underscore
  }

  /** Returns name without a vendor prefix. */
  def unvendor(name: String): String = boundary[String] {
    if (name.length < 2) break(name)
    if (name.charAt(0).toInt != CharCode.$minus) break(name)
    if (name.charAt(1).toInt == CharCode.$minus) break(name)
    var i = 2
    while (i < name.length) {
      if (name.charAt(i).toInt == CharCode.$minus) break(name.substring(i + 1))
      i += 1
    }
    name
  }

  /** Whether string1 and string2 are equal, ignoring ASCII case. */
  def equalsIgnoreCase(string1: Nullable[String], string2: Nullable[String]): Boolean = boundary[Boolean] {
    if (string1.isEmpty && string2.isEmpty) break(true)
    if (string1.isEmpty || string2.isEmpty) break(false)
    val s1 = string1.get
    val s2 = string2.get
    if (s1.length != s2.length) break(false)
    var i = 0
    while (i < s1.length) {
      if (!CharCode.characterEqualsIgnoreCase(s1.charAt(i).toInt, s2.charAt(i).toInt)) break(false)
      i += 1
    }
    true
  }

  /** Whether string starts with prefix, ignoring ASCII case. */
  def startsWithIgnoreCase(string: String, prefix: String): Boolean = boundary[Boolean] {
    if (string.length < prefix.length) break(false)
    var i = 0
    while (i < prefix.length) {
      if (!CharCode.characterEqualsIgnoreCase(string.charAt(i).toInt, prefix.charAt(i).toInt)) break(false)
      i += 1
    }
    true
  }

  /** Destructively updates every element of list with the result of function. */
  def mapInPlace[T](list: ArrayBuffer[T], f: T => T): Unit = {
    var i = 0
    while (i < list.length) {
      list(i) = f(list(i))
      i += 1
    }
  }

  /** Returns the longest common subsequence between list1 and list2. */
  def longestCommonSubsequence[T](
    list1:  List[T],
    list2:  List[T],
    select: (T, T) => Nullable[T] = (a: T, b: T) => if (a == b) Nullable(a) else Nullable.Null
  ): List[T] = {
    val lengths    = Array.ofDim[Int](list1.length + 1, list2.length + 1)
    val selections = Array.ofDim[Any](list1.length, list2.length)

    for {
      i <- list1.indices
      j <- list2.indices
    } {
      val selection = select(list1(i), list2(j))
      selections(i)(j) = if (selection.isDefined) selection.get else null // null in internal array only
      lengths(i + 1)(j + 1) =
        if (selection.isDefined) lengths(i)(j) + 1
        else math.max(lengths(i + 1)(j), lengths(i)(j + 1))
    }

    def backtrack(i: Int, j: Int): List[T] =
      if (i < 0 || j < 0) Nil
      else {
        val sel = selections(i)(j)
        if (sel != null) backtrack(i - 1, j - 1) :+ sel.asInstanceOf[T] // null check for internal array
        else if (lengths(i + 1)(j) > lengths(i)(j + 1)) backtrack(i, j - 1)
        else backtrack(i - 1, j)
      }

    backtrack(list1.length - 1, list2.length - 1)
  }

  /** Removes the first value in list that matches test. */
  def removeFirstWhere[T](list: ArrayBuffer[T], test: T => Boolean, orElse: () => Unit = () => ()): Unit = boundary[Unit] {
    var i = 0
    while (i < list.length) {
      if (test(list(i))) {
        list.remove(i)
        break(())
      }
      i += 1
    }
    orElse()
  }

  /** Like Map.addAll, but for two-layer maps. */
  def mapAddAll2[K1, K2, V](
    destination: scala.collection.mutable.Map[K1, scala.collection.mutable.Map[K2, V]],
    source:      scala.collection.mutable.Map[K1, scala.collection.mutable.Map[K2, V]]
  ): Unit =
    source.foreach { case (key, inner) =>
      destination.get(key) match {
        case Some(innerDest) => innerDest ++= inner
        case None            => destination(key) = inner
      }
    }

  /** Sets all keys in map to value. */
  def setAll[K, V](map: scala.collection.mutable.Map[K, V], keys: Iterable[K], value: V): Unit =
    for (key <- keys) map(key) = value

  /** Rotates elements in list from start (inclusive) to end (exclusive) one index higher. */
  def rotateSlice(list: ArrayBuffer[Any], start: Int, end: Int): Unit = {
    var element = list(end - 1)
    var i       = start
    while (i < end) {
      val next = list(i)
      list(i) = element
      element = next
      i += 1
    }
  }

  /** Flattens the first level of nested iterables vertically. */
  def flattenVertically[T](iterable: Iterable[Iterable[T]]): List[T] = {
    val queues = iterable.map(inner => scala.collection.mutable.Queue.from(inner)).toList
    if (queues.size == 1) queues.head.toList
    else {
      val result    = ArrayBuffer.empty[T]
      var remaining = queues.toBuffer
      while (remaining.nonEmpty)
        remaining = remaining.filter { queue =>
          result += queue.dequeue()
          queue.nonEmpty
        }
      result.toList
    }
  }

  /** Creates a stack frame from a span and member name. */
  def frameForSpan(span: FileSpan, member: String): Frame =
    Frame.fromSpan(span, member)

  /** Converts a codepoint index to a code unit index in string. */
  def codepointIndexToCodeUnitIndex(string: String, codepointIndex: Int): Int = {
    var codeUnitIndex = 0
    var i             = 0
    while (i < codepointIndex) {
      if (CharCode.isHighSurrogate(string.charAt(codeUnitIndex).toInt)) codeUnitIndex += 1
      codeUnitIndex += 1
      i += 1
    }
    codeUnitIndex
  }

  /** Converts a code unit index to a codepoint index in string. */
  def codeUnitIndexToCodepointIndex(string: String, codeUnitIndex: Int): Int = {
    var codepointIndex = 0
    var i              = 0
    while (i < codeUnitIndex) {
      codepointIndex += 1
      if (CharCode.isHighSurrogate(string.charAt(i).toInt)) i += 1
      i += 1
    }
    codepointIndex
  }

  /** Whether two iterables have the same contents. */
  def iterableEquals(iter1: Iterable[Any], iter2: Iterable[Any]): Boolean =
    iter1.size == iter2.size && iter1.zip(iter2).forall { case (a, b) => a == b }

  /** A hash code for an iterable that matches iterableEquals. */
  def iterableHash(iter: Iterable[Any]): Int = {
    var hash = 0
    for (elem <- iter) hash = hash * 31 + elem.hashCode()
    hash
  }

  /** Whether two lists have the same contents. */
  def listEquals(list1: Nullable[List[Any]], list2: Nullable[List[Any]]): Boolean = boundary[Boolean] {
    if (list1.isEmpty && list2.isEmpty) break(true)
    if (list1.isEmpty || list2.isEmpty) break(false)
    iterableEquals(list1.get, list2.get)
  }

  /** A hash code for a list that matches listEquals. */
  def listHash(list: List[Any]): Int = iterableHash(list)

  /** Whether two maps have the same contents. */
  def mapEquals(map1: Map[Any, Any], map2: Map[Any, Any]): Boolean = map1 == map2

  /** A hash code for a map that matches mapEquals. */
  def mapHash(map: Map[Any, Any]): Int = map.hashCode()

  /** Returns the variable name from a span covering a variable declaration. */
  def declarationName(span: FileSpan): String = {
    val text = span.text
    trimAsciiRight(text.substring(0, text.indexOf(':')))
  }
}
