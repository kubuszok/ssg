/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/ColumnSort.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/ColumnSort.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

final case class ColumnSort(column: Int, sort: Sort)

object ColumnSort {

  def columnSort(column: Int, sort: Sort): ColumnSort =
    ColumnSort(column, sort)

  def columnSort(column: Int, descending: Boolean, numeric: Boolean, numericLast: Boolean): ColumnSort =
    if (numeric) {
      if (numericLast) {
        ColumnSort(column, if (descending) Sort.DESCENDING_NUMERIC_LAST else Sort.ASCENDING_NUMERIC_LAST)
      } else {
        ColumnSort(column, if (descending) Sort.DESCENDING_NUMERIC else Sort.ASCENDING_NUMERIC)
      }
    } else {
      ColumnSort(column, if (descending) Sort.DESCENDING else Sort.ASCENDING)
    }
}
