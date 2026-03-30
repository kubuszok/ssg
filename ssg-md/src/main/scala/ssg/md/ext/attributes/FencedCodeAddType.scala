/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/FencedCodeAddType.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

enum FencedCodeAddType(val addToPre: Boolean, val addToCode: Boolean) extends java.lang.Enum[FencedCodeAddType] {
  case ADD_TO_PRE_CODE extends FencedCodeAddType(true, true)
  case ADD_TO_PRE extends FencedCodeAddType(true, false)
  case ADD_TO_CODE extends FencedCodeAddType(false, true)
}
