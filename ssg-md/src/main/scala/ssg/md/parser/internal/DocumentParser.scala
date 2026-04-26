/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/DocumentParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/DocumentParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package internal

import ssg.md.ast.Paragraph
import ssg.md.ast.util.{ ClassifyingBlockTracker, Parsing }
import ssg.md.parser.block._
import ssg.md.parser.core._
import ssg.md.parser.{ InlineParser, InlineParserFactory, Parser }
import ssg.md.util.ast._
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataHolder }
import ssg.md.util.dependency.DependencyResolver
import ssg.md.util.sequence.{ BasedSequence, PrefixedSubSequence }

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.io.{ BufferedReader, IOException, Reader }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class DocumentParser(
  val options:                           DataHolder,
  customBlockParserFactories:            List[CustomBlockParserFactory],
  val paragraphPreProcessorDependencies: List[List[ParagraphPreProcessorFactory]],
  val blockPreProcessorDependencies:     List[List[BlockPreProcessorFactory]],
  _inlineParser:                         InlineParser
) extends ParserState {

  private val myParsing: Parsing = inlineParser.parsing

  private val blockParserFactories: List[BlockParserFactory] =
    customBlockParserFactories.map(_.apply(options))

  private val documentBlockParser: DocumentBlockParser            = DocumentBlockParser()
  private val blankLinesInAst:     Boolean                        = Parser.BLANK_LINES_IN_AST.get(options)
  private val trackDocumentLines:  Boolean                        = Parser.TRACK_DOCUMENT_LINES.get(options)
  private val _lineSegments:       ArrayBuffer[BasedSequence]     = ArrayBuffer.empty
  private val _activeBlockParsers: ArrayBuffer[BlockParser]       = ArrayBuffer.empty
  private val blockTracker:        ClassifyingBlockTracker        = ClassifyingBlockTracker()
  private val lastLineBlankMap:    mutable.HashMap[Node, Boolean] = mutable.HashMap.empty

  private var _line:              BasedSequence = scala.compiletime.uninitialized
  private var _lineWithEOL:       BasedSequence = scala.compiletime.uninitialized
  private var _lineNumber:        Int           = 0
  private var _lineStart:         Int           = 0
  private var _lineEOLIndex:      Int           = 0
  private var _lineEndIndex:      Int           = 0
  private var _index:             Int           = 0
  private var _column:            Int           = 0
  private var columnIsInTab:      Boolean       = false
  private var _nextNonSpace:      Int           = 0
  private var nextNonSpaceColumn: Int           = 0
  private var _indent:            Int           = 0
  private var _blank:             Boolean       = false
  private var _isBlankLine:       Boolean       = false
  private var currentPhase:       ParserPhase   = ParserPhase.STARTING

  activateBlockParser(documentBlockParser)

  // ParserState implementation
  override def line:              BasedSequence       = _line
  override def lineWithEOL:       BasedSequence       = _lineWithEOL
  override def getIndex:          Int                 = _index
  override def nextNonSpaceIndex: Int                 = _nextNonSpace
  override def column:            Int                 = _column
  override def indent:            Int                 = _indent
  override def isBlank:           Boolean             = _blank
  override def isBlankLine:       Boolean             = _isBlankLine
  override def lineNumber:        Int                 = _lineNumber
  override def lineStart:         Int                 = _lineStart
  override def lineEolLength:     Int                 = _lineEndIndex - _lineEOLIndex
  override def lineEndIndex:      Int                 = _lineEndIndex
  override def lineSegments:      List[BasedSequence] = _lineSegments.toList
  override def parserPhase:       ParserPhase         = currentPhase
  override def parsing:           Parsing             = myParsing
  override def properties:        MutableDataHolder   = documentBlockParser.getBlock

  override def activeBlockParser:  BlockParser       = _activeBlockParsers.last
  override def activeBlockParsers: List[BlockParser] = _activeBlockParsers.toList

  override def getActiveBlockParser(node: Block): Nullable[BlockParser] = {
    val bp = blockTracker.getKey(node)
    if (bp == null || bp.isClosed) Nullable.empty // @nowarn - Java interop: blockTracker.getKey may return null
    else Nullable(bp)
  }

  override def inlineParser: InlineParser = _inlineParser

  // BlockTracker delegation
  override def blockAdded(node:                  Block): Unit = blockTracker.blockAdded(node)
  override def blockAddedWithChildren(node:      Block): Unit = blockTracker.blockAddedWithChildren(node)
  override def blockAddedWithDescendants(node:   Block): Unit = blockTracker.blockAddedWithDescendants(node)
  override def blockRemoved(node:                Block): Unit = blockTracker.blockRemoved(node)
  override def blockRemovedWithChildren(node:    Block): Unit = blockTracker.blockRemovedWithChildren(node)
  override def blockRemovedWithDescendants(node: Block): Unit = blockTracker.blockRemovedWithDescendants(node)

  // BlockParserTracker delegation
  override def blockParserAdded(blockParser:   BlockParser): Unit = blockTracker.blockParserAdded(blockParser)
  override def blockParserRemoved(blockParser: BlockParser): Unit = blockTracker.blockParserRemoved(blockParser)

  override def endsWithBlankLine(block: Node): Boolean = boundary {
    var current: Nullable[Node] = Nullable(block)
    while (current.isDefined) {
      if (isLastLineBlank(current.get)) break(true)
      current = current.get.lastBlankLineChild
    }
    false
  }

  override def isLastLineBlank(node: Node): Boolean =
    lastLineBlankMap.getOrElse(node, false)

  /** The main parsing function. Returns a parsed document AST.
    *
    * @param source
    *   source sequence to parse
    * @return
    *   Document node of the resulting AST
    */
  def parse(source: CharSequence): Document = {
    val input        = BasedSequence.of(source)
    var lineStartVar = 0
    _lineNumber = 0

    documentBlockParser.initializeDocument(options, input)
    inlineParser.initializeDocument(documentBlockParser.getBlock)

    currentPhase = ParserPhase.PARSE_BLOCKS

    var lineBreak = Parsing.findLineBreak(input, lineStartVar)
    while (lineBreak != -1) {
      val parsedLine = input.subSequence(lineStartVar, lineBreak)
      val lineEOL    = lineBreak
      val lineEndVar = if (lineBreak + 1 < input.length() && input.charAt(lineBreak) == '\r' && input.charAt(lineBreak + 1) == '\n') {
        lineBreak + 2
      } else {
        lineBreak + 1
      }

      _lineWithEOL = input.subSequence(lineStartVar, lineEndVar)
      _lineStart = lineStartVar
      _lineEOLIndex = lineEOL
      _lineEndIndex = lineEndVar
      incorporateLine(parsedLine)
      _lineNumber += 1
      lineStartVar = lineEndVar
      lineBreak = Parsing.findLineBreak(input, lineStartVar)
    }

    if (input.length() > 0 && (lineStartVar == 0 || lineStartVar < input.length())) {
      _lineWithEOL = input.subSequence(lineStartVar, input.length())
      _lineStart = lineStartVar
      _lineEOLIndex = input.length()
      _lineEndIndex = _lineEOLIndex
      incorporateLine(_lineWithEOL)
      _lineNumber += 1
    }

    finalizeAndProcess()
  }

  @throws[IOException]
  def parse(input: Reader): Document = {
    val bufferedReader = input match {
      case br: BufferedReader => br
      case _ => BufferedReader(input)
    }

    val file   = new StringBuilder
    val buffer = new Array[Char](16384)

    boundary {
      while (true) {
        val charsRead = bufferedReader.read(buffer)
        if (charsRead < 0) break(())
        file.appendAll(buffer, 0, charsRead)
      }
    }

    val source = BasedSequence.of(file.toString)
    parse(source)
  }

  private def activateBlockParser(blockParser: BlockParser): Unit = {
    _activeBlockParsers.addOne(blockParser)
    if (!blockTracker.containsKey(blockParser)) {
      blockParserAdded(blockParser)
    }
  }

  private def deactivateBlockParser(): Unit =
    _activeBlockParsers.remove(_activeBlockParsers.size - 1)

  private def findNextNonSpace(): Unit = {
    var i    = _index
    var cols = _column

    _blank = true
    val lineLen = _line.length()
    boundary {
      while (i < lineLen) {
        val c = _line.charAt(i)
        c match {
          case ' ' =>
            i += 1
            cols += 1
          case '\t' =>
            i += 1
            cols += (4 - (cols % 4))
          case _ =>
            _blank = false
            break(())
        }
      }
    }

    _nextNonSpace = i
    nextNonSpaceColumn = cols
    _indent = nextNonSpaceColumn - _column
  }

  private def setNewIndex(newIndex: Int): Unit = {
    if (newIndex >= _nextNonSpace) {
      // We can start from here, no need to calculate tab stops again
      _index = _nextNonSpace
      _column = nextNonSpaceColumn
    }
    while (_index < newIndex && _index != _line.length())
      advance()
    // If we're going to an index as opposed to a column, we're never within a tab
    columnIsInTab = false
  }

  private def setNewColumn(newColumn: Int): Unit = {
    if (newColumn >= nextNonSpaceColumn) {
      // We can start from here, no need to calculate tab stops again
      _index = _nextNonSpace
      _column = nextNonSpaceColumn
    }
    while (_column < newColumn && _index != _line.length())
      advance()
    if (_column > newColumn) {
      // Last character was a tab and we overshot our target
      _index -= 1
      _column = newColumn
      columnIsInTab = true
    } else {
      columnIsInTab = false
    }
  }

  /** Analyze a line of text and update the document appropriately. We parse markdown text by calling this on each line of input, then finalizing the document.
    */
  private def incorporateLine(ln: BasedSequence): Unit = boundary {
    _line = ln
    _index = 0
    _column = 0
    columnIsInTab = false

    // track lines of document
    if (trackDocumentLines) {
      _lineSegments.addOne(_lineWithEOL)
    }

    // For each containing block, try to parse the associated line start.
    // Bail out on failure: container will point to the last matching block.
    // Set all_matched to false if not all containers match.
    // The document will always match, can be skipped
    var matches = 1
    var blankLine: Nullable[BlankLine] = Nullable.empty

    findNextNonSpace()

    if (_blank) {
      if (blankLinesInAst) {
        // line was blank
        blankLine = Nullable(BlankLine(_lineWithEOL))
        documentBlockParser.getBlock.appendChild(blankLine.get)
      }
    }

    var earlyReturn   = false
    val activeSubList = _activeBlockParsers.slice(1, _activeBlockParsers.size)
    boundary {
      for (blockParser <- activeSubList) {
        val wasBlank = _blank

        findNextNonSpace()

        if (_blank) {
          if (blankLinesInAst) {
            if (blankLine.isEmpty) {
              // line became blank
              blankLine = Nullable(BlankLine(_lineWithEOL))
              documentBlockParser.getBlock.appendChild(blankLine.get)
            }

            if (!wasBlank && blockParser.getBlock.isInstanceOf[BlankLineContainer]) {
              blankLine.get.claimedBlankLine = blockParser.getBlock
            }
          }
        }

        // HACK: may work to detect when a real blank line follows a paragraph
        _isBlankLine = wasBlank

        val result = blockParser.tryContinue(this)
        if (result.isDefined) {
          val blockContinue = result.get.asInstanceOf[BlockContinueImpl]
          if (blockContinue.isFinalize) {
            finalize(blockParser)
            earlyReturn = true
            break(()) // return from incorporateLine
          } else {
            if (blockContinue.newIndex != -1) {
              setNewIndex(blockContinue.newIndex)
              if (!_blank && blockParser.getBlock.isInstanceOf[BlankLineContainer]) {
                findNextNonSpace()
                if (_blank) {
                  blankLine = Nullable(BlankLine(_lineWithEOL, blockParser.getBlock))
                  blockParser.getBlock.appendChild(blankLine.get)
                }
              }
            } else if (blockContinue.newColumn != -1) {
              setNewColumn(blockContinue.newColumn)
              if (!_blank && blockParser.getBlock.isInstanceOf[BlankLineContainer]) {
                findNextNonSpace()
                if (_blank) {
                  blankLine = Nullable(BlankLine(_lineWithEOL, blockParser.getBlock))
                  blockParser.getBlock.appendChild(blankLine.get)
                }
              }
            }
            matches += 1

            if (blankLine.isDefined && (blankLinesInAst || blankLine.get.claimedBlankLine.exists(_ eq blockParser.getBlock))) {
              if (blockParser.getBlock.isInstanceOf[BlankLineContainer]) {
                blockParser.getBlock.appendChild(blankLine.get)
              }
            }
          }
        } else {
          break(()) // break out of for loop
        }
      }
    }

    if (earlyReturn) break(())

    val unmatchedBlockParsers  = ArrayBuffer.from(_activeBlockParsers.slice(matches, _activeBlockParsers.size))
    val lastMatchedBlockParser = _activeBlockParsers(matches - 1)
    var blockParser: BlockParser = lastMatchedBlockParser
    var allClosed = unmatchedBlockParsers.isEmpty

    // Check to see if we've hit 2nd blank line; if so break out of list or any other block type that handles this
    if (_blank && isLastLineBlank(blockParser.getBlock)) {
      val matchedBlockParsers = _activeBlockParsers.slice(0, matches).toList
      breakOutOfLists(matchedBlockParsers)
    }

    // Unless last matched container is a code block, try new container starts,
    // adding children to the last matched container:
    var tryBlockStartsFlag = blockParser.isInterruptible || blockParser.isContainer
    var lastPrefixClaimer: Nullable[BlockParser] = Nullable.empty

    while (tryBlockStartsFlag) {
      val wasBlank = _blank
      findNextNonSpace()

      if (_blank && !wasBlank) lastPrefixClaimer = Nullable(blockParser)

      // this is a little performance optimization:
      if (_blank || (_indent < myParsing.CODE_BLOCK_INDENT && Parsing.isLetter(_line, _nextNonSpace))) {
        setNewIndex(_nextNonSpace)
        tryBlockStartsFlag = false
      } else {
        val blockStart = findBlockStart(blockParser)
        if (blockStart.isEmpty) {
          if (!(blockParser.isRawText && blockParser.isInterruptible)) setNewIndex(_nextNonSpace)
          tryBlockStartsFlag = false
        } else {
          val impl = blockStart.get

          if (!allClosed) {
            finalizeBlocks(unmatchedBlockParsers.toList)
            allClosed = true
          }

          if (impl.newIndex != -1) {
            setNewIndex(impl.newIndex)
          } else if (impl.newColumn != -1) {
            setNewColumn(impl.newColumn)
          }

          if (impl.isReplaceActiveBlockParser) {
            removeActiveBlockParser()
          }

          for (newBlockParser <- impl.blockParsers) {
            blockParser = addChild(newBlockParser)
            tryBlockStartsFlag = newBlockParser.isContainer
          }
        }
      }
    }

    // What remains at the offset is a text line. Add the text to the
    // appropriate block.

    // First check for a lazy paragraph continuation:
    if (!allClosed && !_blank && activeBlockParser.isParagraphParser) {
      // lazy paragraph continuation
      addLine()
    } else {
      // finalize any blocks not matched
      if (!allClosed) {
        finalizeBlocks(unmatchedBlockParsers.toList)
      }

      propagateLastLineBlank(blockParser, lastMatchedBlockParser)

      if (_blank) {
        if (blockParser.getBlock.isInstanceOf[KeepTrailingBlankLineContainer]) {
          if (blankLine.isDefined) {
            blockParser.getBlock.appendChild(blankLine.get)
          } else {
            if (blockParser.isContainer && lastPrefixClaimer.exists(_ eq blockParser)) {
              // need to add it as a blank line if it is attributable to the block, otherwise there is no content
              val bl = BlankLine(_lineWithEOL, blockParser.getBlock)
              blockParser.getBlock.appendChild(bl)
            }
          }
        }
      }

      if (!blockParser.isContainer) {
        addLine()
      } else if (!_blank) {
        // create paragraph container for line
        addChild(ParagraphParser())
        addLine()
      }
    }
  }

  private def findBlockStart(blockParser: BlockParser): Nullable[BlockStartImpl] = {
    val matchedBlockParser = MatchedBlockParserImpl(blockParser)
    boundary {
      for (blockParserFactory <- blockParserFactories)
        if (blockParser.canInterruptBy(blockParserFactory)) {
          val result = blockParserFactory.tryStart(this, matchedBlockParser)
          if (result.isDefined) {
            result.get match {
              case impl: BlockStartImpl => break(Nullable(impl))
              case _ =>
            }
          }
        }
      Nullable.empty
    }
  }

  /** Add block parser as a child of the currently active parser. If the tip can't accept children, close and finalize it and try its parent, and so on til we find a block that can accept children.
    */
  private def addChild(blockParser: BlockParser): BlockParser = {
    while (!activeBlockParser.canContain(this, blockParser, blockParser.getBlock))
      finalize(activeBlockParser)

    activeBlockParser.getBlock.appendChild(blockParser.getBlock)
    activateBlockParser(blockParser)

    blockParser
  }

  /** Finalize a block. Close it and do any necessary postprocessing, e.g. creating string_content from strings, setting the 'tight' or 'loose' status of a list, and parsing the beginnings of
    * paragraphs for reference definitions.
    */
  private def finalize(blockParser: BlockParser): Unit = {
    if (activeBlockParser eq blockParser) {
      deactivateBlockParser()
    }

    val block = blockParser.getBlock

    // move blank lines at end of block to parent if they are not claimed by the block
    if (block.parent.isDefined) {
      val lastChildNullable = block.lastChild
      if (lastChildNullable.isDefined) {
        lastChildNullable.get match {
          case bl: BlankLine =>
            if (!bl.claimedBlankLine.exists(_ eq block)) {
              // move them to parent
              val firstInChain = bl.firstInChain
              block.insertChainAfter(firstInChain)
              block.setCharsFromContentOnly()
            }
          case _ =>
        }
      }
    }

    blockParser.closeBlock(this)
    blockParser.finalizeClosedBlock()

    // remove BlankLine nodes that are part of the block's content
    boundary {
      while (true) {
        val nextNullable = block.next
        if (nextNullable.isDefined) {
          nextNullable.get match {
            case bl: BlankLine if bl.endOffset <= block.endOffset =>
              bl.unlink()
            case _ =>
              break(())
          }
        } else {
          break(())
        }
      }
    }
  }

  private def removeActiveBlockParser(): Unit = {
    val old = activeBlockParser
    deactivateBlockParser()

    blockParserRemoved(old)
    old.getBlock.unlink()
  }

  /** Add line content to the active block parser. We assume it can accept lines -- that check should be done before calling this.
    */
  private def addLine(): Unit = {
    var content = _lineWithEOL.subSequence(_index)
    if (columnIsInTab) {
      // Our column is in a partially consumed tab. Expand the remaining columns (to the next tab stop) to spaces.
      val rest   = content.subSequence(1)
      val spaces = Parsing.columnsToNextTabStop(_column)
      val sb     = new StringBuilder(spaces + rest.length())
      for (_ <- 0 until spaces)
        sb.append(' ')
      content = PrefixedSubSequence.prefixOf(sb.toString, rest)
    }

    activeBlockParser.addLine(this, content)
  }

  /** Break out of all containing lists, resetting the tip of the document to the parent of the highest list, and finalizing all the lists. (This is used to implement the "two blank lines break out of
    * all lists" feature.)
    */
  private def breakOutOfLists(blockParsers: List[BlockParser]): Unit = {
    var lastList = -1
    for (i <- (blockParsers.size - 1) to 0 by -1) {
      val bp = blockParsers(i)
      if (bp.breakOutOnDoubleBlankLine) {
        lastList = i
      }
    }

    if (lastList != -1) {
      finalizeBlocks(blockParsers.slice(lastList, blockParsers.size))
    }
  }

  private def propagateLastLineBlank(blockParser: BlockParser, lastMatchedBlockParser: BlockParser): Unit = {
    if (_blank) {
      val lastChildOpt = blockParser.getBlock.lastChild
      if (lastChildOpt.isDefined) setLastLineBlank(lastChildOpt.get, value = true)
    }

    // Block quote lines are never blank as they start with >
    // and we don't count blanks in fenced code for purposes of tight/loose
    // lists or breaking out of lists. We also don't set lastLineBlank
    // on an empty list item.
    // now implemented by the block parsers to make it available to extensions
    val lastLineBlankFlag = _blank && blockParser.isPropagatingLastBlankLine(lastMatchedBlockParser)

    // Propagate lastLineBlank up through parents
    var node: Nullable[Node] = Nullable(blockParser.getBlock)
    while (node.isDefined) {
      setLastLineBlank(node.get, lastLineBlankFlag)
      node = node.get.parent
    }
  }

  private def setLastLineBlank(node: Node, value: Boolean): Unit =
    lastLineBlankMap.put(node, value)

  /** Finalize blocks of previous line. */
  private def finalizeBlocks(blockParsers: List[BlockParser]): Unit =
    for (i <- (blockParsers.size - 1) to 0 by -1) {
      val bp = blockParsers(i)
      finalize(bp)
    }

  private def advance(): Unit = {
    val c = _line.charAt(_index)
    if (c == '\t') {
      _index += 1
      _column += Parsing.columnsToNextTabStop(_column)
    } else {
      _index += 1
      _column += 1
    }
  }

  private def finalizeAndProcess(): Document = {
    finalizeBlocks(_activeBlockParsers.toList)

    // need to run block pre-processors at this point, before inline processing
    currentPhase = ParserPhase.PRE_PROCESS_PARAGRAPHS
    preProcessParagraphs()

    currentPhase = ParserPhase.PRE_PROCESS_BLOCKS
    preProcessBlocks()

    // can now run inline processing
    currentPhase = ParserPhase.PARSE_INLINES
    processInlines()

    currentPhase = ParserPhase.DONE

    val document = documentBlockParser.getBlock
    inlineParser.finalizeDocument(document)

    document
  }

  private def preProcessParagraph(block: Paragraph, stage: List[ParagraphPreProcessorFactory], processorMap: mutable.HashMap[ParagraphPreProcessorFactory, ParagraphPreProcessor]): Unit = {
    var continue = true
    boundary {
      while (continue) {
        var hadChanges = false

        for (factory <- stage) {
          val processor = processorMap.getOrElseUpdate(factory, factory.apply(this))
          val pos       = processor.preProcessBlock(block, this)

          if (pos > 0) {
            hadChanges = true

            // skip leading blanks
            val blockChars   = block.chars
            val contentChars = blockChars.subSequence(pos + blockChars.countLeading(ssg.md.util.misc.CharPredicate.WHITESPACE, pos, blockChars.length()))

            if (contentChars.isBlank()) {
              // all used up
              block.unlink()
              blockRemoved(block)
              break(())
            } else {
              // skip lines that were removed
              val iMax = block.lineCount
              var i    = 0
              boundary {
                while (i < iMax) {
                  if (block.lineChars(i).endOffset > contentChars.startOffset) break(())
                  i += 1
                }
              }

              if (i >= iMax) {
                // all used up
                block.unlink()
                blockRemoved(block)
                break(())
              } else if (block.lineChars(i).endOffset == contentChars.startOffset) {
                // full lines removed
                block.setContent(block, i, iMax)
              } else {
                // need to change the first line of the line list
                val lines = new java.util.ArrayList[BasedSequence](block.contentLines.subList(i, iMax))
                val start = contentChars.startOffset - lines.get(0).startOffset
                if (start > 0 && start < lines.get(0).length()) {
                  lines.set(0, lines.get(0).subSequence(start))
                }

                // now we copy the indents
                val indents = new Array[Int](iMax - i)
                System.arraycopy(block.lineIndents, i, indents, 0, indents.length)
                block.contentLines = lines
                block.lineIndents = indents
                block.chars = contentChars
              }
            }
          }
        }

        if (!hadChanges || stage.size < 2) continue = false
      }
    }
  }

  private def preProcessParagraphs(): Unit =
    // run paragraph pre-processors - use classifier to iterate only Paragraph nodes
    if (blockTracker.getNodeClassifier.containsCategory(Nullable(classOf[Paragraph]))) {
      val processorMap = mutable.HashMap[ParagraphPreProcessorFactory, ParagraphPreProcessor]()
      for (factoryStage <- paragraphPreProcessorDependencies) {
        val paragraphs = blockTracker.getNodeClassifier.getCategoryItems[Paragraph](
          classOf[Paragraph],
          Array[Class[?]](classOf[Paragraph])
        )
        for (paragraph <- paragraphs.asScala)
          preProcessParagraph(paragraph, factoryStage, processorMap)
      }
    }

  private def preProcessBlocks(): Unit = {
    // Collect all block types that pre-processors are interested in
    val blockTypes = new java.util.HashSet[Class[?]]()
    for (dependents <- blockPreProcessorDependencies)
      for (factory <- dependents) {
        val bt = factory.getBlockTypes
        bt.foreach(blockTypes.add)
      }

    val preProcessBitSet = blockTracker.getNodeClassifier.categoriesBitSet(blockTypes)

    if (!preProcessBitSet.isEmpty) {
      for (dependents <- blockPreProcessorDependencies)
        for (factory <- dependents) {
          val categorySet = new java.util.HashSet[Class[?]]()
          factory.getBlockTypes.foreach(categorySet.add)
          val blockList         = blockTracker.getNodeClassifier.getCategoryItems[Block](classOf[Block], categorySet)
          val blockPreProcessor = factory.apply(this)

          for (block <- blockList.asScala)
            blockPreProcessor.preProcess(this, block)
        }
    }
  }

  private def processInlines(): Unit =
    for (blockParser <- blockTracker.allBlockParsers.asScala)
      blockParser.parseInlines(inlineParser)
}

object DocumentParser {

  val INLINE_PARSER_FACTORY: InlineParserFactory = new InlineParserFactory {
    override def inlineParser(
      options:                DataHolder,
      specialCharacters:      java.util.BitSet,
      delimiterCharacters:    java.util.BitSet,
      delimiterProcessors:    Map[Char, ssg.md.parser.delimiter.DelimiterProcessor],
      linkRefProcessors:      LinkRefProcessorData,
      inlineParserExtensions: List[ssg.md.parser.InlineParserExtensionFactory]
    ): InlineParser =
      CommonmarkInlineParser(options, specialCharacters, delimiterCharacters, delimiterProcessors, linkRefProcessors, inlineParserExtensions)
  }

  private val CORE_FACTORIES_DATA_KEYS: Map[CustomBlockParserFactory, DataKey[Boolean]] = Map(
    BlockQuoteParser.Factory() -> Parser.BLOCK_QUOTE_PARSER,
    HeadingParser.Factory() -> Parser.HEADING_PARSER,
    FencedCodeBlockParser.Factory() -> Parser.FENCED_CODE_BLOCK_PARSER,
    HtmlBlockParser.Factory() -> Parser.HTML_BLOCK_PARSER,
    ThematicBreakParser.Factory() -> Parser.THEMATIC_BREAK_PARSER,
    ListBlockParser.Factory() -> Parser.LIST_BLOCK_PARSER,
    IndentedCodeBlockParser.Factory() -> Parser.INDENTED_CODE_BLOCK_PARSER
  )

  private val CORE_PARAGRAPH_PRE_PROCESSORS: Map[DataKey[Boolean], ParagraphPreProcessorFactory] = Map(
    Parser.REFERENCE_PARAGRAPH_PRE_PROCESSOR -> ReferencePreProcessorFactory()
  )

  def calculateBlockParserFactories(options: DataHolder, customBlockParserFactories: List[CustomBlockParserFactory]): List[CustomBlockParserFactory] = {
    // By having the custom factories come first, extensions are able to change behavior of core syntax.
    val list = ArrayBuffer.from(customBlockParserFactories)

    // need to keep core parsers in the right order, this is done through their dependencies
    for ((factory, dataKey) <- CORE_FACTORIES_DATA_KEYS)
      if (dataKey.get(options)) {
        list.addOne(factory)
      }

    DependencyResolver.resolveFlatDependencies(list.toList, Nullable.empty, Nullable.empty)
  }

  def calculateParagraphPreProcessors(
    options:             DataHolder,
    blockPreProcessors:  List[ParagraphPreProcessorFactory],
    inlineParserFactory: InlineParserFactory
  ): List[List[ParagraphPreProcessorFactory]] = {
    val list = ArrayBuffer.from(blockPreProcessors)

    if (inlineParserFactory eq INLINE_PARSER_FACTORY) {
      for ((preProcessorDataKey, preProcessorFactory) <- CORE_PARAGRAPH_PRE_PROCESSORS)
        if (preProcessorDataKey.get(options)) {
          list.addOne(preProcessorFactory)
        }
    }

    DependencyResolver.resolveDependencies(list.toList, Nullable.empty, Nullable.empty)
  }

  def calculateBlockPreProcessors(
    options:            DataHolder,
    blockPreProcessors: List[BlockPreProcessorFactory]
  ): List[List[BlockPreProcessorFactory]] =
    DependencyResolver.resolveDependencies(blockPreProcessors, Nullable.empty, Nullable.empty)
}
