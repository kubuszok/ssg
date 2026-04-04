/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Date.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Replaced strftime4j with custom strftime→DateTimeFormatter conversion
 *   Idiom: Cross-platform via scala-java-time polyfill
 */
package ssg
package liquid
package filters

import java.time.{ Instant, ZonedDateTime }
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/** Formats dates using strftime-compatible patterns.
  *
  * Cross-platform via scala-java-time polyfill. See: https://shopify.github.io/liquid/filters/date/
  */
class Date extends Filter("date") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val locale = context.parser.locale

    var effectiveValue = value
    if (isArray(effectiveValue) && asArray(effectiveValue, context).length == 1) {
      effectiveValue = asArray(effectiveValue, context)(0)
    }

    try {
      val valAsString = asString(effectiveValue, context)
      val compatibleDate: TemporalAccessor =
        if ("now" == valAsString || "today" == valAsString) {
          ZonedDateTime.now()
        } else if (LValue.isTemporal(effectiveValue)) {
          LValue.asTemporal(effectiveValue, context)
        } else if (isNumber(effectiveValue)) {
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(asNumber(effectiveValue).longValue() * 1000), java.time.ZoneId.systemDefault())
        } else {
          try
            ZonedDateTime.parse(valAsString)
          catch {
            case _: Exception =>
              try
                java.time.LocalDate.parse(valAsString).atStartOfDay(java.time.ZoneId.systemDefault())
              catch {
                case _: Exception => null
              }
          }
        }

      if (compatibleDate == null) {
        effectiveValue
      } else {
        val format = asString(get(0, params), context)
        if (format == null || format.trim().isEmpty) {
          effectiveValue
        } else {
          val javaPattern = Date.strftimeToJava(format)
          val formatter   = DateTimeFormatter.ofPattern(javaPattern, locale)
          formatter.format(compatibleDate)
        }
      }
    } catch {
      case _: Exception => value
    }
  }
}

object Date {

  /** Converts a strftime format string to a Java DateTimeFormatter pattern. */
  private[filters] def strftimeToJava(format: String): String = {
    val sb = new StringBuilder()
    var i  = 0
    while (i < format.length()) {
      val c = format.charAt(i)
      if (c == '%' && i + 1 < format.length()) {
        i += 1
        val spec = format.charAt(i)
        spec match {
          case 'Y'       => sb.append("yyyy")
          case 'y'       => sb.append("yy")
          case 'm'       => sb.append("MM")
          case 'd'       => sb.append("dd")
          case 'H'       => sb.append("HH")
          case 'M'       => sb.append("mm")
          case 'S'       => sb.append("ss")
          case 'L'       => sb.append("SSS")
          case 'p'       => sb.append("a")
          case 'P'       => sb.append("a")
          case 'A'       => sb.append("EEEE")
          case 'a'       => sb.append("EEE")
          case 'B'       => sb.append("MMMM")
          case 'b' | 'h' => sb.append("MMM")
          case 'Z'       => sb.append("z")
          case 'z'       => sb.append("XX")
          case 'j'       => sb.append("DDD")
          case 'e'       => sb.append("d")
          case 'k'       => sb.append("H")
          case 'l'       => sb.append("h")
          case 'I'       => sb.append("hh")
          case 'u'       => sb.append("e")
          case 'w'       => sb.append("e")
          case 'n'       => sb.append("\n")
          case 't'       => sb.append("\t")
          case '%'       => sb.append("'%'")
          case '-'       =>
            if (i + 1 < format.length()) {
              i += 1
              format.charAt(i) match {
                case 'd'   => sb.append("d")
                case 'm'   => sb.append("M")
                case 'H'   => sb.append("H")
                case 'M'   => sb.append("m")
                case 'S'   => sb.append("s")
                case 'I'   => sb.append("h")
                case 'e'   => sb.append("d")
                case other => sb.append(other)
              }
            }
          case other => sb.append("'%").append(other).append("'")
        }
      } else {
        if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
          sb.append("'").append(c).append("'")
        } else {
          sb.append(c)
        }
      }
      i += 1
    }
    sb.toString()
  }
}
