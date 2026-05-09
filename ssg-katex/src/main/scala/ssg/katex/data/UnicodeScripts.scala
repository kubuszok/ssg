/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file defines the Unicode scripts and script families that we
 * support. To add new scripts or families, just add a new entry to the
 * scriptData array below. Adding scripts to the scriptData array allows
 * characters from that script to appear in \text{} environments.
 *
 * Original source: katex src/unicodeScripts.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable

/**
 * Each script or script family has a name and an array of blocks.
 * Each block is an array of two numbers which specify the start and
 * end points (inclusive) of a block of Unicode codepoints.
 */
final case class Script(name: String, blocks: Array[Array[Int]])

/**
 * Unicode block data for the families of scripts we support in \text{}.
 * Scripts only need to appear here if they do not have font metrics.
 */
object UnicodeScripts {

  val scriptData: Array[Script] = Array(
    Script(
      // Latin characters beyond the Latin-1 characters we have metrics for.
      // Needed for Czech, Hungarian and Turkish text, for example.
      name = "latin",
      blocks = Array(
        Array(0x0100, 0x024f), // Latin Extended-A and Latin Extended-B
        Array(0x0300, 0x036f)  // Combining Diacritical marks
      )
    ),
    Script(
      // The Cyrillic script used by Russian and related languages.
      // A Cyrillic subset used to be supported as explicitly defined
      // symbols in symbols.js
      name = "cyrillic",
      blocks = Array(Array(0x0400, 0x04ff))
    ),
    Script(
      // Armenian
      name = "armenian",
      blocks = Array(Array(0x0530, 0x058f))
    ),
    Script(
      // The Brahmic scripts of South and Southeast Asia
      // Devanagari (0900–097F)
      // Bengali (0980–09FF)
      // Gurmukhi (0A00–0A7F)
      // Gujarati (0A80–0AFF)
      // Oriya (0B00–0B7F)
      // Tamil (0B80–0BFF)
      // Telugu (0C00–0C7F)
      // Kannada (0C80–0CFF)
      // Malayalam (0D00–0D7F)
      // Sinhala (0D80–0DFF)
      // Thai (0E00–0E7F)
      // Lao (0E80–0EFF)
      // Tibetan (0F00–0FFF)
      // Myanmar (1000–109F)
      name = "brahmic",
      blocks = Array(Array(0x0900, 0x109f))
    ),
    Script(
      name = "georgian",
      blocks = Array(Array(0x10a0, 0x10ff))
    ),
    Script(
      // Chinese and Japanese.
      // The "k" in cjk is for Korean, but we've separated Korean out
      name = "cjk",
      blocks = Array(
        Array(0x3000, 0x30ff), // CJK symbols and punctuation, Hiragana, Katakana
        Array(0x4e00, 0x9faf), // CJK ideograms
        Array(0xff00, 0xff60)  // Fullwidth punctuation
        // TODO: add halfwidth Katakana and Romanji glyphs
      )
    ),
    Script(
      // Korean
      name = "hangul",
      blocks = Array(Array(0xac00, 0xd7af))
    )
  )

  /**
   * A flattened version of all the supported blocks in a single array.
   * This is an optimization to make supportedCodepoint() fast.
   */
  private lazy val allBlocks: Array[Int] = {
    scriptData.flatMap(s => s.blocks.flatMap(b => b))
  }

  /**
   * Given a codepoint, return the name of the script or script family
   * it is from, or null if it is not part of a known block
   */
  def scriptFromCodepoint(codepoint: Int): Nullable[String] = boundary {
    var i = 0
    while (i < scriptData.length) {
      val script = scriptData(i)
      var j = 0
      while (j < script.blocks.length) {
        val block = script.blocks(j)
        if (codepoint >= block(0) && codepoint <= block(1)) {
          break(Nullable(script.name))
        }
        j += 1
      }
      i += 1
    }
    Nullable.Null
  }

  /**
   * Given a codepoint, return true if it falls within one of the
   * scripts or script families defined above and false otherwise.
   *
   * Micro benchmarks shows that this is faster than
   * /[　-ヿ一-龯＀-｠가-힯ऀ-႟]/.test()
   * in Firefox, Chrome and Node.
   */
  def supportedCodepoint(codepoint: Int): Boolean = boundary {
    val blocks = allBlocks
    var i = 0
    while (i < blocks.length) {
      if (codepoint >= blocks(i) && codepoint <= blocks(i + 1)) {
        break(true)
      }
      i += 2
    }
    false
  }
}
