/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/HtmlBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core

import ssg.md.ast.{
  HtmlBlock,
  HtmlBlockBase,
  HtmlCommentBlock,
  HtmlInnerBlock,
  HtmlInnerBlockComment,
  Paragraph
}
import ssg.md.ast.util.Parsing
import ssg.md.parser.Parser
import ssg.md.parser.block._
import ssg.md.parser.internal.HtmlDeepParser
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.util.regex.Pattern
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class HtmlBlockParser(
  options:                  DataHolder,
  val closingPattern:       Nullable[Pattern],
  isComment:                Boolean,
  private val deepParser:   Nullable[HtmlDeepParser]
) extends AbstractBlockParser {

  private val _block: HtmlBlockBase =
    if (isComment) HtmlCommentBlock() else HtmlBlock()

  private var finished: Boolean = false

  private var content: Nullable[BlockContent] = Nullable(BlockContent())

  private val parseInnerHtmlComments: Boolean =
    Parser.PARSE_INNER_HTML_COMMENTS.get(options)

  private val myHtmlBlockDeepParseNonBlock: Boolean =
    Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK.get(options)

  private val myHtmlBlockDeepParseBlankLineInterrupts: Boolean =
    Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS.get(options)

  private val myHtmlBlockDeepParseMarkdownInterruptsClosed: Boolean =
    Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED.get(options)

  private val myHtmlBlockDeepParseBlankLineInterruptsPartialTag: Boolean =
    Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG.get(options)

  private val myHtmlBlockDeepParseIndentedCodeInterrupts: Boolean =
    Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS.get(options)

  override def getBlock: HtmlBlockBase = _block

  override def getBlockContent: Nullable[BlockContent] = content

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    deepParser match {
      case dp if dp.isDefined =>
        val parser = dp.get
        if (state.isBlank) {
          if (
            parser.isHtmlClosed
              || (myHtmlBlockDeepParseBlankLineInterrupts && !parser.haveOpenRawTag)
              || (myHtmlBlockDeepParseBlankLineInterruptsPartialTag && parser.isBlankLineInterruptible)
          ) {
            BlockContinue.none()
          } else {
            Nullable(BlockContinue.atIndex(state.getIndex))
          }
        } else {
          Nullable(BlockContinue.atIndex(state.getIndex))
        }

      case _ =>
        if (finished) {
          BlockContinue.none()
        } else if (state.isBlank && closingPattern.isEmpty) {
          // Blank line ends type 6 and type 7 blocks
          BlockContinue.none()
        } else {
          Nullable(BlockContinue.atIndex(state.getIndex))
        }
    }
  }

  override def addLine(state: ParserState, line: BasedSequence): Unit = {
    deepParser match {
      case dp if dp.isDefined =>
        val parser = dp.get
        content.foreach { c =>
          if (c.lineCount > 0) {
            // not the first line, which is already parsed
            parser.parseHtmlChunk(line, false, myHtmlBlockDeepParseNonBlock, false)
          }
        }

      case _ =>
        closingPattern.foreach { pattern =>
          if (pattern.matcher(line).find()) {
            finished = true
          }
        }
    }

    content.foreach(_.add(line, state.indent))
  }

  override def canInterruptBy(blockParserFactory: BlockParserFactory): Boolean = {
    myHtmlBlockDeepParseMarkdownInterruptsClosed
      && deepParser.isDefined
      && !(blockParserFactory.isInstanceOf[HtmlBlockParser.Factory]
        || (!myHtmlBlockDeepParseIndentedCodeInterrupts && blockParserFactory
          .isInstanceOf[IndentedCodeBlockParser.BlockFactory]))
      && deepParser.get.isHtmlClosed
  }

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean =
    false

  override def isInterruptible: Boolean =
    myHtmlBlockDeepParseMarkdownInterruptsClosed && deepParser.exists(_.isHtmlClosed)

  override def isRawText: Boolean = true

  override def closeBlock(state: ParserState): Unit = {
    content.foreach { c =>
      _block.setContent(c)
    }
    content = Nullable.empty

    // split out inner comments
    if (!_block.isInstanceOf[HtmlCommentBlock] && parseInnerHtmlComments) {
      // need to break it up into non-comments and comments
      var lastIndex = 0
      var chars     = _block.contentChars
      if (chars.eolEndLength() > 0) {
        chars = chars.midSequence(0, -1)
      }

      // RegEx for HTML can go into an infinite loop, we do manual search to avoid this
      val length = chars.length()
      boundary {
        while (lastIndex < length) {
          // find the opening HTML comment
          val index = chars.indexOf(HtmlBlockParser.HTML_COMMENT_OPEN, lastIndex)
          if (index < 0) break(())

          // now lets find -->
          val end = chars.indexOf(
            HtmlBlockParser.HTML_COMMENT_CLOSE,
            index + HtmlBlockParser.HTML_COMMENT_OPEN.length()
          )

          // if unterminated, ignore
          if (end < 0) break(())

          if (lastIndex < index) {
            val html = HtmlInnerBlock(chars.subSequence(lastIndex, index))
            _block.appendChild(html)
          }

          lastIndex = end + HtmlBlockParser.HTML_COMMENT_CLOSE.length()
          val htmlComment = HtmlInnerBlockComment(chars.subSequence(index, lastIndex))
          _block.appendChild(htmlComment)
        }
      }

      if (lastIndex > 0) {
        if (lastIndex < chars.length()) {
          val html = HtmlInnerBlock(chars.subSequence(lastIndex, chars.length()))
          _block.appendChild(html)
        }
      }
    }
  }
}

object HtmlBlockParser {

  val HTML_COMMENT_OPEN:  String = "<!--"
  val HTML_COMMENT_CLOSE: String = "-->"

  private class Patterns(parsing: Parsing, options: DataHolder) {

    val COMMENT_PATTERN_INDEX: Int = 2

    /** Each entry is (opener, closer) where closer may be empty for blank-line-terminated types. */
    val BLOCK_PATTERNS: Vector[(Nullable[Pattern], Nullable[Pattern])] = {
      // dynamic block tags
      val sb        = new StringBuilder
      var delimiter = ""
      for (tag <- Parser.HTML_BLOCK_TAGS.get(options)) {
        sb.append(delimiter)
          .append("\\Q")
          .append(tag)
          .append("\\E")
        delimiter = "|"
      }

      val forTranslator = Parser.HTML_FOR_TRANSLATOR.get(options)
      if (forTranslator) {
        sb.append(delimiter)
          .append(Parser.TRANSLATION_HTML_BLOCK_TAG_PATTERN.get(options))
        delimiter = "|"
      }

      val blockTags = sb.toString

      Vector(
        (Nullable.empty, Nullable.empty), // not used (no type 0)
        (
          Nullable(Pattern.compile("^<(?:script|pre|style|textarea)(?:\\s|>|$)", Pattern.CASE_INSENSITIVE)),
          Nullable(Pattern.compile("</(?:script|pre|style|textarea)>", Pattern.CASE_INSENSITIVE))
        ),
        (
          Nullable(Pattern.compile("^" + HTML_COMMENT_OPEN)),
          Nullable(Pattern.compile(HTML_COMMENT_CLOSE))
        ),
        (
          Nullable(Pattern.compile("^<[?]")),
          Nullable(Pattern.compile("\\?>"))
        ),
        (
          Nullable(Pattern.compile("^<![A-Z]")),
          Nullable(Pattern.compile(">"))
        ),
        (
          Nullable(Pattern.compile("^<!\\[CDATA\\[")),
          Nullable(Pattern.compile("\\]\\]>"))
        ),
        (
          Nullable(
            Pattern.compile(
              "^</?(?:" + Parsing.XML_NAMESPACE + "(?:" + blockTags + "))(?:\\s|[/]?[>]|$)",
              Pattern.CASE_INSENSITIVE
            )
          ),
          Nullable.empty // terminated by blank line
        ),
        (
          Nullable(
            Pattern.compile(
              "^(?:" + parsing.OPENTAG + '|' + parsing.CLOSETAG + ")\\s*$",
              Pattern.CASE_INSENSITIVE
            )
          ),
          Nullable.empty // terminated by blank line
        )
      )
    }
  }

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[BlockQuoteParser.Factory],
        classOf[HeadingParser.Factory],
        classOf[FencedCodeBlockParser.Factory]
      )
    )

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[ThematicBreakParser.Factory],
        classOf[ListBlockParser.Factory],
        classOf[IndentedCodeBlockParser.Factory]
      )
    )

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private var myPatterns: Nullable[Patterns] = Nullable.empty

    private val myHtmlCommentBlocksInterruptParagraph: Boolean =
      Parser.HTML_COMMENT_BLOCKS_INTERRUPT_PARAGRAPH.get(options)

    private val myHtmlBlockDeepParser: Boolean =
      Parser.HTML_BLOCK_DEEP_PARSER.get(options)

    private val myHtmlBlockDeepParseNonBlock: Boolean =
      Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK.get(options)

    private val myHtmlBlockDeepParseFirstOpenTagOnOneLine: Boolean =
      Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE.get(options)

    private val myHtmlBlockCommentOnlyFullLine: Boolean =
      Parser.HTML_BLOCK_COMMENT_ONLY_FULL_LINE.get(options)

    private val myHtmlBlockStartOnlyOnBlockTags: Boolean =
      Parser.HTML_BLOCK_START_ONLY_ON_BLOCK_TAGS.get(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = {
      val nextNonSpace = state.nextNonSpaceIndex
      val line         = state.line

      if (
        state.indent < 4
          && line.charAt(nextNonSpace) == '<'
          && !matchedBlockParser.blockParser.isInstanceOf[HtmlBlockParser]
      ) {
        if (myHtmlBlockDeepParser) {
          val deepParser = HtmlDeepParser(Parser.HTML_BLOCK_TAGS.get(state.properties))
          deepParser.parseHtmlChunk(
            line.subSequence(nextNonSpace, line.length()),
            myHtmlBlockStartOnlyOnBlockTags,
            myHtmlBlockDeepParseNonBlock,
            myHtmlBlockDeepParseFirstOpenTagOnOneLine
          )
          if (deepParser.hadHtml) {
            if (
              (deepParser.htmlMatch.contains(HtmlDeepParser.HtmlMatch.OPEN_TAG)
                || (!myHtmlCommentBlocksInterruptParagraph && deepParser.htmlMatch
                  .contains(HtmlDeepParser.HtmlMatch.COMMENT)))
                && (!deepParser.isFirstBlockTag && matchedBlockParser.blockParser.getBlock
                  .isInstanceOf[Paragraph])
            ) {
              // cannot interrupt paragraph with non-block open tag or comment (when configured)
              BlockStart.none()
            } else {
              // not paragraph or can interrupt paragraph
              Nullable(
                BlockStart
                  .of(
                    HtmlBlockParser(
                      state.properties,
                      Nullable.empty,
                      deepParser.htmlMatch.contains(HtmlDeepParser.HtmlMatch.COMMENT),
                      Nullable(deepParser)
                    )
                  )
                  .atIndex(state.getIndex)
              )
            }
          } else {
            BlockStart.none()
          }
        } else {
          boundary[Nullable[BlockStart]] {
            var blockType = 1
            while (blockType <= 7) {
              // Type 7 cannot interrupt a paragraph or may not start a block altogether
              if (
                blockType == 7
                  && (myHtmlBlockStartOnlyOnBlockTags || matchedBlockParser.blockParser.getBlock
                    .isInstanceOf[Paragraph])
              ) {
                blockType += 1
              } else {
                if (myPatterns.isEmpty) {
                  myPatterns = Nullable(Patterns(state.parsing, state.properties))
                }

                val patterns       = myPatterns.get
                val (opener, closer) = patterns.BLOCK_PATTERNS(blockType)
                val matcher        = opener.get.matcher(line.subSequence(nextNonSpace, line.length()))
                val matches        = matcher.find()

                // TEST: non-interrupting of paragraphs by HTML comments
                if (
                  matches
                    && (myHtmlCommentBlocksInterruptParagraph
                      || blockType != patterns.COMMENT_PATTERN_INDEX
                      || !matchedBlockParser.blockParser.isInstanceOf[ParagraphParser])
                ) {
                  // Issue #158, HTML Comment followed by text
                  if (blockType == patterns.COMMENT_PATTERN_INDEX && myHtmlBlockCommentOnlyFullLine) {
                    val endMatcher = patterns
                      .BLOCK_PATTERNS(patterns.COMMENT_PATTERN_INDEX)._2
                      .get
                      .matcher(line.subSequence(matcher.end(), line.length()))
                    if (endMatcher.find()) {
                      // see if nothing follows
                      val trailing =
                        line.subSequence(endMatcher.end(), line.length()).trim()
                      if (!trailing.equals("-->")) {
                        break(BlockStart.none())
                      }
                    }
                  }
                  break(
                    Nullable(
                      BlockStart
                        .of(
                          HtmlBlockParser(
                            state.properties,
                            closer,
                            blockType == patterns.COMMENT_PATTERN_INDEX,
                            Nullable.empty
                          )
                        )
                        .atIndex(state.getIndex)
                    )
                  )
                }

                blockType += 1
              }
            }

            BlockStart.none()
          }
        }
      } else {
        BlockStart.none()
      }
    }
  }
}
