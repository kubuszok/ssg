/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/HtmlDeepParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package internal

import ssg.md.ast.util.Parsing

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.util.regex.Pattern

import scala.util.boundary
import scala.util.boundary.break

class HtmlDeepParser(customTags: List[String] = Nil) {

  private val myOpenTags:       ArrayBuffer[String]                = ArrayBuffer.empty
  private var myClosingPattern: Nullable[Pattern]                  = Nullable.empty
  private var myHtmlMatch:      Nullable[HtmlDeepParser.HtmlMatch] = Nullable.empty
  private var myHtmlCount:      Int                                = 0
  private var myFirstBlockTag:  Boolean                            = false

  private val myBlockTags: mutable.HashSet[String] = mutable.HashSet.from(HtmlDeepParser.BLOCK_TAGS)
  myBlockTags.addAll(customTags)

  def openTags: ArrayBuffer[String] = myOpenTags

  def closingPattern: Nullable[Pattern] = myClosingPattern

  def htmlMatch: Nullable[HtmlDeepParser.HtmlMatch] = myHtmlMatch

  def htmlCount: Int = myHtmlCount

  def isFirstBlockTag: Boolean = myFirstBlockTag

  def isHtmlClosed: Boolean = myClosingPattern.isEmpty && myOpenTags.isEmpty

  def isBlankLineInterruptible: Boolean =
    (myOpenTags.isEmpty && myClosingPattern.isEmpty) ||
      (myHtmlMatch.exists(_ == HtmlDeepParser.HtmlMatch.OPEN_TAG) && myClosingPattern.isDefined && myOpenTags.size == 1)

  def haveOpenRawTag: Boolean =
    myClosingPattern.isDefined && !myHtmlMatch.contains(HtmlDeepParser.HtmlMatch.OPEN_TAG)

  def haveOpenBlockTag: Boolean =
    myOpenTags.exists(myBlockTags.contains)

  def hadHtml: Boolean = myHtmlCount > 0 || !isHtmlClosed

  // handle optional closing tags
  private def openTag(tagName: String): Unit = boundary {
    if (myOpenTags.nonEmpty) {
      val lastTag = myOpenTags.last

      HtmlDeepParser.OPTIONAL_TAGS.get(lastTag).foreach { optionalSet =>
        if (optionalSet.contains(tagName)) {
          myOpenTags(myOpenTags.size - 1) = tagName
          myFirstBlockTag = myBlockTags.contains(tagName)
          break(()) // early exit
        }
      }
    }
    myOpenTags.addOne(tagName)
    myFirstBlockTag = myBlockTags.contains(tagName)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Return"))
  def parseHtmlChunk(html: CharSequence, blockTagsOnly: Boolean, parseNonBlock: Boolean, firstOpenTagOnOneLine: Boolean): Unit = {
    if (myHtmlCount == 0 && myHtmlMatch.isDefined) {
      myHtmlCount += 1
    }

    var pendingOpen: Nullable[String] = Nullable.empty
    var useFirstOpenTagOnOneLine = firstOpenTagOnOneLine
    var currentHtml              = html
    var blockTagsOnlyVar         = blockTagsOnly

    boundary {
      while (currentHtml.length() != 0) {
        if (myClosingPattern.isDefined) {
          // see if we find HTML pattern
          val matcher = myClosingPattern.get.matcher(currentHtml)
          if (!matcher.find()) break(())

          if (myHtmlMatch.contains(HtmlDeepParser.HtmlMatch.OPEN_TAG)) {
            if (matcher.group() == "<") {
              // previous open tag not closed, drop it and re-parse from <
              if (pendingOpen.isEmpty) {
                myOpenTags.remove(myOpenTags.size - 1)
              } else {
                if (useFirstOpenTagOnOneLine) {
                  // not recognized as html, skip the line
                  pendingOpen = Nullable.empty
                  myClosingPattern = Nullable.empty
                  break(())
                }
              }
            } else {
              useFirstOpenTagOnOneLine = false
              if (matcher.group().endsWith("/>")) {
                // drop the tag, it is self closed
                if (pendingOpen.isEmpty) {
                  myOpenTags.remove(myOpenTags.size - 1)
                }
                if (myHtmlCount == 0) myHtmlCount += 1
              } else {
                if (pendingOpen.isDefined) {
                  // now we have it
                  if (!HtmlDeepParser.VOID_TAGS.contains(pendingOpen.get)) {
                    openTag(pendingOpen.get)
                  }
                  myHtmlCount += 1
                }
              }
              currentHtml = currentHtml.subSequence(matcher.end(), currentHtml.length())
            }
          } else {
            currentHtml = currentHtml.subSequence(matcher.end(), currentHtml.length())
          }

          pendingOpen = Nullable.empty
          myClosingPattern = Nullable.empty
        } else {
          // start pattern
          val matcher = HtmlDeepParser.START_PATTERN.matcher(currentHtml)
          if (!matcher.find()) break(())

          val nextHtml = currentHtml.subSequence(matcher.end(), currentHtml.length())
          val iMax     = HtmlDeepParser.PATTERN_MAP.length
          myClosingPattern = Nullable.empty

          boundary {
            var i = 1
            while (i < iMax) {
              if (matcher.group(i) != null) { // null check at Java interop boundary
                var group          = matcher.group(i).toLowerCase
                val htmlMatchEntry = HtmlDeepParser.PATTERN_MAP(i)

                val pos = group.indexOf(':')
                if (pos >= 0) {
                  // strip out the namespace
                  group = group.substring(pos + 1)
                }

                val isBlockTag = myBlockTags.contains(group)

                if ((blockTagsOnlyVar || !parseNonBlock) && matcher.start() > 0) {
                  // nothing but blanks allowed before first pattern match when block tags only
                  val leading = currentHtml.subSequence(0, matcher.start()).toString
                  if (leading.trim.nonEmpty) break(())
                }

                // see if self closed and if void or block
                if (htmlMatchEntry.isDefined) {
                  val hm = htmlMatchEntry.get
                  if (hm != HtmlDeepParser.HtmlMatch.OPEN_TAG && hm != HtmlDeepParser.HtmlMatch.CLOSE_TAG) {
                    // block and has closing tag sequence
                    myClosingPattern = Nullable(hm.close.get)
                    myHtmlMatch = htmlMatchEntry
                    myHtmlCount += 1
                    useFirstOpenTagOnOneLine = false
                    break(())
                  }

                  if ((blockTagsOnlyVar || !parseNonBlock) && !isBlockTag) {
                    // we ignore this one, not block
                    break(())
                  }

                  // now anything goes
                  blockTagsOnlyVar = false

                  // if not void or self-closed then add it to the stack
                  if (hm == HtmlDeepParser.HtmlMatch.OPEN_TAG && HtmlDeepParser.VOID_TAGS.contains(group)) {
                    // no closing pattern and we don't push tag
                    if (useFirstOpenTagOnOneLine) {
                      pendingOpen = Nullable(group)
                    } else {
                      myHtmlMatch = htmlMatchEntry
                      myHtmlCount += 1
                    }
                    break(())
                  }

                  if (hm == HtmlDeepParser.HtmlMatch.OPEN_TAG) {
                    // open tag, push to the stack
                    myHtmlMatch = htmlMatchEntry
                    myClosingPattern = hm.close
                    if (useFirstOpenTagOnOneLine) {
                      pendingOpen = Nullable(group)
                    } else {
                      openTag(group)
                      if (myHtmlCount != 0) myHtmlCount += 1
                    }
                  } else {
                    // closing tag, pop it if in the stack, or pop intervening ones if have match higher up
                    val jMax = myOpenTags.size
                    myHtmlMatch = htmlMatchEntry
                    myHtmlCount += 1
                    boundary {
                      var j = jMax - 1
                      while (j >= 0) {
                        val openTagStr = myOpenTags(j)
                        if (openTagStr == group) {
                          // drop all to end of stack
                          var k = jMax - 1
                          while (k >= j) {
                            myOpenTags.remove(j)
                            k -= 1
                          }
                          break(())
                        }

                        if (!isBlockTag) {
                          // don't close unmatched block tag by closing non-block tag.
                          if (myBlockTags.contains(openTagStr)) break(())
                        }
                        j -= 1
                      }
                    }
                  }
                }

                break(())
              }
              i += 1
            }
          }

          currentHtml = nextHtml
        }
      }
    }

    if (pendingOpen.isDefined && myHtmlMatch.contains(HtmlDeepParser.HtmlMatch.OPEN_TAG)) {
      // didn't close, forget it
      myClosingPattern = Nullable.empty
    }
  }
}

object HtmlDeepParser {

  enum HtmlMatch(
    val open:            Nullable[Pattern],
    val close:           Nullable[Pattern],
    val caseInsensitive: Boolean
  ) {
    case NONE extends HtmlMatch(Nullable.empty, Nullable.empty, false)
    case SCRIPT
        extends HtmlMatch(
          Nullable(Pattern.compile("<(script)(?:\\s|>|$)", Pattern.CASE_INSENSITIVE)),
          Nullable(Pattern.compile("</script>", Pattern.CASE_INSENSITIVE)),
          true
        )
    case STYLE
        extends HtmlMatch(
          Nullable(Pattern.compile("<(style)(?:\\s|>|$)", Pattern.CASE_INSENSITIVE)),
          Nullable(Pattern.compile("</style>", Pattern.CASE_INSENSITIVE)),
          true
        )
    case OPEN_TAG
        extends HtmlMatch(
          Nullable(Pattern.compile("<((?:" + Parsing.XML_NAMESPACE + ")[A-Za-z][A-Za-z0-9-]*" + ")", Pattern.CASE_INSENSITIVE)),
          Nullable(Pattern.compile("<|/>|\\s/>|>", Pattern.CASE_INSENSITIVE)),
          true
        )
    case CLOSE_TAG
        extends HtmlMatch(
          Nullable(Pattern.compile("</((?:" + Parsing.XML_NAMESPACE + ")[A-Za-z][A-Za-z0-9-]*" + ")>", Pattern.CASE_INSENSITIVE)),
          Nullable.empty,
          true
        )
    case NON_TAG
        extends HtmlMatch(
          Nullable(Pattern.compile("<(![A-Z])")),
          Nullable(Pattern.compile(">")),
          false
        )
    case TEMPLATE
        extends HtmlMatch(
          Nullable(Pattern.compile("<([?])")),
          Nullable(Pattern.compile("\\?>")),
          false
        )
    case COMMENT
        extends HtmlMatch(
          Nullable(Pattern.compile("<(!--)")),
          Nullable(Pattern.compile("-->")),
          false
        )
    case CDATA
        extends HtmlMatch(
          Nullable(Pattern.compile("<!\\[(CDATA)\\[")),
          Nullable(Pattern.compile("\\]\\]>")),
          false
        )
  }

  val BLOCK_TAGS: Set[String] = Set(
    "address",
    "article",
    "aside",
    "base",
    "basefont",
    "blockquote",
    "body",
    "caption",
    "center",
    "col",
    "colgroup",
    "dd",
    "details",
    "dialog",
    "dir",
    "div",
    "dl",
    "dt",
    "fieldset",
    "figcaption",
    "figure",
    "footer",
    "form",
    "frame",
    "frameset",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "head",
    "header",
    "hr",
    "html",
    "iframe",
    "legend",
    "li",
    "link",
    "main",
    "menu",
    "menuitem",
    "meta",
    "nav",
    "noframes",
    "ol",
    "optgroup",
    "option",
    "p",
    "param",
    "pre",
    "section",
    "source",
    "summary",
    "table",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "title",
    "tr",
    "track",
    "ul"
  )

  val VOID_TAGS: Set[String] = Set(
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "keygen",
    "link",
    "menuitem",
    "meta",
    "param",
    "source",
    "track",
    "wbr"
  )

  val OPTIONAL_TAGS: Map[String, Set[String]] = Map(
    "li" -> Set("li"),
    "dt" -> Set("dt", "dd"),
    "dd" -> Set("dd", "dt"),
    "p" -> Set(
      "address",
      "article",
      "aside",
      "blockquote",
      "details",
      "div",
      "dl",
      "fieldset",
      "figcaption",
      "figure",
      "footer",
      "form",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "header",
      "hr",
      "main",
      "menu",
      "nav",
      "ol",
      "p",
      "pre",
      "section",
      "table",
      "ul"
    ),
    "rt" -> Set("rt", "rp"),
    "rp" -> Set("rt", "rp"),
    "optgroup" -> Set("optgroup"),
    "option" -> Set("option", "optgroup"),
    "colgroup" -> Set("colgroup"),
    "thead" -> Set("tbody", "tfoot"),
    "tbody" -> Set("tbody", "tfoot"),
    "tfoot" -> Set("tbody"),
    "tr" -> Set("tr"),
    "td" -> Set("td", "th"),
    "th" -> Set("td", "th")
  )

  // combine all patterns and create map by pattern number
  val PATTERN_MAP: Array[Nullable[HtmlMatch]] = {
    val values = HtmlMatch.values
    val map    = new Array[Nullable[HtmlMatch]](values.length)
    var index  = 0
    for (state <- values) {
      if (state != HtmlMatch.NONE) {
        map(index) = Nullable(state)
      } else {
        map(index) = Nullable.empty
      }
      index += 1
    }
    map
  }

  val START_PATTERN: Pattern = {
    val sb = new StringBuilder
    for (state <- HtmlMatch.values)
      if (state != HtmlMatch.NONE) {
        state.open.foreach { p =>
          if (sb.nonEmpty) sb.append("|")
          // Cross-platform: (?i:...) inline flag not supported on Scala Native re2.
          // All HTML tag matching is case-insensitive, so we use the global flag instead.
          // Original: wrapped case-insensitive parts in (?i:...)
          // Revert when scala-native#4810 ships.
          sb.append(p.pattern())
        }
      }
    Pattern.compile(sb.toString, Pattern.CASE_INSENSITIVE)
  }
}
