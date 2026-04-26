/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/TableCaptionHandling.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/TableCaptionHandling.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format
package options

enum TableCaptionHandling extends java.lang.Enum[TableCaptionHandling] {
  case AS_IS
  case ADD
  case REMOVE_EMPTY
  case REMOVE
}
