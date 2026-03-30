/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/InlineParserImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package internal

import ssg.md.ast._
import ssg.md.ast.util.{ ReferenceRepository, TextNodeConverter }
import ssg.md.parser._
import ssg.md.parser.block.{ CharacterNodeFactory, ParagraphPreProcessor, ParserState }
import ssg.md.parser.core.delimiter.{ AsteriskDelimiterProcessor, Bracket, Delimiter, UnderscoreDelimiterProcessor }
import ssg.md.parser.delimiter.DelimiterProcessor
import ssg.md.util.ast._
import ssg.md.util.data.DataHolder
import ssg.md.util.dependency.DependencyResolver
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.{ BasedSequence, Escaping, SegmentedSequence, SequenceUtils }

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.util.BitSet
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class InlineParserImpl(
  dataOptions:                        DataHolder,
  specialChars:                       BitSet,
  val delimiterCharacters:            BitSet,
  val delimiterProcessors:            Map[Char, DelimiterProcessor],
  val linkRefProcessorsData:          LinkRefProcessorData,
  inlineParserExtensionFactoriesInit: List[InlineParserExtensionFactory]
) extends LightInlineParserImpl(dataOptions)
    with InlineParser
    with ParagraphPreProcessor {

  protected val originalSpecialCharacters:      BitSet                                                   = specialChars
  protected var specialCharacters:              BitSet                                                   = specialChars
  protected var linkRefProcessors:              Nullable[List[LinkRefProcessor]]                         = Nullable.empty
  protected var inlineParserExtensions:         Nullable[mutable.Map[Char, List[InlineParserExtension]]] = Nullable.empty
  protected var inlineParserExtensionFactories: Nullable[List[InlineParserExtensionFactory]]             =
    if (inlineParserExtensionFactoriesInit.nonEmpty) Nullable(inlineParserExtensionFactoriesInit) else Nullable.empty

  protected var linkDestinationParser: Nullable[LinkDestinationParser] = Nullable.empty

  // used to temporarily override handling of special characters by custom ParagraphPreProcessors
  protected var customCharacters:                 Nullable[BitSet]                          = Nullable.empty
  protected var customSpecialCharacterFactoryMap: Nullable[Map[Char, CharacterNodeFactory]] = Nullable.empty
  protected var customSpecialCharacterNodes:      Nullable[ArrayBuffer[Node]]               = Nullable.empty

  /** Link references by ID, needs to be built up using parseReference before calling parse. */
  protected var referenceRepository: ReferenceRepository = scala.compiletime.uninitialized

  /** Top delimiter (emphasis, strong emphasis or custom emphasis). */
  protected var _lastDelimiter: Nullable[Delimiter] = Nullable.empty

  /** Top opening bracket (`[` or `![`). */
  private var _lastBracket: Nullable[Bracket] = Nullable.empty

  if (_options.useHardcodedLinkAddressParser) {
    linkDestinationParser = Nullable(
      LinkDestinationParser(
        _options.linksAllowMatchedParentheses,
        _options.spaceInLinkUrls,
        _options.parseJekyllMacrosInUrls,
        _options.intellijDummyIdentifier
      )
    )
  }

  override def initializeDocument(document: Document): Unit = {
    _document = document
    referenceRepository = Parser.REFERENCES.get(document)

    linkRefProcessors = Nullable(
      linkRefProcessorsData.processors.map(_.apply(document))
    )

    // create custom processors
    inlineParserExtensionFactories.foreach { factories =>
      val extensions         = InlineParserImpl.calculateInlineParserExtensions(document, factories)
      val extensionMap       = mutable.HashMap[Char, List[InlineParserExtension]]()
      val parserExtensionMap = mutable.HashMap[InlineParserExtensionFactory, InlineParserExtension]()

      for ((ch, factoryList) <- extensions) {
        val extensionList = factoryList.map { factory =>
          parserExtensionMap.getOrElseUpdate(factory, factory.apply(this))
        }
        extensionList.foreach { ext =>
          specialCharacters.set(ch)
        }
        extensionMap.put(ch, extensionList)
      }

      inlineParserExtensions = Nullable(extensionMap)
    }
  }

  override def finalizeDocument(document: Document): Unit =
    inlineParserExtensions.foreach { exts =>
      val seen = mutable.HashSet[InlineParserExtension]()
      for ((_, extensionList) <- exts)
        for (ext <- extensionList)
          if (seen.add(ext)) {
            ext.finalizeDocument(this)
          }
    }

  override def lastDelimiter: Nullable[Delimiter] = _lastDelimiter
  override def lastBracket:   Nullable[Bracket]   = _lastBracket

  /** Parse content in block into inline children, using reference map to resolve references. */
  override def parse(input: BasedSequence, node: Node): Unit = {
    _block = node
    _input = input.trim()
    _index = 0
    _lastDelimiter = Nullable.empty
    _lastBracket = Nullable.empty
    _currentText = Nullable.empty

    val customOnly = node.isInstanceOf[DoNotDecorate]

    var moreToParse = true
    while (moreToParse) {
      val parsed = parseInline(customOnly)
      if (!parsed) {
        moreToParse = false
      }
    }

    processDelimiters(Nullable.empty)
    flushTextNode()

    if (!customOnly) {
      inlineParserExtensions.foreach { exts =>
        val seen = mutable.HashSet[InlineParserExtension]()
        for ((_, extensionList) <- exts)
          for (ext <- extensionList)
            if (seen.add(ext)) {
              ext.finalizeBlock(this)
            }
      }
    }

    // merge nodes if needed
    mergeTextNodes(_block.firstChild, _block.lastChild)
  }

  override def parseCustom(
    input:            BasedSequence,
    node:             Node,
    customCharacters: BitSet,
    nodeFactoryMap:   Map[Char, CharacterNodeFactory]
  ): Nullable[List[Node]] = {
    this.customCharacters = Nullable(customCharacters)
    this.specialCharacters.or(customCharacters)
    this.customSpecialCharacterFactoryMap = Nullable(nodeFactoryMap)
    this.customSpecialCharacterNodes = Nullable.empty
    parse(input, node)
    this.specialCharacters = this.originalSpecialCharacters
    this.customSpecialCharacterFactoryMap = Nullable.empty
    this.customCharacters = Nullable.empty
    this.customSpecialCharacterNodes.map(_.toList)
  }

  override def mergeTextNodes(fromNode: Nullable[Node], toNode: Nullable[Node]): Unit = {
    var first: Nullable[Text] = Nullable.empty
    var last:  Nullable[Text] = Nullable.empty

    var node = fromNode
    boundary {
      while (node.isDefined) {
        node.get match {
          case text: Text =>
            if (first.isEmpty) {
              first = Nullable(text)
            }
            last = Nullable(text)
          case _ =>
            mergeIfNeeded(first, last)
            first = Nullable.empty
            last = Nullable.empty
        }

        if (node == toNode) {
          break(())
        }

        node = node.get.next
      }
    }

    mergeIfNeeded(first, last)
  }

  override def mergeIfNeeded(first: Nullable[Text], last: Nullable[Text]): Unit = {
    if (first.isDefined && last.isDefined && (first.get ne last.get)) {
      val sb = new java.util.ArrayList[BasedSequence]()
      sb.add(first.get.chars)
      var node = first.get.next
      val stop = last.get.next
      while (node.isDefined && node != stop) {
        sb.add(node.get.chars)
        val unlink = node.get
        node = node.get.next
        unlink.unlink()
      }
      val literal = SegmentedSequence.create(first.get.chars, sb)
      first.get.chars = literal
    }
  }

  /*
   *  ParagraphPreProcessor implementation
   */
  override def preProcessBlock(block: Paragraph, state: ParserState): Int = {
    var contentChars  = block.chars
    var leadingSpaces = contentChars.countLeading(CharPredicate.SPACE_TAB)
    var length        = contentChars.length()

    while (leadingSpaces <= 3 && length > 3 + leadingSpaces && contentChars.charAt(leadingSpaces) == '[') {
      if (leadingSpaces > 0) {
        contentChars = contentChars.subSequence(leadingSpaces, length)
        length -= leadingSpaces
      }

      val pos = parseReference(block, contentChars)
      if (pos == 0) {
        // use boundary-like early exit via while condition
        leadingSpaces = 4 // forces while to exit
      } else {
        contentChars = contentChars.subSequence(pos, length)
        length = contentChars.length()
        leadingSpaces = contentChars.countLeading(CharPredicate.SPACE_TAB)
      }
    }

    contentChars.startOffset - block.startOffset
  }

  /** Attempt to parse a reference definition, modifying the internal reference map.
    *
    * @param block
    *   the block whose text is being parsed for references
    * @param s
    *   sequence of the blocks characters
    * @return
    *   number of characters were parsed as a reference from the start of the sequence, `0` if none
    */
  protected def parseReference(block: Block, s: BasedSequence): Int = boundary {
    _input = s
    _index = 0
    val startIndex = _index

    // label:
    val matchChars = parseLinkLabel()
    if (matchChars == 0) {
      break(0)
    }

    // colon:
    if (peek() != ':') {
      break(0)
    }

    val rawLabel = _input.subSequence(0, matchChars + 1)
    _index += 1

    // link url
    spnl()

    val dest = parseLinkDestination()

    if (dest.isEmpty || dest.get.length() == 0) {
      break(0)
    }

    val beforeTitle = _index
    spnl()
    var title = parseLinkTitle()
    if (title.isEmpty) {
      // rewind before spaces
      _index = beforeTitle
    }

    var atLineEnd = true
    if (_index != _input.length() && `match`(myParsing.LINE_END).isEmpty) {
      if (title.isEmpty) {
        atLineEnd = false
      } else {
        // the potential title we found is not at the line end,
        // but it could still be a legal link reference if we
        // discard the title
        title = Nullable.empty
        // rewind before spaces
        _index = beforeTitle
        // and instead check if the link URL is at the line end
        atLineEnd = `match`(myParsing.LINE_END).isDefined
      }
    }

    if (!atLineEnd) {
      break(0)
    }

    val normalizedLabel = Escaping.normalizeReferenceChars(rawLabel, true)
    if (normalizedLabel.isEmpty) {
      break(0)
    }

    val reference = Reference(rawLabel, dest.get, title)

    // NOTE: whether first or last reference is kept is defined by the repository modify behavior setting
    // for CommonMark this is set in the initializeDocument() function of the inline parser
    referenceRepository.put(normalizedLabel, reference)

    block.insertBefore(reference)

    _index - startIndex
  }

  /** Parse the next inline element in subject, advancing input index. On success, add the result to block's children and return true. On failure, return false. */
  private def parseInline(customOnly: Boolean): Boolean = boundary {
    var res = false

    val c = peek()
    if (c == SequenceUtils.NUL) {
      break(false)
    }

    if (!customOnly) {
      inlineParserExtensions.foreach { exts =>
        exts.get(c).foreach { extensions =>
          for (ext <- extensions) {
            res = ext.parse(this)
            if (res) break(true)
          }
        }
      }
    }

    if (customCharacters.isDefined && customCharacters.get.get(c)) {
      res = processCustomCharacters()
      if (!res) {
        _index += 1
        // When we get here, it's only for a single special character that turned out to not have a special meaning.
        // So we shouldn't have a single surrogate here, hence it should be ok to turn it into a String.
        appendText(_input.subSequence(_index - 1, _index))
      } else {
        // Issue: #376, need to clear any delimiter stack since whatever is not used is not a delimiter
        processDelimiters(Nullable.empty)
        this._lastDelimiter = Nullable.empty
      }

      break(true)
    }

    c match {
      case '\r' | '\n' =>
        res = parseNewline()
      case '\\' =>
        res = parseBackslash()
      case '`' =>
        res = parseBackticks()
      case '[' =>
        res = parseOpenBracket()
      case '!' =>
        res = parseBang()
      case ']' =>
        res = parseCloseBracket()
      case '<' =>
        // first we check custom special characters for < delimiters and only allow 2 consecutive ones to allow anchor links and HTML processing
        val isDelimiter = delimiterCharacters.get(c)
        if (isDelimiter && peek(1) == '<') {
          val delimiterProcessor = delimiterProcessors(c)
          res = parseDelimiters(delimiterProcessor, c)
        } else {
          res = parseAutolink() || parseHtmlInline()
        }
      case '&' =>
        res = parseEntity()
      case _ =>
        // first we check custom special characters
        val isDelimiter = delimiterCharacters.get(c)
        if (isDelimiter) {
          val delimiterProcessor = delimiterProcessors(c)
          res = parseDelimiters(delimiterProcessor, c)
        } else {
          res = parseString()
        }
    }

    if (!res) {
      _index += 1
      // When we get here, it's only for a single special character that turned out to not have a special meaning.
      // So we shouldn't have a single surrogate here, hence it should be ok to turn it into a String.
      appendText(_input.subSequence(_index - 1, _index))
    }

    true
  }

  private def processCustomCharacters(): Boolean = boundary {
    val c          = peek()
    if (customSpecialCharacterFactoryMap.isEmpty) break(false)
    val factoryOpt = customSpecialCharacterFactoryMap.get.get(c)
    if (factoryOpt.isEmpty) break(false)

    val factory = factoryOpt.get
    val node    = factory()
    node.chars = _input.subSequence(_index, _index + 1)

    if (_currentText.isDefined) {
      val prevText = SegmentedSequence.create(node.chars, _currentText.get.asJava)
      _currentText = Nullable.empty

      // see if need to trim some off the end
      var pos = prevText.length()
      var skipped: Nullable[BasedSequence] = Nullable.empty
      while (pos > 0 && factory.skipPrev(prevText.charAt(pos - 1))) pos -= 1
      if (pos < prevText.length()) {
        skipped = Nullable(prevText.subSequence(pos))
      }
      val trimmedPrevText = prevText.subSequence(0, pos)

      _block.appendChild(Text(trimmedPrevText))
      skipped.foreach { s =>
        if (factory.wantSkippedWhitespace) {
          _block.appendChild(WhiteSpace(s))
        }
      }
    }

    appendNode(node)
    if (customSpecialCharacterNodes.isEmpty) customSpecialCharacterNodes = Nullable(ArrayBuffer.empty)
    customSpecialCharacterNodes.get.addOne(node)

    val pos = _index + 1
    _index += 1
    var ch = peek()
    while (ch != SequenceUtils.NUL && factory.skipNext(ch)) {
      _index += 1
      ch = peek()
    }

    if (pos < _index && factory.wantSkippedWhitespace) {
      _block.appendChild(WhiteSpace(_input.subSequence(pos, _index)))
    }

    true
  }

  /** Parse a newline. If it was preceded by two spaces, append a hard line break; otherwise a soft line break.
    *
    * @return
    *   true
    */
  override def parseNewline(): Boolean = {
    val crLf      = _index < _input.length() - 1 && _input.charAt(_index + 1) == '\n'
    val crLfDelta = if (crLf) 1 else 0
    _index += 1 + crLfDelta

    // We're gonna add a new node in any case and we need to check the last text node, so flush outstanding text.
    flushTextNode()

    val lastChild = _block.lastChild
    // Check previous text for trailing spaces.
    // The "endsWith" is an optimization to avoid an RE match in the common case.
    if (lastChild.isDefined && lastChild.get.isInstanceOf[Text] && lastChild.get.chars.endsWith(" ")) {
      val text    = lastChild.get.asInstanceOf[Text]
      val literal = text.chars
      val matcher = myParsing.FINAL_SPACE.matcher(literal)
      val spaces  = if (matcher.find()) matcher.end() - matcher.start() else 0
      if (spaces >= 2) {
        appendNode(
          HardLineBreak(
            _input.subSequence(
              _index - (if (_options.hardLineBreakLimit) 3 + crLfDelta else spaces + 1 + crLfDelta),
              _index
            )
          )
        )
      } else {
        appendNode(SoftLineBreak(_input.subSequence(_index - 1 - crLfDelta, _index)))
      }
      if (spaces > 0) {
        if (literal.length() > spaces) {
          lastChild.get.chars = literal.subSequence(0, literal.length() - spaces).trimEnd()
        } else {
          lastChild.get.unlink()
        }
      }
    } else {
      appendNode(SoftLineBreak(_input.subSequence(_index - 1 - crLfDelta, _index)))
    }

    // gobble leading spaces in next line
    while (peek() == ' ') {
      _index += 1
    }
    true
  }

  /** Parse a backslash-escaped special character, adding either the escaped character, a hard line break (if the backslash is followed by a newline), or a literal backslash to the block's children.
    *
    * @return
    *   true
    */
  protected def parseBackslash(): Boolean = {
    _index += 1
    if (peek() == '\n' || peek() == '\r') {
      val charsMatched = if (peek(1) == '\n') 2 else 1
      appendNode(HardLineBreak(_input.subSequence(_index - 1, _index + charsMatched)))
      _index += charsMatched
    } else if (_index < _input.length() && myParsing.ESCAPABLE.matcher(_input.subSequence(_index, _index + 1)).matches()) {
      appendText(_input, _index - 1, _index + 1)
      _index += 1
    } else {
      appendText(_input.subSequence(_index - 1, _index))
    }
    true
  }

  /** Attempt to parse backticks, adding either a backtick code span or a literal sequence of backticks.
    *
    * @return
    *   true if matched backticks, false otherwise
    */
  protected def parseBackticks(): Boolean = boundary {
    val ticks = `match`(myParsing.TICKS_HERE)
    if (ticks.isEmpty) {
      break(false)
    }
    val ticksVal       = ticks.get
    val afterOpenTicks = _index
    var matched        = `match`(myParsing.TICKS)
    while (matched.isDefined) {
      if (matched.get == ticksVal) {
        val ticksLength = ticksVal.length()
        val codeText    = _input.subSequence(afterOpenTicks, _index - ticksLength)
        val node = Code(
          _input.subSequence(afterOpenTicks - ticksLength, afterOpenTicks),
          codeText,
          _input.subSequence(_index - ticksLength, _index)
        )

        if (_options.codeSoftLineBreaks) {
          // add softbreaks to code ast
          val length  = codeText.length()
          var lastPos = 0
          boundary {
            while (lastPos < length) {
              val softBreak = codeText.indexOfAny(CharPredicate.ANY_EOL, lastPos)
              val pos       = if (softBreak == -1) length else softBreak

              val textNode = Text(codeText.subSequence(lastPos, pos))
              node.appendChild(textNode)

              lastPos = pos
              if (lastPos >= length) break(())
              if (codeText.charAt(lastPos) == '\r') {
                lastPos += 1
                if (lastPos >= length) break(())
                if (codeText.charAt(lastPos) == '\n') lastPos += 1
              } else {
                lastPos += 1
              }

              if (lastPos >= length) break(())

              if (pos < lastPos) {
                val softLineBreak = SoftLineBreak(codeText.subSequence(softBreak, lastPos))
                node.appendChild(softLineBreak)
              }
            }
          }
        } else {
          val textNode = Text(codeText)
          node.appendChild(textNode)
        }

        appendNode(node)
        break(true)
      }
      matched = `match`(myParsing.TICKS)
    }

    // If we got here, we didn't match a closing backtick sequence.
    _index = afterOpenTicks
    appendText(ticksVal)
    true
  }

  private[internal] final class DelimiterData(val count: Int, val canOpen: Boolean, val canClose: Boolean)

  /** Attempt to parse delimiters like emphasis, strong emphasis or custom delimiters.
    *
    * @param delimiterProcessor
    *   delimiter processor instance
    * @param delimiterChar
    *   delimiter character being processed
    * @return
    *   true if processed characters false otherwise
    */
  protected def parseDelimiters(delimiterProcessor: DelimiterProcessor, delimiterChar: Char): Boolean = {
    val res = scanDelimiters(delimiterProcessor, delimiterChar)
    if (res.isEmpty) {
      false
    } else {
      val data       = res.get
      val numDelims  = data.count
      val startIndex = _index

      _index += numDelims
      val node = appendSeparateText(_input.subSequence(startIndex, _index))

      // Add entry to stack for this opener
      this._lastDelimiter = Nullable(
        Delimiter(node, _input, delimiterChar, data.canOpen, data.canClose, this._lastDelimiter, startIndex)
      )
      this._lastDelimiter.get.numDelims = numDelims
      this._lastDelimiter.get.previousNullable.foreach { prev =>
        prev.next = this._lastDelimiter
      }
      true
    }
  }

  /** Add open bracket to delimiter stack and add a text node to block's children.
    *
    * @return
    *   true
    */
  protected def parseOpenBracket(): Boolean = {
    val startIndex = _index
    _index += 1

    val node = appendSeparateText(_input.subSequence(_index - 1, _index))

    // Add entry to stack for this opener
    addBracket(Bracket.link(_input, node, startIndex, _lastBracket, _lastDelimiter))
    true
  }

  /** If next character is [, and ! delimiter to delimiter stack and add a text node to block's children. Otherwise just add a text node.
    *
    * @return
    *   true if processed characters false otherwise
    */
  protected def parseBang(): Boolean = {
    val startIndex = _index
    _index += 1
    if (peek() == '[') {
      _index += 1

      val node = appendSeparateText(_input.subSequence(_index - 2, _index))

      // Add entry to stack for this opener
      addBracket(Bracket.image(_input, node, startIndex + 1, _lastBracket, _lastDelimiter))
    } else {
      appendText(_input.subSequence(_index - 1, _index))
    }
    true
  }

  private def addBracket(bracket: Bracket): Unit = {
    _lastBracket.foreach { lb =>
      lb.bracketAfter = true
    }
    _lastBracket = Nullable(bracket)
  }

  private def removeLastBracket(): Unit = {
    _lastBracket = _lastBracket.flatMap(_.previous)
  }

  private final class ReferenceProcessorMatch(
    val processor:       LinkRefProcessor,
    val wantExclamation: Boolean,
    val nodeChars:       BasedSequence
  )

  private def matchLinkRef(opener: Bracket, startIndex: Int, lookAhead: Int, nesting: Int): Nullable[ReferenceProcessorMatch] = boundary {
    if (linkRefProcessorsData.nestingIndex.isEmpty) break(Nullable.empty)

    val nestingIdx = lookAhead + nesting
    if (nestingIdx >= linkRefProcessorsData.nestingIndex.length) break(Nullable.empty)

    val iMax      = linkRefProcessorsData.processors.size
    val startProc = linkRefProcessorsData.nestingIndex(nestingIdx)

    var textNoBang:   Nullable[BasedSequence] = Nullable.empty
    var textWithBang: Nullable[BasedSequence] = Nullable.empty

    var i = startProc
    while (i < iMax) {
      val linkProcessor = linkRefProcessors.get(i)

      if (lookAhead + nesting < linkProcessor.bracketNestingLevel) {
        break(Nullable.empty)
      }

      val wantBang = linkProcessor.wantExclamationPrefix

      // preview the link ref
      val nodeChars: BasedSequence =
        if (opener.image && wantBang) {
          // this one has index off by one for the leading !
          if (textWithBang.isEmpty) textWithBang = Nullable(_input.subSequence(opener.startIndex - 1 - lookAhead, startIndex + lookAhead))
          textWithBang.get
        } else if (wantBang && opener.startIndex >= lookAhead + 1 && _input.charAt(opener.startIndex - 1 - lookAhead) == '!') {
          if (textWithBang.isEmpty) textWithBang = Nullable(_input.subSequence(opener.startIndex - 1 - lookAhead, startIndex + lookAhead))
          textWithBang.get
        } else {
          if (textNoBang.isEmpty) textNoBang = Nullable(_input.subSequence(opener.startIndex - lookAhead, startIndex + lookAhead))
          textNoBang.get
        }

      if (linkProcessor.isMatch(nodeChars)) {
        break(Nullable(ReferenceProcessorMatch(linkProcessor, wantBang, nodeChars)))
      }

      i += 1
    }
    Nullable.empty
  }

  /** Try to match close bracket against an opening in the delimiter stack. Add either a link or image, or a plain [ character, to block's children. If there is a matching delimiter, removeIndex it
    * from the delimiter stack.
    *
    * Also handles custom link ref processing
    *
    * @return
    *   true
    */
  protected def parseCloseBracket(): Boolean = boundary {
    _index += 1
    val startIndex = _index

    // look through stack of delimiters for a [ or ![
    if (_lastBracket.isEmpty) {
      // No matching opener, just return a literal.
      appendText(_input.subSequence(_index - 1, _index))
      break(true)
    }

    var opener = _lastBracket.get

    if (!opener.allowed) {
      // Matching opener but it's not allowed, just return a literal.
      appendText(_input.subSequence(_index - 1, _index))
      removeLastBracket()
      break(true)
    }

    val nestedBrackets = 0

    // Check to see if we have a link/image
    var dest:                    Nullable[BasedSequence]       = Nullable.empty
    var title:                   Nullable[BasedSequence]       = Nullable.empty
    var ref:                     Nullable[BasedSequence]       = Nullable.empty
    var isLinkOrImage                                          = false
    var refIsBare                                              = false
    var linkRefProcessorMatch:   Nullable[ReferenceProcessorMatch] = Nullable.empty
    var refIsDefined                                           = false
    var linkOpener:              BasedSequence                  = BasedSequence.NULL
    var linkCloser:              BasedSequence                  = BasedSequence.NULL
    var bareRef:                 BasedSequence                  = BasedSequence.NULL
    var imageUrlContent:         Nullable[BasedSequence]        = Nullable.empty

    // Inline link?
    val preSpaceIndex = _index

    // May need to skip spaces
    if (_options.spaceInLinkElements && peek() == ' ') {
      sp()
    }

    if (peek() == '(') {
      val savedIndex = _index

      linkOpener = _input.subSequence(_index, _index + 1)
      _index += 1
      spnl()
      dest = parseLinkDestination()
      if (dest.isDefined) {
        if (_options.parseMultiLineImageUrls && opener.image && !dest.get.startsWith("<") && dest.get.endsWith("?") && spnlUrl()) {
          // possible multi-line image url
          val contentStart = _index
          var contentEnd   = contentStart

          boundary {
            while (true) {
              sp()
              val multiLineTitle = parseLinkTitle()
              if (multiLineTitle.isDefined) sp()

              if (peek() == ')') {
                linkCloser = _input.subSequence(_index, _index + 1)
                _index += 1
                imageUrlContent = Nullable(_input.subSequence(contentStart, contentEnd))
                title = multiLineTitle
                isLinkOrImage = true
                break(())
              }

              val restOfLine = toEOL()
              if (restOfLine.isEmpty) break(())
              contentEnd = _index
            }
          }
        } else {
          spnl()
          // title needs a whitespace before
          if (myParsing.WHITESPACE.matcher(_input.subSequence(_index - 1, _index)).matches()) {
            title = parseLinkTitle()
            spnl()
          }

          // test for spaces in url making it invalid, otherwise anything else goes
          if (peek() == ')') {
            linkCloser = _input.subSequence(_index, _index + 1)
            _index += 1
            isLinkOrImage = true
          } else {
            // back out, no match
            _index = savedIndex
          }
        }
      } else {
        _index = savedIndex
      }
    } else {
      _index = preSpaceIndex
    }

    if (!isLinkOrImage) {
      // maybe reference link, need to see if it matches a custom processor or need to skip this reference because it will be processed on the next char
      // as something else, like a wiki link
      if (!_options.matchLookaheadFirst) {
        linkRefProcessorMatch = matchLinkRef(opener, startIndex, 0, nestedBrackets)
      }

      if (linkRefProcessorMatch.isDefined) {
        // have a match, then no look ahead for next matches
      } else {
        // need to figure out max nesting we should test based on what is max processor desire and max available
        // nested inner ones are always only []
        val maxWanted = linkRefProcessorsData.maxNesting
        var maxAvail  = 0

        if (maxWanted > nestedBrackets) {
          // need to see what is available
          var nested = opener
          while (nested.previous.isDefined && nested.startIndex == nested.previous.get.startIndex + 1 && peek(maxAvail) == ']') {
            nested = nested.previous.get
            maxAvail += 1
            if (maxAvail + nestedBrackets == maxWanted || nested.image) {
              // use boundary-style: done searching
              nested = opener // forces while to exit
            }
          }
        }

        var nesting = maxAvail + 1
        boundary {
          while (nesting > 0) {
            nesting -= 1
            linkRefProcessorMatch = matchLinkRef(opener, startIndex, nesting, nestedBrackets)

            if (linkRefProcessorMatch.isDefined) {
              if (nesting > 0) {
                var n = nesting
                while (n > 0) {
                  n -= 1
                  _index += 1
                  _lastBracket.get.node.unlink()
                  removeLastBracket()
                }
                opener = _lastBracket.get
              }
              break(())
            }
          }
        }
        // update opener to current _lastBracket if we consumed nesting
        // (only relevant if linkRefProcessorMatch found after nesting)
      }

      if (linkRefProcessorMatch.isEmpty) {
        // See if there's a link label
        val beforeLabel = _index
        val labelLength = parseLinkLabel()
        if (labelLength > 2) {
          ref = Nullable(_input.subSequence(beforeLabel, beforeLabel + labelLength))
        } else if (!opener.bracketAfter) {
          // Empty or missing second label can only be a reference if there's no unescaped bracket in it.
          bareRef = _input.subSequence(beforeLabel, beforeLabel + labelLength)
          ref =
            if (opener.image) {
              // this one has index off by one for the leading !
              Nullable(_input.subSequence(opener.startIndex - 1, startIndex))
            } else {
              Nullable(_input.subSequence(opener.startIndex, startIndex))
            }
          refIsBare = true
        }

        if (ref.isDefined) {
          val normalizedLabel = Escaping.normalizeReferenceChars(ref.get, true)
          if (referenceRepository.containsKey(normalizedLabel)) {
            val sequence     = _input.subSequence(opener.startIndex, startIndex)
            val containsLinks = InlineParserImpl.containsLinkRefs(if (refIsBare) ref.get else sequence, opener.node.next, Nullable(false))
            isLinkOrImage = !containsLinks
            refIsDefined = true
          } else {
            // need to test if we are cutting in the middle of some other delimiters matching, if we are not then we will make this into a tentative
            if (!opener.isStraddling(ref.get)) {
              // link ref, otherwise we will break
              // it is the innermost ref and is bare, if not bare then we treat it as a ref

              if (!refIsBare && peek() == '[') {
                val nextLength = parseLinkLabel()
                if (nextLength > 0) {
                  // not bare and not defined and followed by another [], roll back to before the label and make it just text
                  _index = beforeLabel
                } else {
                  // undefined ref, create a tentative one but only if does not contain any other link refs
                  val containsLinks = InlineParserImpl.containsLinkRefs(ref.get, opener.node.next, Nullable.empty)
                  if (!containsLinks) {
                    refIsBare = true
                    isLinkOrImage = true
                  }
                }
              } else {
                // undefined ref, bare or followed by empty [], create a tentative link ref but only if does not contain any other link refs
                val containsLinks = InlineParserImpl.containsLinkRefs(ref.get, opener.node.next, Nullable.empty)
                if (!containsLinks) {
                  isLinkOrImage = true
                }
              }
            }
          }
        }
      }
    }

    if (isLinkOrImage || linkRefProcessorMatch.isDefined) {
      // If we got here, open is a potential opener
      // Flush text now. We don't need to worry about combining it with adjacent text nodes, as we'll wrap it in a
      // link or image node.
      flushTextNode()

      val isImage = opener.image

      val insertNode: Node =
        if (linkRefProcessorMatch.isDefined) {
          val lrpm = linkRefProcessorMatch.get
          if (!lrpm.wantExclamation && isImage) {
            appendText(_input.subSequence(opener.startIndex - 1, opener.startIndex))
            opener.node.chars = opener.node.chars.subSequence(1)
          }
          lrpm.processor.createNode(lrpm.nodeChars)
        } else if (ref.isDefined) {
          if (isImage) ImageRef() else LinkRef()
        } else {
          if (isImage) Image() else Link()
        }

      {
        var node = opener.node.next
        while (node.isDefined) {
          val nextN = node.get.next
          insertNode.appendChild(node.get)
          node = nextN
        }
      }

      if (linkRefProcessorMatch.isDefined) {
        val lrpm = linkRefProcessorMatch.get
        // may need to adjust children's text because some characters were part of the processor's opener/closer
        if (insertNode.hasChildren) {
          val original = insertNode.childChars
          val text     = lrpm.processor.adjustInlineText(_document, insertNode)

          // may need to remove some delimiters if they span across original and changed text boundary or if now they are outside text boundary
          var delimiter = _lastDelimiter
          while (delimiter.isDefined) {
            val prevDelimiter = delimiter.get.previousNullable

            val delimiterChars = delimiter.get.input.subSequence(delimiter.get.startIndex, delimiter.get.endIndex)
            if (original.containsAllOf(delimiterChars)) {
              if (!text.containsAllOf(delimiterChars) || !lrpm.processor.allowDelimiters(delimiterChars, _document, insertNode)) {
                // remove it
                removeDelimiterKeepNode(delimiter.get)
              }
            }

            delimiter = prevDelimiter
          }

          if (!text.containsAllOf(original)) {
            // now need to truncate child text
            val iter = insertNode.children.iterator()
            while (iter.hasNext) {
              val childNode     = iter.next()
              val childNodeChars = childNode.chars
              if (text.containsSomeOf(childNodeChars)) {
                if (!text.containsAllOf(childNodeChars)) {
                  // truncate the contents to intersection of node's chars and adjusted chars
                  val truncatedChars = text.intersect(childNodeChars)
                  childNode.chars = truncatedChars
                }
              } else {
                // remove the node
                childNode.unlink()
              }
            }
          }
        }
      }

      appendNode(insertNode)

      if (insertNode.isInstanceOf[RefNode]) {
        // set up the parts
        val refNode = insertNode.asInstanceOf[RefNode]
        refNode.setReferenceChars(ref.get)
        if (refIsDefined) refNode.isDefined = true

        if (!refIsBare) {
          refNode.setTextChars(_input.subSequence(if (isImage) opener.startIndex - 1 else opener.startIndex, startIndex))
        } else if (!bareRef.isEmpty()) {
          refNode.textOpeningMarker = bareRef.subSequence(0, 1)
          refNode.textClosingMarker = bareRef.endSequence(1)
        }
        insertNode.setCharsFromContent()
      } else if (insertNode.isInstanceOf[InlineLinkNode]) {
        // set url and title
        val inlineLinkNode = insertNode.asInstanceOf[InlineLinkNode]
        inlineLinkNode.setUrlChars(dest.getOrElse(BasedSequence.NULL))
        inlineLinkNode.setTitleChars(title.getOrElse(BasedSequence.NULL))
        inlineLinkNode.linkOpeningMarker = linkOpener
        inlineLinkNode.linkClosingMarker = linkCloser
        inlineLinkNode.setTextChars(
          if (isImage) _input.subSequence(opener.startIndex - 1, startIndex)
          else _input.subSequence(opener.startIndex, startIndex)
        )

        imageUrlContent.foreach { content =>
          insertNode.asInstanceOf[Image].urlContent = content
        }

        insertNode.setCharsFromContent()
      }

      // Process delimiters such as emphasis inside link/image
      processDelimiters(opener.previousDelimiter)
      val toRemove = opener.node
      removeLastBracket()

      linkRefProcessorMatch.foreach { lrpm =>
        lrpm.processor.updateNodeElements(_document, insertNode)
      }

      // Links within links are not allowed. We found this link, so there can be no other link around it.
      if (insertNode.isInstanceOf[Link]) {
        var bracket = _lastBracket
        while (bracket.isDefined) {
          if (!bracket.get.image) {
            // Disallow link opener. It will still get matched, but will not result in a link.
            bracket.get.allowed = false
          }
          bracket = bracket.get.previous
        }

        if (_options.linkTextPriorityOverLinkRef || !InlineParserImpl.containsLinkRefs(insertNode, Nullable(false))) {
          // collapse any link refs contained in this link, they are duds, link takes precedence
          InlineParserImpl.collapseLinkRefChildren(
            insertNode,
            child => child.isInstanceOf[LinkRendered] || child.isTentative,
            true
          )
          toRemove.unlink()
        } else {
          // if contains link ref then treat this as plain text
          // leave opener, add children as parent nodes and append closer
          insertNode.unlink()
          _block.takeChildren(insertNode)
          appendText(
            insertNode.baseSubSequence(
              insertNode.asInstanceOf[Link].textClosingMarker.startOffset,
              insertNode.endOffset
            )
          )
        }
      } else if (insertNode.isInstanceOf[RefNode]) {
        // have a link ref, collapse to text any tentative ones contained in it, they are duds
        InlineParserImpl.collapseLinkRefChildren(
          insertNode,
          child => child.isInstanceOf[LinkRendered] || child.isTentative,
          true
        )
        toRemove.unlink()
      } else {
        toRemove.unlink()
      }

      true
    } else { // no link or image
      _index = startIndex
      appendText(_input.subSequence(_index - 1, _index))
      removeLastBracket()
      true
    }
  }

  /** Attempt to parse link destination,
    *
    * @return
    *   the string or Nullable.empty if no match.
    */
  override def parseLinkDestination(): Nullable[BasedSequence] = {
    val res = `match`(myParsing.LINK_DESTINATION_ANGLES)
    if (res.isDefined) {
      res
    } else if (linkDestinationParser.isDefined) {
      val parser  = linkDestinationParser.get
      val matched = parser.parseLinkDestination(_input, _index)
      _index += matched.length()
      Nullable(matched)
    } else {
      val spaceInUrls = _options.spaceInLinkUrls
      if (_options.linksAllowMatchedParentheses) {
        // allow matched parenthesis
        // fix for issue of stack overflow when parsing long input lines, by implementing non-recursive scan
        val matched = `match`(myParsing.LINK_DESTINATION_MATCHED_PARENS)
        if (matched.isEmpty) {
          Nullable.empty
        } else {
          val m         = matched.get
          var openCount = 0
          val iMax      = m.length()
          var truncated: Nullable[BasedSequence] = Nullable.empty
          boundary {
            var i = 0
            while (i < iMax) {
              val c = m.charAt(i)
              if (c == '\\') {
                if (i + 1 < iMax && myParsing.ESCAPABLE.matcher(m.subSequence(i + 1, i + 2)).matches()) {
                  // escape
                  i += 1
                }
              } else if (c == '(') {
                openCount += 1
              } else if (c == ')') {
                if (openCount == 0) {
                  // truncate to this and leave ')' to be parsed
                  _index -= iMax - i
                  truncated = Nullable(if (spaceInUrls) m.subSequence(0, i).trimEnd(CharPredicate.SPACE) else m.subSequence(0, i))
                  break(())
                }
                openCount -= 1
              }
              i += 1
            }
          }
          if (truncated.isDefined) truncated
          else Nullable(if (spaceInUrls) m.trimEnd(CharPredicate.SPACE) else m)
        }
      } else {
        // spec 0.27 compatibility
        val matched = `match`(myParsing.LINK_DESTINATION)
        matched.map { m =>
          if (spaceInUrls) m.trimEnd(CharPredicate.SPACE) else m
        }
      }
    }
  }

  /** Attempt to parse link title (sans quotes),
    *
    * @return
    *   the string or Nullable.empty if no match.
    */
  override def parseLinkTitle(): Nullable[BasedSequence] =
    `match`(myParsing.LINK_TITLE)

  /** Attempt to parse a link label
    *
    * @return
    *   number of characters parsed.
    */
  override def parseLinkLabel(): Int = {
    val m = `match`(myParsing.LINK_LABEL)
    if (m.isEmpty) 0 else m.get.length()
  }

  /** Attempt to parse an autolink (URL or email in pointy brackets).
    *
    * @return
    *   true if processed characters false otherwise
    */
  override def parseAutolink(): Boolean = {
    var m = `match`(myParsing.EMAIL_AUTOLINK)
    if (m.isDefined) {
      val mVal = m.get
      val node = MailLink(mVal.subSequence(0, 1), mVal.subSequence(1, mVal.length() - 1), mVal.subSequence(mVal.length() - 1, mVal.length()))
      appendNode(node)
      true
    } else {
      m = `match`(myParsing.AUTOLINK)
      if (m.isDefined) {
        val mVal = m.get
        val node = AutoLink(mVal.subSequence(0, 1), mVal.subSequence(1, mVal.length() - 1), mVal.subSequence(mVal.length() - 1, mVal.length()))
        appendNode(node)
        true
      } else if (_options.wwwAutoLinkElement) {
        m = `match`(myParsing.WWW_AUTOLINK)
        if (m.isDefined) {
          val mVal = m.get
          val node = AutoLink(mVal.subSequence(0, 1), mVal.subSequence(1, mVal.length() - 1), mVal.subSequence(mVal.length() - 1, mVal.length()))
          appendNode(node)
          true
        } else {
          false
        }
      } else {
        false
      }
    }
  }

  /** Attempt to parse inline HTML.
    *
    * @return
    *   true if processed characters false otherwise
    */
  override def parseHtmlInline(): Boolean = {
    val m = `match`(myParsing.HTML_TAG)
    if (m.isDefined) {
      val mVal = m.get
      // separate HTML comment from herd
      val node: HtmlInlineBase =
        if (mVal.startsWith("<!--") && mVal.endsWith("-->")) {
          HtmlInlineComment(mVal)
        } else {
          HtmlInline(mVal)
        }
      appendNode(node)
      true
    } else {
      false
    }
  }

  /** Attempt to parse an entity, return Entity object if successful.
    *
    * @return
    *   true if processed characters false otherwise
    */
  override def parseEntity(): Boolean = {
    val m = `match`(myParsing.ENTITY_HERE)
    if (m.isDefined) {
      val node = HtmlEntity(m.get)
      appendNode(node)
      true
    } else {
      false
    }
  }

  /** Parse a run of ordinary characters, or a single character with a special meaning in markdown, as a plain string.
    *
    * @return
    *   true if processed characters false otherwise
    */
  protected def parseString(): Boolean = {
    val begin  = _index
    val length = _input.length()
    boundary {
      while (_index != length) {
        if (specialCharacters.get(_input.charAt(_index))) {
          break(())
        }
        _index += 1
      }
    }
    if (begin != _index) {
      appendText(_input, begin, _index)
      true
    } else {
      false
    }
  }

  /** Scan a sequence of characters with code delimiterChar, and return information about the number of delimiters and whether they are positioned such that they can open and/or close emphasis or
    * strong emphasis.
    *
    * @param delimiterProcessor
    *   delimiter processor instance
    * @param delimiterChar
    *   delimiter character being scanned
    * @return
    *   information about delimiter run, or Nullable.empty
    */
  protected def scanDelimiters(delimiterProcessor: DelimiterProcessor, delimiterChar: Char): Nullable[DelimiterData] = {
    val startIndex = _index

    var delimiterCount = 0
    while (peek() == delimiterChar) {
      delimiterCount += 1
      _index += 1
    }

    if (delimiterCount < delimiterProcessor.minLength) {
      _index = startIndex
      Nullable.empty
    } else {
      val before = if (startIndex == 0) SequenceUtils.EOL else String.valueOf(_input.charAt(startIndex - 1))

      val charAfter = peek()
      val after     = if (charAfter == SequenceUtils.NUL) SequenceUtils.EOL else String.valueOf(charAfter)

      // We could be more lazy here, in most cases we don't need to do every match case.
      val beforeIsWhitespace = myParsing.UNICODE_WHITESPACE_CHAR.matcher(before).matches()
      val afterIsWhitespace  = myParsing.UNICODE_WHITESPACE_CHAR.matcher(after).matches()

      val beforeIsPunctuation: Boolean =
        if (_options.inlineDelimiterDirectionalPunctuations) myParsing.PUNCTUATION_OPEN.matcher(before).matches()
        else myParsing.PUNCTUATION.matcher(before).matches()

      val afterIsPunctuation: Boolean =
        if (_options.inlineDelimiterDirectionalPunctuations) myParsing.PUNCTUATION_CLOSE.matcher(after).matches()
        else myParsing.PUNCTUATION.matcher(after).matches()

      val leftFlanking =
        if (_options.inlineDelimiterDirectionalPunctuations) {
          !afterIsWhitespace && (!afterIsPunctuation || beforeIsWhitespace || beforeIsPunctuation)
        } else {
          !afterIsWhitespace && !(afterIsPunctuation && !beforeIsWhitespace && !beforeIsPunctuation)
        }

      val rightFlanking =
        if (_options.inlineDelimiterDirectionalPunctuations) {
          !beforeIsWhitespace && (!beforeIsPunctuation || afterIsWhitespace || afterIsPunctuation)
        } else {
          !beforeIsWhitespace && !(beforeIsPunctuation && !afterIsWhitespace && !afterIsPunctuation)
        }

      val canOpen  = delimiterChar == delimiterProcessor.openingCharacter && delimiterProcessor.canBeOpener(before, after, leftFlanking, rightFlanking, beforeIsPunctuation, afterIsPunctuation, beforeIsWhitespace, afterIsWhitespace)
      val canClose = delimiterChar == delimiterProcessor.closingCharacter && delimiterProcessor.canBeCloser(before, after, leftFlanking, rightFlanking, beforeIsPunctuation, afterIsPunctuation, beforeIsWhitespace, afterIsWhitespace)

      _index = startIndex

      if (canOpen || canClose || !delimiterProcessor.skipNonOpenerCloser) {
        Nullable(DelimiterData(delimiterCount, canOpen, canClose))
      } else {
        Nullable.empty
      }
    }
  }

  override def processDelimiters(stackBottom: Nullable[Delimiter]): Unit = {
    val openersBottom = mutable.HashMap[Char, Delimiter]()

    // find first closer above stackBottom:
    var closer = _lastDelimiter
    while (closer.isDefined && closer.get.previousNullable != stackBottom) {
      closer = closer.get.previousNullable
    }

    // move forward, looking for closers, and handling each
    while (closer.isDefined) {
      val delimiterChar = closer.get.delimiterChar

      val delimiterProcessor = delimiterProcessors.get(delimiterChar)
      if (!closer.get.canClose || delimiterProcessor.isEmpty) {
        closer = closer.get.nextNullable
      } else {
        val dp = delimiterProcessor.get
        val openingDelimiterChar = dp.openingCharacter

        // found delimiter closer. now look back for first matching opener:
        var useDelims            = 0
        var openerFound          = false
        var potentialOpenerFound = false
        var opener               = closer.get.previousNullable
        boundary {
          while (opener.isDefined && opener != stackBottom && !openersBottom.get(delimiterChar).contains(opener.get)) {
            if (opener.get.canOpen && opener.get.delimiterChar == openingDelimiterChar) {
              potentialOpenerFound = true
              useDelims = dp.getDelimiterUse(opener.get, closer.get)

              if (useDelims > 0) {
                openerFound = true
                break(())
              }
            }
            opener = opener.get.previousNullable
          }
        }

        if (!openerFound) {
          if (!potentialOpenerFound) {
            // Set lower bound for future searches for openers.
            closer.get.previousNullable.foreach { prev =>
              openersBottom.put(delimiterChar, prev)
            }
            if (!closer.get.canOpen) {
              // We can remove a closer that can't be an opener,
              // once we've seen there's no matching opener:
              removeDelimiterKeepNode(closer.get)
            }
          }
          closer = closer.get.nextNullable
        } else {
          val openerVal = opener.get

          // Remove number of used delimiters from stack and inline nodes.
          openerVal.numDelims = openerVal.numDelims - useDelims
          closer.get.numDelims = closer.get.numDelims - useDelims

          removeDelimitersBetween(openerVal, closer.get)

          openerVal.numDelims = openerVal.numDelims + useDelims
          closer.get.numDelims = closer.get.numDelims + useDelims

          dp.process(openerVal, closer.get, useDelims)

          openerVal.numDelims = openerVal.numDelims - useDelims
          closer.get.numDelims = closer.get.numDelims - useDelims

          // No delimiter characters left to process, so we can remove delimiter and the now empty node.
          if (openerVal.numDelims == 0) {
            removeDelimiterAndNode(openerVal)
          } else {
            // adjust number of characters in the node by keeping outer of numDelims
            openerVal.node.chars = openerVal.node.chars.subSequence(0, openerVal.numDelims)
          }

          if (closer.get.numDelims == 0) {
            val next = closer.get.nextNullable
            removeDelimiterAndNode(closer.get)
            closer = next
          } else {
            // adjust number of characters in the node by keeping outer of numDelims
            val closerChars  = closer.get.node.chars
            val closerLength = closerChars.length()
            closer.get.node.chars = closerChars.subSequence(closerLength - closer.get.numDelims, closerLength)
            closer.get.setIndex(closer.get.getIndex + useDelims)
          }
        }
      }
    }

    // removeIndex all delimiters
    while (_lastDelimiter.isDefined && _lastDelimiter != stackBottom) {
      removeDelimiterKeepNode(_lastDelimiter.get)
    }
  }

  override def removeDelimitersBetween(opener: Delimiter, closer: Delimiter): Unit = {
    var delim = closer.previousNullable
    while (delim.isDefined && !delim.contains(opener)) {
      val previous = delim.get.previousNullable
      removeDelimiterKeepNode(delim.get)
      delim = previous
    }
  }

  override def removeDelimiterAndNode(delim: Delimiter): Unit = {
    val previousText = delim.previousNonDelimiterTextNode
    val nextText     = delim.nextNonDelimiterTextNode
    if (previousText.isDefined && nextText.isDefined) {
      // Merge adjacent text nodes
      previousText.get.chars = _input.baseSubSequence(previousText.get.startOffset, nextText.get.endOffset)
      nextText.get.unlink()
    }

    delim.node.unlink()
    removeDelimiter(delim)
  }

  override def removeDelimiterKeepNode(delim: Delimiter): Unit = {
    var node: Nullable[Node] = Nullable.empty
    val delimiterProcessor   = delimiterProcessors.get(delim.delimiterChar)
    delimiterProcessor.foreach { dp =>
      node = dp.unmatchedDelimiterNode(this, delim)
    }

    if (node.isDefined) {
      if (node.get ne delim.node) {
        // replace node
        delim.node.insertAfter(node.get)
        delim.node.unlink()
      }
    } else {
      node = Nullable(delim.node)
    }

    val previousText = delim.previousNonDelimiterTextNode
    val nextText     = delim.nextNonDelimiterTextNode
    if (node.get.isInstanceOf[Text] && (previousText.isDefined || nextText.isDefined)) {
      // Merge adjacent text nodes into one
      if (nextText.isDefined && previousText.isDefined) {
        node.get.chars = _input.baseSubSequence(previousText.get.startOffset, nextText.get.endOffset)
        previousText.get.unlink()
        nextText.get.unlink()
      } else if (previousText.isDefined) {
        node.get.chars = _input.baseSubSequence(previousText.get.startOffset, node.get.endOffset)
        previousText.get.unlink()
      } else {
        node.get.chars = _input.baseSubSequence(node.get.startOffset, nextText.get.endOffset)
        nextText.get.unlink()
      }
    }

    removeDelimiter(delim)
  }

  override def removeDelimiter(delim: Delimiter): Unit = {
    delim.previousNullable.foreach { prev =>
      prev.next = delim.nextNullable
    }
    if (delim.nextNullable.isEmpty) {
      // top of stack
      this._lastDelimiter = delim.previousNullable
    } else {
      delim.nextNullable.foreach { next =>
        next.previous = delim.previousNullable
      }
    }
  }
}

object InlineParserImpl {

  protected def containsLinkRefs(node: Node, isTentative: Nullable[Boolean]): Boolean = boundary {
    var next = node.firstChild
    while (next.isDefined) {
      next.get match {
        case lr: LinkRendered if isTentative.isEmpty || lr.isTentative == isTentative.get =>
          break(true)
        case _ => ()
      }
      next = next.get.next
    }
    false
  }

  protected def containsLinkRefs(nodeChars: BasedSequence, nextNode: Nullable[Node], isTentative: Nullable[Boolean]): Boolean = boundary {
    val startOffset = nodeChars.startOffset
    val endOffset   = nodeChars.endOffset
    var next        = nextNode
    while (next.isDefined) {
      next.get match {
        case lrd: LinkRefDerived if (isTentative.isEmpty || lrd.isTentative == isTentative.get) && !(next.get.startOffset >= endOffset || next.get.endOffset <= startOffset) =>
          break(true)
        case _ => ()
      }
      next = next.get.next
    }
    false
  }

  protected def collapseLinkRefChildren(node: Node, isTentative: LinkRefDerived => Boolean, trimFirstLastChild: Boolean): Unit = {
    var child       = node.firstChild
    var hadCollapse = false
    while (child.isDefined) {
      val nextChild = child.get.next
      child.get match {
        case lrd: LinkRefDerived if isTentative(lrd) =>
          // need to collapse this one, moving its text contents to text
          collapseLinkRefChildren(child.get, isTentative, false)
          child.get.unlink()

          val list = TextNodeConverter(child.get.chars)
          list.addChildrenOf(child.get)
          if (nextChild.isDefined) {
            list.insertMergedBefore(nextChild.get)
          } else {
            list.appendMergedTo(node)
          }
          hadCollapse = true
        case _ => ()
      }
      child = nextChild
    }

    if (hadCollapse) {
      TextNodeConverter.mergeTextNodes(node)
    }

    if (trimFirstLastChild) {
      // trim first and last child text
      val firstChild = node.firstChild
      val lastChild  = node.lastChild

      if (firstChild == lastChild) {
        firstChild.foreach { fc =>
          if (!fc.isInstanceOf[DoNotTrim]) fc.chars = fc.chars.trim()
        }
      } else {
        firstChild.foreach { fc =>
          if (!fc.isInstanceOf[DoNotTrim]) fc.chars = fc.chars.trimStart()
        }
        lastChild.foreach { lc =>
          if (!lc.isInstanceOf[DoNotTrim]) lc.chars = lc.chars.trimEnd()
        }
      }
    }
  }

  def calculateDelimiterCharacters(options: DataHolder, characters: Set[Char]): BitSet = {
    val bitSet = new BitSet()
    for (c <- characters)
      bitSet.set(c)
    bitSet
  }

  def calculateSpecialCharacters(options: DataHolder, delimiterCharacters: BitSet): BitSet = {
    val bitSet = new BitSet()
    bitSet.or(delimiterCharacters)
    bitSet.set('\r')
    bitSet.set('\n')
    bitSet.set('`')
    bitSet.set('[')
    bitSet.set(']')
    bitSet.set('\\')
    bitSet.set('!')
    bitSet.set('<')
    bitSet.set('&')
    bitSet
  }

  def calculateDelimiterProcessors(options: DataHolder, delimiterProcessors: List[DelimiterProcessor]): Map[Char, DelimiterProcessor] = {
    val map = mutable.HashMap[Char, DelimiterProcessor]()

    if (Parser.ASTERISK_DELIMITER_PROCESSOR.get(options)) {
      addDelimiterProcessors(List(AsteriskDelimiterProcessor(Parser.STRONG_WRAPS_EMPHASIS.get(options))), map)
    }
    if (Parser.UNDERSCORE_DELIMITER_PROCESSOR.get(options)) {
      addDelimiterProcessors(List(UnderscoreDelimiterProcessor(Parser.STRONG_WRAPS_EMPHASIS.get(options))), map)
    }

    addDelimiterProcessors(delimiterProcessors, map)
    map.toMap
  }

  def calculateLinkRefProcessors(options: DataHolder, linkRefProcessors: List[LinkRefProcessorFactory]): LinkRefProcessorData =
    if (linkRefProcessors.size > 1) {
      var maxNestingLevel = 0

      val sorted = linkRefProcessors.sortWith { (p1, p2) =>
        var lv1 = p1.getBracketNestingLevel(options)
        var lv2 = p2.getBracketNestingLevel(options)
        if (maxNestingLevel < lv1) maxNestingLevel = lv1
        if (maxNestingLevel < lv2) maxNestingLevel = lv2

        if (lv1 == lv2) {
          // processors that want exclamation before the [ have higher priority
          if (!p1.getWantExclamationPrefix(options)) lv1 += 1
          if (!p2.getWantExclamationPrefix(options)) lv2 += 1
        }
        lv1 < lv2
      }

      val maxReferenceLinkNesting = maxNestingLevel
      val nestingLookup           = new Array[Int](maxNestingLevel + 1)

      var prevMaxNesting = -1
      var index          = 0
      boundary {
        for (linkProcessor <- sorted) {
          if (prevMaxNesting < linkProcessor.getBracketNestingLevel(options)) {
            prevMaxNesting = linkProcessor.getBracketNestingLevel(options)
            nestingLookup(prevMaxNesting) = index
            if (prevMaxNesting == maxReferenceLinkNesting) break(())
          }
          index += 1
        }
      }

      LinkRefProcessorData(sorted, maxReferenceLinkNesting, nestingLookup)
    } else if (linkRefProcessors.nonEmpty) {
      val maxNesting    = linkRefProcessors.head.getBracketNestingLevel(options)
      val nestingLookup = new Array[Int](maxNesting + 1)
      LinkRefProcessorData(linkRefProcessors, maxNesting, nestingLookup)
    } else {
      LinkRefProcessorData(linkRefProcessors, 0, Array.empty)
    }

  private def addDelimiterProcessors(delimiterProcessors: List[DelimiterProcessor], map: mutable.HashMap[Char, DelimiterProcessor]): Unit =
    for (delimiterProcessor <- delimiterProcessors) {
      val opening = delimiterProcessor.openingCharacter
      addDelimiterProcessorForChar(opening, delimiterProcessor, map)
      val closing = delimiterProcessor.closingCharacter
      if (opening != closing) {
        addDelimiterProcessorForChar(closing, delimiterProcessor, map)
      }
    }

  private def addDelimiterProcessorForChar(delimiterChar: Char, toAdd: DelimiterProcessor, delimiterProcessors: mutable.HashMap[Char, DelimiterProcessor]): Unit = {
    val existing = delimiterProcessors.put(delimiterChar, toAdd)
    existing.foreach { ex =>
      if (ex.getClass != toAdd.getClass) {
        throw IllegalArgumentException(
          s"Delimiter processor conflict with delimiter char '$delimiterChar', existing ${ex.getClass.getCanonicalName}, added ${toAdd.getClass.getCanonicalName}"
        )
      } else {
        // warning
        println(s"Delimiter processor for char '$delimiterChar', added more than once ${ex.getClass.getCanonicalName}")
      }
    }
  }

  def calculateInlineParserExtensions(
    document:                       Document,
    inlineParserExtensionFactories: List[InlineParserExtensionFactory]
  ): Map[Char, List[InlineParserExtensionFactory]] = {
    val resolved = DependencyResolver.resolveFlatDependencies(inlineParserExtensionFactories, Nullable.empty, Nullable.empty)
    val map      = mutable.HashMap[Char, ArrayBuffer[InlineParserExtensionFactory]]()

    for (factory <- resolved) {
      val chars = factory.getCharacters
      var i     = 0
      while (i < chars.length()) {
        val c = chars.charAt(i)
        map.getOrElseUpdate(c, ArrayBuffer.empty).addOne(factory)
        i += 1
      }
    }

    map.view.mapValues(_.toList).toMap
  }
}
