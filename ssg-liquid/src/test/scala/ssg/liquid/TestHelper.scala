/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import java.time.temporal.TemporalAccessor
import java.util.{ ArrayList => JArrayList, HashMap => JHashMap }

import scala.collection.immutable.VectorMap

/** Shared test utility methods. */
object TestHelper {

  /** Wraps an Any value into a DataView. */
  def dv(value: Any): DataView = value match {
    case null => DataView.nil
    case d:  DataView             => d
    case b:  Boolean              => DataView.from(b)
    case s:  Short                => DataView.from(s)
    case i:  Int                  => DataView.from(i)
    case l:  Long                 => DataView.from(l)
    case f:  Float                => DataView.from(f)
    case d:  Double               => DataView.from(d)
    case s:  String               => DataView.from(s)
    case bd: java.math.BigDecimal => DataView.from(bd)
    case ta: TemporalAccessor     => DataView.from(ta)
    case v:  Vector[?]            => DataView.from(v.map(e => dv(e)))
    case m:  VectorMap[?, ?]      => DataView.from(m.asInstanceOf[VectorMap[String, DataView]])
    case m:  java.util.Map[?, ?]  =>
      var vm = VectorMap.empty[String, DataView]
      val it = m.entrySet().iterator()
      while (it.hasNext) {
        val e = it.next()
        vm = vm.updated(String.valueOf(e.getKey), dv(e.getValue))
      }
      DataView.from(vm)
    case c: java.util.Collection[?] =>
      val vec = Vector.newBuilder[DataView]
      val it  = c.iterator()
      while (it.hasNext) vec += dv(it.next())
      DataView.from(vec.result())
    case a: Array[?] =>
      DataView.from(a.map(e => dv(e)).toVector)
    case other => DataView.from(String.valueOf(other))
  }

  /** Builds a JHashMap[String, DataView] from key-value pairs. Values are auto-wrapped. */
  def mapOf(pairs: (String, Any)*): JHashMap[String, DataView] = {
    val m = new JHashMap[String, DataView]()
    pairs.foreach { case (k, v) => m.put(k, dv(v)) }
    m
  }

  /** Builds a DataView vector from elements. Values are auto-wrapped. */
  def listOf(items: Any*): DataView =
    DataView.from(items.map(dv).toVector)

  /** Minimal JSON string parser for test data. Returns DataView. */
  def parseJson(json: String): DataView = {
    val trimmed = json.trim
    dv(parseJsonRaw(trimmed))
  }

  /** Parse a JSON object string into a JHashMap[String, DataView]. */
  def parseJsonObject(json: String): JHashMap[String, DataView] = {
    val raw = parseObjectRaw(json.trim)
    val m   = new JHashMap[String, DataView]()
    val it  = raw.entrySet().iterator()
    while (it.hasNext) {
      val e = it.next()
      m.put(e.getKey, dv(e.getValue))
    }
    m
  }

  // ---- Internal JSON parsing ----

  private def parseObjectRaw(s: String): JHashMap[String, Any] = {
    val map   = new JHashMap[String, Any]()
    val inner = s.substring(1, s.length - 1).trim
    if (inner.isEmpty) return map

    val tokens = splitTopLevel(inner, ',')
    tokens.foreach { token =>
      val colonIdx = findTopLevelColon(token.trim)
      val key      = token.trim.substring(0, colonIdx).trim
      val cleanKey = if (key.startsWith("\"")) key.substring(1, key.length - 1) else key
      val value    = token.trim.substring(colonIdx + 1).trim
      map.put(cleanKey, parseValue(value))
    }
    map
  }

  private def parseArrayRaw(s: String): JArrayList[Any] = {
    val list  = new JArrayList[Any]()
    val inner = s.substring(1, s.length - 1).trim
    if (inner.isEmpty) return list

    val tokens = splitTopLevel(inner, ',')
    tokens.foreach(t => list.add(parseValue(t.trim)))
    list
  }

  private def parseJsonRaw(s: String): Any = parseValue(s)

  private def parseValue(s: String): Any = {
    val t = s.trim
    if (t.startsWith("{")) parseObjectRaw(t)
    else if (t.startsWith("[")) parseArrayRaw(t)
    else if (t.startsWith("\"")) t.substring(1, t.length - 1)
    else if (t == "true") java.lang.Boolean.TRUE
    else if (t == "false") java.lang.Boolean.FALSE
    else if (t == "null" || t == "nil") null
    else if (t.contains(".")) java.lang.Double.valueOf(t)
    else {
      val v = java.lang.Long.valueOf(t)
      if (v >= Int.MinValue && v <= Int.MaxValue) java.lang.Long.valueOf(v)
      else v
    }
  }

  /** Split a string by delimiter, but only at top level (not inside {} [] or "") */
  private def splitTopLevel(s: String, delim: Char): Array[String] = {
    val result = new java.util.ArrayList[String]()
    var depth  = 0
    var inStr  = false
    var start  = 0
    var i      = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr
      else if (!inStr) {
        if (c == '{' || c == '[') depth += 1
        else if (c == '}' || c == ']') depth -= 1
        else if (c == delim && depth == 0) {
          result.add(s.substring(start, i))
          start = i + 1
        }
      }
      i += 1
    }
    if (start < s.length) result.add(s.substring(start))
    val arr = new Array[String](result.size())
    result.toArray(arr)
    arr
  }

  /** Find the position of the first ':' at top level. */
  private def findTopLevelColon(s: String): Int = {
    var depth = 0
    var inStr = false
    var i     = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr
      else if (!inStr) {
        if (c == '{' || c == '[') depth += 1
        else if (c == '}' || c == ']') depth -= 1
        else if (c == ':' && depth == 0) return i
      }
      i += 1
    }
    -1
  }
}
