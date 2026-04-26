/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiImageType.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiImageType.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package emoji

enum EmojiImageType(val isUnicode: Boolean, val isImage: Boolean) {
  case IMAGE_ONLY extends EmojiImageType(false, true)
  case UNICODE_FALLBACK_TO_IMAGE extends EmojiImageType(true, true)
  case UNICODE_ONLY extends EmojiImageType(true, false)
}
