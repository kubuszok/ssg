/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ListItemParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core

import ssg.md.ast._
import ssg.md.ast.util.Parsing
import ssg.md.parser.ListOptions
import ssg.md.parser.Parser.PARSER_EMULATION_PROFILE
import ssg.md.parser.ParserEmulationProfile._
import ssg.md.parser.block._
import ssg.md.util.ast.{ BlankLineContainer, Block }
import ssg.md.util.misc.Utils

import scala.language.implicitConversions

class ListItemParser(
  val listOptions: ListOptions,
  val myParsing:   Parsing,
  val myListData:  ListBlockParser.ListData
) extends AbstractBlockParser
    with BlankLineContainer {

  private val _block: ListItem =
    if (myListData.isNumberedList) OrderedListItem()
    else BulletListItem()

  _block.openingMarker = myListData.listMarker
  _block.markerSuffix = myListData.markerSuffix

  private var myHadBlankLine: Boolean = false
  private var myIsEmpty:      Boolean = false

  def contentColumn: Int =
    myListData.markerColumn + myListData.listMarker.length() +
      (if (listOptions.isItemContentAfterSuffix) myListData.contentOffset else myListData.markerSuffixOffset)

  def contentIndent: Int =
    myListData.markerIndent + myListData.listMarker.length() +
      (if (listOptions.isItemContentAfterSuffix) myListData.contentOffset else myListData.markerSuffixOffset)

  def markerContentIndent: Int =
    myListData.markerIndent + myListData.listMarker.length() + 1

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = {
    // Issue 66, fenced code can only be contained in GitHub Doc mode if it is indented more than list item
    block match {
      case _: FencedCodeBlock =>
        // see if it indented more than our marker
        if (PARSER_EMULATION_PROFILE.get(state.properties) == GITHUB_DOC) {
          // Issue #66, if we are in a list item and our indent == list indent then we interrupt the list
          val parser = blockParser.asInstanceOf[FencedCodeBlockParser]
          myListData.markerIndent < parser.fenceIndent
        } else {
          true
        }
      case _ => true
    }
  }

  override def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean =
    !(_block.firstChild.isEmpty && (this ne lastMatchedBlockParser))

  override def getBlock: ListItem = _block

  override def closeBlock(state: ParserState): Unit =
    _block.setCharsFromContent()

  private def continueAtColumn(newColumn: Int): BlockContinue = {
    // reset our empty flag, we have content now so we stay open
    if (myHadBlankLine) {
      _block.containsBlankLine_=(true)
    }
    myIsEmpty = false
    BlockContinue.atColumn(newColumn)
  }

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    if (state.isBlank) {
      // now when we have a Blank line after empty list item, now we need to handle it because the list is not closed and we handle next list item conditions
      val firstChild = _block.firstChild
      myIsEmpty = firstChild.isEmpty
      if (myIsEmpty || firstChild.get.next.isEmpty) {
        _block.hadBlankAfterItemParagraph_=(true)
      }
      myHadBlankLine = true
      return Nullable(BlockContinue.atIndex(state.nextNonSpaceIndex)) // @nowarn -- early exit for blank line
    }

    assert(_block.parent.exists(_.isInstanceOf[ListBlock]))

    val listBlockParser = state.getActiveBlockParser(_block.parent.get.asInstanceOf[Block]).asInstanceOf[ListBlockParser]

    val emulationProfile = listOptions.getParserEmulationProfile
    val emulationFamily = emulationProfile.family
    val theContentIndent = contentIndent

    if (emulationFamily == COMMONMARK) {
      tryContinueCommonMark(state, listBlockParser, theContentIndent)
    } else {
      val itemIndent = listOptions.getItemIndent

      if (emulationFamily == FIXED_INDENT) {
        tryContinueFixedIndent(state, listBlockParser, itemIndent)
      } else {
        val markerIndent = listBlockParser.listData.markerIndent
        if (emulationFamily == KRAMDOWN) {
          tryContinueKramdown(state, listBlockParser, theContentIndent, itemIndent, markerIndent)
        } else if (emulationProfile == GITHUB_DOC) {
          tryContinueGithubDoc(state, listBlockParser, theContentIndent, itemIndent, markerIndent)
        } else if (emulationFamily == MARKDOWN) {
          tryContinueMarkdown(state, listBlockParser, theContentIndent, itemIndent, markerIndent)
        } else {
          Nullable(BlockContinue.none().get) // should not happen, fall through to none
        }
      }
    }
  }

  // scalastyle:off method.length
  private def tryContinueCommonMark(state: ParserState, listBlockParser: ListBlockParser, theContentIndent: Int): Nullable[BlockContinue] = {
    // - CommonMark: version 0.27 of the spec, all common mark parsers
    val currentIndent = state.indent
    val newColumn = state.column + theContentIndent

    if (currentIndent >= theContentIndent + listOptions.getCodeIndent) {
      // our indented code child
      listBlockParser.setItemHandledLine(state.line)
      Nullable(continueAtColumn(newColumn))
    } else {
      val listData = ListBlockParser.parseListMarker(listOptions, listOptions.getCodeIndent, state)

      if (currentIndent >= theContentIndent) {
        if (listData.isDefined) {
          val ld = listData.get
          val matched = state.activeBlockParser
          val inParagraph = matched.isParagraphParser
          val inParagraphListItem = inParagraph &&
            matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
            matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

          if (inParagraphListItem &&
            (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
              !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
            // just a lazy continuation of us
            listBlockParser.setItemHandledLineSkipActive(state.line)
            Nullable(continueAtColumn(newColumn))
          } else {
            // our sub list item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(newColumn))
          }
        } else {
          if (myIsEmpty) {
            // our child item, other than a list item, if we are empty then no such thing
            listBlockParser.setItemHandledLine(state.line)
            BlockContinue.none()
          } else {
            listBlockParser.setItemHandledLine(state.line)
            Nullable(continueAtColumn(newColumn))
          }
        }
      } else if (listData.isDefined) {
        val ld = listData.get
        if (!myHadBlankLine && !listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true)) {
          // our text or lazy continuation
          listBlockParser.setItemHandledLine(state.line)
          Nullable(continueAtColumn(state.column + currentIndent))
        } else {
          // here have to see if the item is really a mismatch and we sub-list mismatches
          val overrideSubList = listOptions.isItemTypeMismatchToNewList && listOptions.isItemTypeMismatchToSubList && myHadBlankLine
          if (!overrideSubList && listOptions.startSubList(listBlockParser.getBlock, ld.listBlock)) {
            // we keep it as our sub-item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            if (listOptions.startNewList(listBlockParser.getBlock, ld.listBlock)) {
              // a new list
              listBlockParser.setItemHandledNewListLine(state.line)
              BlockContinue.none()
            } else {
              // the next line in the list
              listBlockParser.setItemHandledNewItemLine(state.line)
              BlockContinue.none()
            }
          }
        }
      } else {
        BlockContinue.none()
      }
    }
  }

  private def tryContinueFixedIndent(state: ParserState, listBlockParser: ListBlockParser, itemIndent: Int): Nullable[BlockContinue] = {
    // - FixedIndent: Pandoc, MultiMarkdown, Pegdown
    val currentIndent = state.indent

    // advance by item indent
    val newColumn = state.column + itemIndent

    if (currentIndent >= listOptions.getCodeIndent) {
      // our indented code child but if it starts with an item prefix and parsing this list's paragraph item then this is a lazy continuation
      if (_block.firstChild.isDefined && _block.firstChild == _block.lastChild) {
        val matched = state.activeBlockParser
        if (matched.isParagraphParser && _block.firstChild.contains(matched.getBlock)) {
          // just a lazy continuation of us
          listBlockParser.setItemHandledLineSkipActive(state.line)
          return Nullable(continueAtColumn(newColumn)) // @nowarn -- early exit
        }
      }

      listBlockParser.setItemHandledLine(state.line)
      Nullable(continueAtColumn(newColumn))
    } else {
      val listData = ListBlockParser.parseListMarker(listOptions, -1, state)

      if (currentIndent >= itemIndent) {
        if (listData.isDefined) {
          val ld = listData.get
          val matched = state.activeBlockParser
          val inParagraph = matched.isParagraphParser
          val inParagraphListItem = inParagraph &&
            matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
            matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

          if (inParagraphListItem && (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
            !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
            // just a lazy continuation of us
            listBlockParser.setItemHandledLineSkipActive(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            // our sub list item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(newColumn))
          }
        } else {
          // our child item, other than a list item, if we are empty then no such thing
          if (myIsEmpty) {
            listBlockParser.setItemHandledLine(state.line)
            BlockContinue.none()
          } else {
            listBlockParser.setItemHandledLine(state.line)
            Nullable(continueAtColumn(newColumn))
          }
        }
      } else if (listData.isDefined) {
        val ld = listData.get
        if (!myHadBlankLine && !listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true)) {
          // our text or lazy continuation
          listBlockParser.setItemHandledLine(state.line)
          Nullable(continueAtColumn(state.column + currentIndent))
        } else {
          // here have to see if the item is really a mismatch and we sub-list mismatches
          val overrideSubList = listOptions.isItemTypeMismatchToNewList && listOptions.isItemTypeMismatchToSubList && myHadBlankLine
          if (!overrideSubList && listOptions.startSubList(listBlockParser.getBlock, ld.listBlock)) {
            // we keep it as our sub-item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            if (listOptions.startNewList(listBlockParser.getBlock, ld.listBlock)) {
              // a new list
              listBlockParser.setItemHandledNewListLine(state.line)
              BlockContinue.none()
            } else {
              // the next line in the list
              listBlockParser.setItemHandledNewItemLine(state.line)
              BlockContinue.none()
            }
          }
        }
      } else {
        BlockContinue.none()
      }
    }
  }

  private def tryContinueKramdown(state: ParserState, listBlockParser: ListBlockParser, theContentIndent: Int, itemIndent: Int, markerIndent: Int): Nullable[BlockContinue] = {
    val currentIndent = state.indent
    val listIndent = markerIndent
    val newColumn = state.column + theContentIndent

    val listData = ListBlockParser.parseListMarker(listOptions, -1, state)

    if (currentIndent >= theContentIndent) {
      // our sub item
      if (listData.isDefined) {
        val ld = listData.get
        val matched = state.activeBlockParser
        val inParagraph = matched.isParagraphParser
        val inParagraphListItem = inParagraph &&
          matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
          matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

        if (inParagraphListItem &&
          (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
            !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
          // just a lazy continuation of us
          listBlockParser.setItemHandledLineSkipActive(state.line)
          Nullable(continueAtColumn(newColumn))
        } else {
          // our sub list item
          listBlockParser.setItemHandledNewListLine(state.line)
          Nullable(continueAtColumn(newColumn))
        }
      } else {
        // our child item, other than a list item, if we are empty then no such thing
        if (myIsEmpty) {
          listBlockParser.setItemHandledLine(state.line)
          BlockContinue.none()
        } else {
          listBlockParser.setItemHandledLine(state.line)
          Nullable(continueAtColumn(newColumn))
        }
      }
    } else {
      if (currentIndent >= listIndent + itemIndent) {
        if (myHadBlankLine) {
          // indented code, interrupts item but keeps loose status
          if (_block.isHadBlankAfterItemParagraph) _block.loose_=(true)
          listBlockParser.setItemHandledLineSkipActive(state.line)
          BlockContinue.none()
        } else {
          // our text or lazy continuation
          listBlockParser.setItemHandledLineSkipActive(state.line)
          Nullable(continueAtColumn(state.column + currentIndent))
        }
      } else if (listData.isDefined) {
        val ld = listData.get
        if (currentIndent >= listIndent) {
          // here have to see if the item is really a mismatch and we sub-list mismatches
          val overrideSubList = listOptions.isItemTypeMismatchToNewList && listOptions.isItemTypeMismatchToSubList && myHadBlankLine
          if (!overrideSubList && listOptions.startSubList(listBlockParser.getBlock, ld.listBlock)) {
            // we keep it as our sub-item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            if (listOptions.startNewList(listBlockParser.getBlock, ld.listBlock)) {
              // a new list
              listBlockParser.setItemHandledNewListLine(state.line)
              BlockContinue.none()
            } else {
              // the next line in the list
              listBlockParser.setItemHandledNewItemLine(state.line)
              BlockContinue.none()
            }
          }
        } else {
          BlockContinue.none()
        }
      } else {
        BlockContinue.none()
      }
    }
  }

  private def tryContinueGithubDoc(state: ParserState, listBlockParser: ListBlockParser, theContentIndent: Int, itemIndent: Int, markerIndent: Int): Nullable[BlockContinue] = {
    val currentIndent = state.indent
    @annotation.nowarn("msg=unused") // faithful port, used in contentIndentRemoval calculation
    val currentIndex = state.getIndex + currentIndent
    val listIndent = markerIndent
    val contentIndentRemoval = Utils.maxLimit(currentIndent, theContentIndent, listIndent + 4)

    if (currentIndent >= listOptions.getCodeIndent) {
      // this could be indented code or our lazy continuation
      listBlockParser.setItemHandledLine(state.line)
      Nullable(continueAtColumn(state.column + Utils.maxLimit(theContentIndent, itemIndent)))
    } else {
      val listData = ListBlockParser.parseListMarker(listOptions, -1, state)

      if (currentIndent > itemIndent) {
        if (listData.isDefined) {
          val ld = listData.get
          // our sub item
          val matched = state.activeBlockParser
          val inParagraph = matched.isParagraphParser
          val inParagraphListItem = inParagraph &&
            matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
            matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

          if (inParagraphListItem &&
            (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
              !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
            // just a lazy continuation of us
            listBlockParser.setItemHandledLineSkipActive(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            // our sub list item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(state.column + contentIndentRemoval))
          }
        } else {
          // our content
          listBlockParser.setItemHandledLine(state.line)
          Nullable(continueAtColumn(state.column + itemIndent))
        }
      } else {
        if (currentIndent > listIndent) {
          if (listData.isDefined) {
            val ld = listData.get
            // our sublist
            val matched = state.activeBlockParser
            val inParagraph = matched.isParagraphParser
            val inParagraphListItem = inParagraph &&
              matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
              matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

            if (inParagraphListItem &&
              (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
                !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
              // just a lazy continuation of us
              listBlockParser.setItemHandledLineSkipActive(state.line)
              Nullable(continueAtColumn(state.column + currentIndent))
            } else {
              // our sub list item
              listBlockParser.setItemHandledNewListLine(state.line)
              Nullable(continueAtColumn(state.column + contentIndentRemoval))
            }
          } else {
            // our content
            listBlockParser.setItemHandledLine(state.line)
            Nullable(continueAtColumn(state.column + contentIndentRemoval))
          }
        } else {
          if (listData.isDefined) {
            val ld = listData.get
            // here have to see if the item is really a mismatch and we sub-list mismatches
            // the next line in the list
            val overrideSubList = listOptions.isItemTypeMismatchToNewList && listOptions.isItemTypeMismatchToSubList && myHadBlankLine
            if (!overrideSubList && listOptions.startSubList(listBlockParser.getBlock, ld.listBlock)) {
              // we keep it as our sub-item
              listBlockParser.setItemHandledNewListLine(state.line)
              Nullable(continueAtColumn(state.column + contentIndentRemoval))
            } else {
              if (listOptions.startNewList(listBlockParser.getBlock, ld.listBlock)) {
                // a new list
                listBlockParser.setItemHandledNewListLine(state.line)
                BlockContinue.none()
              } else {
                val matched = state.activeBlockParser
                val inParagraph = matched.isParagraphParser
                val inParagraphListItem = inParagraph &&
                  matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
                  matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

                if (inParagraphListItem &&
                  (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
                    !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
                  // just a lazy continuation of us
                  listBlockParser.setItemHandledLineSkipActive(state.line)
                  Nullable(continueAtColumn(state.column + currentIndent))
                } else {
                  // the next line in the list
                  listBlockParser.setItemHandledNewItemLine(state.line)
                  BlockContinue.none()
                }
              }
            }
          } else if (!myHadBlankLine || state.activeBlockParser.isInstanceOf[FencedCodeBlockParser]) {
            // our lazy continuation or a new element
            // Issue #66, if fenced code follows then need to interrupt the list
            listBlockParser.setItemHandledLine(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            BlockContinue.none()
          }
        }
      }
    }
  }

  private def tryContinueMarkdown(state: ParserState, listBlockParser: ListBlockParser, theContentIndent: Int, itemIndent: Int, markerIndent: Int): Nullable[BlockContinue] = {
    val currentIndent = state.indent

    if (currentIndent >= listOptions.getCodeIndent) {
      // this could be indented code or our lazy continuation
      listBlockParser.setItemHandledLine(state.line)
      Nullable(continueAtColumn(state.column + itemIndent))
    } else {
      val listData = ListBlockParser.parseListMarker(listOptions, -1, state)

      if (currentIndent > itemIndent) {
        if (listData.isDefined) {
          val ld = listData.get
          // our sub item
          val matched = state.activeBlockParser
          val inParagraph = matched.isParagraphParser
          val inParagraphListItem = inParagraph &&
            matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
            matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

          if (inParagraphListItem &&
            (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
              !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
            // just a lazy continuation of us
            listBlockParser.setItemHandledLineSkipActive(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            // our sub list item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(state.column + itemIndent))
          }
        } else {
          // our content
          listBlockParser.setItemHandledLine(state.line)
          Nullable(continueAtColumn(state.column + itemIndent))
        }
      } else {
        val listIndent = markerIndent
        if (currentIndent > listIndent) {
          if (listData.isDefined) {
            val ld = listData.get
            // our sublist
            val matched = state.activeBlockParser
            val inParagraph = matched.isParagraphParser
            val inParagraphListItem = inParagraph &&
              matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
              matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

            if (inParagraphListItem &&
              (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
                !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
              // just a lazy continuation of us
              listBlockParser.setItemHandledLineSkipActive(state.line)
              Nullable(continueAtColumn(state.column + currentIndent))
            } else {
              // our sub list item
              listBlockParser.setItemHandledNewListLine(state.line)
              Nullable(continueAtColumn(state.column + currentIndent))
            }
          } else {
            // our content
            listBlockParser.setItemHandledLine(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          }
        } else if (listData.isDefined) {
          val ld = listData.get
          // here have to see if the item is really a mismatch and we sub-list mismatches
          // the next line in the list
          val overrideSubList = listOptions.isItemTypeMismatchToNewList && listOptions.isItemTypeMismatchToSubList && myHadBlankLine
          if (!overrideSubList && listOptions.startSubList(listBlockParser.getBlock, ld.listBlock)) {
            // we keep it as our sub-item
            listBlockParser.setItemHandledNewListLine(state.line)
            Nullable(continueAtColumn(state.column + currentIndent))
          } else {
            if (listOptions.startNewList(listBlockParser.getBlock, ld.listBlock)) {
              // a new list
              listBlockParser.setItemHandledNewListLine(state.line)
              BlockContinue.none()
            } else {
              val matched = state.activeBlockParser
              val inParagraph = matched.isParagraphParser
              val inParagraphListItem = inParagraph &&
                matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
                matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

              if (inParagraphListItem &&
                (!listOptions.canInterrupt(ld.listBlock, ld.isEmpty, true) ||
                  !listOptions.canStartSubList(ld.listBlock, ld.isEmpty))) {
                // just a lazy continuation of us
                listBlockParser.setItemHandledLineSkipActive(state.line)
                Nullable(continueAtColumn(state.column + currentIndent))
              } else {
                // the next line in the list
                listBlockParser.setItemHandledNewItemLine(state.line)
                BlockContinue.none()
              }
            }
          }
        } else {
          BlockContinue.none()
        }
      }
    }
  }
  // scalastyle:on method.length
}
