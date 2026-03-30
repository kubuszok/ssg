/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package emoji

trait EmojiVisitor {
  def visit(node: Emoji): Unit
}
