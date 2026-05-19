/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataHolder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataHolder.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

trait MutableDataHolder extends DataHolder, MutableDataSetter {

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
