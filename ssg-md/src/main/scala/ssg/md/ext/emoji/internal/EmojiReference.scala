/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiReference.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package emoji
package internal

import ssg.md.Nullable

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.{ArrayList, List as JList}

object EmojiReference {

  val EMOJI_REFERENCE_TXT: String = "/EmojiReference.txt" // resource path to text data file
  val githubUrl: String = "https://github.githubassets.com/images/icons/emoji/"
  private val EMPTY_ARRAY: Array[String] = Array.empty[String]

  /** Browser types and their subdirectory names */
  enum EmojiBrowserType(val subdirectory: String) {
    case APPL extends EmojiBrowserType("appl")
    case DCM extends EmojiBrowserType("dcm")
    case EMOJI_CHEAT_SHEET extends EmojiBrowserType("emojis")
    case FB extends EmojiBrowserType("fb")
    case GITHUB extends EmojiBrowserType("github")
    case GMAIL extends EmojiBrowserType("gmail")
    case GOOG extends EmojiBrowserType("goog")
    case JOY extends EmojiBrowserType("joy")
    case KDDI extends EmojiBrowserType("kddi")
    case SAMS extends EmojiBrowserType("sams")
    case SB extends EmojiBrowserType("sb")
    case TWTR extends EmojiBrowserType("twtr")
    case WIND extends EmojiBrowserType("wind")
  }

  final class EmojiData(
      val shortcut: Nullable[String],
      var aliasShortcuts: Array[String],
      val category: Nullable[String],
      var subcategory: Nullable[String],
      val emojiCheatSheetFile: Nullable[String],
      val githubFile: Nullable[String],
      val unicodeChars: Nullable[String],
      val unicodeSampleFile: Nullable[String],
      val unicodeCldr: Nullable[String],
      var browserTypes: Array[String],
  )

  @volatile private var emojiList: Nullable[ArrayList[EmojiData]] = Nullable.empty

  def getEmojiList: JList[EmojiData] = {
    emojiList.getOrElse {
      synchronized {
        emojiList.getOrElse {
          // read it in
          val list = new ArrayList[EmojiData](3000)

          try {
            val stream = ssg.md.util.misc.PlatformResources.getResourceAsStream(classOf[EmojiReference.type], EMOJI_REFERENCE_TXT)
            if (stream.isDefined) { // Cross-platform: resource may be unavailable on JS
              val rawStream = stream.get
              val reader = new BufferedReader(new InputStreamReader(rawStream, StandardCharsets.UTF_8))
              // skip first line, it is column names
              reader.readLine()
              var line = reader.readLine()
              while (line != null) { // @nowarn - Java interop: readLine returns null
                val fields = line.split("\t")
                try {
                  val shortcut = if (fields(0).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(0))
                  val category = if (fields(1).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(1))
                  val emojiCheatSheetFile = if (fields(2).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(2))
                  val githubFile = if (fields(3).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(3))
                  val unicodeChars = if (fields(4).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(4))
                  val unicodeSampleFile = if (fields(5).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(5))
                  val unicodeCldr = if (fields(6).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(6))
                  val subcategory = if (fields(7).charAt(0) == ' ') Nullable.empty[String] else Nullable(fields(7))
                  val aliasShortcuts = if (fields(8).charAt(0) == ' ') EMPTY_ARRAY else fields(8).split(",")
                  val browserTypes = if (fields(9).charAt(0) == ' ') EMPTY_ARRAY else fields(9).split(",")

                  val emoji = new EmojiData(shortcut, aliasShortcuts, category, subcategory, emojiCheatSheetFile, githubFile, unicodeChars, unicodeSampleFile, unicodeCldr, browserTypes)
                  list.add(emoji)
                } catch {
                  case e: ArrayIndexOutOfBoundsException =>
                    throw new IllegalStateException("Error processing EmojiReference.txt", e)
                }
                line = reader.readLine()
              }
            }
            // If stream is null (e.g. on JS where classpath resources are unavailable),
            // gracefully degrade with an empty emoji list
          } catch {
            case _: LinkageError =>
            // Safety net: gracefully degrade with an empty emoji list
            // if resource loading fails at the platform level
          }

          emojiList = Nullable(list)
          list
        }
      }
    }
  }
}
