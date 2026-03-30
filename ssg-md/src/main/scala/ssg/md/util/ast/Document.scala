/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Document.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.data._
import ssg.md.util.misc.Utils
import ssg.md.util.sequence.BasedSequence

import java.util.{ Collection, Map }

import scala.util.boundary
import scala.util.boundary.break

class Document(options: Nullable[DataHolder], chars: BasedSequence) extends Block(chars) with MutableDataHolder {

  private val dataSet: MutableDataSet = new MutableDataSet(options)

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def clear(): MutableDataHolder =
    throw new UnsupportedOperationException()

  override def set[T](key: DataKey[T], value: T): MutableDataHolder = dataSet.set(key, value)

  override def set[T](key: NullableDataKey[T], value: Nullable[T]): MutableDataHolder = dataSet.set(key, value)

  override def setFrom(dataSetter: MutableDataSetter): MutableDataSet = dataSet.setFrom(dataSetter)

  override def setAll(other: DataHolder): MutableDataSet = dataSet.setAll(other)

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = dataSet.setIn(dataHolder)

  override def remove(key: DataKeyBase[?]): MutableDataSet = dataSet.remove(key)

  override def getOrCompute(key: DataKeyBase[?], factory: DataValueFactory[?]): AnyRef = dataSet.getOrCompute(key, factory)

  override def toMutable: MutableDataSet = dataSet.toMutable

  override def toImmutable: DataSet = dataSet.toImmutable

  override def toDataSet: MutableDataSet = dataSet.toDataSet

  def aggregate(): DataHolder = dataSet.aggregate()

  override def getAll: Map[? <: DataKeyBase[?], AnyRef] = dataSet.getAll

  override def getKeys: Collection[? <: DataKeyBase[?]] = dataSet.getKeys

  override def contains(key: DataKeyBase[?]): Boolean = dataSet.contains(key)

  override def lineCount: Int =
    if (lineSegments eq BasedSequence.EMPTY_LIST) {
      val c = chars.lastChar()
      (if (c == '\n' || c == '\r') 0 else 1) + getLineNumber(chars.length)
    } else {
      lineSegments.size
    }

  /** Get line number at offset
    *
    * Next line starts after the EOL sequence. offsets between \r and \n are considered part of the same line as offset before \r.
    *
    * @param offset
    *   offset in document text
    * @return
    *   line number at offset
    */
  def getLineNumber(offset: Int): Int =
    if (lineSegments eq BasedSequence.EMPTY_LIST) {
      val preText = chars.baseSubSequence(0, Utils.maxLimit(offset + 1, chars.length))

      if (preText.isEmpty) {
        0
      } else {
        var lineNumber  = 0
        var nextLineEnd = preText.endOfLineAnyEOL(0)
        val length      = preText.length
        while (nextLineEnd < length) {
          val eolLength     = preText.eolStartLength(nextLineEnd)
          val lengthWithEOL = nextLineEnd + eolLength
          if (offset >= lengthWithEOL) lineNumber += 1 // do not treat offset between \r and \n as complete line
          val oldNextLineEnd = nextLineEnd
          nextLineEnd = preText.endOfLineAnyEOL(lengthWithEOL)
          assert(nextLineEnd > oldNextLineEnd)
        }

        lineNumber
      }
    } else {
      boundary {
        val iMax = lineSegments.size
        var i    = 0
        while (i < iMax) {
          if (offset < lineSegments.get(i).endOffset) {
            break(i)
          }
          i += 1
        }
        iMax
      }
    }
}

object Document {
  @scala.annotation.nowarn("msg=deprecated") // null needed for bootstrap Document.NULL
  val NULL: Document = new Document(Nullable.empty, BasedSequence.NULL)

  def merge(dataHolders: DataHolder*): MutableDataSet = MutableDataSet.merge(dataHolders*)

  def aggregateActions(other: DataHolder, overrides: DataHolder): DataHolder = DataSet.aggregateActions(other, overrides)

  def aggregate(other: Nullable[DataHolder], overrides: Nullable[DataHolder]): DataHolder = DataSet.aggregate(other, overrides)
}
