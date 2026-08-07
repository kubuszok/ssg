/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression test for ISS-1232 — the gantt dayjs->java.time pattern converter
 * (`GanttDb.dayjsToJavaTimePattern`) must handle the cleanly-mappable exotic
 * dayjs tokens, not just `Y`/`D`/`[literal]`.
 *
 * Oracle: upstream `ganttDb.js` getStartDate:292 parses task dates with the
 * CONFIGURED `dateFormat` via `dayjs(str, dateFormat.trim(), true)`, so any
 * dayjs token the format uses must round-trip. The bug: a bare `A`/`a` (AM/PM)
 * previously passed through unchanged, and java.time reads a lone `A` as
 * milli-of-day, so a `dateFormat` using AM/PM MIS-PARSED and the task fell back
 * to the lenient patterns (wrong/dropped start date). The converter now lexes
 * dayjs tokens (runs of the same letter + the `Xo` ordinal forms) and maps the
 * portable ones (`A`/`a`->`a`, `dd`/`ddd`/`dddd`->`EE`/`EEE`/`EEEE`,
 * `Z`->`XXX`/`ZZ`->`XX`, `Q`->`Q`) while carving out the impractical ones
 * (ordinals, `X`/`x` unix, `W`/`w` week) so they fall back gracefully.
 */
package ssg
package mermaid
package diagrams
package gantt

import java.time.{ LocalDate, LocalTime }
import java.time.format.DateTimeFormatter

import munit.FunSuite

final class GanttDayjsTokensIss1232Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // RED — the primary bug: an AM/PM `dateFormat` must drive task-date parsing.
  // Before the fix, `A` passed through and java.time read it as milli-of-day,
  // so any am/pm text failed the primary pattern and every lenient fallback,
  // leaving the task on the `lastEndDate` fallback (wrong start).
  //
  // The am/pm marker text is locale-provided, so the expected string is derived
  // from the platform's own formatter (CLDR `en_PL` -> `pm`, `en_US` -> `PM`);
  // `parseDateString` builds its formatter with the same default locale, so a
  // platform that HAS am/pm text round-trips. The Scala Native java.time bundle
  // ships no am/pm locale text (the marker comes back empty), so there an AM/PM
  // format cannot round-trip and must instead fall back gracefully without
  // crashing — asserted below via the capability gate.
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1232 dateFormat with AM/PM (hh:mm A) parses the task start date") {
    val amPmText = LocalTime.of(14, 30).format(DateTimeFormatter.ofPattern("a"))
    if (amPmText.nonEmpty) {
      // Platform with am/pm locale text (JVM, JS): full positive differential.
      val startSpec = s"2024-01-01 ${LocalTime.of(14, 30).format(DateTimeFormatter.ofPattern("hh:mm a"))}"
      val db        = GanttParser.parse(
        s"""gantt
           |    dateFormat YYYY-MM-DD hh:mm A
           |    section S
           |    Task A :a, $startSpec, 3d""".stripMargin
      )
      val a = db.tasks.find(_.id == "a").get
      assertEquals(
        a.startDate,
        LocalDate.of(2024, 1, 1),
        s"AM/PM dateFormat should parse '$startSpec' as 2024-01-01, got ${a.startDate}"
      )
    } else {
      // Platform without am/pm locale text (Scala Native): the format is not
      // parseable, so the task falls back to the lenient patterns; assert only
      // that the gantt still parses without crashing.
      val db = GanttParser.parse(
        """gantt
          |    dateFormat YYYY-MM-DD hh:mm A
          |    section S
          |    Task A :a, 2024-01-01 02:30 PM, 3d""".stripMargin
      )
      assert(db.tasks.exists(_.id == "a"))
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // PORTED — day-of-week NAME forms `dddd` (full) / `ddd` (short) -> `EEEE`/`EEE`.
  // 2024-01-01 is a Monday, so the name and the date agree and parsing resolves.
  // The day-name text is derived from the platform's own formatter so the test
  // is robust across JVM/JS/Native.
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1232 dateFormat with full day-name (dddd) parses the task start date") {
    val dayName = LocalDate.of(2024, 1, 1).format(DateTimeFormatter.ofPattern("EEEE"))
    val db      = GanttParser.parse(
      s"""gantt
         |    dateFormat dddd YYYY-MM-DD
         |    section S
         |    Task A :a, $dayName 2024-01-01, 3d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(a.startDate, LocalDate.of(2024, 1, 1))
  }

  test("ISS-1232 dateFormat with short day-name (ddd) parses the task start date") {
    val dayName = LocalDate.of(2024, 1, 1).format(DateTimeFormatter.ofPattern("EEE"))
    val db      = GanttParser.parse(
      s"""gantt
         |    dateFormat ddd YYYY-MM-DD
         |    section S
         |    Task A :a, $dayName 2024-01-01, 3d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(a.startDate, LocalDate.of(2024, 1, 1))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // PORTED — UTC offset `Z` -> java.time offset (three `X`s); the `[T]` literal
  // keeps the ISO 'T'.
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1232 dateFormat with UTC offset (Z) parses the task start date") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD[T]HH:mmZ
        |    section S
        |    Task A :a, 2024-01-01T10:30+05:00, 3d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(a.startDate, LocalDate.of(2024, 1, 1))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // CARVED OUT — an exotic token with no clean java.time parse mapping must
  // NOT crash the gantt parse; it simply falls back to the lenient patterns
  // (here nothing matches, so the task lands on the resolved fallback date).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1232 dateFormat with a carved-out unix token (X) falls back gracefully") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat X
        |    section S
        |    Task A :a, 1704067200, 3d""".stripMargin
    )
    // No crash and the task still exists (on the lenient-fallback date).
    assert(db.tasks.exists(_.id == "a"))
  }

  test("ISS-1232 dateFormat with a carved-out ordinal token (Do) falls back gracefully") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat Do
        |    section S
        |    Task A :a, 1st, 3d""".stripMargin
    )
    assert(db.tasks.exists(_.id == "a"))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // GUARD — the default `YYYY-MM-DD` still parses (no regression from the
  // char-by-char -> token-lexer rework).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1232 default dateFormat YYYY-MM-DD still parses (guard)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a, 2019-02-01, 3d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(a.startDate, LocalDate.of(2019, 2, 1))
  }
}
