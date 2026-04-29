/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/FormattedCounter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/FormattedCounter.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.misc.Utils

class FormattedCounter(
  val numberFormat: NumberFormat,
  val isLowercase:  Nullable[Boolean],
  val delimiter:    Nullable[String]
) {

  private var _count: Int = 0

  def reset(): Unit =
    _count = 0

  def count: Int = _count

  def nextCount(): Int = {
    _count += 1
    _count
  }

  def getFormatted(withDelimiter: Boolean): String = {
    val s = NumberFormat.getFormat(numberFormat, Utils.minLimit(_count, 1))
    val o =
      if (isLowercase.isEmpty) s
      else if (isLowercase.get) s.toLowerCase
      else s.toUpperCase
    if (withDelimiter && delimiter.isDefined && delimiter.get.nonEmpty) o + delimiter.get
    else o
  }
}
