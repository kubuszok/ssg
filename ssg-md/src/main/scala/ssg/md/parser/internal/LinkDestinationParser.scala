/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/LinkDestinationParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package internal

import ssg.md.util.format.TableFormatOptions
import ssg.md.util.sequence.{ BasedSequence, Escaping }

import java.util.BitSet
import scala.util.boundary
import scala.util.boundary.break

class LinkDestinationParser(
  val allowMatchedParentheses: Boolean,
  val spaceInUrls:             Boolean,
  val parseJekyllMacrosInUrls: Boolean,
  val intellijDummyIdentifier: Boolean
) {

  private val _allowMatchedParentheses: Boolean = allowMatchedParentheses || parseJekyllMacrosInUrls

  // needed for hand rolled link parser
  val EXCLUDED_0_TO_SPACE_CHARS: BitSet = LinkDestinationParser.getCharSetRange('\u0000', '\u0020')
  if (intellijDummyIdentifier) EXCLUDED_0_TO_SPACE_CHARS.clear(TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER_CHAR)

  val JEKYLL_EXCLUDED_CHARS: BitSet = LinkDestinationParser.getCharSet("{}\\")
  JEKYLL_EXCLUDED_CHARS.or(EXCLUDED_0_TO_SPACE_CHARS)
  JEKYLL_EXCLUDED_CHARS.clear(' ')
  JEKYLL_EXCLUDED_CHARS.clear('\t')

  val PAREN_EXCLUDED_CHARS: BitSet = LinkDestinationParser.getCharSet("()\\")
  PAREN_EXCLUDED_CHARS.or(EXCLUDED_0_TO_SPACE_CHARS)

  val PAREN_ESCAPABLE_CHARS: BitSet = LinkDestinationParser.getCharSet(Escaping.ESCAPABLE_CHARS)
  val PAREN_QUOTE_CHARS:     BitSet = LinkDestinationParser.getCharSet("\"'")

  def parseLinkDestination(input: BasedSequence, startIndex: Int): BasedSequence = {
    val iMax      = input.length()
    var lastMatch = startIndex

    var openParenCount   = 0
    var openParenState   = 0
    var jekyllOpenParens = 0

    var openJekyllState = if (parseJekyllMacrosInUrls) 0 else -1

    boundary {
      var i = startIndex
      while (i < iMax) {
        val c         = input.charAt(i)
        val nextIndex = i + 1

        if (openJekyllState >= 0) {
          openJekyllState match {
            case 0 =>
              // looking for {{
              if (openParenState != 1) {
                if (c == '{' && input.safeCharAt(nextIndex) == '{') {
                  openJekyllState = 1
                }
              }

            case 1 =>
              // looking for second {
              if (openParenState == 1) {
                // escaped char
              } else {
                if (c == '{') {
                  jekyllOpenParens = 0
                  openJekyllState = 2
                } else {
                  openJekyllState = 0 // start over
                }
              }

            case 2 =>
              // accumulating or waiting for }}
              if (openParenState != 1) {
                if (c == '}') {
                  openJekyllState = 3
                } else if (c == '(') {
                  jekyllOpenParens += 1
                } else if (c == ')') {
                  if (jekyllOpenParens > 0) {
                    // take ) if main parser did not terminate already
                    if (openParenState != -1) lastMatch = nextIndex
                    jekyllOpenParens -= 1
                  } else {
                    openJekyllState = 0
                  }
                } else if (JEKYLL_EXCLUDED_CHARS.get(c)) {
                  openParenCount += jekyllOpenParens // transfer open parens to normal parser
                  openJekyllState = 0 // start over
                }
              }

            case 3 =>
              // accumulating or waiting for second }
              if (openParenState != 1) {
                if (c == '}') {
                  lastMatch = nextIndex // found it
                  openParenState = 0 // reset in case it was -1
                  openJekyllState = 0 // start over
                } else if (JEKYLL_EXCLUDED_CHARS.get(c)) {
                  openParenCount += jekyllOpenParens // transfer open parens to normal parser
                  openJekyllState = 0 // start over
                } else {
                  openJekyllState = 2 // continue accumulating
                }
              } else {
                openJekyllState = 2 // continue accumulating
              }

            case _ =>
              throw IllegalStateException("Illegal Jekyll Macro Parsing State")
          }
        }

        // parens matching
        if (openParenState >= 0) {
          openParenState match {
            case 0 => // starting
              if (c == '\\') {
                if (PAREN_ESCAPABLE_CHARS.get(input.safeCharAt(nextIndex))) {
                  // escaped, take the next one if available
                  openParenState = 1
                }
                lastMatch = nextIndex
              } else if (c == '(') {
                if (openJekyllState != 2) {
                  if (_allowMatchedParentheses) {
                    openParenCount += 1
                  } else {
                    if (openParenCount == 0) {
                      openParenCount += 1
                    } else {
                      // invalid, parentheses need escaping beyond 1
                      lastMatch = startIndex
                      openParenState = -1
                    }
                  }
                }
                if (openParenState >= 0) lastMatch = nextIndex
              } else if (c == ')') {
                if (openJekyllState != 2) {
                  if (openParenCount > 0) {
                    openParenCount -= 1
                    lastMatch = nextIndex
                  } else if (!_allowMatchedParentheses) {
                    openParenState = -1
                  } else {
                    openParenState = -1
                  }
                }
              } else {
                if (c == ' ') {
                  if (spaceInUrls && !PAREN_QUOTE_CHARS.get(input.safeCharAt(nextIndex))) {
                    // space will be included by next char, ie. trailing spaces not included
                  } else {
                    openParenState = -1
                  }
                } else if (!PAREN_EXCLUDED_CHARS.get(c)) {
                  lastMatch = nextIndex
                } else {
                  // we are done, no more matches here
                  openParenState = -1
                }
              }

            case 1 =>
              // escaped
              lastMatch = nextIndex
              openParenState = 0

            case _ =>
              throw IllegalStateException("Illegal Jekyll Macro Parsing State")
          }
        }

        if (openJekyllState <= 0 && openParenState == -1) {
          break(())
        }

        i += 1
      }
    }

    // always have something even if it is empty
    input.subSequence(startIndex, lastMatch)
  }
}

object LinkDestinationParser {

  def getCharSet(chars: CharSequence): BitSet = {
    val charSet = new BitSet(chars.length())
    val iMax    = chars.length()
    var i       = 0
    while (i < iMax) {
      charSet.set(chars.charAt(i))
      i += 1
    }
    charSet
  }

  def getCharSetRange(charFrom: Char, charTo: Char): BitSet = {
    val charSet = new BitSet()
    var i       = charFrom.toInt
    while (i <= charTo.toInt) {
      charSet.set(i)
      i += 1
    }
    charSet
  }
}
