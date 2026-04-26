/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/string.dart
 * Original: Copyright (c) 2024 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: StringExtension → StringUtil extension
 *   Convention: Uses SpanScanner from Phase 0 infrastructure
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/string.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package util

import scala.util.boundary
import scala.util.boundary.break

object StringUtil {

  extension (str: String) {

    /** Returns a minimally-escaped CSS identifier whose contents evaluates to this string. */
    def toCssIdentifier: String = boundary[String] {
      val buffer  = new StringBuilder()
      val scanner = new SpanScanner(str)

      def writeEscape(character: Int): Unit = {
        buffer.append('\\')
        buffer.append(character.toHexString)
        if (scanner.peekChar() >= 0 && CharCode.isHex(scanner.peekChar())) {
          buffer.append(' ')
        }
      }

      def consumeSurrogatePair(character: Int): Unit = {
        val next = scanner.peekChar(1)
        if (next < 0 || !CharCode.isLowSurrogate(next)) {
          scanner.error("An individual surrogate can't be represented as a CSS identifier.", scanner.position, 1)
        } else if (CharCode.isPrivateUseHighSurrogate(character)) {
          writeEscape(CharCode.combineSurrogates(scanner.readChar(), scanner.readChar()))
        } else {
          buffer.append(scanner.readChar().toChar)
          buffer.append(scanner.readChar().toChar)
        }
      }

      var doubleDash = false
      if (scanner.scanChar(CharCode.$minus)) {
        if (scanner.isDone) break("\\2d")
        buffer.append('-')

        if (scanner.scanChar(CharCode.$minus)) {
          buffer.append('-')
          doubleDash = true
        }
      }

      if (!doubleDash) {
        val c = scanner.peekChar()
        if (c < 0) {
          scanner.error("The empty string can't be represented as a CSS identifier.")
        } else if (c == 0) {
          scanner.error("U+0000 can't be represented as a CSS identifier.")
        } else if (CharCode.isHighSurrogate(c)) {
          consumeSurrogatePair(c)
        } else if (CharCode.isLowSurrogate(c)) {
          scanner.error("An individual surrogate can't be represented as a CSS identifier.", scanner.position, 1)
        } else if (CharCode.isNameStart(c) && !CharCode.isPrivateUseBMP(c)) {
          buffer.append(scanner.readChar().toChar)
        } else {
          writeEscape(scanner.readChar())
        }
      }

      while (!scanner.isDone) {
        val c = scanner.peekChar()
        if (c == 0) {
          scanner.error("U+0000 can't be represented as a CSS identifier.")
        } else if (CharCode.isHighSurrogate(c)) {
          consumeSurrogatePair(c)
        } else if (CharCode.isLowSurrogate(c)) {
          scanner.error("An individual surrogate can't be represented as a CSS identifier.", scanner.position, 1)
        } else if (CharCode.isName(c) && !CharCode.isPrivateUseBMP(c)) {
          buffer.append(scanner.readChar().toChar)
        } else {
          writeEscape(scanner.readChar())
        }
      }

      buffer.toString()
    }
  }

  /** Safe codeUnitAt that returns -1 instead of throwing for out-of-bounds. */
  def codeUnitAtOrNull(s: String, index: Int): Int =
    if (index >= s.length) -1 else s.charAt(index).toInt
}
