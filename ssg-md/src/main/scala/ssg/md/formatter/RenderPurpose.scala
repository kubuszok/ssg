/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/RenderPurpose.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

enum RenderPurpose extends java.lang.Enum[RenderPurpose] {
  case FORMAT, TRANSLATION_SPANS, TRANSLATED_SPANS, TRANSLATED
}
