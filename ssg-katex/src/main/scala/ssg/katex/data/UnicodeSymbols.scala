/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This is an internal module, not part of the KaTeX distribution,
 * whose purpose is to generate unicode symbol mappings.
 * It maps NFC-normalized Unicode characters (single codepoint) back to
 * their decomposed base letter + combining accent(s).
 *
 * Original source: katex src/unicodeSymbols.js
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

import java.text.Normalizer

/**
 * Maps NFC-normalized Unicode characters (single codepoint) back to
 * their decomposed base letter + combining accent(s).
 *
 * In the original JS, this is computed at module load time by iterating
 * over letters and accents, composing them, normalizing to NFC, and
 * keeping only the results that collapse to a single character.
 * We reproduce the same algorithm here.
 */
object UnicodeSymbols {

  private val letters: String =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "αβγδεϵζηθϑικλμνξοπϖρϱςστυφϕχψωΓΔΘΛΞΠΣΥΦΨΩ"

  /**
   * The computed mapping from NFC-normalized single characters back to
   * their decomposed letter + accent(s) representation.
   */
  lazy val unicodeSymbols: Map[String, String] = {
    val result = scala.collection.mutable.Map.empty[String, String]
    val accentKeys = UnicodeAccents.unicodeAccents.keys.toArray

    for (letter <- letters.map(_.toString)) {
      for (accent <- accentKeys) {
        val combined = letter + accent
        val normalized = Normalizer.normalize(combined, Normalizer.Form.NFC)
        if (normalized.length == 1) {
          result(normalized) = combined
        }
        for (accent2 <- accentKeys) {
          if (accent != accent2) {
            val combined2 = combined + accent2
            val normalized2 = Normalizer.normalize(combined2, Normalizer.Form.NFC)
            if (normalized2.length == 1) {
              result(normalized2) = combined2
            }
          }
        }
      }
    }

    result.toMap
  }
}
