/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ElementAlignment.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ElementAlignment.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format
package options

enum ElementAlignment extends java.lang.Enum[ElementAlignment] {
  case NONE
  case LEFT_ALIGN
  case RIGHT_ALIGN

  def isNoChange: Boolean =
    this == NONE

  def isRight: Boolean =
    this == RIGHT_ALIGN

  def isLeft: Boolean =
    this == LEFT_ALIGN
}
