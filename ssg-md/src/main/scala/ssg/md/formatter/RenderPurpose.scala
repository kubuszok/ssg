/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/RenderPurpose.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/RenderPurpose.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package formatter

enum RenderPurpose extends java.lang.Enum[RenderPurpose] {
  case FORMAT, TRANSLATION_SPANS, TRANSLATED_SPANS, TRANSLATED
}
