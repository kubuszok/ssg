/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/LValue.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Abstract class → open class with companion object for statics
 *   Idiom: Pattern matching instead of instanceof chains
 *   Idiom: Temporal methods use runtime class checks to avoid java.time link errors on JS/Native
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/LValue.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid

import java.math.BigDecimal
import java.time.{ Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneId, ZonedDateTime }
import java.time.format.DateTimeFormatter
import java.time.temporal.{ ChronoField, TemporalAccessor, TemporalQueries }
import java.util
import java.util.{ Collection => JCollection, List => JList, Map => JMap }

/** An abstract class the Filter and Tag classes extend.
  *
  * Houses utility methods for type conversion following Ruby/Liquid semantics.
  */
abstract class LValue {

  /** Returns this value as an array. If a value is already an array, it is cast to an Object[], if it's a java.util.Collection, it is converted to an array and in all other cases, value is simply
    * returned as an Object[] with a single value in it. This function treats Map as a single element.
    */
  def asArray(value: Any, context: TemplateContext): Array[Any] =
    if (value == null) {
      Array.empty[Any]
    } else
      value match {
        case arr: Array[?]       => arr.asInstanceOf[Array[Any]]
        case col: JCollection[?] => col.toArray.asInstanceOf[Array[Any]]
        case _ =>
          if (LValue.isTemporal(value)) {
            LValue.temporalAsArray(value)
          } else {
            Array[Any](value)
          }
      }

  /** Returns value as a java.util.List. */
  def asList(value: Any, context: TemplateContext): JList[?] =
    if (value == null) {
      util.Collections.emptyList()
    } else
      value match {
        case list: JList[?]       => list
        case col:  JCollection[?] => new util.ArrayList(col)
        case arr:  Array[?]       => util.Arrays.asList(arr*)
        case _ =>
          if (LValue.isTemporal(value)) {
            val arr = LValue.temporalAsArray(value)
            util.Arrays.asList(arr*)
          } else {
            util.Collections.singletonList(value)
          }
      }

  /** Convert value to a boolean. Note that only nil and false are false, all other values are true. */
  def asBoolean(value: Any): Boolean =
    value match {
      case null => false
      case b: Boolean => b
      case _ => true
    }

  /** Returns value as a Number. Strings will be coerced into either a Long or Double. */
  def asNumber(value: Any): Number =
    value match {
      case null => java.lang.Long.valueOf(0L)
      case n: Number => n
      case other =>
        val str = String.valueOf(other).trim()
        if (str.matches("\\d+")) java.lang.Long.valueOf(str)
        else java.lang.Double.valueOf(str)
    }

  /** Returns value as a strict BigDecimal number. */
  def asStrictNumber(number: Any): BigDecimal =
    if (number == null) {
      null
    } else
      number match {
        case pbd: PlainBigDecimal => pbd
        case _ => PlainBigDecimal(number.toString.trim())
      }

  /** Returns value as a String. */
  def asString(value: Any, context: TemplateContext): String =
    if (value == null) {
      ""
    } else if (LValue.isTemporal(value)) {
      LValue.temporalToString(value)
    } else if (!isArray(value)) {
      String.valueOf(value)
    } else {
      val array   = asArray(value, context)
      val builder = new StringBuilder()
      for (obj <- array)
        builder.append(asString(obj, context))
      builder.toString()
    }

  /** Returns value as an object appendable to ObjectAppender. */
  def asAppendableObject(value: Any, context: TemplateContext): Any =
    if (value == null) {
      ""
    } else if (LValue.isTemporal(value)) {
      LValue.temporalToString(value)
    } else if (!isArray(value)) {
      value
    } else {
      val array   = asArray(value, context)
      val builder = context.newObjectAppender(array.length)
      for (obj <- array)
        builder.append(asAppendableObject(obj, context))
      builder.getResult
    }

  /** Returns true iff value is an array or a java.util.Collection. */
  def isArray(value: Any): Boolean =
    value != null && (value.isInstanceOf[Array[?]] || value.isInstanceOf[JCollection[?]])

  /** Returns true iff value is a whole number (Integer or Long). */
  def isInteger(value: Any): Boolean =
    value.isInstanceOf[Long] || value.isInstanceOf[Integer]

  /** Returns true iff value is a Number. */
  def isNumber(value: Any): Boolean =
    if (value == null) {
      false
    } else if (value.isInstanceOf[Number]) {
      true
    } else {
      val str = String.valueOf(value).trim()
      if (str.matches("\\d+")) {
        true
      } else {
        try {
          java.lang.Double.parseDouble(str)
          true
        } catch {
          case _: Exception => false
        }
      }
    }

  /** Returns true iff value is a String (CharSequence). */
  def isString(value: Any): Boolean =
    value != null && value.isInstanceOf[CharSequence]

  def isTruthy(value: Any, context: TemplateContext): Boolean = !isFalsy(value, context)

  def isFalsy(value: Any, context: TemplateContext): Boolean =
    if (value == null) {
      true
    } else
      value match {
        case b:  Boolean      => !b
        case cs: CharSequence => cs.length() == 0
        case m:  JMap[?, ?]   => m.isEmpty
        case _ =>
          if (isArray(value)) asArray(value, context).length == 0
          else false
      }

  def canBeInteger(value: Any): Boolean = String.valueOf(value).trim().matches("-?\\d+")

  def canBeDouble(value: Any): Boolean = String.valueOf(value).trim().matches("-?\\d+(\\.\\d*)?")

  def canBeNumber(value: Any): Boolean =
    if (value == null) false
    else canBeInteger(value) || canBeDouble(value)

  def isMap(value: Any): Boolean = value.isInstanceOf[JMap[?, ?]]

  def asMap(value: Any): JMap[String, Any] = value.asInstanceOf[JMap[String, Any]]

  /** Introspects a Map as an array of [key, value] pairs. */
  protected def mapAsArray(value: JMap[?, ?]): Array[Any] = {
    val keyValuePairs = new util.ArrayList[Array[Any]]()
    val it            = value.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      keyValuePairs.add(Array[Any](entry.getKey, entry.getValue))
    }
    keyValuePairs.toArray.asInstanceOf[Array[Any]]
  }
}

object LValue {

  /** Sentinel value for break statements in loops. */
  val BREAK: LValue = new LValue {
    override def toString: String = ""
  }

  /** Sentinel value for continue statements in loops. */
  val CONTINUE: LValue = new LValue {
    override def toString: String = ""
  }

  /** Returns true iff a and b are equals, where (int) 1 is equal to (double) 1.0 */
  def areEqual(a: Any, b: Any): Boolean =
    if (a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]) {
      true
    } else if (a == null || b == null) {
      false
    } else
      (a, b) match {
        case (na: Number, nb: Number) =>
          val delta = na.doubleValue() - nb.doubleValue()
          Math.abs(delta) < 0.00000000001
        case _ =>
          if (isEmpty(a) && isEmpty(b)) true
          else a == b
      }

  private def isEmpty(value: Any): Boolean =
    if (value == null) true
    else
      value match {
        case cs:  CharSequence   => cs.length() == 0
        case col: JCollection[?] => col.isEmpty
        case m:   JMap[?, ?]     => m.isEmpty
        case arr: Array[?]       => arr.length == 0
        case _ => false
      }

  // sample: 2007-11-01 15:25:00 +0900
  val rubyDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XX")

  /** Checks if a value is a temporal type. */
  def isTemporal(value: Any): Boolean =
    value.isInstanceOf[TemporalAccessor]

  /** Converts a temporal value to a string using Ruby date format.
    *
    * Partial temporals (LocalDate, Instant, etc.) are first converted to ZonedDateTime so that the Ruby format string can be fully resolved.
    */
  def temporalToString(value: Any): String =
    value match {
      case zdt: ZonedDateTime    => rubyDateTimeFormat.format(zdt)
      case ta:  TemporalAccessor =>
        val zdt = toZonedDateTime(ta, ZoneId.systemDefault())
        if (zdt != null) rubyDateTimeFormat.format(zdt)
        else value.toString
      case _ => value.toString
    }

  // https://apidock.com/ruby/Time/to_a
  // Returns a ten-element array of values for time:
  // [sec, min, hour, day, month, year, wday, yday, isdst, zone]
  def temporalAsArray(value: Any): Array[Any] = {
    val time = toZonedDateTime(value, ZoneId.systemDefault())
    if (time == null) {
      Array[Any](value)
    } else {
      val sec   = time.get(ChronoField.SECOND_OF_MINUTE)
      val min   = time.getMinute
      val hour  = time.getHour
      val day   = time.getDayOfMonth
      val month = time.get(ChronoField.MONTH_OF_YEAR)
      val year  = time.get(ChronoField.YEAR)
      val wday  = time.getDayOfWeek.getValue
      val yday  = time.get(ChronoField.DAY_OF_YEAR)
      val isdst = time.getZone.getRules.isDaylightSavings(time.toInstant)
      val zone  = time.getZone.getId
      Array[Any](sec, min, hour, day, month, year, wday, yday, isdst, zone)
    }
  }

  /** Keeps an original temporal type as is. */
  def asTemporal(value: Any, context: TemplateContext): TemporalAccessor =
    value match {
      case ta: TemporalAccessor => ta
      case _ =>
        throw new UnsupportedOperationException(
          s"Cannot convert ${if (value == null) "null" else value.getClass.getName} to TemporalAccessor"
        )
    }

  /** Ruby has a single date type, and its equivalent is ZonedDateTime.
    *
    * Follows Ruby rules: if some datetime part is missing, the default is taken from `now` with the system default zone.
    */
  def asRubyDate(value: Any, context: TemplateContext): ZonedDateTime = {
    val defaultZone = ZoneId.systemDefault()
    value match {
      case ta: TemporalAccessor => toZonedDateTime(ta, defaultZone)
      case _ => ZonedDateTime.now(defaultZone)
    }
  }

  /** Converts a TemporalAccessor to ZonedDateTime, filling in missing parts from `now` at the given default zone.
    *
    * Ported from: liqp/filters/date/BasicDateParser#getZonedDateTimeFromTemporalAccessor
    */
  private def toZonedDateTime(value: Any, defaultZone: ZoneId): ZonedDateTime =
    value match {
      case null => ZonedDateTime.now(defaultZone)
      case zdt:  ZonedDateTime    => zdt
      case inst: Instant          => ZonedDateTime.ofInstant(inst, defaultZone)
      case odt:  OffsetDateTime   => odt.toZonedDateTime
      case ldt:  LocalDateTime    => ldt.atZone(defaultZone)
      case ld:   LocalDate        => ld.atStartOfDay(defaultZone)
      case ta:   TemporalAccessor =>
        val zoneId = ta.query(TemporalQueries.zone())
        if (zoneId == null) {
          val date =
            if (ta.query(TemporalQueries.localDate()) != null) ta.query(TemporalQueries.localDate())
            else LocalDate.now(defaultZone)
          val time =
            if (ta.query(TemporalQueries.localTime()) != null) ta.query(TemporalQueries.localTime())
            else LocalTime.now(defaultZone)
          ZonedDateTime.of(date, time, defaultZone)
        } else {
          var now    = LocalDateTime.now(zoneId)
          val fields = Array(
            ChronoField.YEAR,
            ChronoField.MONTH_OF_YEAR,
            ChronoField.DAY_OF_MONTH,
            ChronoField.HOUR_OF_DAY,
            ChronoField.MINUTE_OF_HOUR,
            ChronoField.SECOND_OF_MINUTE,
            ChronoField.NANO_OF_SECOND
          )
          for (tf <- fields)
            if (ta.isSupported(tf)) {
              now = now.`with`(tf, ta.getLong(tf))
            }
          now.atZone(zoneId)
        }
      case _ => null
    }

  def isBlank(string: String): Boolean =
    if (string == null || string.length() == 0) {
      true
    } else {
      var i        = 0
      val l        = string.length()
      var allBlank = true
      while (i < l && allBlank) {
        if (!isWhitespace(string.codePointAt(i))) {
          allBlank = false
        }
        i += 1
      }
      allBlank
    }

  private def isWhitespace(c: Int): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r'

  /** Mimic ruby's BigDecimal.to_f with standard java capabilities. Ensures at least 1 decimal place (e.g., 5.0 not 5).
    *
    * Original: bd.setScale(Math.max(1, bd.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY) We avoid stripTrailingZeros().scale() which is unreliable on Native, and instead strip trailing
    * zeros from the string representation directly.
    */
  def asFormattedNumber(bd: BigDecimal): BigDecimal = {
    val s = bd.toString
    if (s.contains('.')) {
      // Strip trailing zeros from the fractional part, but keep at least one digit after '.'
      val dotIdx   = s.indexOf('.')
      val intPart  = s.substring(0, dotIdx)
      var fracPart = s.substring(dotIdx + 1)
      // Remove trailing zeros
      while (fracPart.length > 1 && fracPart.charAt(fracPart.length - 1) == '0')
        fracPart = fracPart.substring(0, fracPart.length - 1)
      PlainBigDecimal(intPart + "." + fracPart)
    } else {
      // No decimal point — add .0
      PlainBigDecimal(s + ".0")
    }
  }
}
