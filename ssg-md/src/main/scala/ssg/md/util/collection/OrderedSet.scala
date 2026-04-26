/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/OrderedSet.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/OrderedSet.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable
import ssg.md.util.collection.iteration.*

import java.lang.reflect.Array as JArray
import java.util.{ BitSet, Iterator as JIterator, Map as JMap, Spliterator }

import scala.language.implicitConversions

class OrderedSet[E](capacity: Int, private val host: Nullable[CollectionHost[E]]) extends java.util.Set[E] with java.lang.Iterable[E] {

  def this() = this(0, Nullable.empty)

  def this(capacity: Int) = this(capacity, Nullable.empty)

  def this(host: CollectionHost[E]) = this(0, Nullable(host))

  private val keyMap:                          java.util.HashMap[E, Integer] = new java.util.HashMap[E, Integer](capacity)
  private[collection] val valueList:           java.util.ArrayList[E]        = new java.util.ArrayList[E](capacity)
  private val _validIndices:                   BitSet                        = new BitSet()
  private var modificationCount:               Int                           = Int.MinValue
  private var indexedProxy:                    Nullable[Indexed[E]]          = Nullable.empty
  private var allowConcurrentModsIndexedProxy: Nullable[Indexed[E]]          = Nullable.empty

  def indexBitSet(items: java.lang.Iterable[? <: E]): BitSet = {
    val bitSet = new BitSet()
    val iter   = items.iterator()
    while (iter.hasNext) {
      val element = iter.next()
      val i       = indexOf(element)
      if (i != -1) {
        bitSet.set(i)
      }
    }
    bitSet
  }

  def differenceBitSet(items: java.lang.Iterable[? <: E]): BitSet =
    differenceBitSet(items.iterator())

  def differenceBitSet(items: JIterator[? <: E]): BitSet = {
    val bitSet = new BitSet()
    var j      = 0
    while (items.hasNext) {
      val element = items.next()
      val i       = indexOf(element)
      if (i != j) {
        bitSet.set(i)
      }
      j += 1
    }
    bitSet
  }

  def keyDifferenceBitSet(items: java.lang.Iterable[? <: JMap.Entry[? <: E, ?]]): BitSet =
    keyDifferenceBitSet(items.iterator())

  def keyDifferenceBitSet(items: JIterator[? <: JMap.Entry[? <: E, ?]]): BitSet = {
    val bitSet = new BitSet()
    var j      = 0
    while (items.hasNext) {
      val entry = items.next()
      val i     = indexOf(entry.getKey)
      if (i != j) {
        bitSet.set(i)
      }
      j += 1
    }
    bitSet
  }

  def valueDifferenceBitSet(items: java.lang.Iterable[? <: JMap.Entry[?, ? <: E]]): BitSet =
    valueDifferenceBitSet(items.iterator())

  def valueDifferenceBitSet(items: JIterator[? <: JMap.Entry[?, ? <: E]]): BitSet = {
    val bitSet = new BitSet()
    var j      = 0
    while (items.hasNext) {
      val entry = items.next()
      val i     = indexOf(entry.getValue)
      if (i != j) {
        bitSet.set(i)
      }
      j += 1
    }
    bitSet
  }

  private class IndexedProxy(private val allowConcurrentMods: Boolean) extends Indexed[E] {

    override def get(index: Int): E = getValue(index)

    override def set(index: Int, item: E): Unit =
      setValueAt(index, item, Nullable.empty[Object])

    override def removeAt(index: Int): Unit =
      removeIndexHosted(index)

    override def size: Int = valueList.size()

    override def modificationCount: Int =
      if (allowConcurrentMods) 0 else getIteratorModificationCount
  }

  def getIndexedProxy(): Indexed[E] =
    if (indexedProxy.isDefined) indexedProxy.get
    else {
      val proxy = new IndexedProxy(false)
      indexedProxy = Nullable(proxy)
      proxy
    }

  def getConcurrentModsIndexedProxy(): Indexed[E] =
    if (allowConcurrentModsIndexedProxy.isDefined) allowConcurrentModsIndexedProxy.get
    else {
      val proxy = new IndexedProxy(true)
      allowConcurrentModsIndexedProxy = Nullable(proxy)
      proxy
    }

  def getModificationCount: Int = modificationCount

  private[collection] def getIteratorModificationCount: Int =
    if (host.isDefined) host.get.getIteratorModificationCount else modificationCount

  def inHostUpdate: Boolean =
    host.isDefined && host.get.skipHostUpdate()

  def indexOf(element: Any): Int = {
    val result = keyMap.get(element)
    if (result == null) -1 else result.intValue() // Java HashMap interop — get returns null for missing keys
  }

  def isValidIndex(index: Int): Boolean =
    index >= 0 && index < valueList.size() && _validIndices.get(index)

  def validateIndex(index: Int): Unit =
    if (!isValidIndex(index)) {
      throw new IndexOutOfBoundsException("Index " + index + " is not valid, size=" + valueList.size() + " validIndices[" + index + "]=" + _validIndices.get(index))
    }

  def getValue(index: Int): E = {
    validateIndex(index)
    valueList.get(index)
  }

  def getValueOrNull(index: Int): Nullable[E] =
    if (isValidIndex(index)) Nullable(valueList.get(index)) else Nullable.empty

  override def size(): Int = keyMap.size()

  override def isEmpty: Boolean = keyMap.isEmpty

  override def contains(o: Any): Boolean = keyMap.containsKey(o)

  def getValueList: java.util.List[E] = valueList

  def values(): java.util.List[E] =
    if (!isSparse) valueList
    else {
      val list = new java.util.ArrayList[E]()
      val iter = iterable().iterator()
      while (iter.hasNext)
        list.add(iter.next())
      list
    }

  def setValueAt(index: Int, value: Nullable[E], o: Nullable[Object]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — key may be null
    val valueRaw = value.orNull
    // if index is after end then we add nulls
    val existingIndex = indexOf(valueRaw)
    if (existingIndex != -1) {
      if (index != existingIndex) {
        throw new IllegalStateException("Trying to add existing element " + valueRaw + "[" + existingIndex + "] at index " + index)
      }
      // same index, same element, nothing to update
      false
    } else {
      if (index < valueList.size()) {
        if (_validIndices.get(index)) {
          // already have another element at index
          throw new IllegalStateException("Trying to add new element " + valueRaw + " at index " + index + ", already occupied by " + valueList.get(index))
        }
        // old element was removed, just replace
      } else {
        if (index > valueList.size()) addNulls(index - 1)
      }

      if (host.isDefined && !host.get.skipHostUpdate()) {
        @annotation.nowarn("msg=deprecated") // Java interop — passing potentially null Object to host callback
        val oRaw = o.orNull
        host.get.adding(index, value, oRaw)
      }

      keyMap.put(valueRaw, Integer.valueOf(index))
      valueList.set(index, valueRaw)
      _validIndices.set(index)

      true
    }
  }

  def isSparse: Boolean =
    _validIndices.nextClearBit(0) < valueList.size()

  def addNull(): Unit =
    addNulls(valueList.size())

  def addNulls(index: Int): Unit = {
    assert(index >= valueList.size())

    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.addingNulls(index)
    }

    modificationCount += 1

    // no need they are 0's by default
    // validIndices.set(start, index + 1);
    while (valueList.size() <= index) {
      @annotation.nowarn("msg=deprecated") // Java ArrayList interop — adding null placeholder
      val n: E = Nullable.empty[E].orNull
      valueList.add(n)
    }
  }

  def indexIterator(): ReversibleIterator[Int] =
    new BitSetIterator(_validIndices)

  def reversedIndexIterator(): ReversibleIterator[Int] =
    new BitSetIterator(_validIndices, true)

  def indexIterable(): ReversibleIterable[Int] =
    new BitSetIterable(_validIndices)

  def reversedIndexIterable(): ReversibleIterable[Int] =
    new BitSetIterable(_validIndices, true)

  override def iterator(): ReversibleIndexedIterator[E] =
    new IndexedIterator[E, E, ReversibleIterator[Int]](getIndexedProxy(), indexIterator())

  // Resolve diamond inheritance conflict between java.util.Set and java.lang.Iterable
  override def spliterator(): Spliterator[E] =
    super[Set].spliterator()

  def reversedIterator(): ReversibleIndexedIterator[E] =
    new IndexedIterator[E, E, ReversibleIterator[Int]](getIndexedProxy(), reversedIndexIterator())

  def iterable(): ReversibleIterable[E] =
    new IndexedIterable[E, E, ReversibleIterable[Int]](getIndexedProxy(), indexIterable())

  def reversedIterable(): ReversibleIterable[E] =
    new IndexedIterable[E, E, ReversibleIterable[Int]](getIndexedProxy(), reversedIndexIterable())

  override def toArray(): Array[Object] = {
    val objects = new Array[Object](keyMap.size())
    var index   = -1
    var i       = -1
    while (index + 1 < valueList.size()) {
      index += 1
      if (_validIndices.get(index)) {
        i += 1
        objects(i) = valueList.get(index).asInstanceOf[Object]
      }
    }
    objects
  }

  override def toArray[T](array: Array[T & Object]): Array[T & Object] = {
    var objects: Array[Object] = array.asInstanceOf[Array[Object]]

    if (array.length < keyMap.size()) {
      objects =
        if (array.getClass == classOf[Array[Object]]) new Array[Object](keyMap.size())
        else JArray.newInstance(array.getClass.getComponentType, keyMap.size()).asInstanceOf[Array[Object]]
    }

    var index = -1
    var i     = -1
    while (index + 1 < valueList.size()) {
      index += 1
      if (_validIndices.get(index)) {
        i += 1
        objects(i) = valueList.get(index).asInstanceOf[Object]
      }
    }

    i += 1
    if (objects.length > i) {
      objects(i) = null // Java array interop — marking end of elements with null
    }
    objects.asInstanceOf[Array[T & Object]]
  }

  override def add(e: E): Boolean = add(e, Nullable.empty[Object])

  def add(e: E, o: Nullable[Object]): Boolean =
    if (keyMap.containsKey(e)) false
    else {
      val i = valueList.size()

      if (host.isDefined && !host.get.skipHostUpdate()) {
        @annotation.nowarn("msg=deprecated") // Java interop — passing potentially null Object to host callback
        val oRaw = o.orNull
        host.get.adding(i, Nullable(e), oRaw)
      }

      modificationCount += 1
      keyMap.put(e, Integer.valueOf(i))
      valueList.add(e)
      _validIndices.set(i)
      true
    }

  def removeIndex(index: Int): Boolean =
    removeIndexHosted(index) != null // Java interop — null means not found

  private[collection] def removeIndexHosted(index: Int): Object = {
    validateIndex(index)

    val o = valueList.get(index)
    var r: Object = null.asInstanceOf[Object] // Java interop — r may be null when host returns null
    if (host.isDefined && !host.get.skipHostUpdate()) {
      r = host.get.removing(index, Nullable(o)).asInstanceOf[Object]
    } else {
      r = o.asInstanceOf[Object]
    }

    modificationCount += 1
    keyMap.remove(o)

    if (keyMap.size() == 0) {
      if (host.isDefined && !host.get.skipHostUpdate()) {
        host.get.clearing()
      }
      valueList.clear()
      _validIndices.clear()
    } else {
      if (host.isEmpty && index == valueList.size() - 1) {
        valueList.remove(index)
      }
      _validIndices.clear(index)
    }

    r
  }

  override def remove(o: Any): Boolean =
    removeHosted(o) != null // Java interop — null means not found

  def removeHosted(o: Any): Object = {
    val index = keyMap.get(o)
    if (index == null) null.asInstanceOf[Object] // Java HashMap interop — get returns null for missing keys
    else removeIndexHosted(index.intValue())
  }

  override def containsAll(collection: java.util.Collection[?]): Boolean =
    scala.util.boundary {
      val iter = collection.iterator()
      while (iter.hasNext)
        if (!keyMap.containsKey(iter.next())) {
          scala.util.boundary.break(false)
        }
      true
    }

  override def addAll(collection: java.util.Collection[? <: E]): Boolean = {
    var changed = false
    val iter    = collection.iterator()
    while (iter.hasNext)
      if (add(iter.next())) changed = true
    changed
  }

  override def retainAll(collection: java.util.Collection[?]): Boolean = {
    val removeSet = new BitSet(valueList.size())
    removeSet.set(0, valueList.size())
    removeSet.and(_validIndices)

    val iter = collection.iterator()
    while (iter.hasNext) {
      val index = indexOf(iter.next())
      if (index != -1) {
        removeSet.clear(index)
      }
    }

    // Java7
    var index = valueList.size()
    if (index == 0) false
    else {
      var changed = false
      index -= 1
      while (index >= 0) {
        index = removeSet.previousSetBit(index)
        if (index == -1) {
          index = -1 // will exit loop
        } else {
          remove(valueList.get(index))
          changed = true
          index -= 1
        }
      }
      changed
    }
  }

  override def removeAll(collection: java.util.Collection[?]): Boolean = {
    var changed = false
    val iter    = collection.iterator()
    while (iter.hasNext) {
      val o = iter.next()
      if (keyMap.containsKey(o)) {
        if (remove(o)) changed = true
      }
    }
    changed
  }

  override def clear(): Unit = {
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.clearing()
    }

    modificationCount += 1
    keyMap.clear()
    valueList.clear()
    _validIndices.clear()
  }

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) true
    else {
      o match {
        case set: OrderedSet[?] =>
          if (size() != set.size()) false
          else {
            val setIterator  = set.iterator()
            val thisIterator = this.iterator()
            var equal        = true
            while (thisIterator.hasNext && equal) {
              val e    = thisIterator.next()
              val eSet = setIterator.next()
              if (e != eSet) equal = false
            }
            equal
          }
        case _ => false
      }
    }

  override def hashCode(): Int = {
    var result = keyMap.hashCode()
    result = 31 * result + valueList.hashCode()
    result = 31 * result + _validIndices.hashCode()
    result
  }

  def getValidIndices: BitSet = _validIndices
}
