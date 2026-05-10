/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: dagre-d3-es/src/graphlib/data/priority-queue.js
 * Original author: Chris Pettitt and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: JS class PriorityQueue -> Scala class PriorityQueue
 *   Idiom: mutable.ArrayBuffer + mutable.HashMap for heap + index map
 *   Renames: none
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package layout
package graph

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable

/** A min-priority queue data structure. This algorithm is derived from Cormen, et al., "Introduction to Algorithms". The basic idea of a min-priority queue is that you can efficiently (in O(1) time)
  * get the smallest key in the queue. Adding and removing elements takes O(log n) time. A key can have its priority decreased in O(log n) time.
  */
final class PriorityQueue {

  private val _arr:        mutable.ArrayBuffer[PriorityQueue.Entry] = mutable.ArrayBuffer.empty
  private val _keyIndices: mutable.HashMap[String, Int]             = mutable.HashMap.empty

  /** Returns the number of elements in the queue. Takes `O(1)` time. */
  def size: Int = _arr.length

  /** Returns true if the queue is empty. */
  def isEmpty: Boolean = _arr.isEmpty

  /** Returns the keys that are in the queue. Takes `O(n)` time. */
  def keys(): Array[String] =
    _arr.map(_.key).toArray

  /** Returns `true` if `key` is in the queue and `false` if not. */
  def has(key: String): Boolean = _keyIndices.contains(key)

  /** Returns the priority for `key`. If `key` is not present in the queue then this function returns Nullable.Null. Takes `O(1)` time.
    */
  def priority(key: String): Nullable[Double] =
    _keyIndices.get(key) match {
      case Some(index) => Nullable(_arr(index).priority)
      case scala.None  => Nullable.Null
    }

  /** Returns the key for the minimum element in this queue. If the queue is empty this function throws an Error. Takes `O(1)` time.
    */
  def min(): String = {
    if (size == 0) {
      throw new IllegalStateException("Queue underflow")
    }
    _arr(0).key
  }

  /** Inserts a new key into the priority queue. If the key already exists in the queue this function returns `false`; otherwise it will return `true`. Takes `O(n)` time.
    *
    * @param key
    *   the key to add
    * @param priority
    *   the initial priority for the key
    */
  def add(key: String, priority: Double): Boolean =
    if (!_keyIndices.contains(key)) {
      val index = _arr.length
      _keyIndices(key) = index
      _arr += PriorityQueue.Entry(key, priority)
      decrease(index)
      true
    } else {
      false
    }

  /** Removes and returns the smallest key in the queue. Takes `O(log n)` time. */
  def removeMin(): String = {
    swap(0, _arr.length - 1)
    val minEntry = _arr.remove(_arr.length - 1)
    _keyIndices.remove(minEntry.key)
    heapify(0)
    minEntry.key
  }

  /** Decreases the priority for `key` to `priority`. If the new priority is greater than the previous priority, this function will throw an Error.
    *
    * @param key
    *   the key for which to raise priority
    * @param priority
    *   the new priority for the key
    */
  def decrease(key: String, priority: Double): Unit = {
    val index = _keyIndices(key)
    if (priority > _arr(index).priority) {
      throw new IllegalArgumentException(
        "New priority is greater than current priority. " +
          "Key: " + key + " Old: " + _arr(index).priority + " New: " + priority
      )
    }
    _arr(index) = _arr(index).copy(priority = priority)
    decrease(index)
  }

  private def heapify(i: Int): Unit = {
    val l = 2 * i + 1
    val r = l + 1
    // 0-based heap: children of i are at 2i+1 and 2i+2
    var largest = i
    if (l < _arr.length) {
      if (_arr(l).priority < _arr(largest).priority) {
        largest = l
      }
      if (r < _arr.length) {
        if (_arr(r).priority < _arr(largest).priority) {
          largest = r
        }
      }
      if (largest != i) {
        swap(i, largest)
        heapify(largest)
      }
    }
  }

  private def decrease(index: Int): Unit = {
    val priority = _arr(index).priority
    var idx      = index
    boundary {
      while (idx != 0) {
        val parent = (idx - 1) >> 1
        // 0-based heap: parent of idx is at (idx-1)/2
        if (_arr(parent).priority < priority) {
          break()
        }
        swap(idx, parent)
        idx = parent
      }
    }
  }

  private def swap(i: Int, j: Int): Unit = {
    val origI = _arr(i)
    val origJ = _arr(j)
    _arr(i) = origJ
    _arr(j) = origI
    _keyIndices(origJ.key) = i
    _keyIndices(origI.key) = j
  }
}

object PriorityQueue {

  final private case class Entry(key: String, priority: Double)
}
