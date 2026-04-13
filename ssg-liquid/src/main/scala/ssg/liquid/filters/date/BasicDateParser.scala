/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/date/BasicDateParser.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.date → ssg.liquid.filters.date
 *   Convention: Abstract Java class → abstract Scala class
 *   Idiom: Uses Nullable[ZonedDateTime] instead of null returns
 */
package ssg
package liquid
package filters
package date

import ssg.liquid.Nullable

import java.time.*
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.{ ChronoField, TemporalAccessor, TemporalQueries }
import java.util.{ ArrayList, List => JList, Locale }

import scala.util.boundary
import scala.util.boundary.break

/** Abstract base for date parsers with cached pattern fallback.
  *
  * Subclasses provide the `parse` method. The base class caches patterns and provides `parseUsingCachedPatterns` which tries each pattern in order.
  */
abstract class BasicDateParser {

  private val cachedPatterns: ArrayList[String] = new ArrayList()

  protected def this(patterns: JList[String]) = {
    this()
    cachedPatterns.addAll(patterns)
  }

  protected def storePattern(pattern: String): Unit =
    cachedPatterns.add(pattern)

  /** Parse a date string into a ZonedDateTime.
    *
    * @return
    *   the parsed date, or `Nullable.empty` if unparseable
    */
  def parse(valAsString: String, locale: Locale, timeZone: ZoneId): Nullable[ZonedDateTime]

  /** Tries each cached pattern until one parses successfully. */
  protected def parseUsingCachedPatterns(str: String, locale: Locale, defaultZone: ZoneId): Nullable[ZonedDateTime] = boundary {
    var i = 0
    while (i < cachedPatterns.size()) {
      try {
        val temporalAccessor = parseUsingPattern(str, cachedPatterns.get(i), locale)
        break(Nullable(BasicDateParser.getZonedDateTimeFromTemporalAccessor(temporalAccessor, defaultZone)))
      } catch {
        case _: Exception => // ignore, try next pattern
      }
      i += 1
    }
    // Could not parse the string into a meaningful date
    Nullable.empty
  }

  protected def parseUsingPattern(normalized: String, pattern: String, locale: Locale): TemporalAccessor = {
    val timeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(locale)
    timeFormatter.parse(normalized)
  }
}

object BasicDateParser {

  /** Follow ruby rules: if some datetime part is missing, the default is taken from `now` with default zone.
    */
  def getZonedDateTimeFromTemporalAccessor(temporal: TemporalAccessor, defaultZone: ZoneId): ZonedDateTime =
    if (temporal == null) {
      ZonedDateTime.now(defaultZone)
    } else {
      temporal match {
        case zdt:  ZonedDateTime => zdt
        case inst: Instant       => ZonedDateTime.ofInstant(inst, defaultZone)
        case _ =>
          val zoneId = temporal.query(TemporalQueries.zone())
          if (zoneId == null) {
            var localDate = temporal.query(TemporalQueries.localDate())
            var localTime = temporal.query(TemporalQueries.localTime())
            if (localDate == null) localDate = LocalDate.now(defaultZone)
            if (localTime == null) localTime = LocalTime.now(defaultZone)
            ZonedDateTime.of(localDate, localTime, defaultZone)
          } else {
            var now       = LocalDateTime.now(zoneId)
            val copyThese = Array[java.time.temporal.TemporalField](
              ChronoField.YEAR,
              ChronoField.MONTH_OF_YEAR,
              ChronoField.DAY_OF_MONTH,
              ChronoField.HOUR_OF_DAY,
              ChronoField.MINUTE_OF_HOUR,
              ChronoField.SECOND_OF_MINUTE,
              ChronoField.NANO_OF_SECOND
            )
            var i = 0
            while (i < copyThese.length) {
              val tf = copyThese(i)
              if (temporal.isSupported(tf)) {
                now = now.`with`(tf, temporal.get(tf).toLong)
              }
              i += 1
            }
            now.atZone(zoneId)
          }
      }
    }
}
