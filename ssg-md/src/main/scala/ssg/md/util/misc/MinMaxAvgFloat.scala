/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgFloat.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgFloat.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package misc

class MinMaxAvgFloat {
  var min:   Float = Float.MaxValue
  var max:   Float = Float.MinValue
  var total: Float = 0.0f

  def add(value: Float): Unit = {
    total += value
    min = Math.min(min, value)
    max = Math.max(max, value)
  }

  def add(other: MinMaxAvgFloat): Unit = {
    total += other.total
    min = Math.min(min, other.min)
    max = Math.max(max, other.max)
  }

  def diff(start: Float, end: Float): Unit =
    add(end - start)

  def avg(count: Float): Float =
    if (count == 0) 0 else total / count
}
