/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentedSequenceStats.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentedSequenceStats.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable
import ssg.md.util.misc.MinMaxAvgLong

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

class SegmentedSequenceStats private () {

  private var aggregatedStats: Nullable[ArrayBuffer[SegmentedSequenceStats.StatsEntry]] = Nullable.empty
  private val stats = new java.util.HashMap[SegmentedSequenceStats.StatsEntry, SegmentedSequenceStats.StatsEntry]()

  def addStats(segments: Int, length: Int, overhead: Int): Unit = {
    var entry = new SegmentedSequenceStats.StatsEntry(segments)
    entry = stats.computeIfAbsent(entry, k => k)
    entry.add(segments, length, overhead)
  }

  def getCount(segments: Int): Int = {
    val entry = new SegmentedSequenceStats.StatsEntry(segments)
    if (stats.containsKey(entry)) {
      stats.get(entry).count
    } else {
      0
    }
  }

  def getStatsText(entries: java.util.List[SegmentedSequenceStats.StatsEntry]): String = {
    val out  = new StringBuilder()
    val iMax = entries.size()

    out
      .append(
        String.format(
          "%10s,%10s,%10s,%10s,%10s,%10s,%10s,%10s,%10s,%10s,%10s,%10s,%10s,%8s",
          "count",
          "min-seg",
          "avg-seg",
          "max-seg",
          "min-len",
          "avg-len",
          "max-len",
          "min-ovr",
          "avg-ovr",
          "max-ovr",
          "tot-len",
          "tot-chr",
          "tot-ovr",
          "ovr %"
        )
      )
      .append("\n")

    var i = iMax
    while (i > 0) {
      i -= 1
      val entry = entries.get(i)
      out
        .append(
          String.format(
            java.util.Locale.US,
            "%10d,%10d,%10d,%10d,%10d,%10d,%10d,%10d,%10d,%10d,%10d,%10d,%10d,%8.3f",
            Integer.valueOf(entry.count),
            Long.box(if (entry.count == 1) entry.segments.toLong else entry.segStats.min),
            Long.box(if (entry.count == 1) entry.segments.toLong else entry.segStats.avg(entry.count.toLong)),
            Long.box(if (entry.count == 1) entry.segments.toLong else entry.segStats.max),
            Long.box(entry.length.min),
            Long.box(entry.length.avg(entry.count.toLong)),
            Long.box(entry.length.max),
            Long.box(entry.overhead.min),
            Long.box(entry.overhead.avg(entry.count.toLong)),
            Long.box(entry.overhead.max),
            Long.box(entry.length.total),
            Long.box(entry.length.total * 2),
            Long.box(entry.overhead.total),
            Double.box(if (entry.length.total == 0) 0.0 else 100.0 * entry.overhead.total / entry.length.total / 2.0)
          )
        )
        .append("\n")
    }
    out.toString
  }

  def getAggregatedStatsText: String =
    getStatsText(getAggregatedStats)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def getAggregatedStats: java.util.List[SegmentedSequenceStats.StatsEntry] = {
    if (aggregatedStats.isEmpty) {
      val entries = getStats
      // Use null entries as bucket placeholders — this mirrors the original Java algorithm
      val buckets = ArrayBuffer.fill[SegmentedSequenceStats.StatsEntry](SegmentedSequenceStats.MAX_BUCKETS)(null) // @nowarn null used as bucket placeholder

      var currentBucket         = SegmentedSequenceStats.MAX_BUCKETS - 1
      var currentBucketSegments = SegmentedSequenceStats.AGGR_STEPS(currentBucket)

      val iMax = entries.size()

      var i = iMax
      while (i > 0) {
        i -= 1
        val entry = entries.get(i)
        if (entry.segments < currentBucketSegments) {
          // find the next bucket to hold this entry
          boundary {
            while (currentBucket > 0) {
              currentBucket -= 1
              currentBucketSegments = SegmentedSequenceStats.AGGR_STEPS(currentBucket)
              if (entry.segments >= currentBucketSegments) break()
            }
          }
          assert(currentBucket >= 0)
        }

        assert(entry.segments >= currentBucketSegments)

        var aggrEntry = buckets(currentBucket)

        if (aggrEntry == null) { // @nowarn null check for bucket placeholder
          aggrEntry = new SegmentedSequenceStats.StatsEntry(currentBucketSegments)
          buckets(currentBucket) = aggrEntry
        }

        aggrEntry.add(entry)
      }

      aggregatedStats = Nullable(buckets.filter(_ != null)) // @nowarn null filter for bucket placeholders
    }

    val buf    = aggregatedStats.get
    val result = new java.util.ArrayList[SegmentedSequenceStats.StatsEntry](buf.size)
    buf.foreach(result.add)
    result
  }

  def getStatsText: String = {
    val entries = getStats
    getStatsText(entries)
  }

  def clear(): Unit =
    stats.clear()

  def getStats: java.util.List[SegmentedSequenceStats.StatsEntry] = {
    val entries = new java.util.ArrayList[SegmentedSequenceStats.StatsEntry](stats.keySet())
    entries.sort((a, b) => a.compareTo(b))
    entries
  }
}

object SegmentedSequenceStats {

  final class StatsEntry(var segments: Int) extends Comparable[StatsEntry] {
    assert(segments >= 1, s"segments: $segments < 1")

    var count:    Int           = 0
    val segStats: MinMaxAvgLong = new MinMaxAvgLong()
    val length:   MinMaxAvgLong = new MinMaxAvgLong()
    val overhead: MinMaxAvgLong = new MinMaxAvgLong()

    def add(segments: Int, length: Int, overhead: Int): Unit = {
      count += 1
      this.segStats.add(segments.toLong)
      this.length.add(length.toLong)
      this.overhead.add(overhead.toLong)
    }

    def add(other: StatsEntry): Unit = {
      count += other.count
      this.segStats.add(other.segStats)
      this.length.add(other.length)
      this.overhead.add(other.overhead)
    }

    override def compareTo(o: StatsEntry): Int = {
      val segs = Integer.compare(segments, o.segments)
      if (segs != 0) segs
      else Integer.compare(count, o.count)
    }

    override def equals(o: Any): Boolean =
      o match {
        case that: StatsEntry => this.segments == that.segments
        case _ => false
      }

    override def hashCode(): Int = segments
  }

  private val AGGR_STEPS: Array[Int] = {
    val steps    = ArrayBuffer[Int](1, 2, 3, 4, 5, 6, 7, 8, 15, 16, 256)
    val step     = 65536
    val start    = 65536
    val nextStep = 65536 * 16
    var i        = start
    while (i < nextStep) {
      steps += i
      i += step
    }
    steps.toArray
  }

  private val MAX_BUCKETS: Int = AGGR_STEPS.length

  def getInstance: SegmentedSequenceStats = new SegmentedSequenceStats()
}
