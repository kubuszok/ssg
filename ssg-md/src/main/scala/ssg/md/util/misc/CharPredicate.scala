/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/CharPredicate.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/CharPredicate.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package misc

import ssg.md.Nullable

import java.util.BitSet
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** Interface for set of characters to use for inclusion exclusion tests. Can be used for code points since the argument is int.
  */
trait CharPredicate {

  /** Test whether the given code point matches this predicate. */
  def test(value: Int): Boolean

  /** Test whether the given char matches this predicate. */
  def test(value: Char): Boolean = test(value.toInt)

  /** Returns a composed predicate that represents a short-circuiting logical AND of this predicate and another. When evaluating the composed predicate, if this predicate is `false`, then the `other`
    * predicate is not evaluated.
    *
    * Any exceptions thrown during evaluation of either predicate are relayed to the caller; if evaluation of this predicate throws an exception, the `other` predicate will not be evaluated.
    *
    * @param other
    *   a predicate that will be logically-ANDed with this predicate
    * @return
    *   a composed predicate that represents the short-circuiting logical AND of this predicate and the `other` predicate
    */
  def and(other: CharPredicate): CharPredicate =
    if (this eq CharPredicate.NONE) CharPredicate.NONE
    else if (other eq CharPredicate.NONE) CharPredicate.NONE
    else if (this eq CharPredicate.ALL) other
    else if (other eq CharPredicate.ALL) this
    else {
      val self = this
      (value: Int) => self.test(value) && other.test(value)
    }

  /** Returns a predicate that represents the logical negation of this predicate.
    *
    * @return
    *   a predicate that represents the logical negation of this predicate
    */
  def negate(): CharPredicate =
    if (this eq CharPredicate.NONE) CharPredicate.ALL
    else if (this eq CharPredicate.ALL) CharPredicate.NONE
    else {
      val self = this
      (value: Int) => !self.test(value)
    }

  /** Returns a composed predicate that represents a short-circuiting logical OR of this predicate and another. When evaluating the composed predicate, if this predicate is `true`, then the `other`
    * predicate is not evaluated.
    *
    * Any exceptions thrown during evaluation of either predicate are relayed to the caller; if evaluation of this predicate throws an exception, the `other` predicate will not be evaluated.
    *
    * @param other
    *   a predicate that will be logically-ORed with this predicate
    * @return
    *   a composed predicate that represents the short-circuiting logical OR of this predicate and the `other` predicate
    */
  def or(other: CharPredicate): CharPredicate =
    if (this eq CharPredicate.ALL) CharPredicate.ALL
    else if (other eq CharPredicate.ALL) CharPredicate.ALL
    else if (this eq CharPredicate.NONE) other
    else if (other eq CharPredicate.NONE) this
    else {
      val self = this
      (value: Int) => self.test(value) || other.test(value)
    }
}

object CharPredicate {

  /** Creates a CharPredicate from a function. */
  implicit def fromFunction(f: Int => Boolean): CharPredicate =
    new CharPredicate {
      def test(value: Int): Boolean = f(value)
    }

  val NONE:                    CharPredicate = (value: Int) => false
  val ALL:                     CharPredicate = (value: Int) => true
  val SPACE:                   CharPredicate = (value: Int) => value == ' '
  val TAB:                     CharPredicate = (value: Int) => value == '\t'
  val EOL:                     CharPredicate = (value: Int) => value == '\n'
  val ANY_EOL:                 CharPredicate = (value: Int) => value == '\n' || value == '\r'
  val ANY_EOL_NUL:             CharPredicate = (value: Int) => value == '\n' || value == '\r' || value == '\u0000'
  val BACKSLASH:               CharPredicate = (value: Int) => value == '\\'
  val SLASH:                   CharPredicate = (value: Int) => value == '/'
  val LINE_SEP:                CharPredicate = (value: Int) => value == '\u2028'
  val HASH:                    CharPredicate = (value: Int) => value == '#'
  val SPACE_TAB:               CharPredicate = (value: Int) => value == ' ' || value == '\t'
  val SPACE_TAB_NUL:           CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\u0000'
  val SPACE_TAB_LINE_SEP:      CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\u2028'
  val SPACE_TAB_NBSP_LINE_SEP: CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\u00a0' || value == '\u2028'
  val SPACE_EOL:               CharPredicate = (value: Int) => value == ' ' || value == '\n'
  val SPACE_ANY_EOL:           CharPredicate = (value: Int) => value == ' ' || value == '\r' || value == '\n'
  val SPACE_TAB_NBSP:          CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\u00a0'
  val SPACE_TAB_EOL:           CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n'
  val SPACE_TAB_NBSP_EOL:      CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n' || value == '\u00a0'
  val WHITESPACE:              CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n' || value == '\r'
  val WHITESPACE_OR_NUL:       CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u0000'
  val WHITESPACE_NBSP:         CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u00a0'
  val WHITESPACE_NBSP_OR_NUL:  CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u00a0' || value == '\u0000'
  val BLANKSPACE:              CharPredicate = (value: Int) => value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u000b' || value == '\f'
  val HEXADECIMAL_DIGITS:      CharPredicate = (value: Int) => (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F')
  val DECIMAL_DIGITS:          CharPredicate = (value: Int) => value >= '0' && value <= '9'
  val OCTAL_DIGITS:            CharPredicate = (value: Int) => value >= '0' && value <= '7'
  val BINARY_DIGITS:           CharPredicate = (value: Int) => value >= '0' && value <= '1'

  @deprecated("Use NONE instead", "0.1.0")
  val FALSE: CharPredicate = NONE
  @deprecated("Use ALL instead", "0.1.0")
  val TRUE: CharPredicate = ALL
  @deprecated("Use SPACE_TAB_NUL instead", "0.1.0")
  val SPACE_TAB_OR_NUL: CharPredicate = SPACE_TAB_NUL

  def standardOrAnyOf(c1: Char): CharPredicate =
    if (SPACE.test(c1)) SPACE
    else if (EOL.test(c1)) EOL
    else if (TAB.test(c1)) TAB
    else (value: Int) => value == c1.toInt

  def standardOrAnyOf(c1: Char, c2: Char): CharPredicate =
    if (c1 == c2) standardOrAnyOf(c1)
    else if (SPACE_TAB.test(c1) && SPACE_TAB.test(c2)) SPACE_TAB
    else if (ANY_EOL.test(c1) && ANY_EOL.test(c2)) ANY_EOL
    else (value: Int) => value == c1.toInt || value == c2.toInt

  def standardOrAnyOf(c1: Char, c2: Char, c3: Char): CharPredicate =
    if (c1 == c2 && c2 == c3) standardOrAnyOf(c1)
    else if (c1 == c2 || c1 == c3) standardOrAnyOf(c2, c3)
    else if (c2 == c3) standardOrAnyOf(c1, c3)
    else (value: Int) => value == c1.toInt || value == c2.toInt || value == c3.toInt

  def standardOrAnyOf(c1: Char, c2: Char, c3: Char, c4: Char): CharPredicate =
    if (c1 == c2 && c2 == c3 && c3 == c4) standardOrAnyOf(c1)
    else if (c1 == c2 || c1 == c3 || c1 == c4) standardOrAnyOf(c2, c3, c4)
    else if (c2 == c3 || c2 == c4) standardOrAnyOf(c1, c3, c4)
    else if (c3 == c4) standardOrAnyOf(c1, c2, c3)
    else if (WHITESPACE.test(c1) && WHITESPACE.test(c2) && WHITESPACE.test(c3) && WHITESPACE.test(c4)) WHITESPACE
    else (value: Int) => value == c1.toInt || value == c2.toInt || value == c3.toInt || value == c4.toInt

  def anyOf(chars: Char*): CharPredicate =
    chars.length match {
      case 0 => NONE
      case 1 => standardOrAnyOf(chars(0))
      case 2 => standardOrAnyOf(chars(0), chars(1))
      case 3 => standardOrAnyOf(chars(0), chars(1), chars(2))
      case 4 => standardOrAnyOf(chars(0), chars(1), chars(2), chars(3))
      case _ => anyOf(String.valueOf(chars.toArray))
    }

  def indexOf(thizz: CharSequence, c: Char): Int =
    indexOf(thizz, c, 0, thizz.length())

  def indexOf(thizz: CharSequence, c: Char, fromIndex: Int, endIndex: Int): Int = {
    val start = Math.max(fromIndex, 0)
    val end   = Math.min(thizz.length(), endIndex)
    boundary {
      var i = start
      while (i < end) {
        if (c == thizz.charAt(i)) break(i)
        i += 1
      }
      -1
    }
  }

  def anyOf(chars: CharSequence): CharPredicate = {
    val maxFixed = 4
    chars.length() match {
      case 0 => NONE
      case 1 => standardOrAnyOf(chars.charAt(0))
      case 2 => standardOrAnyOf(chars.charAt(0), chars.charAt(1))
      case 3 => standardOrAnyOf(chars.charAt(0), chars.charAt(1), chars.charAt(2))
      case 4 => standardOrAnyOf(chars.charAt(0), chars.charAt(1), chars.charAt(2), chars.charAt(3))
      case _ =>
        // create bit set for ascii and add any above as a string index of test
        var ascii:  Nullable[BitSet]        = Nullable.empty
        var others: Nullable[StringBuilder] = Nullable.empty
        val iMax = chars.length()

        var i = 0
        while (i < iMax) {
          val c = chars.charAt(i)
          if (c <= 127) {
            if (ascii.isEmpty) ascii = Nullable(new BitSet())
            ascii.get.set(c.toInt)
          } else {
            if (others.isEmpty) others = Nullable(new StringBuilder())
            if (indexOf(others.get, c) == -1) {
              others.get.append(c)
            }
          }
          i += 1
        }

        val finalOthers: Nullable[String]        = others.map(_.toString)
        val testOthers:  Nullable[CharPredicate] =
          if (finalOthers.isEmpty || finalOthers.get.isEmpty) Nullable.empty
          else if (finalOthers.get.length <= maxFixed) Nullable(anyOf(finalOthers.get))
          else Nullable((value: Int) => indexOf(finalOthers.get, value.toChar) != -1)
        val testAscii: Nullable[CharPredicate] =
          if (ascii.isEmpty || ascii.get.cardinality() == 0) Nullable.empty
          else Nullable((value: Int) => ascii.get.get(value))

        assert(testAscii.isDefined || testOthers.isDefined)

        if (testAscii.isDefined && testOthers.isDefined) testAscii.get.or(testOthers.get)
        else if (testAscii.isDefined) testAscii.get
        else testOthers.get
    }
  }
}
