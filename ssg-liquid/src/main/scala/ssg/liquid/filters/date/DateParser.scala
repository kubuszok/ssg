/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/date/Parser.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.date.Parser → ssg.liquid.filters.date.DateParser
 *     (renamed to avoid clash with ssg.liquid.parser.*)
 *   Convention: Static initializer → companion object val
 *   Idiom: toBeReplaced as immutable Map
 */
package ssg
package liquid
package filters
package date

import ssg.liquid.Nullable

import java.time.{ ZoneId, ZonedDateTime }
import java.util.{ ArrayList, Locale }

/** Default date parser with 63 fallback patterns and ordinal normalization.
  *
  * In case if anyone interesting about full set of supported by ruby date patterns: there no such set as the parsing there happening based on heuristic algorithms. This is how it looks like(~3K lines
  * just for date parse): https://github.com/ruby/ruby/blob/ee102de6d7ec2454dc5da223483737478eb7bcc7/ext/date/date_parse.c
  *
  * And here's python. Just an example how it is violating standard in details regarding timezone representation: https://docs.python.org/3/library/datetime.html#strftime-and-strptime-behavior
  */
class DateParser extends BasicDateParser(DateParser.datePatterns) {

  override def parse(str: String, locale: Locale, defaultZone: ZoneId): Nullable[ZonedDateTime] = {
    var normalized = str.toLowerCase
    for ((ordinal, replacement) <- DateParser.toBeReplaced)
      normalized = normalized.replace(ordinal, replacement)
    parseUsingCachedPatterns(normalized, locale, defaultZone)
  }
}

object DateParser {

  // Since Liquid supports dates like `March 1st`, this list will
  // hold strings that will be removed from the input string.
  private val toBeReplaced: Map[String, String] = Map(
    "1st" -> "1",
    "2nd" -> "2",
    "3rd" -> "3",
    "4th" -> "4",
    "5th" -> "5",
    "6th" -> "6",
    "7th" -> "7",
    "8th" -> "8",
    "9th" -> "9",
    "0th" -> "0"
  )

  val datePatterns: ArrayList[String] = {
    val p = new ArrayList[String]()

    p.add("EEE MMM d hh:mm:ss yyyy")
    p.add("EEE MMM d hh:mm yyyy")
    p.add("yyyy-M-d")
    p.add("d-M-yyyy")
    p.add("d-M-yy")
    p.add("yy-M-d")

    p.add("d/M/yyyy")
    p.add("yyyy/M/d")
    p.add("d/M/yy")
    p.add("yy/M/d")
    p.add("M/yyyy")
    p.add("yyyy/M")
    p.add("M/d")
    p.add("d/M")

    // this is section without `T`, change here and do same change in section below with `T`
    p.add("yyyy-M-d HH:mm")
    p.add("yyyy-M-d HH:mm X")
    p.add("yyyy-M-d HH:mm Z")
    p.add("yyyy-M-d HH:mm z")
    p.add("yyyy-M-d HH:mm'Z'")

    p.add("yyyy-M-d HH:mm:ss")
    p.add("yyyy-M-d HH:mm:ss X")
    p.add("yyyy-M-d HH:mm:ss Z")
    p.add("yyyy-M-d HH:mm:ss z")
    p.add("yyyy-M-d HH:mm:ss'Z'")

    p.add("yyyy-M-d HH:mm:ss.SSS")
    p.add("yyyy-M-d HH:mm:ss.SSS X")
    p.add("yyyy-M-d HH:mm:ss.SSS Z")
    p.add("yyyy-M-d HH:mm:ss.SSS z")
    p.add("yyyy-M-d HH:mm:ss.SSS'Z'")

    p.add("yyyy-M-d HH:mm:ss.SSSSSS")
    p.add("yyyy-M-d HH:mm:ss.SSSSSS X")
    p.add("yyyy-M-d HH:mm:ss.SSSSSS Z")
    p.add("yyyy-M-d HH:mm:ss.SSSSSS z")
    p.add("yyyy-M-d HH:mm:ss.SSSSSS'Z'")

    p.add("yyyy-M-d HH:mm:ss.SSSSSSSSS")
    p.add("yyyy-M-d HH:mm:ss.SSSSSSSSS X")
    p.add("yyyy-M-d HH:mm:ss.SSSSSSSSS Z")
    p.add("yyyy-M-d HH:mm:ss.SSSSSSSSS z")
    p.add("yyyy-M-d HH:mm:ss.SSSSSSSSS'Z'")

    // this is section with `T`
    p.add("yyyy-M-d'T'HH:mm")
    p.add("yyyy-M-d'T'HH:mm X")
    p.add("yyyy-M-d'T'HH:mm Z")
    p.add("yyyy-M-d'T'HH:mm z")
    p.add("yyyy-M-d'T'HH:mm'Z'")

    p.add("yyyy-M-d'T'HH:mm:ss")
    p.add("yyyy-M-d'T'HH:mm:ss X")
    p.add("yyyy-M-d'T'HH:mm:ss Z")
    p.add("yyyy-M-d'T'HH:mm:ss z")
    p.add("yyyy-M-d'T'HH:mm:ss'Z'")

    p.add("yyyy-M-d'T'HH:mm:ss.SSS")
    p.add("yyyy-M-d'T'HH:mm:ss.SSS X")
    p.add("yyyy-M-d'T'HH:mm:ss.SSS Z")
    p.add("yyyy-M-d'T'HH:mm:ss.SSS z")
    p.add("yyyy-M-d'T'HH:mm:ss.SSS'Z'")

    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSS")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSS X")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSS Z")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSS z")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSS'Z'")

    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSSSSS")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSSSSS X")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSSSSS Z")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSSSSS z")
    p.add("yyyy-M-d'T'HH:mm:ss.SSSSSSSSS'Z'")

    p.add("EEE MMM d HH:mm:ss yyyy")
    p.add("EEE, d MMM yyyy HH:mm:ss Z")
    p.add("EEE, d MMM yyyy HH:mm:ss z")
    p.add("MMM d HH:mm:ss yyyy")
    p.add("d MMM yyyy HH:mm:ss Z")
    p.add("d MMM yyyy HH:mm:ss z")
    p.add("yyyy-M-d'T'HH:mm:ssXXX")

    p.add("d MMM")
    p.add("d MMM yy")
    p.add("d MMM yyyy")
    p.add("d MMMM")
    p.add("d MMMM yy")
    p.add("d MMMM yyyy")

    p.add("MMM d")
    p.add("MMM d, yy")
    p.add("MMM d, yyyy")

    p.add("MMMM d")
    p.add("MMMM d, yy")
    p.add("MMMM d, yyyy")

    p.add("MMM")
    p.add("MMM yy")
    p.add("MMM yyyy")

    p.add("MMMM")
    p.add("MMMM yy")
    p.add("MMMM yyyy")

    p.add("H:mm")
    p.add("H:mm:ss")

    p
  }

  /** Adds a new date pattern to the default pattern list. */
  def addDatePattern(pattern: String): Unit = {
    if (pattern == null) throw new NullPointerException("date-pattern cannot be null")
    datePatterns.add(pattern)
  }

  /** Removes a date pattern from the default pattern list. */
  def removeDatePattern(pattern: String): Unit =
    datePatterns.remove(pattern)
}
