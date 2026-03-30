/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/internal/Utilities.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package media
package tags
package internal

import ssg.md.Nullable

object Utilities {

  def resolveAudioType(source: String): Nullable[String] = {
    val period = source.lastIndexOf(".")
    if (period == -1) Nullable.empty
    else {
      val extension = source.substring(period + 1, source.length)
      extension match {
        case "opus" => Nullable("audio/ogg; codecs=opus")
        case "weba" => Nullable("audio/webm")
        case "webm" => Nullable("audio/webm; codecs=opus")
        case "ogg"  => Nullable("audio/ogg")
        case "mp3"  => Nullable("audio/mpeg")
        case "wav"  => Nullable("audio/wav")
        case "flac" => Nullable("audio/flac")
        case _      => Nullable.empty
      }
    }
  }

  def resolveVideoType(source: String): Nullable[String] = {
    val period = source.lastIndexOf(".")
    if (period == -1) Nullable.empty
    else {
      val extension = source.substring(period + 1, source.length)
      extension match {
        case "mp4"  => Nullable("video/mp4")
        case "webm" => Nullable("video/webm")
        case "ogv"  => Nullable("video/ogg")
        case "3gp"  => Nullable("video/3gp")
        case _      => Nullable.empty
      }
    }
  }
}
