/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ElementPlacementSort.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ElementPlacementSort.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format
package options

enum ElementPlacementSort extends java.lang.Enum[ElementPlacementSort] {
  case AS_IS
  case SORT
  case SORT_UNUSED_LAST
  case SORT_DELETE_UNUSED
  case DELETE_UNUSED

  def isUnused: Boolean =
    this == SORT_UNUSED_LAST || this == SORT_DELETE_UNUSED || this == DELETE_UNUSED

  def isDeleteUnused: Boolean =
    this == SORT_DELETE_UNUSED || this == DELETE_UNUSED

  def isSort: Boolean =
    this == SORT_UNUSED_LAST || this == SORT_DELETE_UNUSED || this == SORT
}
