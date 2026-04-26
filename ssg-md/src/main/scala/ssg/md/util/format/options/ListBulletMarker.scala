/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ListBulletMarker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/ListBulletMarker.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format
package options

enum ListBulletMarker extends java.lang.Enum[ListBulletMarker] {
  case ANY
  case DASH
  case ASTERISK
  case PLUS
}
