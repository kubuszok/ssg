/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionItemBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.Nullable
import ssg.md.ast.{Paragraph, util}
import ssg.md.parser.{InlineParser, ParserEmulationProfile}
import ssg.md.parser.block.*
import ssg.md.parser.core.{DocumentBlockParser, ParagraphParser}
import ssg.md.util.ast.{BlankLine, Block, Document}
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.mappers.{SpecialLeadInCharsHandler, SpecialLeadInHandler}

import scala.language.implicitConversions

class DefinitionItemBlockParser(options: DataHolder, itemData: DefinitionItemBlockParser.ItemData) extends AbstractBlockParser {

  private val defOptions: DefinitionOptions = new DefinitionOptions(options)
  private val block: DefinitionItem = new DefinitionItem()
  private var hadBlankLine: Boolean = false

  block.openingMarker = itemData.itemMarker
  block.tight_=(itemData.isTight)

  private def contentIndent: Int = itemData.markerIndent + itemData.itemMarker.length() + itemData.contentOffset

  override def getBlock: Block = block

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def tryContinue(state: ParserState): Nullable[BlockContinue] = {
    val firstChild = block.firstChild
    val isEmpty = firstChild.isEmpty

    if (state.isBlank) {
      firstChild.foreach { fc =>
        if (fc.next.isEmpty) {
          block.hadBlankAfterItemParagraph_=(true)
        }
      }
      hadBlankLine = true
      Nullable(BlockContinue.atIndex(state.nextNonSpaceIndex))
    } else {
      val emulationFamily = defOptions.myParserEmulationProfile.family
      if (emulationFamily == ParserEmulationProfile.COMMONMARK || emulationFamily == ParserEmulationProfile.KRAMDOWN || emulationFamily == ParserEmulationProfile.MARKDOWN) {
        val currentIndent = state.indent
        val newColumn = state.column + contentIndent

        if (currentIndent >= contentIndent) {
          // our child element
          Nullable(BlockContinue.atColumn(newColumn))
        } else {
          if (isEmpty) {
            Nullable(BlockContinue.atIndex(state.getIndex + currentIndent))
          } else {
            val parsedItemData = DefinitionItemBlockParser.parseItemMarker(defOptions, state, false)
            if (parsedItemData != null) { // @nowarn - parseItemMarker may return null
              BlockContinue.none()
            } else if (!hadBlankLine) {
              Nullable(BlockContinue.atIndex(state.getIndex + currentIndent))
            } else {
              BlockContinue.none()
            }
          }
        }
      } else if (emulationFamily == ParserEmulationProfile.FIXED_INDENT) {
        val currentIndent = state.indent
        val newColumn = state.column + defOptions.itemIndent

        if (currentIndent >= defOptions.itemIndent) {
          Nullable(BlockContinue.atColumn(newColumn))
        } else {
          if (isEmpty) {
            Nullable(BlockContinue.atIndex(state.getIndex + currentIndent))
          } else {
            val parsedItemData = DefinitionItemBlockParser.parseItemMarker(defOptions, state, false)
            if (parsedItemData != null) { // @nowarn - parseItemMarker may return null
              BlockContinue.none()
            } else if (!hadBlankLine) {
              Nullable(BlockContinue.atIndex(state.getIndex + currentIndent))
            } else {
              BlockContinue.none()
            }
          }
        }
      } else {
        BlockContinue.none()
      }
    }
  }

  override def addLine(state: ParserState, line: BasedSequence): Unit = {}

  override def closeBlock(state: ParserState): Unit = {
    block.setCharsFromContent()
  }

  override def parseInlines(inlineParser: InlineParser): Unit = {}
}

object DefinitionItemBlockParser {

  final case class ItemData(
    isEmpty: Boolean,
    isTight: Boolean,
    markerIndex: Int,
    markerColumn: Int,
    markerIndent: Int,
    contentOffset: Int,
    itemMarker: BasedSequence
  )

  def parseItemMarker(options: DefinitionOptions, state: ParserState, isTight: Boolean): ItemData = {
    val line = state.line
    val markerIndex = state.nextNonSpaceIndex
    val markerColumn = state.column + state.indent
    val markerIndent = state.indent

    val rest = line.subSequence(markerIndex, line.length())
    val c1 = rest.firstChar()
    if (!(c1 == ':' && options.colonMarker) && !(c1 == '~' && options.tildeMarker)) {
      null.asInstanceOf[ItemData] // @nowarn - faithful port: returns null when no match
    } else {
      // marker doesn't include tabs, so counting them as columns directly is ok
      var contentOffset = 0

      // See at which column the content starts if there is content
      var hasContent = false
      var i = markerIndex + 1
      while (i < line.length()) {
        val c = line.charAt(i)
        if (c == '\t') {
          contentOffset += util.Parsing.columnsToNextTabStop(markerColumn + 1 + contentOffset)
        } else if (c == ' ') {
          contentOffset += 1
        } else {
          hasContent = true
          i = line.length() // break
        }
        i += 1
      }

      if (hasContent && contentOffset < options.markerSpaces) {
        null.asInstanceOf[ItemData] // @nowarn - faithful port: returns null when no match
      } else {
        if (!hasContent || (options.myParserEmulationProfile == ParserEmulationProfile.COMMONMARK && contentOffset > options.newItemCodeIndent)) {
          // If this line is blank or has a code block, default to 1 space after marker
          contentOffset = 1
        }

        ItemData(!hasContent, isTight, markerIndex, markerColumn, markerIndent, contentOffset, rest.subSequence(0, 1))
      }
    }
  }

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def getLeadInHandler(options: DataHolder): Nullable[SpecialLeadInHandler] = {
      val colon = DefinitionExtension.COLON_MARKER.get(options)
      val tilde = DefinitionExtension.TILDE_MARKER.get(options)
      if (colon && tilde) Nullable(SpecialLeadInCharsHandler.create(":~"))
      else if (colon) Nullable(SpecialLeadInCharsHandler.create(":"))
      else if (tilde) Nullable(SpecialLeadInCharsHandler.create("~"))
      else Nullable.empty
    }

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val defOptions: DefinitionOptions = new DefinitionOptions(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = {
      val blockParser = matchedBlockParser.blockParser
      if (blockParser.isInstanceOf[DocumentBlockParser]) {
        // if document has paragraph or another definition item at end then we can proceed
        val node = blockParser.asInstanceOf[DocumentBlockParser].getBlock.asInstanceOf[Document]
        val lastChildAnyNot = node.lastChildAnyNot(classOf[BlankLine])
        if (!lastChildAnyNot.exists(_.isInstanceOf[Paragraph]) && !lastChildAnyNot.exists(_.isInstanceOf[DefinitionItem])) {
          BlockStart.none()
        } else {
          // check if we break list on double blank
          if (defOptions.doubleBlankLineBreaksList) {
            lastChildAnyNot.foreach(_.setCharsFromContent())
            val charSequence = state.line.baseSubSequence(lastChildAnyNot.get.endOffset, state.line.startOffset).normalizeEOL()
            val interSpace = BasedSequence.of(charSequence)
            if (interSpace.countLeading(ssg.md.util.misc.CharPredicate.EOL) >= 2) {
              return BlockStart.none() // @nowarn - boundary would be overkill here
            }
          }
          tryStartInternal(state)
        }
      } else if (!(blockParser.isInstanceOf[DefinitionItemBlockParser] || blockParser.isInstanceOf[ParagraphParser])) {
        BlockStart.none()
      } else {
        tryStartInternal(state)
      }
    }

    private def tryStartInternal(state: ParserState): Nullable[BlockStart] = {
      // check if we break list on double blank
      val emulationFamily = defOptions.myParserEmulationProfile
      val currentIndent = state.indent
      val codeIndent = if (emulationFamily == ParserEmulationProfile.COMMONMARK || emulationFamily == ParserEmulationProfile.FIXED_INDENT) defOptions.codeIndent else defOptions.itemIndent

      if (currentIndent < codeIndent) {
        val itemData = parseItemMarker(defOptions, state, state.activeBlockParser.isInstanceOf[ParagraphParser])
        if (itemData != null) { // @nowarn - parseItemMarker may return null
          Nullable(BlockStart.of(new DefinitionItemBlockParser(state.properties, itemData))
            .atColumn(itemData.markerColumn + itemData.itemMarker.length() + itemData.contentOffset))
        } else {
          BlockStart.none()
        }
      } else {
        BlockStart.none()
      }
    }
  }
}
