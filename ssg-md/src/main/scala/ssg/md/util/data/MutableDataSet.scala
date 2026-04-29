/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataSet.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataSet.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

class MutableDataSet(other: Nullable[DataHolder]) extends DataSet(other), MutableDataHolder {

  def this() =
    this(Nullable.empty[DataHolder])

  override def set[T](key: DataKey[T], value: T): MutableDataSet =
    setByBase(key, value)

  override def set[T](key: NullableDataKey[T], value: Nullable[T]): MutableDataSet =
    setByBase(key, value.asInstanceOf[T])

  private def setByBase[T](key: DataKeyBase[T], value: T): MutableDataSet = {
    dataSet.put(key, value.asInstanceOf[AnyRef])
    this
  }

  override def setFrom(dataSetter: MutableDataSetter): MutableDataSet = {
    dataSetter.setIn(this)
    this
  }

  override def setAll(other: DataHolder): MutableDataSet = {
    dataSet.putAll(other.getAll)
    this
  }

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.setAll(this)
    dataHolder
  }

  override def remove(key: DataKeyBase[?]): MutableDataSet = {
    dataSet.remove(key)
    this
  }

  @annotation.nowarn("msg=deprecated") // orNull needed at DataSet storage boundary
  override def getOrCompute(key: DataKeyBase[?], factory: DataValueFactory[?]): AnyRef =
    if (dataSet.containsKey(key)) {
      dataSet.get(key)
    } else {
      // Factory returns Nullable[T] (opaque type). Must unwrap to raw value
      // before storing in HashMap, otherwise NestedNone leaks into storage.
      val result = factory.apply(this)
      val value  = result.orNull.asInstanceOf[AnyRef]
      dataSet.put(key, value)
      value
    }

  override def toMutable: MutableDataSet =
    this

  override def toImmutable: DataSet =
    new DataSet(Nullable(this.asInstanceOf[DataHolder]))

  override def toDataSet: MutableDataSet =
    this

  override def clear(): MutableDataSet = {
    dataSet.clear()
    this
  }
}

object MutableDataSet {

  def merge(dataHolders: DataHolder*): MutableDataSet = {
    val ds = new MutableDataSet()
    for (dataHolder <- dataHolders)
      if (dataHolder != null) {
        ds.dataSet.putAll(dataHolder.getAll)
      }
    ds
  }
}
