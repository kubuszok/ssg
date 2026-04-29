/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.{ ArrayList => JArrayList, HashMap => JHashMap }

/** Shared test utility methods. */
object TestHelper {

  /** Builds a JHashMap from key-value pairs. */
  def mapOf(pairs: (String, Any)*): JHashMap[String, Any] = {
    val m = new JHashMap[String, Any]()
    pairs.foreach { case (k, v) => m.put(k, v) }
    m
  }

  /** Builds a JArrayList from elements. */
  def listOf(items: Any*): JArrayList[Any] = {
    val l = new JArrayList[Any]()
    items.foreach(l.add)
    l
  }

  /** Minimal JSON string parser for test data. Supports: objects, arrays,
    * strings, numbers, booleans, null. No escaping or nested whitespace.
    *
    * This is intentionally a simple test-only parser; do not use in production.
    */
  def parseJson(json: String): Any = {
    val trimmed = json.trim
    if (trimmed.startsWith("{")) parseObject(trimmed)
    else if (trimmed.startsWith("[")) parseArray(trimmed)
    else if (trimmed.startsWith("\"")) trimmed.substring(1, trimmed.length - 1)
    else if (trimmed == "true") java.lang.Boolean.TRUE
    else if (trimmed == "false") java.lang.Boolean.FALSE
    else if (trimmed == "null" || trimmed == "nil") null
    else if (trimmed.contains(".")) java.lang.Double.valueOf(trimmed)
    else java.lang.Long.valueOf(trimmed)
  }

  /** Parse a JSON object string into a JHashMap. */
  def parseJsonObject(json: String): JHashMap[String, Any] =
    parseObject(json.trim).asInstanceOf[JHashMap[String, Any]]

  // ---- Internal JSON parsing ----

  private def parseObject(s: String): JHashMap[String, Any] = {
    val map = new JHashMap[String, Any]()
    val inner = s.substring(1, s.length - 1).trim
    if (inner.isEmpty) return map

    val tokens = splitTopLevel(inner, ',')
    tokens.foreach { token =>
      val colonIdx = findTopLevelColon(token.trim)
      val key = token.trim.substring(0, colonIdx).trim
      val cleanKey = if (key.startsWith("\"")) key.substring(1, key.length - 1) else key
      val value = token.trim.substring(colonIdx + 1).trim
      map.put(cleanKey, parseValue(value))
    }
    map
  }

  private def parseArray(s: String): JArrayList[Any] = {
    val list = new JArrayList[Any]()
    val inner = s.substring(1, s.length - 1).trim
    if (inner.isEmpty) return list

    val tokens = splitTopLevel(inner, ',')
    tokens.foreach(t => list.add(parseValue(t.trim)))
    list
  }

  private def parseValue(s: String): Any = {
    val t = s.trim
    if (t.startsWith("{")) parseObject(t)
    else if (t.startsWith("[")) parseArray(t)
    else if (t.startsWith("\"")) t.substring(1, t.length - 1)
    else if (t == "true") java.lang.Boolean.TRUE
    else if (t == "false") java.lang.Boolean.FALSE
    else if (t == "null" || t == "nil") null
    else if (t.contains(".")) java.lang.Double.valueOf(t)
    else {
      val v = java.lang.Long.valueOf(t)
      // Return Integer for small values to match liqp expectations
      if (v >= Int.MinValue && v <= Int.MaxValue) java.lang.Long.valueOf(v)
      else v
    }
  }

  /** Split a string by delimiter, but only at top level (not inside {} [] or "") */
  private def splitTopLevel(s: String, delim: Char): Array[String] = {
    val result = new java.util.ArrayList[String]()
    var depth = 0
    var inStr = false
    var start = 0
    var i = 0
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
    var i = 0
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
