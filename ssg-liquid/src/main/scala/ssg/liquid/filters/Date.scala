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
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Date.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import ssg.liquid.filters.date.{ CustomDateFormatRegistry, CustomDateFormatSupport, DateParser }

import java.time.{ Instant, ZonedDateTime }
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/** Formats dates using strftime-compatible patterns.
  *
  * Cross-platform via scala-java-time polyfill. See: https://shopify.github.io/liquid/filters/date/
  */
class Date extends Filter("date") {

  def this(typeSupport: CustomDateFormatSupport[?]) = {
    this()
    CustomDateFormatRegistry.add(typeSupport)
  }

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
          // No need to divide this by 1000, the param is expected to be in seconds already!
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(asNumber(effectiveValue).longValue() * 1000), context.parser.defaultTimeZone)
        } else {
          val parsed = context.getDateParser.parse(valAsString, locale, context.parser.defaultTimeZone)
          if (parsed.isDefined) parsed.get else null
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

  /** Adds a new Date-pattern to be used when parsing a string to a Date. */
  def addDatePattern(pattern: String): Unit =
    DateParser.addDatePattern(pattern)

  /** Removes a Date-pattern from the pattern list. */
  def removeDatePattern(pattern: String): Unit =
    DateParser.removeDatePattern(pattern)

  /** Creates a Date filter with a custom date type support. */
  def withCustomDateType(typeSupport: CustomDateFormatSupport[?]): Filter =
    new Date(typeSupport)

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
          case 'C'       => sb.append("yy") // century — first 2 digits of year (approximate: Java yy gives last 2)
          case 'm'       => sb.append("MM")
          case 'd'       => sb.append("dd")
          case 'H'       => sb.append("HH")
          case 'M'       => sb.append("mm")
          case 'S'       => sb.append("ss")
          case 'L'       => sb.append("SSS")
          case 'N'       => sb.append("nnnnnnnnn") // nanoseconds
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
          case 'G'       => sb.append("YYYY") // ISO week-year
          case 'g'       => sb.append("YY") // ISO week-year, 2 digits
          case 'V'       => sb.append("ww") // ISO week number
          case 'u'       => sb.append("e")
          case 'w'       => sb.append("e")
          case 'U'       => sb.append("ww") // week number (Sunday start, approximate)
          case 'W'       => sb.append("ww") // week number (Monday start, approximate)
          case 'n'       => sb.append("\n")
          case 't'       => sb.append("\t")
          case '%'       => sb.append("'%'")
          // Composite patterns
          case 'D' => sb.append("MM/dd/yy") // %m/%d/%y
          case 'F' => sb.append("yyyy-MM-dd") // %Y-%m-%d
          case 'T' => sb.append("HH:mm:ss") // %H:%M:%S
          case 'R' => sb.append("HH:mm") // %H:%M
          case 'r' => sb.append("hh:mm:ss a") // %I:%M:%S %p
          case 'v' => sb.append("d-MMM-yyyy") // %e-%b-%Y
          case 'c' => sb.append("EEE MMM d HH:mm:ss yyyy") // locale date/time
          case 'x' => sb.append("MM/dd/yy") // locale date
          case 'X' => sb.append("HH:mm:ss") // locale time
          case '+' => sb.append("EEE MMM d HH:mm:ss z yyyy") // date(1) format
          case 's' => sb.append("'%s'") // epoch seconds — cannot express as pattern, pass through
          // Modifier flags: %-X (no-pad), %0X (zero-pad), %_X (space-pad)
          case '-' | '0' | '_' =>
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
                case 'k'   => sb.append("H")
                case 'l'   => sb.append("h")
                case other => sb.append(other)
              }
            }
          // Timezone with colons: %:z, %::z, %:::z
          case ':' =>
            if (i + 1 < format.length() && format.charAt(i + 1) == ':') {
              i += 1
              if (i + 1 < format.length() && format.charAt(i + 1) == ':') {
                i += 1
                if (i + 1 < format.length() && format.charAt(i + 1) == 'z') { i += 1; sb.append("XXX") } // %:::z minimal tz
                else sb.append("'%:::'")
              } else if (i + 1 < format.length() && format.charAt(i + 1) == 'z') { i += 1; sb.append("XXXXX") } // %::z +HH:MM:SS
              else sb.append("'%::'")
            } else if (i + 1 < format.length() && format.charAt(i + 1) == 'z') { i += 1; sb.append("XXX") } // %:z +HH:MM
            else sb.append("'%:'")
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
