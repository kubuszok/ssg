/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgLong.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgLong.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package misc

class MinMaxAvgLong {
  var min:   Long = Long.MaxValue
  var max:   Long = Long.MinValue
  var total: Long = 0L

  def add(value: Long): Unit = {
    total += value
    min = Math.min(min, value)
    max = Math.max(max, value)
  }

  def add(other: MinMaxAvgLong): Unit = {
    total += other.total
    min = Math.min(min, other.min)
    max = Math.max(max, other.max)
  }

  def diff(start: Long, end: Long): Unit =
    add(end - start)

  def avg(count: Long): Long =
    if (count == 0L) 0L else total / count
}
