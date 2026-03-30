/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiShortcutType.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package emoji

import ssg.md.Nullable

enum EmojiShortcutType(val isEmojiCheatSheet: Boolean, val isGitHub: Boolean) {
  case EMOJI_CHEAT_SHEET extends EmojiShortcutType(true, false)
  case GITHUB extends EmojiShortcutType(false, true)
  case ANY_EMOJI_CHEAT_SHEET_PREFERRED extends EmojiShortcutType(true, true)
  case ANY_GITHUB_PREFERRED extends EmojiShortcutType(true, true)

  def getPreferred(emojiCheatSheet: Nullable[String], gitHub: Nullable[String]): Nullable[String] = this match {
    case EMOJI_CHEAT_SHEET => emojiCheatSheet
    case GITHUB => gitHub
    case ANY_EMOJI_CHEAT_SHEET_PREFERRED =>
      if (emojiCheatSheet.isDefined) emojiCheatSheet else gitHub
    case ANY_GITHUB_PREFERRED =>
      if (gitHub.isDefined) gitHub else emojiCheatSheet
  }
}
