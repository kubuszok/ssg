/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/HeadingStyle.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/HeadingStyle.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format
package options

// IMPORTANT: implement this format option
enum HeadingStyle extends java.lang.Enum[HeadingStyle] {
  case AS_IS
  case ATX_PREFERRED
  case SETEXT_PREFERRED

  def isNoChange: Boolean =
    this == AS_IS

  def isNoChange(isSetext: Boolean, level: Int): Boolean =
    this == AS_IS || this == SETEXT_PREFERRED && (isSetext || level > 2) || this == ATX_PREFERRED && !isSetext

  def isAtx: Boolean =
    this == ATX_PREFERRED

  def isSetext: Boolean =
    this == SETEXT_PREFERRED
}
