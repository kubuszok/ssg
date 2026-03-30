/*
 * Shortcuts and images by http://www.emoji-cheat-sheet.com/
 * from https://github.com/WebpageFX/emoji-cheat-sheet.com
 *
 * Updated from https://api.github.com/emojis
 *
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiShortcuts.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package emoji
package internal

import ssg.md.Nullable
import ssg.md.ext.emoji.internal.EmojiReference.EmojiData

import scala.language.implicitConversions

import java.io.File
import java.util.HashMap

object EmojiShortcuts {

  val gitHubUrlPrefix: String = EmojiReference.githubUrl

  private val emojiShortcuts: HashMap[String, EmojiData] = new HashMap[String, EmojiData]()
  private val emojiURIs: HashMap[String, EmojiData] = new HashMap[String, EmojiData]()
  private val emojiUnicodeChars: HashMap[EmojiData, String] = new HashMap[EmojiData, String]()

  def getUnicodeChars(emoji: EmojiData): Nullable[String] = synchronized {
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    val isNull = emoji == null // @nowarn - Java interop: HashMap may return null
    if (isNull || emoji.unicodeChars.isEmpty) {
      Nullable.empty
    } else {
      val value = emojiUnicodeChars.get(emoji)
      if (value != null) { // @nowarn - Java interop: HashMap.get returns null
        Nullable(value)
      } else {
        val unicodePoints = emoji.unicodeChars.get.replace("U+", "").split(" ")
        val sb = new StringBuilder(16)
        for (unicodePoint <- unicodePoints) {
          sb.appendAll(Character.toChars(Integer.parseInt(unicodePoint, 16)))
        }
        val result = sb.toString()
        emojiUnicodeChars.put(emoji, result)
        Nullable(result)
      }
    }
  }

  def extractFileName(emojiURI: String): String = {
    val fileName = new File(emojiURI).getName
    val pos = fileName.indexOf(".png")
    if (pos >= 0) fileName.substring(0, pos) else fileName
  }

  def getEmojiShortcuts: HashMap[String, EmojiData] = {
    updateEmojiShortcuts()
    emojiShortcuts
  }

  def getEmojiURIs: HashMap[String, EmojiData] = {
    updateEmojiShortcuts()
    emojiURIs
  }

  def getEmojiFromShortcut(shortcut: String): Nullable[EmojiData] = {
    updateEmojiShortcuts()
    Nullable(emojiShortcuts.get(shortcut))
  }

  def getEmojiFromURI(imageURI: String): Nullable[EmojiData] = {
    updateEmojiURIs()
    Nullable(emojiURIs.get(extractFileName(imageURI)))
  }

  private def updateEmojiShortcuts(): Unit = synchronized {
    if (emojiShortcuts.isEmpty) {
      EmojiReference.getEmojiList.forEach { emoji =>
        emoji.shortcut.foreach { sc =>
          emojiShortcuts.put(sc, emoji)
        }
      }
    }
  }

  private def updateEmojiURIs(): Unit = synchronized {
    if (emojiURIs.isEmpty) {
      // create it
      EmojiReference.getEmojiList.forEach { emoji =>
        emoji.emojiCheatSheetFile.foreach { f =>
          emojiURIs.put(extractFileName(f), emoji)
        }
        emoji.githubFile.foreach { f =>
          emojiURIs.put(extractFileName(f), emoji)
        }
        emoji.unicodeSampleFile.foreach { f =>
          emojiURIs.put(extractFileName(f), emoji)
        }
      }
    }
  }
}

