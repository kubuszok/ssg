/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/OrderedMultiMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/OrderedMultiMap.java
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
import ssg.md.util.misc.{ Pair, Paired }

import java.util.{ BitSet, Map as JMap }
import java.util.function.Consumer

import scala.language.implicitConversions

class OrderedMultiMap[K, V](capacity: Int, private val host: Nullable[CollectionHost[Paired[Nullable[K], Nullable[V]]]]) extends java.util.Map[K, V] with java.lang.Iterable[JMap.Entry[K, V]] {

  def this() = this(0, Nullable.empty)

  def this(capacity: Int) = this(capacity, Nullable.empty)

  def this(host: CollectionHost[Paired[Nullable[K], Nullable[V]]]) = this(0, Nullable(host))

  private var isInKeyUpdate:   Boolean                             = false
  private var isInValueUpdate: Boolean                             = false
  private var indexedProxy:    Nullable[Indexed[JMap.Entry[K, V]]] = Nullable.empty

  private val _valueSet: OrderedSet[V] = new OrderedSet[V](
    capacity,
    new CollectionHost[V] {
      override def adding(index: Int, v: Nullable[V], k: Nullable[Object]): Unit =
        addingValue(index, v, k)

      override def removing(index: Int, v: Nullable[V]): Nullable[Object] =
        removingValue(index, v)

      override def clearing(): Unit =
        OrderedMultiMap.this.clear()

      override def addingNulls(index: Int): Unit =
        addingNullValue(index)

      override def skipHostUpdate(): Boolean = isInKeyUpdate

      override def getIteratorModificationCount: Int =
        OrderedMultiMap.this.getModificationCount
    }
  )

  private val _keySet: OrderedSet[K] = new OrderedSet[K](
    capacity,
    new CollectionHost[K] {
      override def adding(index: Int, k: Nullable[K], v: Nullable[Object]): Unit =
        addingKey(index, k, v)

      override def removing(index: Int, k: Nullable[K]): Nullable[Object] =
        removingKey(index, k)

      override def clearing(): Unit =
        OrderedMultiMap.this.clear()

      override def addingNulls(index: Int): Unit =
        addingNullKey(index)

      override def skipHostUpdate(): Boolean = isInValueUpdate

      override def getIteratorModificationCount: Int =
        OrderedMultiMap.this.getModificationCount
    }
  )

  def getIndexedProxy(): Indexed[JMap.Entry[K, V]] =
    if (indexedProxy.isDefined) indexedProxy.get
    else {
      val proxy = new Indexed[JMap.Entry[K, V]] {
        override def get(index: Int): JMap.Entry[K, V] =
          OrderedMultiMap.this.getEntry(index)

        override def set(index: Int, item: JMap.Entry[K, V]): Unit =
          throw new UnsupportedOperationException()

        override def removeAt(index: Int): Unit =
          OrderedMultiMap.this.removeEntryIndex(index)

        override def size: Int = OrderedMultiMap.this.size()

        override def modificationCount: Int =
          OrderedMultiMap.this.getModificationCount
      }
      indexedProxy = Nullable(proxy)
      proxy
    }

  private[collection] def getEntry(index: Int): JMap.Entry[K, V] = {
    @annotation.nowarn("msg=deprecated") // Java interop — MapEntry accepts nullable values
    val k = _keySet.getValueOrNull(index).orNull
    @annotation.nowarn("msg=deprecated") // Java interop — MapEntry accepts nullable values
    val v = _valueSet.getValueOrNull(index).orNull
    new MapEntry[K, V](k, v)
  }

  def getModificationCount: Int =
    (_keySet.getModificationCount.toLong + _valueSet.getModificationCount.toLong).toInt

  private def addingKey(index: Int, k: Nullable[K], v: Nullable[Object]): Unit = {
    assert(!isInValueUpdate)

    isInValueUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.adding(index, Nullable(new Pair(k, v.map(_.asInstanceOf[V]))), Nullable.empty)
    }
    if (v.isEmpty) _valueSet.addNulls(index)
    else _valueSet.add(v.get.asInstanceOf[V])
    isInValueUpdate = false
  }

  private def addingNullKey(index: Int): Unit = {
    assert(!isInValueUpdate)

    isInValueUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.addingNulls(index)
    }
    while (valueSet().size() <= index) {
      @annotation.nowarn("msg=deprecated") // Java interop — adding null to OrderedSet
      val n: V = Nullable.empty[V].orNull
      _valueSet.add(n)
    }
    isInValueUpdate = false
  }

  private def removingKey(index: Int, k: Nullable[K]): Object = {
    assert(!isInValueUpdate)

    isInValueUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.removing(index, Nullable(new Pair(k, Nullable.empty[V])))
    }
    val r = _valueSet.removeIndexHosted(index)
    isInValueUpdate = false
    r
  }

  private def addingValue(index: Int, v: Nullable[V], k: Nullable[Object]): Unit = {
    assert(!isInKeyUpdate)

    isInKeyUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.adding(index, Nullable(new Pair(k.map(_.asInstanceOf[K]), v)), Nullable.empty)
    }
    if (k.isEmpty) _keySet.addNulls(index)
    else _keySet.add(k.get.asInstanceOf[K])
    isInKeyUpdate = false
  }

  private def addingNullValue(index: Int): Unit = {
    assert(!isInKeyUpdate)

    isInKeyUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.addingNulls(index)
    }
    while (_keySet.size() <= index) {
      @annotation.nowarn("msg=deprecated") // Java interop — adding null to OrderedSet
      val n: K = Nullable.empty[K].orNull
      _keySet.add(n)
    }
    isInKeyUpdate = false
  }

  private def removingValue(index: Int, v: Nullable[V]): Object = {
    assert(!isInKeyUpdate)

    isInKeyUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.removing(index, Nullable(new Pair(Nullable.empty[K], v)))
    }
    val r = _keySet.removeIndexHosted(index)
    isInKeyUpdate = false
    r
  }

  override def size(): Int = _keySet.size()

  override def isEmpty: Boolean = _keySet.isEmpty

  override def containsKey(o: Any): Boolean = _keySet.contains(o)

  override def containsValue(o: Any): Boolean = {
    val index = _valueSet.indexOf(o)
    _keySet.isValidIndex(index)
  }

  override def get(o: Any): V = getKeyValue(o)

  def getKeyValue(o: Any): V = {
    val index = _keySet.indexOf(o)
    if (index == -1) {
      @annotation.nowarn("msg=deprecated") // Java Map interop — returning null for missing key
      val n: V = Nullable.empty[V].orNull
      n
    } else {
      _valueSet.getValue(index)
    }
  }

  def getValueKey(o: Any): K = {
    val index = _valueSet.indexOf(o)
    if (index == -1) {
      @annotation.nowarn("msg=deprecated") // Java Map interop — returning null for missing value
      val n: K = Nullable.empty[K].orNull
      n
    } else {
      _keySet.getValue(index)
    }
  }

  override def put(k: K, v: V): V = putKeyValue(k, v)

  def addNullEntry(index: Int): Unit = {
    isInKeyUpdate = true
    isInValueUpdate = true

    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.addingNulls(index)
    }
    _keySet.addNulls(index)
    _valueSet.addNulls(index)

    isInValueUpdate = false
    isInKeyUpdate = false
  }

  def putEntry(e: JMap.Entry[K, V]): Boolean = addKeyValue(e.getKey, e.getValue)

  def putKeyValueEntry(e: JMap.Entry[K, V]): Boolean = addKeyValue(e.getKey, e.getValue)

  def putValueKeyEntry(e: JMap.Entry[V, K]): Boolean = addKeyValue(e.getValue, e.getKey)

  def putKeyValuePair(e: Paired[Nullable[K], Nullable[V]]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — unwrapping Nullable for internal use
    val k = e.first.orNull
    @annotation.nowarn("msg=deprecated") // Java interop — unwrapping Nullable for internal use
    val v = e.second.orNull
    addKeyValue(k, v)
  }

  def putValueKeyPair(e: Paired[Nullable[V], Nullable[K]]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — unwrapping Nullable for internal use
    val k = e.second.orNull
    @annotation.nowarn("msg=deprecated") // Java interop — unwrapping Nullable for internal use
    val v = e.first.orNull
    addKeyValue(k, v)
  }

  def putKeyValue(k: K, v: V): V =
    if (!addKeyValue(k, v)) v
    else {
      @annotation.nowarn("msg=deprecated") // Java Map interop — returning null when value was added
      val n: V = Nullable.empty[V].orNull
      n
    }

  def putValueKey(v: V, k: K): K =
    if (!addKeyValue(k, v)) k
    else {
      @annotation.nowarn("msg=deprecated") // Java Map interop — returning null when key was added
      val n: K = Nullable.empty[K].orNull
      n
    }

  private def addKeyValue(k: K, v: V): Boolean = {
    val keyIndex   = _keySet.indexOf(k)
    val valueIndex = _valueSet.indexOf(v)

    if (keyIndex == -1 && valueIndex == -1) {
      // neither one exists, we add both
      isInKeyUpdate = true
      isInValueUpdate = true
      if (host.isDefined && !host.get.skipHostUpdate()) {
        host.get.adding(_keySet.getValueList.size(), Nullable(new Pair(Nullable(k), Nullable(v))), Nullable.empty)
      }

      if (k == null) _keySet.addNull() // Java interop — null key
      else _keySet.add(k, Nullable(v.asInstanceOf[Object]))

      if (k == null) _valueSet.addNull() // Java interop — null signals addNull for value too
      else _valueSet.add(v, Nullable(k.asInstanceOf[Object]))

      isInValueUpdate = false
      isInKeyUpdate = false

      true
    } else if (keyIndex == -1) {
      isInKeyUpdate = true
      isInValueUpdate = true
      if (host.isDefined && !host.get.skipHostUpdate()) {
        host.get.adding(valueIndex, Nullable(new Pair(Nullable(k), Nullable(v))), Nullable.empty)
      }

      if (k == null) _keySet.removeIndex(valueIndex) // Java interop — null key means remove
      else _keySet.setValueAt(valueIndex, Nullable(k), Nullable(v.asInstanceOf[Object]))

      isInValueUpdate = false
      isInKeyUpdate = false
      true
    } else if (valueIndex == -1) {
      isInKeyUpdate = true
      isInValueUpdate = true
      if (host.isDefined && !host.get.skipHostUpdate()) {
        host.get.adding(keyIndex, Nullable(new Pair(Nullable(k), Nullable(v))), Nullable.empty)
      }

      if (k == null) _valueSet.removeIndex(valueIndex) // Java interop — null key means remove
      else _valueSet.setValueAt(keyIndex, Nullable(v), Nullable(k.asInstanceOf[Object]))

      isInValueUpdate = false
      true
    } else {
      if (valueIndex != keyIndex) {
        throw new IllegalStateException("keySet[" + keyIndex + "]=" + k + " and valueSet[" + valueIndex + "]=" + v + " are out of sync")
      }

      false
    }
  }

  override def remove(o: Any): V = removeKey(o)

  def removeEntry(e: JMap.Entry[K, V]): Nullable[JMap.Entry[K, V]] = {
    val b = removeEntryIndex(-1, Nullable(e.getKey), Nullable(e.getValue))
    if (b) Nullable(e) else Nullable.empty
  }

  private[collection] def removeEntryIndex(index: Int): Boolean =
    removeEntryIndex(index, _keySet.getValueOrNull(index), _valueSet.getValueOrNull(index))

  private def removeEntryIndex(index: Int, k: Nullable[K], v: Nullable[V]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — indexOf expects raw value
    val kRaw = k.orNull
    @annotation.nowarn("msg=deprecated") // Java interop — indexOf expects raw value
    val vRaw       = v.orNull
    val keyIndex   = _keySet.indexOf(kRaw)
    val valueIndex = _valueSet.indexOf(vRaw)

    if (keyIndex != valueIndex) {
      throw new IllegalStateException("keySet[" + keyIndex + "]=" + kRaw + " and valueSet[" + valueIndex + "]=" + vRaw + " are out of sync")
    }

    if (index != -1 && keyIndex != index) {
      throw new IllegalStateException(
        "removeEntryIndex " + index + " does not match keySet[" + keyIndex + "]=" + kRaw + " and valueSet[" + valueIndex + "]=" + vRaw + " are out of sync"
      )
    }

    if (keyIndex != -1) {
      isInKeyUpdate = true
      isInValueUpdate = true
      if (host.isDefined && !host.get.skipHostUpdate()) {
        host.get.removing(keyIndex, Nullable(new Pair(k, v)))
      }
      _keySet.removeHosted(kRaw)
      _valueSet.removeHosted(vRaw)
      isInValueUpdate = false
      isInKeyUpdate = false
      true
    } else {
      false
    }
  }

  def removeKey(o: Any): V = {
    isInKeyUpdate = true
    if (host.isDefined && !host.get.skipHostUpdate()) {
      val index = _keySet.indexOf(o)
      if (index != -1) {
        val v: Nullable[V] = if (_valueSet.isValidIndex(index)) Nullable(_valueSet.getValue(index)) else Nullable.empty
        host.get.removing(index, Nullable(new Pair(Nullable(o.asInstanceOf[K]), v)))
      }
    }
    val r = _keySet.removeHosted(o).asInstanceOf[V]
    isInKeyUpdate = false
    r
  }

  def removeValue(o: Any): K = {
    isInValueUpdate = true
    val index = _valueSet.indexOf(o)
    if (host.isDefined && !host.get.skipHostUpdate()) {
      if (index != -1) {
        val k: Nullable[K] = if (_keySet.isValidIndex(index)) Nullable(_keySet.getValue(index)) else Nullable.empty
        host.get.removing(index, Nullable(new Pair(k, Nullable(o.asInstanceOf[V]))))
      }
    }
    val r = _valueSet.removeHosted(o).asInstanceOf[K]
    isInValueUpdate = false
    r
  }

  override def putAll(map: java.util.Map[? <: K, ? <: V]): Unit = putAllKeyValues(map)

  def putAllKeyValues(map: java.util.Map[? <: K, ? <: V]): Unit = {
    val iter = map.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      put(entry.getKey, entry.getValue)
    }
  }

  def putAllValueKeys(map: java.util.Map[? <: V, ? <: K]): Unit = {
    val iter = map.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      putValueKey(entry.getKey, entry.getValue)
    }
  }

  override def clear(): Unit = {
    isInValueUpdate = true
    isInKeyUpdate = true

    if (host.isDefined && !host.get.skipHostUpdate()) {
      host.get.clearing()
    }
    _keySet.clear()
    _valueSet.clear()

    isInKeyUpdate = false
    isInValueUpdate = false
  }

  override def keySet(): OrderedSet[K] = _keySet

  override def values(): java.util.Collection[V] =
    if (!_keySet.isSparse) {
      _valueSet
    } else {
      val vals = new java.util.ArrayList[V](_keySet.size())
      vals.addAll(_valueSet)
      vals
    }

  def valueSet(): OrderedSet[V] = _valueSet

  def keys(): java.util.Collection[K] =
    if (!_keySet.isSparse) {
      _keySet
    } else {
      val vals = new java.util.ArrayList[K](_valueSet.size())
      vals.addAll(_keySet)
      vals
    }

  def getKey(index: Int): K =
    if (!_keySet.isValidIndex(index)) {
      @annotation.nowarn("msg=deprecated") // Java interop — returning null for invalid index
      val n: K = Nullable.empty[K].orNull
      n
    } else {
      _keySet.getValueList.get(index)
    }

  def getValue(index: Int): V =
    if (!_valueSet.isValidIndex(index)) {
      @annotation.nowarn("msg=deprecated") // Java interop — returning null for invalid index
      val n: V = Nullable.empty[V].orNull
      n
    } else {
      _valueSet.getValue(index)
    }

  override def entrySet(): OrderedSet[JMap.Entry[K, V]] = keyValueEntrySet()

  def valueIterator(): ReversibleIndexedIterator[V] = _valueSet.iterator()

  def reversedValueIterator(): ReversibleIndexedIterator[V] = _valueSet.reversedIterator()

  def valueIterable(): ReversibleIterable[V] =
    new IndexedIterable[V, V, ReversibleIterable[Int]](_valueSet.getIndexedProxy(), _valueSet.indexIterable())

  def reversedValueIterable(): ReversibleIterable[V] =
    new IndexedIterable[V, V, ReversibleIterable[Int]](_valueSet.getIndexedProxy(), _valueSet.reversedIndexIterable())

  def keyIterator(): ReversibleIndexedIterator[K] = keySet().iterator()

  def reversedKeyIterator(): ReversibleIndexedIterator[K] = keySet().reversedIterator()

  def keyIterable(): ReversibleIterable[K] =
    new IndexedIterable[K, K, ReversibleIterable[Int]](_keySet.getIndexedProxy(), _keySet.indexIterable())

  def reversedKeyIterable(): ReversibleIterable[K] =
    new IndexedIterable[K, K, ReversibleIterable[Int]](_keySet.getIndexedProxy(), _keySet.reversedIndexIterable())

  def entrySetIterator(): ReversibleIndexedIterator[JMap.Entry[K, V]] = {
    val bitSet = getKeyValueUnionSet
    new IndexedIterator[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterator[Int]](getIndexedProxy(), new BitSetIterator(bitSet))
  }

  def reversedEntrySetIterator(): ReversibleIndexedIterator[JMap.Entry[K, V]] = {
    val bitSet = getKeyValueUnionSet
    new IndexedIterator[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterator[Int]](getIndexedProxy(), new BitSetIterator(bitSet, true))
  }

  def entrySetIterable(): ReversibleIterable[JMap.Entry[K, V]] = {
    val bitSet = getKeyValueUnionSet
    new IndexedIterable[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterable[Int]](getIndexedProxy(), new BitSetIterable(bitSet))
  }

  def reversedEntrySetIterable(): ReversibleIterable[JMap.Entry[K, V]] = {
    val bitSet = getKeyValueUnionSet
    new IndexedIterable[JMap.Entry[K, V], JMap.Entry[K, V], ReversibleIterable[Int]](getIndexedProxy(), new BitSetIterable(bitSet))
  }

  private def getKeyValueUnionSet: BitSet = {
    val bitSet = new BitSet(_keySet.size())
    bitSet.or(_keySet.getValidIndices)
    bitSet.or(_valueSet.getValidIndices)
    bitSet
  }

  // Available for future use
  @annotation.nowarn("msg=unused private member")
  private def getKeyValueIntersectionSet: BitSet = {
    val bitSet = new BitSet(_keySet.size())
    bitSet.or(_keySet.getValidIndices)
    bitSet.and(_valueSet.getValidIndices)
    bitSet
  }

  /*
   * Iterable
   */

  override def iterator(): java.util.Iterator[JMap.Entry[K, V]] = entrySetIterator()

  override def forEach(consumer: Consumer[? >: JMap.Entry[K, V]]): Unit = {
    val iter = entrySetIterator()
    while (iter.hasNext)
      consumer.accept(iter.next())
  }

  def keyValueEntrySet(): OrderedSet[JMap.Entry[K, V]] = {
    // create it with inHostUpdate already set so we can populate it without callbacks
    isInValueUpdate = true
    isInKeyUpdate = true

    val values = new OrderedSet[JMap.Entry[K, V]](
      _keySet.size(),
      new CollectionHost[JMap.Entry[K, V]] {
        override def adding(index: Int, entry: Nullable[JMap.Entry[K, V]], v: Nullable[Object]): Unit = {
          assert(v.isEmpty)
          val e = entry.get
          OrderedMultiMap.this.putKeyValue(e.getKey, e.getValue)
        }

        override def removing(index: Int, entry: Nullable[JMap.Entry[K, V]]): Nullable[Object] = {
          val e = entry.get
          val b = OrderedMultiMap.this.removeEntryIndex(index, Nullable(e.getKey), Nullable(e.getValue))
          if (b) Nullable(e.asInstanceOf[Object]) else Nullable.empty
        }

        override def clearing(): Unit =
          OrderedMultiMap.this.clear()

        override def addingNulls(index: Int): Unit =
          OrderedMultiMap.this.addNullEntry(index)

        override def skipHostUpdate(): Boolean = isInKeyUpdate || isInValueUpdate

        override def getIteratorModificationCount: Int =
          OrderedMultiMap.this.getModificationCount
      }
    )

    val iter = entrySetIterator()
    while (iter.hasNext)
      values.add(iter.next())

    // release it for host update
    isInValueUpdate = false
    isInKeyUpdate = false

    values
  }

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) true
    else {
      o match {
        case set: OrderedMultiMap[?, ?] =>
          if (size() != set.size()) false
          else entrySet().equals(set.entrySet())
        case _ => false
      }
    }

  override def hashCode(): Int = {
    var result = _keySet.hashCode()
    result = 31 * result + _valueSet.hashCode()
    result
  }
}
