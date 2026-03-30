/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataHolder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

trait MutableDataHolder extends DataHolder with MutableDataSetter {

  /** Get the given key, if it does not exist then use the key's factory to create a new value and put it into the collection so that the following get of the same key will find a value
    *
    * @param key
    *   data key
    * @return
    *   return stored value or newly created value
    * @deprecated
    *   use key.get(dataHolder) instead, which will do the same thing and carries nullable information for the data
    */
  @deprecated("use key.get(dataHolder) instead", "0.50.x")
  override def get[T](key: DataKey[T]): T =
    key.get(Nullable(this.asInstanceOf[DataHolder]))

  override def getOrCompute(key: DataKeyBase[?], factory: DataValueFactory[?]): AnyRef

  /** Store the given value for the key
    *
    * @tparam T
    *   data type of the data referred by the key
    * @param key
    *   data key
    * @param value
    *   value to store
    * @return
    *   mutable data holder for chained calls
    */
  def set[T](key: DataKey[T], value: T): MutableDataHolder

  /** Store the given value for the key
    *
    * @tparam T
    *   data type of the data referred by the key
    * @param key
    *   data key
    * @param value
    *   value to store
    * @return
    *   mutable data holder for chained calls
    */
  def set[T](key: NullableDataKey[T], value: Nullable[T]): MutableDataHolder

  /** Remove the stored value for the key, used to force to default or to force recompute
    *
    * @param key
    *   data key to remove
    * @return
    *   mutable data holder for chained calls
    */
  def remove(key: DataKeyBase[?]): MutableDataHolder

  /** Store the given value for the key
    *
    * @param dataSetter
    *   data setter which will set values
    * @return
    *   mutable data holder for chained calls
    */
  def setFrom(dataSetter: MutableDataSetter): MutableDataHolder

  /** Copy all values from one data holder to this data holder
    *
    * @param other
    *   data holder from which to copy all values
    * @return
    *   mutable data holder for chained calls
    */
  def setAll(other: DataHolder): MutableDataHolder

  /** Set options in given mutable data holder
    *
    * @param dataHolder
    *   data holder where to copy options from this data holder
    * @return
    *   dataHolder
    */
  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder

  /** clear all options out of the data set
    *
    * @return
    *   mutable data holder for chained calls
    */
  def clear(): MutableDataHolder
}
