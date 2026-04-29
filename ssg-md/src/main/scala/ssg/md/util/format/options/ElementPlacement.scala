/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ElementPlacement.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ElementPlacement.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format
package options

enum ElementPlacement extends java.lang.Enum[ElementPlacement] {
  case AS_IS
  case DOCUMENT_TOP
  case GROUP_WITH_FIRST
  case GROUP_WITH_LAST
  case DOCUMENT_BOTTOM

  def isNoChange: Boolean =
    this == AS_IS

  def isChange: Boolean =
    this != AS_IS

  def isTop: Boolean =
    this == DOCUMENT_TOP

  def isBottom: Boolean =
    this == DOCUMENT_BOTTOM

  def isGroupFirst: Boolean =
    this == GROUP_WITH_FIRST

  def isGroupLast: Boolean =
    this == GROUP_WITH_LAST
}
