/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/FormattingPhase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/FormattingPhase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package formatter

enum FormattingPhase extends java.lang.Enum[FormattingPhase] {
  case COLLECT, DOCUMENT_FIRST, DOCUMENT_TOP, DOCUMENT, DOCUMENT_BOTTOM
}
