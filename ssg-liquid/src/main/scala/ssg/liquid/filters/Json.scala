/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Json.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Replaced Jackson ObjectMapper with simple JSON serialization
 *
 * NOTE: Simple JSON serialization — no Jackson dependency.
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Json.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import java.time.temporal.TemporalAccessor
import java.util.{ Collection => JCollection, Map => JMap }

/** Serializes a value to JSON. */
class Json extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    try
      DataView.from(Json.toJson(value))
    catch {
      case e: Exception =>
        context.addError(e)
        if (context.getErrorMode == TemplateParser.ErrorMode.STRICT) {
          throw new RuntimeException(e.getMessage, e)
        }
        value
    }
}

object Json {

  /** Simple JSON serialization without Jackson. */
  def toJson(value: Any): String =
    value match {
      case null => "null"
      case dv:  DataView                                    => toJsonDataView(dv)
      case b:   Boolean                                     => b.toString
      case n:   java.lang.Double if n.isNaN || n.isInfinite => "null"
      case n:   java.lang.Float if n.isNaN || n.isInfinite  => "null"
      case n:   Number                                      => n.toString
      case ta:  TemporalAccessor                            => quoteString(ta.toString)
      case s:   String                                      => quoteString(s)
      case cs:  CharSequence                                => quoteString(cs.toString)
      case arr: Array[?]                                    => arr.map(toJson).mkString("[", ",", "]")
      case col: JCollection[?]                              =>
        val sb    = new StringBuilder("[")
        val it    = col.iterator()
        var first = true
        while (it.hasNext) {
          if (!first) sb.append(",")
          sb.append(toJson(it.next()))
          first = false
        }
        sb.append("]").toString()
      case map: JMap[?, ?] =>
        val sb    = new StringBuilder("{")
        val it    = map.entrySet().iterator()
        var first = true
        while (it.hasNext) {
          val entry = it.next()
          if (!first) sb.append(",")
          sb.append(quoteString(String.valueOf(entry.getKey)))
          sb.append(":")
          sb.append(toJson(entry.getValue))
          first = false
        }
        sb.append("}").toString()
      case other => quoteString(other.toString)
    }

  private def toJsonDataView(dv: DataView): String =
    if (dv.isNull) "null"
    else
      dv.view match {
        case b:  Boolean                                     => b.toString
        case n:  java.lang.Double if n.isNaN || n.isInfinite => "null"
        case n:  java.lang.Float if n.isNaN || n.isInfinite  => "null"
        case n:  Number                                      => n.toString
        case ta: TemporalAccessor                            => quoteString(ta.toString)
        case s:  String                                      => quoteString(s)
        case v:  Vector[?]                                   =>
          v.asInstanceOf[Vector[DataView]].map(toJsonDataView).mkString("[", ",", "]")
        case m: scala.collection.immutable.VectorMap[?, ?] =>
          val map   = m.asInstanceOf[scala.collection.immutable.VectorMap[String, DataView]]
          val sb    = new StringBuilder("{")
          var first = true
          map.foreach { case (k, v) =>
            if (!first) sb.append(",")
            sb.append(quoteString(k))
            sb.append(":")
            sb.append(toJsonDataView(v))
            first = false
          }
          sb.append("}").toString()
        case other => quoteString(String.valueOf(other))
      }

  private def quoteString(s: String): String = {
    val sb = new StringBuilder("\"")
    var i  = 0
    while (i < s.length()) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _    =>
          if (c < 0x20) {
            sb.append("\\u").append(String.format("%04x", c.toInt))
          } else {
            sb.append(c)
          }
      }
      i += 1
    }
    sb.append("\"").toString()
  }
}
