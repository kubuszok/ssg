/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/date/FuzzyDateDateParser.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.date → ssg.liquid.filters.date
 *   Convention: Skeleton implementation preserved from original
 *   Note: The original Java source has unfinished fuzzy parsing
 *     (reconstructPattern returns null, parsePart returns empty list).
 *     Ported faithfully — this class is only useful for its cached-pattern
 *     fallback inherited from BasicDateParser.
 */
package ssg
package liquid
package filters
package date

import ssg.liquid.Nullable

import java.time.{ ZoneId, ZonedDateTime }
import java.util.{ ArrayList, Locale }

/** Fuzzy date parser that attempts heuristic date detection.
  *
  * Falls back to cached pattern matching from BasicDateParser. The heuristic fuzzy parsing is an unfinished skeleton in the original liqp source.
  */
class FuzzyDateDateParser extends BasicDateParser {

  override def parse(valAsString: String, locale: Locale, defaultZone: ZoneId): Nullable[ZonedDateTime] = {
    val normalized = valAsString.toLowerCase
    val zonedDateTime = parseUsingCachedPatterns(normalized, locale, defaultZone)
    if (zonedDateTime.isDefined) {
      zonedDateTime
    } else {
      val parts = new ArrayList[FuzzyDateDateParser.Part]()
      // we start as one big single unparsed part
      parts.add(new FuzzyDateDateParser.UnparsedPart(0, normalized.length, normalized))

      // Skeleton: original Java implementation is unfinished
      // (parsePart returns empty, reconstructPattern returns null)
      // Kept for API compatibility
      Nullable.empty
    }
  }
}

object FuzzyDateDateParser {

  private[date] class DateParseContext()

  private[date] enum PartState extends java.lang.Enum[PartState] {
    case UNPARSED, PARSED, KNOWN_CONSTANT, UNRECOGNIZED
  }

  private[date] trait Part {
    def start: Int // before symbol
    def end:   Int // after symbol
    def state: PartState
  }

  private[date] class UnparsedPart(val start: Int, val end: Int, value: String) extends Part {
    override def state: PartState = PartState.UNPARSED
  }

  private[date] enum PartKind extends java.lang.Enum[PartKind] {
    case CONSTANT, YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, MILLISECOND, MICROSECOND, NANOSECOND
  }

  private[date] final case class PartItem(kind: PartKind, pattern: String, start: Int, end: Int)
}
