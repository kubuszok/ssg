/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/Utils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

import ssg.md.Nullable

import java.io.{ BufferedReader, IOException, InputStream, InputStreamReader }
import java.net.{ URLDecoder, URLEncoder }
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

object Utils {

  def ifNull[T](receiver: Nullable[T], altValue: T): T =
    if (receiver.isEmpty) altValue else receiver.get

  def ifNullOr[T](receiver: Nullable[T], condition: Boolean, altValue: T): T =
    if (receiver.isEmpty || condition) altValue else receiver.get

  def ifNullOrNot[T](receiver: Nullable[T], condition: Boolean, altValue: T): T =
    if (receiver.isEmpty || !condition) altValue else receiver.get

  def ifNullOr[T](receiver: Nullable[T], condition: T => Boolean, altValue: T): T =
    if (receiver.isEmpty || condition(receiver.get)) altValue else receiver.get

  def ifNullOrNot[T](receiver: Nullable[T], condition: T => Boolean, altValue: T): T =
    if (receiver.isEmpty || !condition(receiver.get)) altValue else receiver.get

  def ifNullOrEmpty(receiver: Nullable[String], altValue: String): String =
    if (receiver.isEmpty || receiver.get.isEmpty) altValue else receiver.get

  def ifNullOrBlank(receiver: Nullable[String], altValue: String): String =
    if (receiver.isEmpty || isBlank(receiver)) altValue else receiver.get

  def ifEmpty(receiver: Nullable[String], arg: String): String =
    if (receiver.isDefined && !receiver.get.isEmpty) receiver.get
    else arg

  def ifEmpty(receiver: Nullable[String], ifEmptyArg: String, ifNotEmptyArg: String): String =
    if (receiver.isEmpty || receiver.get.isEmpty) ifEmptyArg else ifNotEmptyArg

  def ifEmptyNullArgs(receiver: Nullable[String], ifEmptyArg: String, ifNotEmptyArg: String): String =
    if (receiver.isEmpty || receiver.get.isEmpty) ifEmptyArg else ifNotEmptyArg

  def ifEmpty(receiver: Nullable[String], arg: () => String): String =
    if (receiver.isDefined && !receiver.get.isEmpty) receiver.get
    else arg()

  def ifEmpty(
    receiver:      Nullable[String],
    ifEmptyArg:    () => String,
    ifNotEmptyArg: () => String
  ): String =
    if (receiver.isEmpty || receiver.get.isEmpty) ifEmptyArg() else ifNotEmptyArg()

  def isBlank(receiver: Nullable[String]): Boolean =
    receiver.isEmpty || receiver.get.trim().isEmpty

  // TODO: rewrite these to use BasedSequence implementation
  def isWhiteSpaceNoEOL(receiver: String): Boolean =
    boundary {
      val iMax = receiver.length
      var i    = 0
      while (i < iMax) {
        val c = receiver.charAt(i)
        if (c != ' ' && c != '\t') break(false)
        i += 1
      }
      true
    }

  def orEmpty(receiver: Nullable[String]): String =
    if (receiver.isEmpty) "" else receiver.get

  def wrapWith(receiver: Nullable[String], prefixSuffix: Char): String =
    wrapWith(receiver, prefixSuffix, prefixSuffix)

  def wrapWith(receiver: Nullable[String], prefix: Char, suffix: Char): String =
    if (receiver.isEmpty || receiver.get.isEmpty) ""
    else prefix.toString + receiver.get + suffix.toString

  def wrapWith(receiver: Nullable[String], prefixSuffix: String): String =
    wrapWith(receiver, prefixSuffix, prefixSuffix)

  def wrapWith(receiver: Nullable[String], prefix: String, suffix: String): String =
    if (receiver.isEmpty || receiver.get.isEmpty) ""
    else prefixWith(suffixWith(receiver, suffix), prefix)

  def suffixWith(receiver: Nullable[String], suffix: Char): String =
    suffixWith(receiver, suffix, ignoreCase = false)

  def suffixWithEol(receiver: Nullable[String]): String =
    suffixWith(receiver, '\n', ignoreCase = false)

  def suffixWith(receiver: Nullable[String], suffix: Char, ignoreCase: Boolean): String =
    if (receiver.isDefined && !receiver.get.isEmpty && !endsWith(receiver.get, String.valueOf(suffix), ignoreCase)) {
      receiver.get + suffix
    } else {
      orEmpty(receiver)
    }

  def suffixWith(receiver: Nullable[String], suffix: String): String =
    suffixWith(receiver, suffix, ignoreCase = false)

  def suffixWith(receiver: Nullable[String], suffix: Nullable[String], ignoreCase: Boolean): String =
    if (receiver.isDefined && !receiver.get.isEmpty && suffix.isDefined && !suffix.get.isEmpty && !endsWith(receiver.get, suffix.get, ignoreCase)) {
      receiver.get + suffix.get
    } else {
      orEmpty(receiver)
    }

  def prefixWith(receiver: Nullable[String], prefix: Char): String =
    prefixWith(receiver, prefix, ignoreCase = false)

  def prefixWith(receiver: Nullable[String], prefix: Char, ignoreCase: Boolean): String =
    if (receiver.isDefined && !receiver.get.isEmpty && !startsWith(receiver.get, String.valueOf(prefix), ignoreCase)) {
      prefix.toString + receiver.get
    } else {
      orEmpty(receiver)
    }

  def prefixWith(receiver: Nullable[String], prefix: String): String =
    prefixWith(receiver, prefix, ignoreCase = false)

  def prefixWith(receiver: Nullable[String], prefix: Nullable[String], ignoreCase: Boolean): String =
    if (receiver.isDefined && !receiver.get.isEmpty && prefix.isDefined && !prefix.get.isEmpty && !startsWith(receiver.get, prefix.get, ignoreCase)) {
      prefix.get + receiver.get
    } else {
      orEmpty(receiver)
    }

  def isIn(receiver: Nullable[String], list: String*): Boolean =
    boundary {
      if (receiver.isEmpty) break(false)
      val r = receiver.get
      for (item <- list)
        if (r == item) break(true)
      false
    }

  def endsWith(receiver: Nullable[String], needles: String*): Boolean =
    endsWith(receiver, ignoreCase = false, needles*)

  def endsWith(receiver: Nullable[String], ignoreCase: Boolean, needles: String*): Boolean =
    boundary {
      if (receiver.isEmpty) break(false)
      val r = receiver.get
      if (ignoreCase) {
        for (needle <- needles)
          if (r.length >= needle.length && r.substring(r.length - needle.length).equalsIgnoreCase(needle)) {
            break(true)
          }
      } else {
        for (needle <- needles)
          if (r.endsWith(needle)) {
            break(true)
          }
      }
      false
    }

  def startsWith(receiver: Nullable[String], needles: String*): Boolean =
    startsWith(receiver, ignoreCase = false, needles*)

  def startsWith(receiver: Nullable[String], ignoreCase: Boolean, needles: String*): Boolean =
    boundary {
      if (receiver.isEmpty) break(false)
      val r = receiver.get
      if (ignoreCase) {
        for (needle <- needles)
          if (r.length >= needle.length && r.substring(0, needle.length).equalsIgnoreCase(needle)) {
            break(true)
          }
      } else {
        for (needle <- needles)
          if (r.startsWith(needle)) {
            break(true)
          }
      }
      false
    }

  def count(receiver: Nullable[String], c: Char, startIndex: Int, endIndex: Int): Int =
    if (receiver.isEmpty) 0
    else {
      val r         = receiver.get
      var cnt       = 0
      var pos       = startIndex
      val lastIndex = Math.min(r.length, endIndex)
      while (pos >= 0 && pos <= lastIndex) {
        pos = r.indexOf(c.toInt, pos)
        if (pos < 0) {
          pos = -1 // exit loop
        } else {
          cnt += 1
          pos += 1
        }
      }
      cnt
    }

  def count(receiver: Nullable[String], c: String, startIndex: Int, endIndex: Int): Int =
    if (receiver.isEmpty) 0
    else {
      val r         = receiver.get
      var cnt       = 0
      var pos       = startIndex
      val lastIndex = Math.min(r.length, endIndex)
      while (pos >= 0 && pos <= lastIndex) {
        pos = r.indexOf(c, pos)
        if (pos < 0 || pos > lastIndex) {
          pos = -1 // exit loop
        } else {
          cnt += 1
          pos += 1
        }
      }
      cnt
    }

  def urlDecode(receiver: Nullable[String], charSet: Nullable[String]): String =
    try
      URLDecoder.decode(orEmpty(receiver), if (charSet.isDefined) charSet.get else "UTF-8")
    catch {
      case _: (java.io.UnsupportedEncodingException | IllegalArgumentException) =>
        // e.printStackTrace()
        orEmpty(receiver)
    }

  def urlEncode(receiver: Nullable[String], charSet: Nullable[String]): String =
    try
      URLEncoder.encode(orEmpty(receiver), if (charSet.isDefined) charSet.get else "UTF-8")
    catch {
      case _: java.io.UnsupportedEncodingException =>
        // e.printStackTrace()
        orEmpty(receiver)
    }

  def removePrefix(receiver: Nullable[String], prefix: Char): String =
    if (receiver.isDefined) {
      val r = receiver.get
      if (r.startsWith(String.valueOf(prefix))) r.substring(1)
      else r
    } else {
      ""
    }

  def removePrefix(receiver: Nullable[String], prefix: String): String =
    if (receiver.isDefined) {
      val r = receiver.get
      if (r.startsWith(prefix)) r.substring(prefix.length)
      else r
    } else {
      ""
    }

  def removeAnyPrefix(receiver: Nullable[String], prefixes: String*): String =
    boundary {
      if (receiver.isDefined) {
        val r = receiver.get
        for (prefix <- prefixes)
          if (r.startsWith(prefix)) break(r.substring(prefix.length))
        r
      } else {
        ""
      }
    }

  def removePrefixIncluding(receiver: Nullable[String], delimiter: String): String =
    if (receiver.isDefined) {
      val r   = receiver.get
      val pos = r.indexOf(delimiter)
      if (pos != -1) r.substring(pos + delimiter.length)
      else r
    } else {
      ""
    }

  def removeSuffix(receiver: Nullable[String], suffix: Char): String =
    if (receiver.isDefined) {
      val r = receiver.get
      if (r.endsWith(String.valueOf(suffix))) r.substring(0, r.length - 1)
      else r
    } else {
      ""
    }

  def removeSuffix(receiver: Nullable[String], suffix: String): String =
    if (receiver.isDefined) {
      val r = receiver.get
      if (r.endsWith(suffix)) r.substring(0, r.length - suffix.length)
      else r
    } else {
      ""
    }

  def removeAnySuffix(receiver: Nullable[String], suffixes: String*): String =
    boundary {
      if (receiver.isDefined) {
        val r = receiver.get
        for (suffix <- suffixes)
          if (r.endsWith(suffix)) break(r.substring(0, r.length - suffix.length))
        r
      } else {
        ""
      }
    }

  def stringSorted[T](receiver: Iterable[T], stringer: T => String): List[T] =
    receiver.toList.sortBy(stringer)

  def regexGroup(receiver: Nullable[String]): String =
    "(?:" + orEmpty(receiver) + ")"

  def regionMatches(
    receiver:    CharSequence,
    thisOffset:  Int,
    other:       String,
    otherOffset: Int,
    length:      Int,
    ignoreCase:  Boolean
  ): Boolean =
    boundary {
      if (ignoreCase) {
        var i = 0
        while (i < length) {
          if (Character.toLowerCase(receiver.charAt(i + thisOffset)) != Character.toLowerCase(other.charAt(i + otherOffset))) {
            break(false)
          }
          i += 1
        }
      } else {
        var i = 0
        while (i < length) {
          if (receiver.charAt(i + thisOffset) != other.charAt(i + otherOffset)) {
            break(false)
          }
          i += 1
        }
      }
      true
    }

  def endsWith(receiver: CharSequence, suffix: String, ignoreCase: Boolean): Boolean =
    receiver.length >= suffix.length && regionMatches(receiver, receiver.length - suffix.length, suffix, 0, suffix.length, ignoreCase)

  def startsWith(receiver: CharSequence, prefix: String, ignoreCase: Boolean): Boolean =
    receiver.length >= prefix.length && regionMatches(receiver, 0, prefix, 0, prefix.length, ignoreCase)

  def splice(receiver: Array[String], delimiter: String): String = {
    val result = new StringBuilder(receiver.length * (delimiter.length + 10))
    var delim  = ""
    for (elem <- receiver) {
      result.append(delim)
      delim = delimiter
      result.append(elem)
    }
    result.toString
  }

  /** Longest Common Prefix for a set of strings
    *
    * @param s
    *   array of strings or null
    * @return
    *   longest common prefix
    */
  def getLongestCommonPrefix(s: String*): String =
    boundary {
      if (s.isEmpty) break("")
      if (s.length == 1) break(s(0))

      val s0   = s(0)
      var iMax = s0.length
      val jMax = s.length

      var j = 1
      while (j < jMax) {
        iMax = Math.min(s(j).length, iMax)
        j += 1
      }

      var i = 0
      while (i < iMax) {
        val c = s0.charAt(i)
        j = 1
        while (j < jMax) {
          if (s(j).charAt(i) != c) break(s0.substring(0, i))
          j += 1
        }
        i += 1
      }
      s0.substring(0, iMax)
    }

  def getAbbreviatedText(text: Nullable[String], maxLength: Int): String =
    if (text.isEmpty) ""
    else {
      val t = text.get
      if (t.length <= maxLength || maxLength < 6) t
      else {
        val prefix = maxLength / 2
        val suffix = maxLength - 3 - prefix
        t.substring(0, prefix) + " … " + t.substring(t.length - suffix)
      }
    }

  def splice(receiver: Iterable[String], delimiter: String, skipNullOrEmpty: Boolean): String = {
    val result = new StringBuilder(receiver.size * (delimiter.length + 10))
    var delim  = ""
    for (elem <- receiver)
      if ((elem != null && !elem.isEmpty) || !skipNullOrEmpty) {
        if (!skipNullOrEmpty || (!elem.startsWith(delimiter) && !endsWith(result.toString, delimiter))) {
          result.append(delim)
        }
        delim = delimiter
        result.append(orEmpty(Nullable(elem)))
      }
    result.toString
  }

  def join(items: Array[String], prefix: String, suffix: String, itemPrefix: String, itemSuffix: String): String = {
    val sb = new StringBuilder()
    sb.append(prefix)
    for (item <- items)
      sb.append(itemPrefix).append(item).append(itemSuffix)
    sb.append(suffix)
    sb.toString
  }

  def join(
    items:      Iterable[String],
    prefix:     String,
    suffix:     String,
    itemPrefix: String,
    itemSuffix: String
  ): String = {
    val sb = new StringBuilder()
    sb.append(prefix)
    for (item <- items)
      sb.append(itemPrefix).append(item).append(itemSuffix)
    sb.append(suffix)
    sb.toString
  }

  def repeat(text: String, repeatCount: Int): String =
    if (repeatCount > 0) {
      val sb    = new StringBuilder(text.length * repeatCount)
      var count = repeatCount
      while (count > 0) {
        sb.append(text)
        count -= 1
      }
      sb.toString
    } else {
      ""
    }

  /*
     Limits and other numeric helpers
   */

  def max(receiver: Int, others: Int*): Int = {
    var m = receiver
    for (other <- others)
      if (m < other) m = other
    m
  }

  def min(receiver: Int, others: Int*): Int = {
    var m = receiver
    for (other <- others)
      if (m > other) m = other
    m
  }

  def minLimit(receiver: Int, minBound: Int*): Int =
    max(receiver, minBound*)

  def maxLimit(receiver: Int, maxBound: Int*): Int =
    min(receiver, maxBound*)

  def rangeLimit(receiver: Int, minBound: Int, maxBound: Int): Int =
    Math.min(Math.max(receiver, minBound), maxBound)

  def max(receiver: Float, others: Float*): Float = {
    var m = receiver
    for (other <- others)
      if (m < other) m = other
    m
  }

  def min(receiver: Float, others: Float*): Float = {
    var m = receiver
    for (other <- others)
      if (m > other) m = other
    m
  }

  def minLimit(receiver: Float, minBound: Float*): Float =
    max(receiver, minBound*)

  def maxLimit(receiver: Float, maxBound: Float*): Float =
    min(receiver, maxBound*)

  def rangeLimit(receiver: Float, minBound: Float, maxBound: Float): Float =
    Math.min(Math.max(receiver, minBound), maxBound)

  def compare(n1: Nullable[Number], n2: Nullable[Number]): Int =
    if (n1.isEmpty && n2.isEmpty) 0
    else if (n1.isEmpty) -1
    else if (n2.isEmpty) 1
    else if (n1.get.isInstanceOf[Double] || n2.get.isInstanceOf[Double] || n1.get.isInstanceOf[Float] || n2.get.isInstanceOf[Float]) {
      java.lang.Double.compare(n1.get.doubleValue(), n2.get.doubleValue())
    } else {
      java.lang.Long.compare(n1.get.longValue(), n2.get.longValue())
    }

  def compareNullable[T <: Comparable[T]](i1: Nullable[T], i2: Nullable[T]): Int =
    if (i1.isEmpty || i2.isEmpty) 0
    else i1.get.compareTo(i2.get)

  def putIfMissing[K, V](receiver: scala.collection.mutable.Map[K, V], key: K, value: () => V): V =
    receiver.get(key) match {
      case Some(elem) => elem
      case None       =>
        val elem = value()
        receiver.put(key, elem)
        elem
    }

  def withDefaults[K, V](receiver: scala.collection.mutable.Map[K, V], defaults: scala.collection.mutable.Map[K, V]): scala.collection.mutable.Map[K, V] = {
    val map = scala.collection.mutable.HashMap.from(receiver)
    for ((k, v) <- defaults)
      putIfMissing(map, k, () => v)
    map
  }

  def removeIf[K, V](receiver: scala.collection.mutable.Map[K, V], removeFilter: ((K, V)) => Boolean): Unit = {
    val keys = scala.collection.mutable.Buffer.empty[K]
    for ((k, v) <- receiver)
      if (removeFilter((k, v))) {
        keys += k
      }
    for (key <- keys)
      receiver.remove(key)
  }

  def removeIf[K, V](receiver: scala.collection.mutable.Map[K, V], removeFilter: (K, V) => Boolean): Unit =
    removeIf(receiver, (entry: (K, V)) => removeFilter(entry._1, entry._2))

  def streamAppend(sb: StringBuilder, inputStream: InputStream): Unit = {
    val br = new BufferedReader(new InputStreamReader(inputStream))
    try {
      var done = false
      while (!done) {
        val line = br.readLine()
        if (line == null) {
          done = true
        } else {
          sb.append(line).append('\n')
        }
      }
    } catch {
      case e: IOException => e.printStackTrace()
    } finally
      try
        br.close()
      catch {
        case e: IOException => e.printStackTrace()
      }
  }

  def getResourceAsString(clazz: Class[?], resourcePath: String): String = {
    val stream = PlatformResources.getResourceAsStream(clazz, resourcePath)
    if (stream.isEmpty) ""
    else {
      val sb = new StringBuilder()
      streamAppend(sb, stream.get)
      sb.toString
    }
  }

  def escapeJavaString(param: Nullable[CharSequence]): String =
    if (param.isEmpty) "null"
    else {
      val out = new StringBuilder()
      escapeJavaString(out, param.get)
      out.toString
    }

  def quoteJavaString(param: Nullable[CharSequence]): String =
    if (param.isEmpty) "null"
    else {
      val out = new StringBuilder()
      out.append("\"")
      escapeJavaString(out, param.get)
      out.append("\"")
      out.toString
    }

  def escapeJavaString(out: StringBuilder, chars: CharSequence): Unit = {
    val iMax = chars.length
    var i    = 0
    while (i < iMax) {
      val c = chars.charAt(i)
      c match {
        case '"'      => out.append("\\\"")
        case '\n'     => out.append("\\n")
        case '\r'     => out.append("\\r")
        case '\t'     => out.append("\\t")
        case '\b'     => out.append("\\b")
        case '\f'     => out.append("\\f")
        case '\u0000' => out.append("\\0")
        case _        =>
          if (c < ' ') {
            out.append('%').append(String.format("%02x", c.toInt))
          } else {
            out.append(c)
          }
      }
      i += 1
    }
  }

  def getOrNull[T](list: List[T], index: Int): Nullable[T] =
    if (index >= 0 && index < list.size) Nullable(list(index))
    else Nullable.empty

  def getOrNull[T, S <: T](list: List[T], index: Int, elementClass: Class[S]): Nullable[S] =
    if (index >= 0 && index < list.size) {
      val value = list(index)
      if (elementClass.isInstance(value)) Nullable(elementClass.cast(value))
      else Nullable.empty
    } else {
      Nullable.empty
    }

  def setOrAdd[T](list: scala.collection.mutable.Buffer[T], index: Int, value: T): Nullable[T] =
    if (index == list.size) {
      list += value
      Nullable.empty
    } else {
      val old = list(index)
      list(index) = value
      Nullable(old)
    }
}
