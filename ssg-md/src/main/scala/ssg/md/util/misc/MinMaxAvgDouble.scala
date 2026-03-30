/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgDouble.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

class MinMaxAvgDouble {
  var min:   Double = Double.MaxValue
  var max:   Double = Double.MinValue
  var total: Double = 0.0

  def add(value: Double): Unit = {
    total += value
    min = Math.min(min, value)
    max = Math.max(max, value)
  }

  def add(other: MinMaxAvgDouble): Unit = {
    total += other.total
    min = Math.min(min, other.min)
    max = Math.max(max, other.max)
  }

  def diff(start: Double, end: Double): Unit =
    add(end - start)

  def avg(count: Double): Double =
    if (count == 0) 0 else total / count
}
