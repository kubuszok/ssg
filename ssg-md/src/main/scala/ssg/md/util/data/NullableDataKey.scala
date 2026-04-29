/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/NullableDataKey.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/NullableDataKey.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

class NullableDataKey[T](name: String, dv: T, f: DataValueFactory[T]) extends DataKeyBase[T](name, dv, f) {

  /** Creates a DataKey with a computed default value dynamically.
    *
    * On construction will invoke factory with null data holder to get the default value
    *
    * @param name
    *   See [[name]].
    * @param factory
    *   data value factory for creating a new default value for the key
    */
  def this(name: String, factory: DataValueNullableFactory[T]) =
    this(name, factory.apply(Nullable.empty[DataHolder]).asInstanceOf[T], factory)

  /** Creates a DataKey with nullable data value and factory not dependent on data holder
    *
    * @param name
    *   See [[name]].
    * @param supplier
    *   data value factory for creating a new default value for the key not dependent on dataHolder
    */
  def this(name: String, supplier: () => T) =
    this(name,
         supplier(),
         new DataValueFactory[T] {
           def apply(dataHolder: DataHolder): Nullable[T] = Nullable(supplier())
         }
    )

  /** Creates a NullableDataKey with a dynamic default value taken from a value of another key.
    *
    * Does not cache the returned default value but will always delegate to another key until this key gets its own value set.
    *
    * @param name
    *   See [[name]].
    * @param defaultKey
    *   The DataKeyBase to take the default value from at time of construction.
    */
  def this(name: String, defaultKey: DataKeyBase[T]) =
    this(
      name,
      defaultKey.defaultValue,
      new DataValueFactory[T] {
        def apply(dataHolder: DataHolder): Nullable[T] = Nullable(defaultKey.get(Nullable(dataHolder)))
      }
    )

  /** Create a NullableDataKey with null default value and factory producing null values
    *
    * @param name
    *   key name
    */
  def this(name: String) =
    this(name,
         null.asInstanceOf[T],
         new DataValueFactory[T] {
           def apply(dataHolder: DataHolder): Nullable[T] = Nullable.empty[T]
         }
    )

  override def get(holder: Nullable[DataHolder]): T =
    super.get(holder)

  override def set(dataHolder: MutableDataHolder, value: T): MutableDataHolder =
    dataHolder.set(this, Nullable(value))

  override def toString: String = {
    // factory applied to null in constructor, no sense doing it again here
    val dv = defaultValue
    if (dv != null) {
      "DataKey<" + dv.asInstanceOf[AnyRef].getClass.getSimpleName + "> " + name
    } else {
      "DataKey<null> " + name
    }
  }
}
