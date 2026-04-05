/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Character code constants and classification utilities for Sass parsing.
 * Replaces Dart's `charcode` package and `util/character.dart`.
 */
package ssg
package sass
package util

/** ASCII character code constants used throughout the Sass parser. */
object CharCode {

  // Whitespace
  final val $tab: Int = 0x09
  final val $lf: Int = 0x0A
  final val $vt: Int = 0x0B
  final val $ff: Int = 0x0C
  final val $cr: Int = 0x0D
  final val $space: Int = 0x20

  // Digits
  final val $0: Int = 0x30
  final val $1: Int = 0x31
  final val $2: Int = 0x32
  final val $3: Int = 0x33
  final val $4: Int = 0x34
  final val $5: Int = 0x35
  final val $6: Int = 0x36
  final val $7: Int = 0x37
  final val $8: Int = 0x38
  final val $9: Int = 0x39

  // Uppercase letters
  final val $A: Int = 0x41
  final val $B: Int = 0x42
  final val $C: Int = 0x43
  final val $D: Int = 0x44
  final val $E: Int = 0x45
  final val $F: Int = 0x46
  final val $G: Int = 0x47
  final val $H: Int = 0x48
  final val $I: Int = 0x49
  final val $J: Int = 0x4A
  final val $K: Int = 0x4B
  final val $L: Int = 0x4C
  final val $M: Int = 0x4D
  final val $N: Int = 0x4E
  final val $O: Int = 0x4F
  final val $P: Int = 0x50
  final val $Q: Int = 0x51
  final val $R: Int = 0x52
  final val $S: Int = 0x53
  final val $T: Int = 0x54
  final val $U: Int = 0x55
  final val $V: Int = 0x56
  final val $W: Int = 0x57
  final val $X: Int = 0x58
  final val $Y: Int = 0x59
  final val $Z: Int = 0x5A

  // Lowercase letters
  final val $a: Int = 0x61
  final val $b: Int = 0x62
  final val $c: Int = 0x63
  final val $d: Int = 0x64
  final val $e: Int = 0x65
  final val $f: Int = 0x66
  final val $g: Int = 0x67
  final val $h: Int = 0x68
  final val $i: Int = 0x69
  final val $j: Int = 0x6A
  final val $k: Int = 0x6B
  final val $l: Int = 0x6C
  final val $m: Int = 0x6D
  final val $n: Int = 0x6E
  final val $o: Int = 0x6F
  final val $p: Int = 0x70
  final val $q: Int = 0x71
  final val $r: Int = 0x72
  final val $s: Int = 0x73
  final val $t: Int = 0x74
  final val $u: Int = 0x75
  final val $v: Int = 0x76
  final val $w: Int = 0x77
  final val $x: Int = 0x78
  final val $y: Int = 0x79
  final val $z: Int = 0x7A

  // Symbols & punctuation
  final val $exclamation: Int = 0x21
  final val $double_quote: Int = 0x22
  final val $hash: Int = 0x23
  final val $dollar: Int = 0x24
  final val $percent: Int = 0x25
  final val $ampersand: Int = 0x26
  final val $single_quote: Int = 0x27
  final val $lparen: Int = 0x28
  final val $rparen: Int = 0x29
  final val $asterisk: Int = 0x2A
  final val $plus: Int = 0x2B
  final val $comma: Int = 0x2C
  final val $minus: Int = 0x2D
  final val $dot: Int = 0x2E
  final val $slash: Int = 0x2F
  final val $colon: Int = 0x3A
  final val $semicolon: Int = 0x3B
  final val $lt: Int = 0x3C
  final val $equal: Int = 0x3D
  final val $gt: Int = 0x3E
  final val $question: Int = 0x3F
  final val $at: Int = 0x40
  final val $lbracket: Int = 0x5B
  final val $backslash: Int = 0x5C
  final val $rbracket: Int = 0x5D
  final val $circumflex: Int = 0x5E
  final val $underscore: Int = 0x5F
  final val $backtick: Int = 0x60
  final val $lbrace: Int = 0x7B
  final val $pipe: Int = 0x7C
  final val $rbrace: Int = 0x7D
  final val $tilde: Int = 0x7E

  /** Highest allowed Unicode code point in CSS. */
  final val maxAllowedCharacter: Int = 0x10FFFF

  // --- Classification methods ---

  /** Whether [c] is an ASCII alphabetic character. */
  def isAlphabetic(c: Int): Boolean =
    (c >= $a && c <= $z) || (c >= $A && c <= $Z)

  /** Whether [c] is an ASCII digit. */
  def isDigit(c: Int): Boolean =
    c >= $0 && c <= $9

  /** Whether [c] is an ASCII alphanumeric character. */
  def isAlphanumeric(c: Int): Boolean =
    isAlphabetic(c) || isDigit(c)

  /** Whether [c] is a hexadecimal digit. */
  def isHex(c: Int): Boolean =
    isDigit(c) || (c >= $a && c <= $f) || (c >= $A && c <= $F)

  /** Whether [c] can start a Sass identifier (letter, underscore, or non-ASCII). */
  def isNameStart(c: Int): Boolean =
    c == $underscore || isAlphabetic(c) || c >= 0x0080

  /** Whether [c] can appear in a Sass identifier body. */
  def isName(c: Int): Boolean =
    isNameStart(c) || isDigit(c) || c == $minus

  /** Whether [c] is an ASCII whitespace character. */
  def isWhitespace(c: Int): Boolean =
    c == $space || c == $tab || isNewline(c)

  /** Whether [c] is an ASCII newline character (LF, CR, or FF). */
  def isNewline(c: Int): Boolean =
    c == $lf || c == $cr || c == $ff

  /** Whether [c] is a space or tab. */
  def isSpaceOrTab(c: Int): Boolean =
    c == $space || c == $tab

  /** Whether [c] is a UTF-16 high surrogate. */
  def isHighSurrogate(c: Int): Boolean =
    c >= 0xD800 && c <= 0xDBFF

  /** Whether [c] is a UTF-16 low surrogate. */
  def isLowSurrogate(c: Int): Boolean =
    c >= 0xDC00 && c <= 0xDFFF

  /** Whether [c] is a private-use character in the BMP. */
  def isPrivateUseBMP(c: Int): Boolean =
    c >= 0xE000 && c <= 0xF8FF

  /** Whether [c] is a high surrogate for a supplementary private-use code point. */
  def isPrivateUseHighSurrogate(c: Int): Boolean =
    c >= 0xDB80 && c <= 0xDBFF

  // --- Conversion utilities ---

  /** Converts a hexadecimal digit character to its integer value (0-15). */
  def asHex(c: Int): Int = {
    if (c >= $0 && c <= $9) c - $0
    else if (c >= $a && c <= $f) 10 + c - $a
    else if (c >= $A && c <= $F) 10 + c - $A
    else throw new IllegalArgumentException(s"Not a hex digit: ${c.toChar}")
  }

  /** Converts a value 0-15 to a lowercase hex digit character code. */
  def hexCharFor(number: Int): Int = {
    if (number < 10) $0 + number
    else $a - 10 + number
  }

  /** Converts a decimal digit character to its integer value (0-9). */
  def asDecimal(c: Int): Int = {
    if (c >= $0 && c <= $9) c - $0
    else throw new IllegalArgumentException(s"Not a digit: ${c.toChar}")
  }

  /** Converts a value 0-9 to a digit character code. */
  def decimalCharFor(number: Int): Int = $0 + number

  /** Returns the closing bracket for an opening bracket, and vice versa. */
  def opposite(c: Int): Int = c match {
    case `$lparen` => $rparen
    case `$rparen` => $lparen
    case `$lbracket` => $rbracket
    case `$rbracket` => $lbracket
    case `$lbrace` => $rbrace
    case `$rbrace` => $lbrace
    case _ => throw new IllegalArgumentException(s"Not a bracket: ${c.toChar}")
  }

  /** Converts an ASCII character to uppercase. */
  def toUpperCase(c: Int): Int =
    if (c >= $a && c <= $z) c - 0x20 else c

  /** Converts an ASCII character to lowercase. */
  def toLowerCase(c: Int): Int =
    if (c >= $A && c <= $Z) c + 0x20 else c

  /** Whether two characters are equal ignoring ASCII case. */
  def characterEqualsIgnoreCase(c1: Int, c2: Int): Boolean =
    c1 == c2 || toLowerCase(c1) == toLowerCase(c2)

  /**
   * Optimized case-insensitive comparison when [letter] is known to be
   * a lowercase ASCII letter.
   */
  def equalsLetterIgnoreCase(letter: Int, actual: Int): Boolean =
    actual == letter || actual == letter - 0x20

  /** Combines a UTF-16 surrogate pair into a single code point. */
  def combineSurrogates(high: Int, low: Int): Int =
    0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00)

  /** Whether [identifier] starts with `-` or `_` (a Sass private member). */
  def isPrivate(identifier: String): Boolean =
    identifier.nonEmpty && {
      val first = identifier.charAt(0).toInt
      first == $minus || first == $underscore
    }
}
