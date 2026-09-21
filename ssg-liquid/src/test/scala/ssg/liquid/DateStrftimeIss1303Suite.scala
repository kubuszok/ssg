/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import java.time.ZoneId
import java.util.{ HashMap => JHashMap, Locale }

/** Directive-battery test for strftime formatting faithfulness to Ruby/strftime4j.
  *
  * Ground-truth values verified with: ruby -e 'require "time"; t = Time.new(2026,1,5,13,7,9,"+00:00"); puts t.strftime("%C")' (and similar for each directive)
  *
  * Reference: liqp Date.java:63 delegates to StrftimeFormatter (strftime4j, Ruby/C semantics).
  *
  * ISS-1303: Date.scala strftime directives diverge from Ruby/strftime4j for ~16 directives.
  */
final class DateStrftimeIss1303Suite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // Helper: render a strftime format against a fixed epoch timestamp in UTC
  // ---------------------------------------------------------------------------

  private val utcZone = ZoneId.of("UTC")

  /** Format an epoch-seconds timestamp with the given strftime pattern, in UTC with ENGLISH locale. */
  private def fmt(epochSeconds: Long, pattern: String): String = {
    val vars = new JHashMap[String, DataView]()
    vars.put("ts", TestHelper.dv(java.lang.Long.valueOf(epochSeconds)))
    val parser = new TemplateParser.Builder().withDefaultTimeZone(utcZone).withLocale(Locale.ENGLISH).build()
    parser.parse(s"{{ ts | date: '$pattern' }}").render(vars)
  }

  // Fixed timestamps (UTC):
  //   2026-01-05 13:07:09 = Monday    epoch = 1767618429
  //   2026-01-04 09:03:05 = Sunday    epoch = 1767517385
  //   2026-01-01 03:05:00 = Thursday  epoch = 1767236700
  //   2025-12-31 23:59:59 = Wednesday epoch = 1767225599

  // Ruby: Time.new(2026,1,5,13,7,9,"+00:00").to_i => 1767618429
  private val mondayEpoch = 1767618429L
  // Ruby: Time.new(2026,1,4,9,3,5,"+00:00").to_i => 1767517385
  private val sundayEpoch = 1767517385L
  // Ruby: Time.new(2026,1,1,3,5,0,"+00:00").to_i => 1767236700
  private val thursdayEpoch = 1767236700L
  // Ruby: Time.new(2025,12,31,23,59,59,"+00:00").to_i => 1767225599
  private val wedEndOfYear = 1767225599L

  // ---------------------------------------------------------------------------
  // (a) Monday 2026-01-05 13:07:09 UTC — divergent directives
  // ---------------------------------------------------------------------------

  // ruby: Time.new(2026,1,5,13,7,9,"+00:00").strftime("%C") => "20"
  test("ISS-1303: %C century = year/100, zero-padded (2026 -> 20)") {
    assertEquals(fmt(mondayEpoch, "%C"), "20")
  }

  // ruby: => "1"
  test("ISS-1303: %u weekday Mon=1..Sun=7 (Monday -> 1)") {
    assertEquals(fmt(mondayEpoch, "%u"), "1")
  }

  // ruby: => "1"
  test("ISS-1303: %w weekday Sun=0..Sat=6 (Monday -> 1)") {
    assertEquals(fmt(mondayEpoch, "%w"), "1")
  }

  // ruby: => "01"
  test("ISS-1303: %U week number Sunday-start (2026-01-05 -> 01)") {
    assertEquals(fmt(mondayEpoch, "%U"), "01")
  }

  // ruby: => "01"
  test("ISS-1303: %W week number Monday-start (2026-01-05 -> 01)") {
    assertEquals(fmt(mondayEpoch, "%W"), "01")
  }

  // ruby: => "02"
  test("ISS-1303: %V ISO week (2026-01-05 -> 02)") {
    assertEquals(fmt(mondayEpoch, "%V"), "02")
  }

  // ruby: => "2026"
  test("ISS-1303: %G ISO week-based year (2026-01-05 -> 2026)") {
    assertEquals(fmt(mondayEpoch, "%G"), "2026")
  }

  // ruby: => "26"
  test("ISS-1303: %g ISO week-based year last 2 digits (2026-01-05 -> 26)") {
    assertEquals(fmt(mondayEpoch, "%g"), "26")
  }

  // ruby: => " 5" (space + 5)
  test("ISS-1303: %e day space-padded width 2 (5 -> ' 5')") {
    assertEquals(fmt(mondayEpoch, "%e"), " 5")
  }

  // ruby: => "13"
  test("ISS-1303: %k hour-24 space-padded (13 -> '13')") {
    assertEquals(fmt(mondayEpoch, "%k"), "13")
  }

  // ruby: => " 1" (1 PM in 12-hour = 1)
  test("ISS-1303: %l hour-12 space-padded (13 -> ' 1')") {
    assertEquals(fmt(mondayEpoch, "%l"), " 1")
  }

  // ruby: => "pm"
  test("ISS-1303: %P lowercase am/pm (13:07 -> 'pm')") {
    assertEquals(fmt(mondayEpoch, "%P"), "pm")
  }

  // ruby: => "PM"
  test("ISS-1303: %p uppercase AM/PM (13:07 -> 'PM')") {
    assertEquals(fmt(mondayEpoch, "%p"), "PM")
  }

  // ruby: => " 5-JAN-2026"
  test("ISS-1303: %v composite space-pad day + uppercase month (-> ' 5-JAN-2026')") {
    assertEquals(fmt(mondayEpoch, "%v"), " 5-JAN-2026")
  }

  // ruby: => "Mon Jan  5 13:07:09 2026"
  test("ISS-1303: %c composite date/time with space-padded day") {
    assertEquals(fmt(mondayEpoch, "%c"), "Mon Jan  5 13:07:09 2026")
  }

  // ruby: Time.new(2026,1,5,13,7,9,"+00:00").strftime("%s") => "1767618429"
  test("ISS-1303: %s epoch seconds") {
    assertEquals(fmt(mondayEpoch, "%s"), "1767618429")
  }

  // ---------------------------------------------------------------------------
  // (b) Sunday 2026-01-04 09:03:05 UTC
  // ---------------------------------------------------------------------------

  // ruby: => "7"
  test("ISS-1303: %u on Sunday = 7") {
    assertEquals(fmt(sundayEpoch, "%u"), "7")
  }

  // ruby: => "0"
  test("ISS-1303: %w on Sunday = 0") {
    assertEquals(fmt(sundayEpoch, "%w"), "0")
  }

  // ruby: => "01"  (Sunday Jan 4 starts week 1 under Sunday-start)
  test("ISS-1303: %U on Sunday 2026-01-04 = 01") {
    assertEquals(fmt(sundayEpoch, "%U"), "01")
  }

  // ruby: => "00"
  test("ISS-1303: %W on Sunday 2026-01-04 = 00") {
    assertEquals(fmt(sundayEpoch, "%W"), "00")
  }

  // ruby: => "01"
  test("ISS-1303: %V on Sunday 2026-01-04 = 01") {
    assertEquals(fmt(sundayEpoch, "%V"), "01")
  }

  // ---------------------------------------------------------------------------
  // (c) Year-boundary: Thursday 2026-01-01 and Wednesday 2025-12-31
  // ---------------------------------------------------------------------------

  // ruby: => "00"
  test("ISS-1303: %U on Thursday 2026-01-01 = 00") {
    assertEquals(fmt(thursdayEpoch, "%U"), "00")
  }

  // ruby: => "00"
  test("ISS-1303: %W on Thursday 2026-01-01 = 00") {
    assertEquals(fmt(thursdayEpoch, "%W"), "00")
  }

  // ruby: Time.new(2026,1,1,3,5,0,"+00:00").strftime("%V") => "01"
  test("ISS-1303: %V on Thursday 2026-01-01 = 01 (ISO week)") {
    assertEquals(fmt(thursdayEpoch, "%V"), "01")
  }

  // ruby: Time.new(2025,12,31,23,59,59,"+00:00").strftime("%V") => "01"
  // (2025-12-31 belongs to ISO week 1 of 2026)
  test("ISS-1303: %V on 2025-12-31 = 01 (rolls to next ISO year)") {
    assertEquals(fmt(wedEndOfYear, "%V"), "01")
  }

  // ruby: => "2026"
  test("ISS-1303: %G on 2025-12-31 = 2026 (ISO week-based year rolls forward)") {
    assertEquals(fmt(wedEndOfYear, "%G"), "2026")
  }

  // ruby: => "26"
  test("ISS-1303: %g on 2025-12-31 = 26") {
    assertEquals(fmt(wedEndOfYear, "%g"), "26")
  }

  // ruby: => "52"
  test("ISS-1303: %U on 2025-12-31 = 52") {
    assertEquals(fmt(wedEndOfYear, "%U"), "52")
  }

  // ruby: => "52"
  test("ISS-1303: %W on 2025-12-31 = 52") {
    assertEquals(fmt(wedEndOfYear, "%W"), "52")
  }

  // ---------------------------------------------------------------------------
  // (d) Space-pad, zero-pad, no-pad flag modifiers
  // ---------------------------------------------------------------------------

  // ruby: Time.new(2026,1,5,13,7,9,"+00:00").strftime("%_d") => " 5"
  test("ISS-1303: %_d space-pad day (5 -> ' 5')") {
    assertEquals(fmt(mondayEpoch, "%_d"), " 5")
  }

  // ruby: => "05"
  test("ISS-1303: %0e zero-pad day (normally space-padded, forced zero -> '05')") {
    assertEquals(fmt(mondayEpoch, "%0e"), "05")
  }

  // ruby: => "5"
  test("ISS-1303: %-d no-pad day (5 -> '5')") {
    assertEquals(fmt(mondayEpoch, "%-d"), "5")
  }

  // ruby: => "5"
  test("ISS-1303: %-e no-pad day (5 -> '5')") {
    assertEquals(fmt(mondayEpoch, "%-e"), "5")
  }

  // ruby: Time.new(2026,1,4,9,3,5,"+00:00").strftime("%_H") => " 9"
  test("ISS-1303: %_H space-pad hour (9 -> ' 9')") {
    assertEquals(fmt(sundayEpoch, "%_H"), " 9")
  }

  // ruby: => "9"
  test("ISS-1303: %-H no-pad hour (9 -> '9')") {
    assertEquals(fmt(sundayEpoch, "%-H"), "9")
  }

  // ruby: => "09"
  test("ISS-1303: %0k zero-pad hour-24 (normally space, forced zero -> '09')") {
    assertEquals(fmt(sundayEpoch, "%0k"), "09")
  }

  // ruby: => "9"
  test("ISS-1303: %-k no-pad hour-24 (9 -> '9')") {
    assertEquals(fmt(sundayEpoch, "%-k"), "9")
  }

  // ruby: Time.new(2026,1,5,13,7,9,"+00:00").strftime("%_l") => " 1"
  test("ISS-1303: %_l space-pad hour-12 (already space, stays ' 1')") {
    assertEquals(fmt(mondayEpoch, "%_l"), " 1")
  }

  // ruby: Time.new(2026,1,5,13,7,9,"+00:00").strftime("%_m") => " 1"
  test("ISS-1303: %_m space-pad month (1 -> ' 1')") {
    assertEquals(fmt(mondayEpoch, "%_m"), " 1")
  }

  // ---------------------------------------------------------------------------
  // (e) Nanosecond width modifiers %3N, %6N, %9N
  // ---------------------------------------------------------------------------

  // With 0 nanoseconds (epoch-second input has no sub-second):
  // ruby: Time.new(2026,1,5,13,7,9,"+00:00").strftime("%3N") => "000"
  test("ISS-1303: %3N nanoseconds truncated to 3 digits (-> '000')") {
    assertEquals(fmt(mondayEpoch, "%3N"), "000")
  }

  // ruby: => "000000"
  test("ISS-1303: %6N nanoseconds truncated to 6 digits (-> '000000')") {
    assertEquals(fmt(mondayEpoch, "%6N"), "000000")
  }

  // ruby: => "000000000"
  test("ISS-1303: %9N nanoseconds 9 digits (-> '000000000')") {
    assertEquals(fmt(mondayEpoch, "%9N"), "000000000")
  }

  // ruby: => "000000000"
  test("ISS-1303: %N nanoseconds default 9 digits (-> '000000000')") {
    assertEquals(fmt(mondayEpoch, "%N"), "000000000")
  }

  // ---------------------------------------------------------------------------
  // (f) %s epoch seconds on different dates
  // ---------------------------------------------------------------------------

  // ruby: Time.new(2026,1,1,3,5,0,"+00:00").to_i => 1767236700
  test("ISS-1303: %s epoch seconds for 2026-01-01 03:05:00 UTC") {
    assertEquals(fmt(thursdayEpoch, "%s"), "1767236700")
  }

  // ruby: Time.new(2025,12,31,23,59,59,"+00:00").to_i => 1767225599
  test("ISS-1303: %s epoch seconds for 2025-12-31 23:59:59 UTC") {
    assertEquals(fmt(wedEndOfYear, "%s"), "1767225599")
  }

  // ---------------------------------------------------------------------------
  // Faithful directives remain correct (sanity check)
  // ---------------------------------------------------------------------------

  test("ISS-1303: faithful directives unchanged (%Y %m %d %H %M %S)") {
    assertEquals(fmt(mondayEpoch, "%Y"), "2026")
    assertEquals(fmt(mondayEpoch, "%m"), "01")
    assertEquals(fmt(mondayEpoch, "%d"), "05")
    assertEquals(fmt(mondayEpoch, "%H"), "13")
    assertEquals(fmt(mondayEpoch, "%M"), "07")
    assertEquals(fmt(mondayEpoch, "%S"), "09")
  }

  test("ISS-1303: faithful composites unchanged (%F %T %D %R)") {
    assertEquals(fmt(mondayEpoch, "%F"), "2026-01-05")
    assertEquals(fmt(mondayEpoch, "%T"), "13:07:09")
    assertEquals(fmt(mondayEpoch, "%D"), "01/05/26")
    assertEquals(fmt(mondayEpoch, "%R"), "13:07")
  }

  test("ISS-1303: %% literal percent") {
    assertEquals(fmt(mondayEpoch, "%%"), "%")
  }

  test("ISS-1303: %n newline, %t tab") {
    assertEquals(fmt(mondayEpoch, "%n"), "\n")
    assertEquals(fmt(mondayEpoch, "%t"), "\t")
  }
}
