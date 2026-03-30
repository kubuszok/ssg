/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataKeyBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

import scala.language.implicitConversions

abstract class DataKeyBase[T](
  val name:         String,
  val defaultValue: T,
  val factory:      DataValueFactory[T]
) extends MutableDataValueSetter[T] {

  /** Creates a DataKeyBase with a dynamic default value taken from a value of another key.
    *
    * Does not cache the returned default value but will always delegate to another key until this key gets its own value set.
    *
    * @param name
    *   See [[name]].
    * @param defaultKey
    *   The DataKeyBase to take the default value from at time of construction.
    */
  def this(name: String, defaultKey: DataKeyBase[T]) = {
    this(
      name,
      defaultKey.defaultValue,
      new DataValueFactory[T] {
        def apply(dataHolder: DataHolder): Nullable[T] = defaultKey.get(Nullable(dataHolder))
      }
    )
  }

  def this(name: String, defaultValue: T) = {
    this(
      name,
      defaultValue,
      new DataValueFactory[T] {
        // capture defaultValue in closure
        private val dv:                    T           = defaultValue
        def apply(dataHolder: DataHolder): Nullable[T] = Nullable(dv)
      }
    )
  }

  @annotation.nowarn("msg=deprecated") // orNull needed — factory returns Nullable opaque type
  def getDefaultValue(holder: DataHolder): T =
    factory.apply(holder).orNull.asInstanceOf[T]

  def get(holder: Nullable[DataHolder]): T =
    if (holder.isEmpty) {
      defaultValue
    } else {
      holder.get
        .getOrCompute(this,
                      new DataValueFactory[T] {
                        def apply(dataHolder: DataHolder): Nullable[T] = Nullable(getDefaultValue(dataHolder))
                      }
        )
        .asInstanceOf[T]
    }

  /** @param holder
    *   data holder
    * @return
    *   return default value if holder is null, current value in holder or compute a new value
    * @deprecated
    *   use get
    */
  @deprecated("use get", "0.50.x")
  final def getFrom(holder: Nullable[DataHolder]): T =
    get(holder)

  override def toString: String =
    if (defaultValue != null) {
      "NullableDataKey<" + defaultValue.asInstanceOf[AnyRef].getClass.getSimpleName + "> " + name
    } else {
      "NullableDataKey<unknown> " + name
    }

  /** Compare only by address. Every key instance is unique
    *
    * @param o
    *   other
    * @return
    *   true if equal
    */
  final override def equals(o: Any): Boolean =
    this eq o.asInstanceOf[AnyRef]

  final override def hashCode(): Int =
    System.identityHashCode(this)
}
