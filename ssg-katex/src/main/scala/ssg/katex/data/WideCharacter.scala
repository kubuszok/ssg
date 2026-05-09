/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file provides support for Unicode range U+1D400 to U+1D7FF,
 * Mathematical Alphanumeric Symbols.
 *
 * Function wideCharacterFont takes a wide character as input and returns
 * the font information necessary to render it properly.
 *
 * Original source: katex src/wide-character.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

/**
 * This file provides support for Unicode range U+1D400 to U+1D7FF,
 * Mathematical Alphanumeric Symbols.
 *
 * Function wideCharacterFont takes a wide character as input and returns
 * the font information necessary to render it properly.
 */
object WideCharacter {

  /**
   * Data below is from https://www.unicode.org/charts/PDF/U1D400.pdf
   * That document sorts characters into groups by font type, say bold or italic.
   *
   * In the arrays below, each subarray consists three elements:
   *      * The CSS class of that group when in math mode.
   *      * The CSS class of that group when in text mode.
   *      * The font name, so that KaTeX can get font metrics.
   */
  private val wideLatinLetterData: Array[(String, String, String)] = Array(
    ("mathbf", "textbf", "Main-Bold"),                // A-Z bold upright
    ("mathbf", "textbf", "Main-Bold"),                // a-z bold upright

    ("mathnormal", "textit", "Math-Italic"),          // A-Z italic
    ("mathnormal", "textit", "Math-Italic"),          // a-z italic

    ("boldsymbol", "boldsymbol", "Main-BoldItalic"),  // A-Z bold italic
    ("boldsymbol", "boldsymbol", "Main-BoldItalic"),  // a-z bold italic

    // Map fancy A-Z letters to script, not calligraphic.
    // This aligns with unicode-math and math fonts (except Cambria Math).
    ("mathscr", "textscr", "Script-Regular"),         // A-Z script
    ("", "", ""),                                     // a-z script.  No font

    ("", "", ""),                                     // A-Z bold script. No font
    ("", "", ""),                                     // a-z bold script. No font

    ("mathfrak", "textfrak", "Fraktur-Regular"),      // A-Z Fraktur
    ("mathfrak", "textfrak", "Fraktur-Regular"),      // a-z Fraktur

    ("mathbb", "textbb", "AMS-Regular"),              // A-Z double-struck
    ("mathbb", "textbb", "AMS-Regular"),              // k double-struck

    // Note that we are using a bold font, but font metrics for regular Fraktur.
    ("mathboldfrak", "textboldfrak", "Fraktur-Regular"),  // A-Z bold Fraktur
    ("mathboldfrak", "textboldfrak", "Fraktur-Regular"),  // a-z bold Fraktur

    ("mathsf", "textsf", "SansSerif-Regular"),        // A-Z sans-serif
    ("mathsf", "textsf", "SansSerif-Regular"),        // a-z sans-serif

    ("mathboldsf", "textboldsf", "SansSerif-Bold"),   // A-Z bold sans-serif
    ("mathboldsf", "textboldsf", "SansSerif-Bold"),   // a-z bold sans-serif

    ("mathitsf", "textitsf", "SansSerif-Italic"),     // A-Z italic sans-serif
    ("mathitsf", "textitsf", "SansSerif-Italic"),     // a-z italic sans-serif

    ("", "", ""),                                     // A-Z bold italic sans. No font
    ("", "", ""),                                     // a-z bold italic sans. No font

    ("mathtt", "texttt", "Typewriter-Regular"),       // A-Z monospace
    ("mathtt", "texttt", "Typewriter-Regular")        // a-z monospace
  )

  private val wideNumeralData: Array[(String, String, String)] = Array(
    ("mathbf", "textbf", "Main-Bold"),                // 0-9 bold
    ("", "", ""),                           // 0-9 double-struck. No KaTeX font.
    ("mathsf", "textsf", "SansSerif-Regular"),        // 0-9 sans-serif
    ("mathboldsf", "textboldsf", "SansSerif-Bold"),   // 0-9 bold sans-serif
    ("mathtt", "texttt", "Typewriter-Regular")        // 0-9 monospace
  )

  /**
   * Returns (fontName, cssClass) for the given wide character and mode.
   */
  def wideCharacterFont(wideChar: String, mode: Mode): (String, String) = {
    // IE doesn't support codePointAt(). So work with the surrogate pair.
    val h = wideChar.charAt(0).toInt  // high surrogate
    val l = wideChar.charAt(1).toInt  // low surrogate
    val codePoint = ((h - 0xd800) * 0x400) + (l - 0xdc00) + 0x10000

    val j = if (mode == Mode.Math) 0 else 1 // column index for CSS class.

    if (0x1d400 <= codePoint && codePoint < 0x1d6a4) {
      // wideLatinLetterData contains exactly 26 chars on each row.
      // So we can calculate the relevant row. No traverse necessary.
      val i = (codePoint - 0x1d400) / 26
      val entry = wideLatinLetterData(i)
      val cssClass = if (j == 0) entry._1 else entry._2
      (entry._3, cssClass)

    } else if (0x1d7ce <= codePoint && codePoint <= 0x1d7ff) {
      // Numerals, ten per row.
      val i = (codePoint - 0x1d7ce) / 10
      val entry = wideNumeralData(i)
      val cssClass = if (j == 0) entry._1 else entry._2
      (entry._3, cssClass)

    } else if (codePoint == 0x1d6a5 || codePoint == 0x1d6a6) {
      // dotless i or j
      val entry = wideLatinLetterData(0)
      val cssClass = if (j == 0) entry._1 else entry._2
      (entry._3, cssClass)

    } else if (0x1d6a6 < codePoint && codePoint < 0x1d7ce) {
      // Greek letters. Not supported, yet.
      ("", "")

    } else {
      // We don't support any wide characters outside 1D400–1D7FF.
      throw new ParseError("Unsupported character: " + wideChar)
    }
  }
}
