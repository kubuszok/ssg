/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiResolvedShortcut.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiResolvedShortcut.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package emoji
package internal

import ssg.md.Nullable
import ssg.md.ext.emoji.internal.EmojiReference.EmojiData

final class EmojiResolvedShortcut(
  val emoji:     Nullable[EmojiData],
  val emojiText: Nullable[String],
  val isUnicode: Boolean,
  val alt:       Nullable[String]
)

object EmojiResolvedShortcut {

  def getEmojiText(node: Emoji, useShortcutType: EmojiShortcutType, useImageType: EmojiImageType, rootImagePath: String): EmojiResolvedShortcut =
    getEmojiText(node.text.toString, useShortcutType, useImageType, rootImagePath, useUnicodeFileName = false)

  def getEmojiText(node: Emoji, useShortcutType: EmojiShortcutType, useImageType: EmojiImageType, rootImagePath: String, useUnicodeFileName: Boolean): EmojiResolvedShortcut =
    getEmojiText(node.text.toString, useShortcutType, useImageType, rootImagePath, useUnicodeFileName)

  def getEmojiText(emojiId: String, useShortcutType: EmojiShortcutType, useImageType: EmojiImageType, rootImagePath: String): EmojiResolvedShortcut =
    getEmojiText(emojiId, useShortcutType, useImageType, rootImagePath, useUnicodeFileName = false)

  def getEmojiText(emojiId: String, useShortcutType: EmojiShortcutType, useImageType: EmojiImageType, rootImagePath: String, useUnicodeFileName: Boolean): EmojiResolvedShortcut = {
    val emoji = EmojiShortcuts.getEmojiFromShortcut(emojiId)
    var emojiText: Nullable[String] = Nullable.empty
    var isUnicode = false
    var alt: Nullable[String] = Nullable.empty

    emoji.foreach { e =>
      var unicodeText: Nullable[String] = Nullable.empty
      var imageText:   Nullable[String] = Nullable.empty

      if (useImageType.isUnicode && e.unicodeChars.isDefined) {
        unicodeText = EmojiShortcuts.getUnicodeChars(e)
      }

      if (useImageType.isImage) {
        var gitHubFile:     Nullable[String] = Nullable.empty
        var cheatSheetFile: Nullable[String] = Nullable.empty

        if (useShortcutType.isGitHub && e.githubFile.isDefined) {
          gitHubFile = Nullable(EmojiShortcuts.gitHubUrlPrefix + e.githubFile.get)
        }

        if (useShortcutType.isEmojiCheatSheet && e.emojiCheatSheetFile.isDefined) {
          if (useUnicodeFileName && e.unicodeSampleFile.isDefined) {
            cheatSheetFile = Nullable(rootImagePath + e.unicodeSampleFile.get)
          } else {
            cheatSheetFile = Nullable(rootImagePath + e.emojiCheatSheetFile.get)
          }
        }

        imageText = useShortcutType.getPreferred(cheatSheetFile, gitHubFile)
      }

      if (imageText.isDefined || unicodeText.isDefined) {
        if (unicodeText.isDefined) {
          emojiText = unicodeText
          isUnicode = true
        } else {
          emojiText = imageText
        }

        // CAUTION: this exact string is used by html parser to convert emoji from Apple Mail HTML
        // Java concatenates possibly-null values yielding literal "null" substrings; match that behavior
        alt = Nullable("emoji " + e.category.getOrElse("null") + ":" + e.shortcut.getOrElse("null"))
      }
    }

    new EmojiResolvedShortcut(emoji, emojiText, isUnicode, alt)
  }
}
