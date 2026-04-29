/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/Sort.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/Sort.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

enum Sort extends java.lang.Enum[Sort] {
  case NONE
  case ASCENDING
  case DESCENDING
  case ASCENDING_NUMERIC
  case DESCENDING_NUMERIC
  case ASCENDING_NUMERIC_LAST
  case DESCENDING_NUMERIC_LAST

  def isDescending: Boolean =
    this == DESCENDING || this == DESCENDING_NUMERIC || this == DESCENDING_NUMERIC_LAST

  def isNumeric: Boolean =
    this == ASCENDING_NUMERIC || this == ASCENDING_NUMERIC_LAST || this == DESCENDING_NUMERIC || this == DESCENDING_NUMERIC_LAST

  def isNumericLast: Boolean =
    this == ASCENDING_NUMERIC_LAST || this == DESCENDING_NUMERIC_LAST
}
