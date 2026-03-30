/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/options/EqualizeTrailingMarker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format
package options

enum EqualizeTrailingMarker extends java.lang.Enum[EqualizeTrailingMarker] {
  case AS_IS
  case ADD
  case EQUALIZE
  case REMOVE
}
