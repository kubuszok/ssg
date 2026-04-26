/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/BlockQuoteMarker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/BlockQuoteMarker.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format
package options

enum BlockQuoteMarker extends java.lang.Enum[BlockQuoteMarker] {
  case AS_IS
  case ADD_COMPACT
  case ADD_COMPACT_WITH_SPACE
  case ADD_SPACED
}
