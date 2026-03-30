/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataHolder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

import java.util.{ Collection, Map }

trait DataHolder extends MutableDataSetter {

  def getAll:  Map[? <: DataKeyBase[?], AnyRef]
  def getKeys: Collection[? <: DataKeyBase[?]]

  def contains(key: DataKeyBase[?]): Boolean

  /** @param key
    *   data key
    * @tparam T
    *   Type returned by key
    * @return
    *   Use key.get(dataHolder) instead
    */
  @deprecated("use key.get(dataHolder) instead", "0.50.x")
  def get[T](key: DataKey[T]): T =
    key.get(Nullable(this))

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder =
    dataHolder.setAll(this)

  /** Get key if it exists or compute using supplier
    *
    * NOTE: MutableDataHolders will compute an absent key and add it to its dataSet. DataHolders will return computed value but not change contained dataSet because they are immutable. So value will
    * be computed every time it is requested.
    *
    * @param key
    *   data key
    * @param factory
    *   factory taking this data holder and computing/providing default value
    * @return
    *   object value for the key
    */
  def getOrCompute(key: DataKeyBase[?], factory: DataValueFactory[?]): AnyRef

  def toMutable:   MutableDataHolder
  def toImmutable: DataHolder

  def toDataSet: DataSet =
    this match {
      case ds:  DataSet if !ds.isInstanceOf[MutableDataHolder] => ds
      case mdh: MutableDataHolder                              => new MutableDataSet(Nullable(mdh.asInstanceOf[DataHolder]))
      case _ => new DataSet(Nullable(this))
    }
}

object DataHolder {
  val NULL: DataHolder = new DataSet()
}
