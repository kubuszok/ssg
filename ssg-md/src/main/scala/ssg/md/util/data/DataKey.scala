/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataKey.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataKey.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

/** NOTE: Constructors have changed in a breaking way from 0.50.x and prior implementations
  *
  * Previously you could provide:
  *
  *   1. [T] defaultValue
  *   2. DataValueFactory[T]
  *   3. DataKey[T]
  *
  * Options 1. and 2. are not available separately and both have to be provided to the constructor to eliminate the need for handling null for DataHolder in the data value factory.
  *
  * Now you have the following choices:
  *
  *   1. [T] defaultValue AND DataNotNullValueFactory
  *   2. NotNullValueSupplier[T]
  *   3. DataKey[T] from which default value will be taken on construction, and values will be retrieved if no value is set for this key
  *
  * Additional changes include separating NullableDataKey out so that DataKey values cannot be null. If you need a key with null result value then use NullableDataKey which is identical to DataKey but
  * allows nulls to be used for values.
  *
  * @tparam T
  *   type of data held by the key
  */
class DataKey[T](name: String, dv: T, f: DataNotNullValueFactory[T]) extends DataKeyBase[T](name, dv, f) {

  /** Creates a DataKey with non-null data value and factory not dependent on dataHolder
    *
    * @param name
    *   See [[name]].
    * @param supplier
    *   data value factory for creating a new default value for the key not dependent on dataHolder
    */
  def this(name: String, supplier: NotNullValueSupplier[T]) =
    this(name,
         supplier.get,
         new DataNotNullValueFactory[T] {
           def apply(dataHolder: DataHolder): Nullable[T] = Nullable(supplier.get)
         }
    )

  /** Creates a DataKey with a dynamic default value taken from a value of another key.
    *
    * Does not cache the returned default value but will always delegate to another key until this key gets its own value set.
    *
    * @param name
    *   See [[name]].
    * @param defaultKey
    *   The DataKey to take the default value from at time of construction.
    */
  def this(name: String, defaultKey: DataKey[T]) =
    this(
      name,
      defaultKey.defaultValue,
      new DataNotNullValueFactory[T] {
        def apply(dataHolder: DataHolder): Nullable[T] = Nullable(defaultKey.get(Nullable(dataHolder)))
      }
    )

  def this(name: String, defaultValue: T) =
    this(
      name,
      defaultValue,
      new DataNotNullValueFactory[T] {
        private val dv:                    T           = defaultValue
        def apply(dataHolder: DataHolder): Nullable[T] = Nullable(dv)
      }
    )

  override def get(holder: Nullable[DataHolder]): T =
    super.get(holder)

  override def set(dataHolder: MutableDataHolder, value: T): MutableDataHolder =
    dataHolder.set(this, value)

  override def toString: String = {
    // factory applied to null in constructor, no sense doing it again here
    val dv = defaultValue
    "DataKey<" + dv.asInstanceOf[AnyRef].getClass.getSimpleName + "> " + name
  }
}
