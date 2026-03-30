/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TrackedOffsetList.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format

import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.Seg
import ssg.md.util.sequence.builder.tree.{ BasedOffsetTracker, SegmentOffsetTree }

import java.util
import java.util.{ ArrayList, Collections, Comparator, Iterator as JIterator, List as JList, ListIterator, Spliterator }
import java.util.function.UnaryOperator

class TrackedOffsetList private (
  private val myBaseSeq: BasedSequence,
  trackedOffsets:        JList[TrackedOffset]
) extends JList[TrackedOffset] {

  private val myTrackedOffsets: ArrayList[TrackedOffset] = new ArrayList[TrackedOffset](trackedOffsets)
  myTrackedOffsets.sort(Comparator.comparing[TrackedOffset, Integer]((t: TrackedOffset) => t.offset))

  private val myBasedOffsetTracker: BasedOffsetTracker = {
    val segments = new ArrayList[Seg](trackedOffsets.size())
    val iter     = myTrackedOffsets.iterator()
    while (iter.hasNext) {
      val to = iter.next()
      segments.add(Seg.segOf(to.offset, to.offset + 1))
    }
    val segmentOffsetTree = SegmentOffsetTree.build(segments, "")
    BasedOffsetTracker.create(myBaseSeq, segmentOffsetTree)
  }

  assert(myBasedOffsetTracker.size == myTrackedOffsets.size())

  def getUnresolvedOffsets: TrackedOffsetList = {
    val unresolved = new ArrayList[TrackedOffset]()
    val iter       = myTrackedOffsets.iterator()
    while (iter.hasNext) {
      val to = iter.next()
      if (!to.isResolved) unresolved.add(to)
    }
    if (unresolved.isEmpty) TrackedOffsetList.EMPTY_LIST else new TrackedOffsetList(myBaseSeq, unresolved)
  }

  def haveUnresolved: Boolean = {
    val iter = myTrackedOffsets.iterator()
    while (iter.hasNext)
      if (!iter.next().isResolved) return true // NOTE: early return from search
    false
  }

  def getBaseSeq: BasedSequence = myBaseSeq

  def getTrackedOffsets: JList[TrackedOffset] = myTrackedOffsets

  def getBasedOffsetTracker: BasedOffsetTracker = myBasedOffsetTracker

  def getTrackedOffsets(startOffset: Int, endOffset: Int): TrackedOffsetList = {
    val startInfo = myBasedOffsetTracker.getOffsetInfo(startOffset, startOffset == endOffset)
    val endInfo   = myBasedOffsetTracker.getOffsetInfo(endOffset, true)
    var startSeg  = startInfo.pos
    var endSeg    = endInfo.pos

    if (startSeg < 0 && endSeg >= 0) {
      startSeg = 0
      endSeg += 1
    } else if (startSeg >= 0 && endSeg >= 0) {
      endSeg += 1
    } else {
      return TrackedOffsetList.EMPTY_LIST // NOTE: early return for empty result
    }

    endSeg = Math.min(myBasedOffsetTracker.size, endSeg)

    if (startSeg >= endSeg) {
      TrackedOffsetList.EMPTY_LIST
    } else {
      if (myTrackedOffsets.get(startSeg).offset < startOffset) startSeg += 1
      if (myTrackedOffsets.get(endSeg - 1).offset > endOffset) endSeg -= 1

      if (startSeg >= endSeg) TrackedOffsetList.EMPTY_LIST
      else new TrackedOffsetList(myBaseSeq, myTrackedOffsets.subList(startSeg, endSeg))
    }
  }

  // @formatter:off
  override def add(offset: TrackedOffset): Boolean = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def set(index: Int, element: TrackedOffset): TrackedOffset = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def add(index: Int, element: TrackedOffset): Unit = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def remove(index: Int): TrackedOffset = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def addAll(c: util.Collection[? <: TrackedOffset]): Boolean = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def addAll(index: Int, c: util.Collection[? <: TrackedOffset]): Boolean = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def removeAll(c: util.Collection[?]): Boolean = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def retainAll(c: util.Collection[?]): Boolean = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def replaceAll(operator: UnaryOperator[TrackedOffset]): Unit = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def sort(c: Comparator[? >: TrackedOffset]): Unit = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def clear(): Unit = { throw new IllegalStateException("Not supported. Immutable list.") }
  override def remove(o: Any): Boolean = { throw new IllegalStateException("Not supported. Immutable list.") }
  // @formatter:on

  override def size():                                  Int                         = myTrackedOffsets.size()
  override def isEmpty:                                 Boolean                     = myTrackedOffsets.isEmpty
  override def contains(o:         Any):                Boolean                     = myTrackedOffsets.contains(o)
  override def iterator():                              JIterator[TrackedOffset]    = myTrackedOffsets.iterator()
  override def toArray:                                 Array[AnyRef]               = myTrackedOffsets.toArray
  override def toArray[T](a:       Array[T & AnyRef]):  Array[T & AnyRef]           = myTrackedOffsets.toArray(a)
  override def containsAll(c:      util.Collection[?]): Boolean                     = myTrackedOffsets.containsAll(c)
  override def equals(o:           Any):                Boolean                     = myTrackedOffsets.equals(o)
  override def hashCode:                                Int                         = myTrackedOffsets.hashCode()
  override def get(index:          Int):                TrackedOffset               = myTrackedOffsets.get(index)
  override def indexOf(o:          Any):                Int                         = myTrackedOffsets.indexOf(o)
  override def lastIndexOf(o:      Any):                Int                         = myTrackedOffsets.lastIndexOf(o)
  override def listIterator():                          ListIterator[TrackedOffset] = myTrackedOffsets.listIterator()
  override def listIterator(index: Int):                ListIterator[TrackedOffset] = myTrackedOffsets.listIterator(index)
  override def subList(fromIndex:  Int, toIndex: Int):  JList[TrackedOffset]        = myTrackedOffsets.subList(fromIndex, toIndex)
  override def spliterator():                           Spliterator[TrackedOffset]  = myTrackedOffsets.spliterator()
}

object TrackedOffsetList {

  val EMPTY_LIST: TrackedOffsetList = new TrackedOffsetList(BasedSequence.NULL, Collections.emptyList())

  def create(baseSeq: BasedSequence, trackedOffsets: JList[TrackedOffset]): TrackedOffsetList =
    trackedOffsets match {
      case tol: TrackedOffsetList => tol
      case _ => new TrackedOffsetList(baseSeq, trackedOffsets)
    }

  def create(baseSeq: BasedSequence, offsets: Array[Int]): TrackedOffsetList = {
    val trackedOffsets = new ArrayList[TrackedOffset](offsets.length)
    var i              = 0
    while (i < offsets.length) {
      trackedOffsets.add(TrackedOffset.track(offsets(i)))
      i += 1
    }
    new TrackedOffsetList(baseSeq, trackedOffsets)
  }
}
