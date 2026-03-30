/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParsedOptionStatus.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package options

enum ParsedOptionStatus(val level: Int) extends java.lang.Enum[ParsedOptionStatus] {
  case VALID extends ParsedOptionStatus(0)
  case IGNORED extends ParsedOptionStatus(1)
  case WEAK_WARNING extends ParsedOptionStatus(2)
  case WARNING extends ParsedOptionStatus(3)
  case ERROR extends ParsedOptionStatus(4)

  def escalate(other: ParsedOptionStatus): ParsedOptionStatus =
    if (level < other.level) other else this
}
