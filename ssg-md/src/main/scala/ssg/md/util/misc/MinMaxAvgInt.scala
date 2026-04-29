/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgInt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/MinMaxAvgInt.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package misc

class MinMaxAvgInt {
  var min:   Int = Int.MaxValue
  var max:   Int = Int.MinValue
  var total: Int = 0

  def add(value: Int): Unit = {
    total += value
    min = Math.min(min, value)
    max = Math.max(max, value)
  }

  def add(other: MinMaxAvgInt): Unit = {
    total += other.total
    min = Math.min(min, other.min)
    max = Math.max(max, other.max)
  }

  def diff(start: Int, end: Int): Unit =
    add(end - start)

  def avg(count: Int): Int =
    if (count == 0) 0 else total / count
}
