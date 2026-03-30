/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/LightInlineParserImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.ast.Text
import ssg.md.ast.util.Parsing
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.{ BasedSequence, SegmentedSequence, SequenceUtils }

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import java.util.regex.{ Matcher, Pattern }

import scala.language.implicitConversions

class LightInlineParserImpl(dataOptions: DataHolder) extends LightInlineParser {

  protected val _options:     InlineParserOptions                  = InlineParserOptions(dataOptions)
  protected val myParsing:    Parsing                              = Parsing(dataOptions)
  protected var _block:       Node                                 = scala.compiletime.uninitialized
  protected var _input:       BasedSequence                        = scala.compiletime.uninitialized
  protected var _index:       Int                                  = 0
  protected var _currentText: Nullable[ArrayBuffer[BasedSequence]] = Nullable.empty
  protected var _document:    Document                             = scala.compiletime.uninitialized

  override def currentText: ArrayBuffer[BasedSequence] = {
    if (_currentText.isEmpty) {
      _currentText = ArrayBuffer[BasedSequence]()
    }
    _currentText.get
  }

  override def input: BasedSequence = _input

  override def input_=(value: BasedSequence): Unit =
    _input = value

  override def index: Int = _index

  override def index_=(value: Int): Unit =
    _index = value

  override def document: Document = _document

  override def document_=(value: Document): Unit =
    _document = value

  override def options: InlineParserOptions = _options

  override def parsing: Parsing = myParsing

  override def block: Node = _block

  override def block_=(value: Node): Unit =
    _block = value

  override def moveNodes(fromNode: Node, toNode: Node): Unit = {
    if (fromNode ne toNode) {
      var next = fromNode.next
      while (next.isDefined) {
        val nextNode = next.get.next
        next.get.unlink()
        fromNode.appendChild(next.get)
        if (next.contains(toNode)) {
          next = Nullable.empty
        } else {
          next = nextNode
        }
      }
    }

    fromNode.setCharsFromContent()
  }

  override def appendText(text: BasedSequence): Unit =
    currentText.addOne(text)

  override def appendText(text: BasedSequence, beginIndex: Int, endIndex: Int): Unit =
    currentText.addOne(text.subSequence(beginIndex, endIndex))

  override def appendNode(node: Node): Unit = {
    flushTextNode()
    _block.appendChild(node)
  }

  // In some cases, we don't want the text to be appended to an existing node, we need it separate
  override def appendSeparateText(text: BasedSequence): Text = {
    val node = Text(text)
    appendNode(node)
    node
  }

  override def flushTextNode(): Boolean =
    if (_currentText.isDefined) {
      _block.appendChild(Text(SegmentedSequence.create(_input, _currentText.get.asJava)))
      _currentText = Nullable.empty
      true
    } else {
      false
    }

  /** If RE matches at current index in the input, advance index and return the match; otherwise return Nullable.empty.
    *
    * @param re
    *   pattern to match
    * @return
    *   sequence matched or Nullable.empty
    */
  override def `match`(re: Pattern): Nullable[BasedSequence] =
    if (_index >= _input.length()) {
      Nullable.empty
    } else {
      val m = re.matcher(_input)
      m.region(_index, _input.length())
      if (m.find()) {
        _index = m.end()
        val result = m.toMatchResult
        Nullable(_input.subSequence(result.start(), result.end()))
      } else {
        Nullable.empty
      }
    }

  /** If RE matches at current index in the input, advance index and return the match with groups; otherwise return Nullable.empty.
    *
    * @param re
    *   pattern to match
    * @return
    *   array of sequences matched (index 0 = full match, 1+ = groups) or Nullable.empty
    */
  override def matchWithGroups(re: Pattern): Nullable[Array[BasedSequence]] =
    if (_index >= _input.length()) {
      Nullable.empty
    } else {
      val m = re.matcher(_input)
      m.region(_index, _input.length())
      if (m.find()) {
        _index = m.end()
        val result  = m.toMatchResult
        val iMax    = m.groupCount() + 1
        val results = new Array[BasedSequence](iMax)
        results(0) = _input.subSequence(result.start(), result.end())
        var i = 1
        while (i < iMax) {
          if (m.group(i) != null) { // null check at Java interop boundary
            results(i) = _input.subSequence(result.start(i), result.end(i))
          } else {
            results(i) = BasedSequence.NULL
          }
          i += 1
        }
        Nullable(results)
      } else {
        Nullable.empty
      }
    }

  /** If RE matches at current index in the input, advance index and return the matcher; otherwise return Nullable.empty.
    *
    * @param re
    *   pattern to match
    * @return
    *   matched matcher or Nullable.empty
    */
  override def matcher(re: Pattern): Nullable[Matcher] =
    if (_index >= _input.length()) {
      Nullable.empty
    } else {
      val m = re.matcher(_input)
      m.region(_index, _input.length())
      if (m.find()) {
        _index = m.end()
        Nullable(m)
      } else {
        Nullable.empty
      }
    }

  /** @return
    *   the char at the current input index, or `'\0'` in case there are no more characters.
    */
  override def peek(): Char =
    if (_index < _input.length()) {
      _input.charAt(_index)
    } else {
      SequenceUtils.NUL
    }

  override def peek(ahead: Int): Char =
    if (_index + ahead < _input.length()) {
      _input.charAt(_index + ahead)
    } else {
      SequenceUtils.NUL
    }

  /** Parse zero or more space characters, including at most one newline and zero or more spaces.
    *
    * @return
    *   true
    */
  override def spnl(): Boolean = {
    `match`(myParsing.SPNL)
    true
  }

  /** Parse zero or more non-indent spaces.
    *
    * @return
    *   true
    */
  override def nonIndentSp(): Boolean = {
    `match`(myParsing.SPNI)
    true
  }

  /** Parse zero or more spaces.
    *
    * @return
    *   true
    */
  override def sp(): Boolean = {
    `match`(myParsing.SP)
    true
  }

  /** Parse zero or more space characters, including at one newline.
    *
    * @return
    *   true
    */
  override def spnlUrl(): Boolean =
    `match`(myParsing.SPNL_URL).isDefined

  /** Parse to end of line, including EOL.
    *
    * @return
    *   characters parsed or Nullable.empty if no end of line
    */
  override def toEOL(): Nullable[BasedSequence] =
    `match`(myParsing.REST_OF_LINE)
}
