/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/OrderedMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/OrderedMap.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable
import ssg.md.util.collection.iteration.*

import java.util.Map as JMap
import java.util.function.{ Consumer, Function as JFunction }

import scala.language.implicitConversions

class OrderedMap[K, V](capacity: Int, private val host: Nullable[CollectionHost[K]]) extends java.util.Map[K, V] with java.lang.Iterable[JMap.Entry[K, V]] {

  def this() = this(0, Nullable.empty)

  def this(capacity: Int) = this(capacity, Nullable.empty)

  def this(host: CollectionHost[K]) = this(0, Nullable(host))

  private val _valueList:           java.util.ArrayList[V]              = new java.util.ArrayList[V](capacity)
  private[collection] var inUpdate: Boolean                             = false
  private var indexedEntryProxy:    Nullable[Indexed[JMap.Entry[K, V]]] = Nullable.empty
  private var indexedValueProxy:    Nullable[Indexed[V]]                = Nullable.empty

  private[collection] val _keySet: OrderedSet[K] = new OrderedSet[K](
    capacity,
    new CollectionHost[K] {
      override def adding(index: Int, k: Nullable[K], v: Nullable[Object]): Unit =
        OrderedMap.this.adding(index, k, v)

      override def removing(index: Int, k: Nullable[K]): Nullable[Object] =
        OrderedMap.this.removing(index, k)

      override def clearing(): Unit =
        OrderedMap.this.clearing()

      override def addingNulls(index: Int): Unit =
        // can add anything, it will not be accessed, its a dummy holder
        OrderedMap.this.addingNull(index)

      override def skipHostUpdate(): Boolean = inUpdate

      override def getIteratorModificationCount: Int =
        OrderedMap.this.getModificationCount
    }
  )

  def getIndexedEntryProxy(): Indexed[JMap.Entry[K, V]] =
    if (indexedEntryProxy.isDefined) indexedEntryProxy.get
    else {
      val proxy = new Indexed[JMap.Entry[K, V]] {
        override def get(index: Int): JMap.Entry[K, V] =
          OrderedMap.this.getEntry(index)

        override def set(index: Int, item: JMap.Entry[K, V]): Unit =
          throw new UnsupportedOperationException()

        override def removeAt(index: Int): Unit =
          OrderedMap.this._keySet.removeIndexHosted(index)

        override def size: Int = OrderedMap.this.size()

        override def modificationCount: Int =
          OrderedMap.this.getModificationCount
      }
      indexedEntryProxy = Nullable(proxy)
      proxy
    }

  def getIndexedValueProxy(): Indexed[V] =
    if (indexedValueProxy.isDefined) indexedValueProxy.get
    else {
      val proxy = new Indexed[V] {
        override def get(index: Int): V =
          OrderedMap.this.getValue(index)

        override def set(index: Int, item: V): Unit =
          throw new UnsupportedOperationException()

        override def removeAt(index: Int): Unit =
          OrderedMap.this._keySet.removeIndexHosted(index)

        override def size: Int = OrderedMap.this.size()

        override def modificationCount: Int =
          OrderedMap.this.getModificationCount
      }
      indexedValueProxy = Nullable(proxy)
      proxy
    }

  private[collection] def getEntry(index: Int): JMap.Entry[K, V] =
    new MapEntry[K, V](_keySet.getValue(index), _valueList.get(index))

  def getModificationCount: Int = _keySet.getModificationCount

  private[collection] def adding(index: Int, k: Nullable[K], v: Nullable[Object]): Unit = {
    if (v.isEmpty) throw new IllegalArgumentException()
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.adding(index, k, v)
    }
    _valueList.add(v.get.asInstanceOf[V])
  }

  private[collection] def addingNull(index: Int): Unit = {
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.addingNulls(index)
    }
    addNulls(index)
  }

  private[collection] def removing(index: Int, k: Nullable[K]): Object = {
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.removing(index, k)
    }
    _valueList.get(index).asInstanceOf[Object]
  }

  private[collection] def clearing(): Unit = {
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.clearing()
    }
    _valueList.clear()
  }

  override def size(): Int = _keySet.size()

  override def isEmpty: Boolean = _keySet.isEmpty

  override def containsKey(o: Any): Boolean = _keySet.contains(o)

  override def containsValue(o: Any): Boolean = {
    val index = _valueList.indexOf(o)
    _keySet.isValidIndex(index)
  }

  def addNull(): Unit = addNulls(_valueList.size())

  def addNulls(index: Int): Unit = {
    if (index < _valueList.size()) {
      throw new IllegalArgumentException("addNulls(" + index + ") called when valueList size is " + _valueList.size())
    }
    while (_valueList.size() <= index) {
      @annotation.nowarn("msg=deprecated") // Java ArrayList interop — adding null placeholder
      val n: V = Nullable.empty[V].orNull
      _valueList.add(n)
    }
  }

  override def get(o: Any): V = {
    val index = _keySet.indexOf(o)
    if (index == -1) {
      @annotation.nowarn("msg=deprecated") // Java Map interop — returning null for missing key
      val n: V = Nullable.empty[V].orNull
      n
    } else {
      _valueList.get(index)
    }
  }

  override def put(k: K, v: V): V = {
    val index = _keySet.indexOf(k)
    if (index == -1) {
      _keySet.add(k, Nullable(v.asInstanceOf[Object]))
      @annotation.nowarn("msg=deprecated") // Java Map interop — returning null when no previous value
      val n: V = Nullable.empty[V].orNull
      n
    } else {
      val old = _valueList.get(index)
      _valueList.set(index, v)
      old
    }
  }

  def computeIfMissing(k: K, runnableValue: JFunction[? >: K, ? <: V]): V = {
    val index = _keySet.indexOf(k)
    if (index == -1) {
      val v = runnableValue.apply(k)
      _keySet.add(k, Nullable(v.asInstanceOf[Object]))
      v
    } else {
      _valueList.get(index)
    }
  }

  override def remove(o: Any): V =
    _keySet.removeHosted(o).asInstanceOf[V]

  override def putAll(map: java.util.Map[? <: K, ? <: V]): Unit = {
    val iter = map.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      put(entry.getKey, entry.getValue)
    }
  }

  def addAll(entries: java.util.Collection[? <: JMap.Entry[? <: K, ? <: V]]): Unit = {
    val iter = entries.iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      put(entry.getKey, entry.getValue)
    }
  }

  override def clear(): Unit = _keySet.clear()

  override def keySet(): OrderedSet[K] = _keySet

  override def values(): java.util.Collection[V] =
    if (!_keySet.isSparse) {
      _valueList
    } else {
      val vals = new java.util.ArrayList[V](_keySet.size())
      val iter = _keySet.indexIterator()
      while (iter.hasNext)
        vals.add(_valueList.get(iter.next()))
      vals
    }

  def getKey(index: Int): Nullable[K] =
    if (!_keySet.isValidIndex(index)) Nullable.empty
    else Nullable(_keySet.getValueList.get(index))

  def getValue(index: Int): V =
    if (!_keySet.isValidIndex(index)) {
      @annotation.nowarn("msg=deprecated") // Java interop — returning null for invalid index
      val n: V = Nullable.empty[V].orNull
      n
    } else {
      _valueList.get(index)
    }

  override def entrySet(): OrderedSet[JMap.Entry[K, V]] = {
    // create it with inHostUpdate already set so we can populate it without callbacks
    inUpdate = true
    val values = new OrderedSet[JMap.Entry[K, V]](_keySet.size(), new EntryCollectionHost())
    val iter   = entryIterator()
    while (iter.hasNext)
      values.add(iter.next())

    // release it for host update
    inUpdate = false

    values
  }

  def entries(): java.util.List[JMap.Entry[K, V]] = {
    // create it with inHostUpdate already set so we can populate it without callbacks
    val values = new java.util.ArrayList[JMap.Entry[K, V]]()
    val iter   = entryIterator()
    while (iter.hasNext)
      values.add(iter.next())
    values
  }

  def keys(): java.util.List[K] =
    // create it with inHostUpdate already set so we can populate it without callbacks
    _keySet.values()

  def valueIterator(): ReversibleIndexedIterator[V] =
    new IndexedIterator[V, V, ReversibleIterator[Int]](getIndexedValueProxy(), _keySet.indexIterator())

  def reversedValueIterator(): ReversibleIndexedIterator[V] =
    new IndexedIterator[V, V, ReversibleIterator[Int]](getIndexedValueProxy(), _keySet.reversedIndexIterator())

  def keyIterator(): ReversibleIndexedIterator[K] = _keySet.iterator()

  def reversedKeyIterator(): ReversibleIndexedIterator[K] = _keySet.reversedIterator()

  def entryIterator(): ReversibleIndexedIterator[JMap.Entry[K, V]] =
    new IndexedIterator[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterator[Int]](getIndexedEntryProxy(), _keySet.indexIterator())

  def reversedEntryIterator(): ReversibleIndexedIterator[JMap.Entry[K, V]] =
    new IndexedIterator[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterator[Int]](getIndexedEntryProxy(), _keySet.reversedIndexIterator())

  def reversedIterator(): ReversibleIndexedIterator[JMap.Entry[K, V]] = reversedEntryIterator()

  def valueIterable(): ReversibleIterable[V] =
    new IndexedIterable[V, V, ReversibleIterable[Int]](getIndexedValueProxy(), _keySet.indexIterable())

  def reversedValueIterable(): ReversibleIterable[V] =
    new IndexedIterable[V, V, ReversibleIterable[Int]](getIndexedValueProxy(), _keySet.reversedIndexIterable())

  def keyIterable(): ReversibleIterable[K] = _keySet.iterable()

  def reversedKeyIterable(): ReversibleIterable[K] = _keySet.reversedIterable()

  def entryIterable(): ReversibleIterable[JMap.Entry[K, V]] =
    new IndexedIterable[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterable[Int]](getIndexedEntryProxy(), _keySet.indexIterable())

  def reversedEntryIterable(): ReversibleIterable[JMap.Entry[K, V]] =
    new IndexedIterable[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterable[Int]](getIndexedEntryProxy(), _keySet.reversedIndexIterable())

  def reversedIterable(): ReversibleIterable[JMap.Entry[K, V]] = reversedEntryIterable()

  /*
   * Iterable
   */

  override def iterator(): ReversibleIndexedIterator[JMap.Entry[K, V]] = entryIterator()

  override def forEach(consumer: Consumer[? >: JMap.Entry[K, V]]): Unit = {
    val iter = iterator()
    while (iter.hasNext)
      consumer.accept(iter.next())
  }

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) true
    else {
      o match {
        case set: OrderedMap[?, ?] =>
          if (size() != set.size()) false
          else entrySet().equals(set.entrySet())
        case _ => false
      }
    }

  override def hashCode(): Int = {
    var result = _keySet.hashCode()
    result = 31 * result + _valueList.hashCode()
    result
  }

  private class EntryCollectionHost extends CollectionHost[JMap.Entry[K, V]] {

    override def adding(index: Int, entry: Nullable[JMap.Entry[K, V]], v: Nullable[Object]): Unit = {
      assert(v.isEmpty)
      val e = entry.get
      OrderedMap.this._keySet.add(e.getKey, Nullable(e.getValue.asInstanceOf[Object]))
    }

    override def removing(index: Int, entry: Nullable[JMap.Entry[K, V]]): Nullable[Object] = {
      OrderedMap.this._keySet.removeIndex(index)
      Nullable(entry.get.asInstanceOf[Object])
    }

    override def clearing(): Unit =
      OrderedMap.this._keySet.clear()

    override def addingNulls(index: Int): Unit =
      OrderedMap.this._keySet.addNulls(index)

    override def skipHostUpdate(): Boolean = inUpdate

    override def getIteratorModificationCount: Int =
      OrderedMap.this.getModificationCount
  }
}
