/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.time.ZoneId
import java.util.{ HashMap => JHashMap }

final class DateFilterSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private def render(template: String): String =
    Template.parse(template).render()

  // ---------------------------------------------------------------------------
  // "now" and "today" keywords
  // ---------------------------------------------------------------------------

  test("date filter: 'now' keyword produces current year") {
    val result = render("{{ 'now' | date: '%Y' }}")
    assertEquals(result, java.time.Year.now().toString)
  }

  test("date filter: 'today' keyword produces current year") {
    val result = render("{{ 'today' | date: '%Y' }}")
    assertEquals(result, java.time.Year.now().toString)
  }

  test("date filter: 'now' with full date format") {
    val result = render("{{ 'now' | date: '%Y-%m-%d' }}")
    val today  = java.time.LocalDate.now()
    assertEquals(result, today.toString)
  }

  test("date filter: 'now' with %F composite") {
    val result = render("{{ 'now' | date: '%F' }}")
    val today  = java.time.LocalDate.now()
    assertEquals(result, today.toString)
  }

  test("date filter: 'now' with %T produces valid time") {
    val result = render("{{ 'now' | date: '%T' }}")
    assert(result.matches("\\d{2}:\\d{2}:\\d{2}"), s"Expected HH:mm:ss format, got: $result")
  }

  test("date filter: 'now' with %D produces date in MM/dd/yy") {
    val result = render("{{ 'now' | date: '%D' }}")
    assert(result.matches("\\d{2}/\\d{2}/\\d{2}"), s"Expected MM/dd/yy format, got: $result")
  }

  test("date filter: 'now' with %R produces HH:mm") {
    val result = render("{{ 'now' | date: '%R' }}")
    assert(result.matches("\\d{2}:\\d{2}"), s"Expected HH:mm format, got: $result")
  }

  // ---------------------------------------------------------------------------
  // strftime directives via 'now' (bypasses DateParser string parsing)
  // ---------------------------------------------------------------------------

  test("date filter: %Y produces 4-digit year via 'now'") {
    val result = render("{{ 'now' | date: '%Y' }}")
    assert(result.matches("\\d{4}"), s"Expected 4-digit year, got: $result")
  }

  test("date filter: %y produces 2-digit year via 'now'") {
    val result = render("{{ 'now' | date: '%y' }}")
    assert(result.matches("\\d{2}"), s"Expected 2-digit year, got: $result")
  }

  test("date filter: %m produces 2-digit month via 'now'") {
    val result = render("{{ 'now' | date: '%m' }}")
    assert(result.matches("\\d{2}"), s"Expected 2-digit month, got: $result")
  }

  test("date filter: %d produces 2-digit day via 'now'") {
    val result = render("{{ 'now' | date: '%d' }}")
    assert(result.matches("\\d{2}"), s"Expected 2-digit day, got: $result")
  }

  test("date filter: %H produces 2-digit hour via 'now'") {
    val result = render("{{ 'now' | date: '%H' }}")
    assert(result.matches("\\d{2}"), s"Expected 2-digit hour, got: $result")
  }

  test("date filter: %M produces 2-digit minute via 'now'") {
    val result = render("{{ 'now' | date: '%M' }}")
    assert(result.matches("\\d{2}"), s"Expected 2-digit minute, got: $result")
  }

  test("date filter: %S produces 2-digit second via 'now'") {
    val result = render("{{ 'now' | date: '%S' }}")
    assert(result.matches("\\d{2}"), s"Expected 2-digit second, got: $result")
  }

  test("date filter: %B produces full month name via 'now'") {
    val result = render("{{ 'now' | date: '%B' }}")
    val months = Set("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    assert(months.contains(result), s"Expected a month name, got: $result")
  }

  test("date filter: %b produces abbreviated month name via 'now'") {
    val result = render("{{ 'now' | date: '%b' }}")
    val months = Set("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    assert(months.contains(result), s"Expected abbreviated month, got: $result")
  }

  test("date filter: %A produces full weekday name via 'now'") {
    val result = render("{{ 'now' | date: '%A' }}")
    val days   = Set("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    assert(days.contains(result), s"Expected a weekday name, got: $result")
  }

  test("date filter: %a produces abbreviated weekday name via 'now'") {
    val result = render("{{ 'now' | date: '%a' }}")
    val days   = Set("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    assert(days.contains(result), s"Expected abbreviated weekday, got: $result")
  }

  // ---------------------------------------------------------------------------
  // Epoch timestamp parsing
  // ---------------------------------------------------------------------------

  test("date filter: epoch timestamp 0 formats to 1970") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(0L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    val result = parser.parse("{{ ts | date: '%Y-%m-%d' }}").render(vars)
    assertEquals(result, "1970-01-01")
  }

  test("date filter: epoch timestamp 1710505200 formats correctly") {
    // 1710505200 = 2024-03-15T15:00:00Z
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(1710505200L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    val result = parser.parse("{{ ts | date: '%Y-%m-%d' }}").render(vars)
    assertEquals(result, "2024-03-15")
  }

  test("date filter: epoch timestamp as integer") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Integer.valueOf(0))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    val result = parser.parse("{{ ts | date: '%Y' }}").render(vars)
    assertEquals(result, "1970")
  }

  test("date filter: epoch timestamp with %F composite") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(0L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    val result = parser.parse("{{ ts | date: '%F' }}").render(vars)
    assertEquals(result, "1970-01-01")
  }

  test("date filter: epoch timestamp with %T composite") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(0L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    val result = parser.parse("{{ ts | date: '%T' }}").render(vars)
    assertEquals(result, "00:00:00")
  }

  test("date filter: epoch timestamp with %D composite") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(0L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    val result = parser.parse("{{ ts | date: '%D' }}").render(vars)
    assertEquals(result, "01/01/70")
  }

  test("date filter: epoch timestamp with individual directives") {
    val vars = new JHashMap[String, Any]()
    // 1710504000 = 2024-03-15T12:00:00Z (exactly noon UTC)
    vars.put("ts", java.lang.Long.valueOf(1710504000L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    assertEquals(parser.parse("{{ ts | date: '%Y' }}").render(vars), "2024")
    assertEquals(parser.parse("{{ ts | date: '%m' }}").render(vars), "03")
    assertEquals(parser.parse("{{ ts | date: '%d' }}").render(vars), "15")
    assertEquals(parser.parse("{{ ts | date: '%H' }}").render(vars), "12")
    assertEquals(parser.parse("{{ ts | date: '%M' }}").render(vars), "00")
    assertEquals(parser.parse("{{ ts | date: '%S' }}").render(vars), "00")
  }

  test("date filter: epoch timestamp with %B full month name") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(1710505200L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    assertEquals(parser.parse("{{ ts | date: '%B' }}").render(vars), "March")
  }

  test("date filter: epoch timestamp with %a abbreviated weekday") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(1710505200L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    assertEquals(parser.parse("{{ ts | date: '%a' }}").render(vars), "Fri")
  }

  test("date filter: epoch timestamp with %A full weekday") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(1710505200L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    assertEquals(parser.parse("{{ ts | date: '%A' }}").render(vars), "Friday")
  }

  test("date filter: epoch timestamp with %y two-digit year") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(1710505200L))
    val tz     = ZoneId.of("UTC")
    val parser = new TemplateParser.Builder().withDefaultTimeZone(tz).build()
    assertEquals(parser.parse("{{ ts | date: '%y' }}").render(vars), "24")
  }

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------

  test("date filter: empty format returns original value") {
    assertEquals(render("{{ '2024-03-15' | date: '' }}"), "2024-03-15")
  }

  test("date filter: unparseable string returns original") {
    assertEquals(render("{{ 'not-a-date' | date: '%Y' }}"), "not-a-date")
  }

  test("date filter: nil input returns empty") {
    assertEquals(render("{{ nil | date: '%Y' }}"), "")
  }

  // ---------------------------------------------------------------------------
  // Custom date parser via Builder
  // ---------------------------------------------------------------------------

  test("date filter: custom DateParser via Builder") {
    val customParser = new filters.date.DateParser()
    val parser       = new TemplateParser.Builder().withDateParser(customParser).build()
    val vars         = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(1710505200L))
    val result = parser.parse("{{ ts | date: '%F' }}").render(vars)
    // Not UTC, so we just check it matches a date pattern
    assert(result.matches("\\d{4}-\\d{2}-\\d{2}"), s"Expected yyyy-MM-dd format, got: $result")
  }

  test("date filter: default time zone affects epoch rendering") {
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(0L))
    val utcParser = new TemplateParser.Builder().withDefaultTimeZone(ZoneId.of("UTC")).build()
    val result    = utcParser.parse("{{ ts | date: '%Y-%m-%d' }}").render(vars)
    assertEquals(result, "1970-01-01")
  }

  test("date filter: different time zones produce different hour results") {
    // Epoch 0 = 1970-01-01T00:00:00Z. In UTC the hour is 00, in UTC+5 the hour is 05.
    val vars = new JHashMap[String, Any]()
    vars.put("ts", java.lang.Long.valueOf(0L))
    val utcParser = new TemplateParser.Builder().withDefaultTimeZone(ZoneId.of("UTC")).build()
    val utcResult = utcParser.parse("{{ ts | date: '%H' }}").render(vars)
    assertEquals(utcResult, "00")
    val plus5Parser = new TemplateParser.Builder().withDefaultTimeZone(ZoneId.of("+05:00")).build()
    val plus5Result = plus5Parser.parse("{{ ts | date: '%H' }}").render(vars)
    assertEquals(plus5Result, "05")
  }

  // ---------------------------------------------------------------------------
  // strftimeToJava: unit-level verification through 'now'
  // ---------------------------------------------------------------------------

  test("date filter: combined %Y-%m-%d %H:%M:%S via 'now'") {
    val result = render("{{ 'now' | date: '%Y-%m-%d %H:%M:%S' }}")
    assert(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), s"Expected datetime format, got: $result")
  }

  test("date filter: %F %T composite via 'now'") {
    val result = render("{{ 'now' | date: '%F %T' }}")
    assert(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), s"Expected datetime format, got: $result")
  }

  // ---------------------------------------------------------------------------
  // DateParser.addDatePattern / removeDatePattern
  // ---------------------------------------------------------------------------

  test("date filter: addDatePattern adds a new pattern") {
    // Verify the static method exists and doesn't throw
    val before = filters.date.DateParser.datePatterns.size()
    filters.Date.addDatePattern("dd.MM.yyyy")
    assertEquals(filters.date.DateParser.datePatterns.size(), before + 1)
    // Clean up
    filters.Date.removeDatePattern("dd.MM.yyyy")
    assertEquals(filters.date.DateParser.datePatterns.size(), before)
  }
}
