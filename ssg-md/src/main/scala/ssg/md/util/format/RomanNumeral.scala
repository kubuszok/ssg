/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/RomanNumeral.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/RomanNumeral.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format

import java.util.regex.Pattern

/*
   taken from: http://math.hws.edu/eck/cs124/javanotes3/c9/ex-9-3-answer.html

   An object of type RomanNumeral is an integer between 1 and 3999.  It can
   be constructed either from an integer or from a string that represents
   a Roman numeral in this range.  The function toString() will return a
   standardized Roman numeral representation of the number.  The function
   toInt() will return the number as a value of type int.
 */

class RomanNumeral private (val num: Int) {

  override def toString: String = {
    // Return the standard representation of this Roman numeral.
    val roman = new StringBuilder()
    var n     = num
    var i     = 0
    while (i < RomanNumeral.numbers.length) {
      while (n >= RomanNumeral.numbers(i)) {
        roman.append(RomanNumeral.letters(i))
        n -= RomanNumeral.numbers(i)
      }
      i += 1
    }
    roman.toString
  }

  def toInt: Int =
    // Return the value of this Roman numeral as an int.
    num
}

object RomanNumeral {

  /*
     The following arrays are used by the toString() function to construct
     the standard Roman numeral representation of the number.  For each i,
     the number numbers[i] is represented by the corresponding string, letters[i].
   */

  // @formatter:off
  private val numbers: Array[Int]    = Array(1000, 900,  500, 400,  100, 90,   50,  40,   10,  9,    5,   4,    1)
  private val letters: Array[String] = Array("M",  "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
  // @formatter:on

  val ROMAN_NUMERAL:                   Pattern = Pattern.compile("M{0,3}(?:CM|DC{0,3}|CD|C{1,3})?(?:XC|LX{0,3}|XL|X{1,3})?(?:IX|VI{0,3}|IV|I{1,3})?")
  val LOWERCASE_ROMAN_NUMERAL:         Pattern = Pattern.compile("m{0,3}(?:cm|dc{0,3}|cd|c{1,3})?(?:xc|lx{0,3}|xl|x{1,3})?(?:ix|vi{0,3}|iv|i{1,3})?")
  val LIMITED_ROMAN_NUMERAL:           Pattern = Pattern.compile("(?:X{1,3})?(?:IX|VI{0,3}|IV|I{1,3})?")
  val LIMITED_LOWERCASE_ROMAN_NUMERAL: Pattern = Pattern.compile("(?:x{1,3})?(?:ix|vi{0,3}|iv|i{1,3})?")

  def apply(arabic: Int): RomanNumeral = {
    // Constructor.  Creates the Roman number with the int value specified
    // by the parameter.  Throws a NumberFormatException if arabic is
    // not in the range 1 to 3999 inclusive.
    if (arabic < 1)
      throw new NumberFormatException("Value of RomanNumeral must be positive.")
    if (arabic > 3999)
      throw new NumberFormatException("Value of RomanNumeral must be 3999 or less.")
    new RomanNumeral(arabic)
  }

  def apply(roman: String): RomanNumeral = {
    // Constructor.  Creates the Roman number with the given representation.
    // For example, RomanNumeral("xvii") is 17.  If the parameter is not a
    // legal Roman numeral, a NumberFormatException is thrown.  Both upper and
    // lower case letters are allowed.

    if (roman.isEmpty)
      throw new NumberFormatException("An empty string does not define a Roman numeral.")

    val upper = roman.toUpperCase

    var i      = 0 // A position in the string, roman;
    var arabic = 0 // Arabic numeral equivalent of the part of the string that has been converted so far.

    while (i < upper.length()) {

      val letter = upper.charAt(i)
      val number = letterToNumber(letter)

      if (number < 0)
        throw new NumberFormatException("Illegal character \"" + letter + "\" in roman numeral.")

      i += 1

      if (i == upper.length()) {
        // There is no letter in the string following the one we have just processed.
        // So just add the number corresponding to the single letter to arabic.
        arabic += number
      } else {
        // Look at the next letter in the string.  If it has a larger Roman numeral
        // equivalent than number, then the two letters are counted together as
        // a Roman numeral with value (nextNumber - number).
        val nextNumber = letterToNumber(upper.charAt(i))
        if (nextNumber > number) {
          // Combine the two letters to get one value, and move on to next position in string.
          arabic += (nextNumber - number)
          i += 1
        } else {
          // Don't combine the letters.  Just add the value of the one letter onto the number.
          arabic += number
        }
      }
    }

    if (arabic > 3999)
      throw new NumberFormatException("Roman numeral must have value 3999 or less.")

    new RomanNumeral(arabic)
  }

  private def letterToNumber(letter: Char): Int =
    // Find the integer value of letter considered as a Roman numeral.  Return
    // -1 if letter is not a legal Roman numeral.  The letter must be upper case.
    letter match {
      case 'I' => 1
      case 'V' => 5
      case 'X' => 10
      case 'L' => 50
      case 'C' => 100
      case 'D' => 500
      case 'M' => 1000
      case _   => -1
    }
}
