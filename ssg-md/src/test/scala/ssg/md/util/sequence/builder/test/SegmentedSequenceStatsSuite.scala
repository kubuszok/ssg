/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package test

import ssg.md.util.data.MutableDataSet

import scala.language.implicitConversions

final class SegmentedSequenceStatsSuite extends munit.FunSuite {

  test("basic_stats") {
    val stats = SegmentedSequenceStats.getInstance

    stats.addStats(3, 3, 6)
    assertEquals(stats.getCount(3), 1)

    stats.addStats(3, 3, 6)
    assertEquals(stats.getCount(3), 2)

    stats.addStats(3, 1, 1)
    assertEquals(stats.getCount(3), 3)
    assertEquals(stats.getCount(3), 3)

    stats.addStats(5, 0, 0)
    assertEquals(stats.getCount(5), 1)

    stats.addStats(5, 0, 0)
    assertEquals(stats.getCount(5), 2)

    stats.addStats(5, 1, 1)
    assertEquals(stats.getCount(5), 3)
    assertEquals(stats.getCount(5), 3)

    assertEquals(
      stats.getStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "         3,         5,         5,         5,         0,         0,         1,         0,         0,         1,         1,         2,         1,  50.000\n" +
        "         3,         3,         3,         3,         1,         2,         3,         1,         4,         6,         7,        14,        13,  92.857\n"
    )
  }

  test("basic_statsCollection") {
    val sC      = "0123456789"
    val options = new MutableDataSet()
    val stats   = SegmentedSequenceStats.getInstance
    options.set(BasedOptionsHolder.SEGMENTED_STATS, stats)

    val s1 = BasedOptionsSequence.of(sC, BasedOptionsHolder.F_COLLECT_SEGMENTED_STATS, options)
    val s  = BasedSequence.of(s1).subSequence(0, s1.length())

    val iMax = s.length()
    var i    = 0
    while (i < iMax) {
      var j = i
      while (j <= iMax) {
        s.subSequence(i, j).prefixWith("  ").suffixWith("\n")
        j += 1
      }
      i += 1
    }

    assertEquals(
      stats.getStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "        55,         3,         3,         3,         4,         7,        13,        30,        30,        30,       385,       770,      1650, 214.286\n" +
        "        10,         2,         2,         2,         3,         3,         3,        20,        20,        20,        30,        60,       200, 333.333\n"
    )
  }

  test("aggregatedStatsBuckets") {
    val stats = SegmentedSequenceStats.getInstance

    val iMax = 65540
    var i    = 1
    while (i < iMax) {
      stats.addStats(i, i, i)
      i += 1
    }

    assertEquals(
      stats.getAggregatedStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "         4,     65536,     65537,     65539,     65536,     65537,     65539,     65536,     65537,     65539,    262150,    524300,    262150,  50.000\n" +
        "     65280,       256,     32895,     65535,       256,     32895,     65535,       256,     32895,     65535,2147418240,4294836480,2147418240,  50.000\n" +
        "       240,        16,       135,       255,        16,       135,       255,        16,       135,       255,     32520,     65040,     32520,  50.000\n" +
        "         1,        15,        15,        15,        15,        15,        15,        15,        15,        15,        15,        30,        15,  50.000\n" +
        "         7,         8,        11,        14,         8,        11,        14,         8,        11,        14,        77,       154,        77,  50.000\n" +
        "         1,         7,         7,         7,         7,         7,         7,         7,         7,         7,         7,        14,         7,  50.000\n" +
        "         1,         6,         6,         6,         6,         6,         6,         6,         6,         6,         6,        12,         6,  50.000\n" +
        "         1,         5,         5,         5,         5,         5,         5,         5,         5,         5,         5,        10,         5,  50.000\n" +
        "         1,         4,         4,         4,         4,         4,         4,         4,         4,         4,         4,         8,         4,  50.000\n" +
        "         1,         3,         3,         3,         3,         3,         3,         3,         3,         3,         3,         6,         3,  50.000\n" +
        "         1,         2,         2,         2,         2,         2,         2,         2,         2,         2,         2,         4,         2,  50.000\n" +
        "         1,         1,         1,         1,         1,         1,         1,         1,         1,         1,         1,         2,         1,  50.000\n"
    )
  }

  test("aggregatedStatsNonBased") {
    val stats = SegmentedSequenceStats.getInstance

    val iMax = 256
    var i    = 1
    while (i < iMax) {
      stats.addStats(i, i, i)
      i += 1
    }

    assertEquals(
      stats.getAggregatedStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "       240,        16,       135,       255,        16,       135,       255,        16,       135,       255,     32520,     65040,     32520,  50.000\n" +
        "         1,        15,        15,        15,        15,        15,        15,        15,        15,        15,        15,        30,        15,  50.000\n" +
        "         7,         8,        11,        14,         8,        11,        14,         8,        11,        14,        77,       154,        77,  50.000\n" +
        "         1,         7,         7,         7,         7,         7,         7,         7,         7,         7,         7,        14,         7,  50.000\n" +
        "         1,         6,         6,         6,         6,         6,         6,         6,         6,         6,         6,        12,         6,  50.000\n" +
        "         1,         5,         5,         5,         5,         5,         5,         5,         5,         5,         5,        10,         5,  50.000\n" +
        "         1,         4,         4,         4,         4,         4,         4,         4,         4,         4,         4,         8,         4,  50.000\n" +
        "         1,         3,         3,         3,         3,         3,         3,         3,         3,         3,         3,         6,         3,  50.000\n" +
        "         1,         2,         2,         2,         2,         2,         2,         2,         2,         2,         2,         4,         2,  50.000\n" +
        "         1,         1,         1,         1,         1,         1,         1,         1,         1,         1,         1,         2,         1,  50.000\n"
    )
  }

  test("aggregatedStatsSegments") {
    val stats = SegmentedSequenceStats.getInstance

    val iMax = 256
    var i    = 1
    while (i < iMax) {
      stats.addStats(i, 0, 0)
      i += 1
    }

    assertEquals(
      stats.getAggregatedStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "       240,        16,       135,       255,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,        15,        15,        15,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         7,         8,        11,        14,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         7,         7,         7,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         6,         6,         6,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         5,         5,         5,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         4,         4,         4,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         3,         3,         3,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         2,         2,         2,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n" +
        "         1,         1,         1,         1,         0,         0,         0,         0,         0,         0,         0,         0,         0,   0.000\n"
    )
  }

  test("aggregatedStatsLength") {
    val stats = SegmentedSequenceStats.getInstance

    val iMax = 256
    var i    = 1
    while (i < iMax) {
      stats.addStats(i, i, 0)
      i += 1
    }

    assertEquals(
      stats.getAggregatedStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "       240,        16,       135,       255,        16,       135,       255,         0,         0,         0,     32520,     65040,         0,   0.000\n" +
        "         1,        15,        15,        15,        15,        15,        15,         0,         0,         0,        15,        30,         0,   0.000\n" +
        "         7,         8,        11,        14,         8,        11,        14,         0,         0,         0,        77,       154,         0,   0.000\n" +
        "         1,         7,         7,         7,         7,         7,         7,         0,         0,         0,         7,        14,         0,   0.000\n" +
        "         1,         6,         6,         6,         6,         6,         6,         0,         0,         0,         6,        12,         0,   0.000\n" +
        "         1,         5,         5,         5,         5,         5,         5,         0,         0,         0,         5,        10,         0,   0.000\n" +
        "         1,         4,         4,         4,         4,         4,         4,         0,         0,         0,         4,         8,         0,   0.000\n" +
        "         1,         3,         3,         3,         3,         3,         3,         0,         0,         0,         3,         6,         0,   0.000\n" +
        "         1,         2,         2,         2,         2,         2,         2,         0,         0,         0,         2,         4,         0,   0.000\n" +
        "         1,         1,         1,         1,         1,         1,         1,         0,         0,         0,         1,         2,         0,   0.000\n"
    )
  }

  test("aggregatedStatsOverhead") {
    val stats = SegmentedSequenceStats.getInstance

    val iMax = 256
    var i    = 1
    while (i < iMax) {
      stats.addStats(i, 0, i)
      i += 1
    }

    assertEquals(
      stats.getAggregatedStatsText,
      "     count,   min-seg,   avg-seg,   max-seg,   min-len,   avg-len,   max-len,   min-ovr,   avg-ovr,   max-ovr,   tot-len,   tot-chr,   tot-ovr,   ovr %\n" +
        "       240,        16,       135,       255,         0,         0,         0,        16,       135,       255,         0,         0,     32520,   0.000\n" +
        "         1,        15,        15,        15,         0,         0,         0,        15,        15,        15,         0,         0,        15,   0.000\n" +
        "         7,         8,        11,        14,         0,         0,         0,         8,        11,        14,         0,         0,        77,   0.000\n" +
        "         1,         7,         7,         7,         0,         0,         0,         7,         7,         7,         0,         0,         7,   0.000\n" +
        "         1,         6,         6,         6,         0,         0,         0,         6,         6,         6,         0,         0,         6,   0.000\n" +
        "         1,         5,         5,         5,         0,         0,         0,         5,         5,         5,         0,         0,         5,   0.000\n" +
        "         1,         4,         4,         4,         0,         0,         0,         4,         4,         4,         0,         0,         4,   0.000\n" +
        "         1,         3,         3,         3,         0,         0,         0,         3,         3,         3,         0,         0,         3,   0.000\n" +
        "         1,         2,         2,         2,         0,         0,         0,         2,         2,         2,         0,         0,         2,   0.000\n" +
        "         1,         1,         1,         1,         0,         0,         0,         1,         1,         1,         0,         0,         1,   0.000\n"
    )
  }
}
