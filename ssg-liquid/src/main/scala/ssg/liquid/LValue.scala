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

import ssg.data.DataView

import java.math.BigDecimal
import java.time.{ Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneId, ZonedDateTime }
import java.time.format.DateTimeFormatter
import java.time.temporal.{ ChronoField, TemporalAccessor, TemporalQueries }

import scala.collection.immutable.VectorMap

/** An abstract class the Filter and Tag classes extend.
  *
  * Houses utility methods for type conversion following Ruby/Liquid semantics.
  */
abstract class LValue {

  /** Returns this value as a Vector[DataView]. If a value is already a vector, it is returned directly. If it's a VectorMap, it is converted via mapAsVector. Otherwise, value is wrapped in a
    * single-element Vector.
    */
  def asArray(value: DataView, context: TemplateContext): Vector[DataView] =
    if (value.isNull) {
      Vector.empty
    } else
      value.view match {
        case v:  Vector[?]        => v.asInstanceOf[Vector[DataView]]
        case m:  VectorMap[?, ?]  => mapAsVector(m.asInstanceOf[VectorMap[String, DataView]])
        case ta: TemporalAccessor =>
          LValue.temporalAsVector(ta)
        case _ => Vector(value)
      }

  /** Convert value to a boolean. Note that only nil and false are false, all other values are true. */
  def asBoolean(value: DataView): Boolean =
    if (value.isNull) false
    else
      value.view match {
        case b: Boolean => b
        case _ => true
      }

  /** Returns value as a Number. Strings will be coerced into either a Long or Double. */
  def asNumber(value: DataView): Number =
    if (value.isNull) java.lang.Long.valueOf(0L)
    else
      value.view match {
        case s:  Short      => java.lang.Short.valueOf(s)
        case i:  Int        => java.lang.Integer.valueOf(i)
        case l:  Long       => java.lang.Long.valueOf(l)
        case f:  Float      => java.lang.Float.valueOf(f)
        case d:  Double     => java.lang.Double.valueOf(d)
        case bd: BigDecimal => bd
        case _ =>
          val str = value.toString.trim()
          if (str.matches("\\d+")) java.lang.Long.valueOf(str)
          else java.lang.Double.valueOf(str)
      }

  /** Returns value as a strict BigDecimal number. */
  def asStrictNumber(number: DataView): BigDecimal =
    if (number.isNull) {
      null
    } else
      number.view match {
        case pbd: PlainBigDecimal => pbd
        case _ => PlainBigDecimal(number.toString.trim())
      }

  /** Returns value as a String. */
  def asString(value: DataView, context: TemplateContext): String =
    if (value.isNull) {
      ""
    } else
      value.view match {
        case ta: TemporalAccessor =>
          LValue.temporalToString(ta)
        case v: Vector[?] =>
          val vec     = v.asInstanceOf[Vector[DataView]]
          val builder = new StringBuilder()
          vec.foreach(dv => builder.append(asString(dv, context)))
          builder.toString()
        case other => String.valueOf(other)
      }

  /** Returns value as an object appendable to ObjectAppender. */
  def asAppendableObject(value: DataView, context: TemplateContext): Any =
    if (value.isNull) {
      ""
    } else
      value.view match {
        case ta: TemporalAccessor =>
          LValue.temporalToString(ta)
        case v: Vector[?] =>
          val vec     = v.asInstanceOf[Vector[DataView]]
          val builder = context.newObjectAppender(vec.size)
          vec.foreach(dv => builder.append(asAppendableObject(dv, context)))
          builder.getResult
        case _ => value.toString
      }

  /** Returns true iff value is a Vector (array). */
  def isArray(value: DataView): Boolean =
    !value.isNull && value.view.isInstanceOf[Vector[?]]

  /** Returns true iff value is a whole number (Int or Long). */
  def isInteger(value: DataView): Boolean =
    if (value.isNull) false
    else
      value.view match {
        case _: Int | _: Long => true
        case _                => false
      }

  /** Returns true iff value is a Number type. */
  def isNumber(value: DataView): Boolean =
    if (value.isNull) {
      false
    } else
      value.view match {
        case _: (Short | Int | Long | Float | Double | BigDecimal) => true
        case _ =>
          val str = value.toString.trim()
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

  /** Returns true iff value is a String. */
  def isString(value: DataView): Boolean =
    !value.isNull && value.view.isInstanceOf[String]

  def isTruthy(value: DataView, context: TemplateContext): Boolean = !isFalsy(value, context)

  def isFalsy(value: DataView, context: TemplateContext): Boolean =
    if (value.isNull) {
      true
    } else
      value.view match {
        case b: Boolean         => !b
        case s: String          => s.length() == 0
        case m: VectorMap[?, ?] => m.isEmpty
        case v: Vector[?]       => v.isEmpty
        case _ => false
      }

  def canBeInteger(value: DataView): Boolean = String.valueOf(value.view).trim().matches("-?\\d+")

  def canBeDouble(value: DataView): Boolean = String.valueOf(value.view).trim().matches("-?\\d+(\\.\\d*)?")

  def canBeNumber(value: DataView): Boolean =
    if (value.isNull) false
    else canBeInteger(value) || canBeDouble(value)

  def isMap(value: DataView): Boolean =
    !value.isNull && value.view.isInstanceOf[VectorMap[?, ?]]

  def asMap(value: DataView): VectorMap[String, DataView] =
    value.asMap.getOrElse(VectorMap.empty)

  /** Introspects a VectorMap as a Vector of [key, value] pairs. */
  protected def mapAsVector(value: VectorMap[String, DataView]): Vector[DataView] =
    value.toVector.map { case (k, v) =>
      DataView(Vector[DataView](DataView.from(k), v))
    }
}

object LValue {

  /** Returns true iff a and b are equals, where (int) 1 is equal to (double) 1.0 */
  def areEqual(a: DataView, b: DataView): Boolean =
    if (a eq b) {
      true
    } else if (a.isNull || b.isNull) {
      a.isNull && b.isNull
    } else
      (a.view, b.view) match {
        case (na: Number, nb: Number) =>
          val delta = na.doubleValue() - nb.doubleValue()
          Math.abs(delta) < 0.00000000001
        case _ =>
          if (isEmpty(a) && isEmpty(b)) true
          else a.view == b.view
      }

  private def isEmpty(value: DataView): Boolean =
    if (value.isNull) true
    else
      value.view match {
        case s: String          => s.length() == 0
        case v: Vector[?]       => v.isEmpty
        case m: VectorMap[?, ?] => m.isEmpty
        case _ => false
      }

  // sample: 2007-11-01 15:25:00 +0900
  val rubyDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XX")

  /** Checks if a DataView contains a temporal type. */
  def isTemporal(value: DataView): Boolean =
    !value.isNull && value.view.isInstanceOf[TemporalAccessor]

  /** Converts a temporal value to a string using Ruby date format. */
  def temporalToString(ta: TemporalAccessor): String =
    ta match {
      case zdt: ZonedDateTime => rubyDateTimeFormat.format(zdt)
      case _ =>
        val zdt = toZonedDateTime(ta, ZoneId.systemDefault())
        if (zdt != null) rubyDateTimeFormat.format(zdt)
        else ta.toString
    }

  // https://apidock.com/ruby/Time/to_a
  // Returns a ten-element vector of values for time:
  // [sec, min, hour, day, month, year, wday, yday, isdst, zone]
  def temporalAsVector(ta: TemporalAccessor): Vector[DataView] = {
    val time = toZonedDateTime(ta, ZoneId.systemDefault())
    if (time == null) {
      Vector(DataView.from(ta))
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
      Vector(
        DataView.from(sec),
        DataView.from(min),
        DataView.from(hour),
        DataView.from(day),
        DataView.from(month),
        DataView.from(year),
        DataView.from(wday),
        DataView.from(yday),
        DataView.from(isdst),
        DataView.from(zone)
      )
    }
  }

  /** Keeps an original temporal type as is. */
  def asTemporal(value: DataView, context: TemplateContext): TemporalAccessor =
    if (value.isNull) {
      throw new UnsupportedOperationException("Cannot convert null to TemporalAccessor")
    } else
      value.view match {
        case ta: TemporalAccessor => ta
        case _ =>
          throw new UnsupportedOperationException(
            s"Cannot convert ${value.view.getClass.getName} to TemporalAccessor"
          )
      }

  /** Ruby has a single date type, and its equivalent is ZonedDateTime. */
  def asRubyDate(value: DataView, context: TemplateContext): ZonedDateTime = {
    val defaultZone = ZoneId.systemDefault()
    if (value.isNull) ZonedDateTime.now(defaultZone)
    else
      value.view match {
        case ta: TemporalAccessor => toZonedDateTime(ta, defaultZone)
        case _ => ZonedDateTime.now(defaultZone)
      }
  }

  /** Converts a TemporalAccessor to ZonedDateTime, filling in missing parts from `now`. */
  private def toZonedDateTime(ta: TemporalAccessor, defaultZone: ZoneId): ZonedDateTime =
    ta match {
      case null => ZonedDateTime.now(defaultZone)
      case zdt:  ZonedDateTime  => zdt
      case inst: Instant        => ZonedDateTime.ofInstant(inst, defaultZone)
      case odt:  OffsetDateTime => odt.toZonedDateTime
      case ldt:  LocalDateTime  => ldt.atZone(defaultZone)
      case ld:   LocalDate      => ld.atStartOfDay(defaultZone)
      case _ =>
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

  /** Mimic ruby's BigDecimal.to_f with standard java capabilities. Ensures at least 1 decimal place (e.g., 5.0 not 5). */
  def asFormattedNumber(bd: BigDecimal): BigDecimal = {
    val s = bd.toString
    if (s.contains('.')) {
      val dotIdx   = s.indexOf('.')
      val intPart  = s.substring(0, dotIdx)
      var fracPart = s.substring(dotIdx + 1)
      while (fracPart.length > 1 && fracPart.charAt(fracPart.length - 1) == '0')
        fracPart = fracPart.substring(0, fracPart.length - 1)
      PlainBigDecimal(intPart + "." + fracPart)
    } else {
      PlainBigDecimal(s + ".0")
    }
  }
}
