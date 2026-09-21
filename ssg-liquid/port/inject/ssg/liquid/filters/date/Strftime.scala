/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Date.java, which formats a date by handing the
 *   pattern to ua.co.k.strftime.StrftimeFormatter (strftime4j)
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 * Adapted from the hand-written Scala port in ssg (Apache-2.0, SSG contributors)
 *
 * Migration notes:
 *   Origin: strftime4j is a JVM-only artifact, so the strftime pattern is evaluated here.
 *   Convention: Ruby/C strftime, which is what strftime4j implements and what liquid's own
 *     date filter documents. Follows Ruby per ISS-1303 where strftime4j differs: `%C`, `%e`,
 *     `%v`, `%c`, the ISO week-year at a year boundary (`%V` `%G` `%g`) and the pad flags
 *     (`%0e`, `%0k`, `%-k`) — strftime4j answers `21`, an unpadded day, week 53 of the old
 *     year, and ignores the flags.
 *   Idiom: the directives java.time can spell go through one DateTimeFormatter pattern, built
 *     up as the scan runs and flushed whenever a directive has to be computed by hand.
 */
package ssg
package liquid
package filters
package date

import java.time.{ Instant, ZonedDateTime }
import java.time.format.DateTimeFormatter
import java.time.temporal.{ ChronoField, IsoFields, TemporalAccessor }

/** Evaluates a strftime pattern against a date, and pads a field the way strftime4j padded it. */
object Strftime {

  /** strftime4j's own left-pad, which one of liqp's own tests calls directly. */
  def padLeft(input: String, count: Int, padSymbol: Char): String =
    if (input.length >= count) input else String.valueOf(padSymbol) * (count - input.length) + input

  /** Formats a date by a strftime pattern.
    *
    * The directives java.time can spell (%Y %y %m %d %H %M %S %L %I %j %z %:z %::z %A %a %B %b %Z
    * %p %n %t %% %D %F %T %R %r %x %X) go through a DateTimeFormatter at the given locale; the rest
    * (%C %u %w %U %W %V %G %g %e %k %l %P %N %s %v %c and every pad flag) are computed from
    * java.time fields, locale-invariantly, which is what Ruby and strftime4j do.
    */
  def format(format: String, temporal: TemporalAccessor, locale: java.util.Locale): String = {
    val out        = new StringBuilder()
    val patternBuf = new StringBuilder() // accumulates faithful DateTimeFormatter pattern segments
    var i          = 0

    // Flush any accumulated faithful pattern via DateTimeFormatter
    def flushPattern(): Unit =
      if (patternBuf.nonEmpty) {
        val formatter = DateTimeFormatter.ofPattern(patternBuf.toString(), locale)
        out.append(formatter.format(temporal))
        patternBuf.setLength(0)
      }

    // Query a ChronoField defensively from the TemporalAccessor
    def queryField(field: ChronoField, fallback: Int): Int =
      if (temporal.isSupported(field)) temporal.get(field)
      else fallback

    // Day-of-week as ISO Mon=1..Sun=7 (ChronoField.DAY_OF_WEEK uses this encoding)
    def dayOfWeekIso: Int = queryField(ChronoField.DAY_OF_WEEK, 1)

    // Day-of-year (1-366)
    def dayOfYear: Int = queryField(ChronoField.DAY_OF_YEAR, 1)

    // Compute the hour in 12-hour format (1-12), matching Ruby %l/%I semantics
    def hour12: Int = {
      val h   = queryField(ChronoField.HOUR_OF_DAY, 0)
      val h12 = h % 12
      if (h12 == 0) 12 else h12
    }

    // Zero-pad an integer to the given width
    def zeroPad(value: Int, width: Int): String = {
      val s = value.toString
      if (s.length >= width) s
      else "0" * (width - s.length) + s
    }

    // Space-pad an integer to the given width
    def spacePad(value: Int, width: Int): String = {
      val s = value.toString
      if (s.length >= width) s
      else " " * (width - s.length) + s
    }

    // %U: week number, Sunday-start (Sun=0..Sat=6), range 00-53
    // Formula: wday = dayOfWeekIso % 7 (Sun=0); U = (dayOfYear + 6 - wday) / 7
    def weekSundayStart: Int = {
      val wday = dayOfWeekIso % 7 // convert ISO Mon=1..Sun=7 -> Sun=0..Sat=6
      (dayOfYear + 6 - wday) / 7
    }

    // %W: week number, Monday-start (Mon=0..Sun=6), range 00-53
    // Formula: wdayMon = (dayOfWeekIso + 6) % 7 (Mon=0); W = (dayOfYear + 6 - wdayMon) / 7
    def weekMondayStart: Int = {
      val wdayMon = (dayOfWeekIso + 6) % 7 // convert ISO Mon=1..Sun=7 -> Mon=0..Sun=6
      (dayOfYear + 6 - wdayMon) / 7
    }

    // Epoch seconds from the temporal (needs zone information)
    def epochSeconds: Long =
      try
        ZonedDateTime.from(temporal).toEpochSecond
      catch {
        case _: java.time.DateTimeException =>
          try
            Instant.from(temporal).getEpochSecond
          catch {
            case _: java.time.DateTimeException => 0L
          }
      }

    // Emit a directive value with a given default padding (zero or space) and width,
    // potentially overridden by a pad-flag modifier.
    // padFlag: None = use default, Some('-') = no pad, Some('_') = space pad, Some('0') = zero pad
    def emitPadded(value: Int, defaultPad: Char, width: Int, padFlag: Option[Char]): Unit = {
      flushPattern()
      val effectivePad = padFlag.getOrElse(defaultPad)
      effectivePad match {
        case '-' => out.append(value.toString)
        case '_' => out.append(spacePad(value, width))
        case '0' => out.append(zeroPad(value, width))
        case _   => out.append(zeroPad(value, width))
      }
    }

    // Handle a single directive (after consuming the '%' and optional pad flag).
    // Returns the number of additional characters consumed from format (beyond the spec char).
    def handleDirective(spec: Char, padFlag: Option[Char]): Int = {
      spec match {
        // --- Faithful directives: accumulate into pattern buffer ---
        case 'Y' => patternBuf.append("yyyy"); 0
        case 'y' => patternBuf.append("yy"); 0
        case 'm' =>
          padFlag match {
            case None       => patternBuf.append("MM"); 0
            case Some(flag) =>
              emitPadded(queryField(ChronoField.MONTH_OF_YEAR, 1), '0', 2, Some(flag)); 0
          }
        case 'd' =>
          padFlag match {
            case None       => patternBuf.append("dd"); 0
            case Some(flag) =>
              emitPadded(queryField(ChronoField.DAY_OF_MONTH, 1), '0', 2, Some(flag)); 0
          }
        case 'H' =>
          padFlag match {
            case None       => patternBuf.append("HH"); 0
            case Some(flag) =>
              emitPadded(queryField(ChronoField.HOUR_OF_DAY, 0), '0', 2, Some(flag)); 0
          }
        case 'M' =>
          padFlag match {
            case None       => patternBuf.append("mm"); 0
            case Some(flag) =>
              emitPadded(queryField(ChronoField.MINUTE_OF_HOUR, 0), '0', 2, Some(flag)); 0
          }
        case 'S' =>
          padFlag match {
            case None       => patternBuf.append("ss"); 0
            case Some(flag) =>
              emitPadded(queryField(ChronoField.SECOND_OF_MINUTE, 0), '0', 2, Some(flag)); 0
          }
        case 'L' => patternBuf.append("SSS"); 0
        case 'I' =>
          padFlag match {
            case None       => patternBuf.append("hh"); 0
            case Some(flag) => emitPadded(hour12, '0', 2, Some(flag)); 0
          }
        case 'j'       => patternBuf.append("DDD"); 0
        case 'z'       => patternBuf.append("XX"); 0
        case 'A'       => patternBuf.append("EEEE"); 0
        case 'a'       => patternBuf.append("EEE"); 0
        case 'B'       => patternBuf.append("MMMM"); 0
        case 'b' | 'h' => patternBuf.append("MMM"); 0
        // %Z is the zone's FULL name — `Pacific Daylight Time`, which is what strftime4j and
        // ruby answer; `z` alone renders the abbreviation `PDT`.
        case 'Z'       => patternBuf.append("zzzz"); 0
        case 'p'       => patternBuf.append("a"); 0 // uppercase AM/PM via locale
        case 'n'       => patternBuf.append("\n"); 0
        case 't'       => patternBuf.append("\t"); 0
        case '%'       => patternBuf.append("'%'"); 0
        // Composite faithful patterns
        case 'D' => patternBuf.append("MM/dd/yy"); 0 // %m/%d/%y
        case 'F' => patternBuf.append("yyyy-MM-dd"); 0 // %Y-%m-%d
        case 'T' => patternBuf.append("HH:mm:ss"); 0 // %H:%M:%S
        case 'R' => patternBuf.append("HH:mm"); 0 // %H:%M
        case 'r' => patternBuf.append("hh:mm:ss a"); 0 // %I:%M:%S %p
        case 'x' => patternBuf.append("MM/dd/yy"); 0 // locale date
        case 'X' => patternBuf.append("HH:mm:ss"); 0 // locale time
        case '+' => patternBuf.append("EEE MMM d HH:mm:ss z yyyy"); 0 // date(1) format

        // --- Divergent directives: compute directly ---

        // %C: century = year / 100, zero-padded to 2 digits
        case 'C' =>
          val year = queryField(ChronoField.YEAR, 0)
          emitPadded(year / 100, '0', 2, padFlag)
          0

        // %u: weekday Mon=1..Sun=7 (ISO encoding, matches ChronoField.DAY_OF_WEEK)
        case 'u' =>
          flushPattern()
          out.append(dayOfWeekIso.toString)
          0

        // %w: weekday Sun=0..Sat=6
        case 'w' =>
          flushPattern()
          out.append((dayOfWeekIso % 7).toString)
          0

        // %U: week number, Sunday-start, 00-53
        case 'U' =>
          emitPadded(weekSundayStart, '0', 2, padFlag)
          0

        // %W: week number, Monday-start, 00-53
        case 'W' =>
          emitPadded(weekMondayStart, '0', 2, padFlag)
          0

        // %V: ISO 8601 week number, 01-53
        case 'V' =>
          val isoWeek =
            if (temporal.isSupported(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
              temporal.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            else 1
          emitPadded(isoWeek, '0', 2, padFlag)
          0

        // %G: ISO 8601 week-based year, 4 digits
        case 'G' =>
          flushPattern()
          val isoYear =
            if (temporal.isSupported(IsoFields.WEEK_BASED_YEAR))
              temporal.get(IsoFields.WEEK_BASED_YEAR)
            else queryField(ChronoField.YEAR, 0)
          out.append(zeroPad(isoYear, 4))
          0

        // %g: ISO 8601 week-based year, last 2 digits
        case 'g' =>
          flushPattern()
          val isoYear =
            if (temporal.isSupported(IsoFields.WEEK_BASED_YEAR))
              temporal.get(IsoFields.WEEK_BASED_YEAR)
            else queryField(ChronoField.YEAR, 0)
          out.append(zeroPad(isoYear % 100, 2))
          0

        // %e: day of month, space-padded to width 2
        // ISS-1303: strftime4j writes the day UNPADDED (liqp's own test compares `%e` against
        // `SimpleDateFormat("d")`); ssg follows Ruby, which pads it with a space.
        case 'e' =>
          emitPadded(queryField(ChronoField.DAY_OF_MONTH, 1), '_', 2, padFlag)
          0

        // %k: hour (24h), space-padded to width 2
        case 'k' =>
          emitPadded(queryField(ChronoField.HOUR_OF_DAY, 0), '_', 2, padFlag)
          0

        // %l: hour (12h, 1-12), space-padded to width 2
        case 'l' =>
          emitPadded(hour12, '_', 2, padFlag)
          0

        // %P: lowercase am/pm, computed locale-invariantly
        case 'P' =>
          flushPattern()
          val h = queryField(ChronoField.HOUR_OF_DAY, 0)
          out.append(if (h < 12) "am" else "pm")
          0

        // %N: nanoseconds; honors width modifier %3N, %6N, %9N (default 9)
        case 'N' =>
          flushPattern()
          val nano    = queryField(ChronoField.NANO_OF_SECOND, 0)
          val nanoStr = zeroPad(nano, 9)
          // Check if the next char is a digit width modifier — already handled by caller for %3N etc.
          // Default width is 9 when no width prefix was given
          val width = padFlag match {
            case Some(c) if c >= '1' && c <= '9' => c - '0'
            case _                               => 9
          }
          if (width <= 9) out.append(nanoStr.substring(0, width))
          else out.append(nanoStr + "0" * (width - 9))
          0

        // %s: epoch seconds
        case 's' =>
          flushPattern()
          out.append(epochSeconds.toString)
          0

        // %v: composite = %e-%^b-%Y = space-pad day + "-" + UPPERCASE abbreviated month + "-" + 4-digit year
        case 'v' =>
          flushPattern()
          val day   = queryField(ChronoField.DAY_OF_MONTH, 1)
          val month = DateTimeFormatter.ofPattern("MMM", locale).format(temporal).toUpperCase(locale)
          val year  = queryField(ChronoField.YEAR, 0)
          out.append(spacePad(day, 2)).append("-").append(month).append("-").append(zeroPad(year, 4))
          0

        // %c: composite = %a %b %e %T %Y = "Mon Jan  5 13:07:09 2026"
        case 'c' =>
          flushPattern()
          val dayName   = DateTimeFormatter.ofPattern("EEE", locale).format(temporal)
          val monthName = DateTimeFormatter.ofPattern("MMM", locale).format(temporal)
          val day       = queryField(ChronoField.DAY_OF_MONTH, 1)
          val time      = DateTimeFormatter.ofPattern("HH:mm:ss", locale).format(temporal)
          val year      = queryField(ChronoField.YEAR, 0)
          // ISS-1303: strftime4j's `%c` is `EEE MMM d HH:mm:ss yyyy`, the day unpadded; ssg follows
          // Ruby, whose `%c` holds `%e` — the day space-padded to width 2
          out.append(dayName).append(" ").append(monthName).append(" ").append(spacePad(day, 2)).append(" ").append(time).append(" ").append(zeroPad(year, 4))
          0

        // Timezone with colons: %:z, %::z, %:::z
        case ':' =>
          // This is handled inline since it peeks ahead
          -1 // sentinel: handled by caller

        case other =>
          flushPattern()
          out.append('%').append(other)
          0
      }
    }

    // whether the pattern currently ends inside an open run of quoted literal characters
    var literalOpen = false

    while (i < format.length()) {
      val c = format.charAt(i)
      if (c == '%' && i + 1 < format.length()) {
        literalOpen = false
        i += 1
        var spec = format.charAt(i)

        // Check for pad-flag modifiers: %-X, %_X, %0X
        // Also check for digit-width prefix for %N: %3N, %6N, %9N
        spec match {
          case '-' | '_' | '0' if i + 1 < format.length() =>
            val flag = spec
            i += 1
            spec = format.charAt(i)
            val consumed = handleDirective(spec, Some(flag))
            i += consumed

          // Width prefix for %N: %<digit>N
          case d if d >= '1' && d <= '9' && i + 1 < format.length() && format.charAt(i + 1) == 'N' =>
            i += 1 // skip past the digit to 'N'
            // Pass the digit as the "pad flag" so handleDirective can use it as width
            handleDirective('N', Some(d))

          // Timezone with colons: %:z, %::z, %:::z
          case ':' =>
            if (i + 1 < format.length() && format.charAt(i + 1) == ':') {
              i += 1
              if (i + 1 < format.length() && format.charAt(i + 1) == ':') {
                i += 1
                if (i + 1 < format.length() && format.charAt(i + 1) == 'z') {
                  i += 1; patternBuf.append("XXX") // %:::z minimal tz
                } else { flushPattern(); out.append("%:::") }
              } else if (i + 1 < format.length() && format.charAt(i + 1) == 'z') {
                i += 1; patternBuf.append("XXXXX") // %::z +HH:MM:SS
              } else { flushPattern(); out.append("%::") }
            } else if (i + 1 < format.length() && format.charAt(i + 1) == 'z') {
              i += 1; patternBuf.append("XXX") // %:z +HH:MM
            } else { flushPattern(); out.append("%:") }

          case _ =>
            val consumed = handleDirective(spec, None)
            i += consumed
        }
      } else {
        // Literal character — quote letters for DateTimeFormatter safety
        if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
          // ONE quoted run per stretch of literal letters. Quoting each letter on its own gives
          // `'m''u'`, and DateTimeFormatter reads the `''` in the middle as an escaped quote —
          // `date: 'mu'` rendered `m'u` where strftime4j rendered `mu`.
          if (literalOpen) {
            patternBuf.setLength(patternBuf.length - 1)
            patternBuf.append(c).append("'")
          } else {
            patternBuf.append("'").append(c).append("'")
            literalOpen = true
          }
        } else {
          patternBuf.append(c)
          literalOpen = false
        }
      }
      i += 1
    }
    flushPattern()
    out.toString()
  }
}
