/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableScopedDataSet.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableScopedDataSet.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

import java.util
import java.util.{ Collection, HashMap, Map }

class MutableScopedDataSet(
  val parent: Nullable[DataHolder],
  other:      Nullable[MutableDataHolder]
) extends MutableDataSet(other.map(_.asInstanceOf[DataHolder])) {

  def this(parent: Nullable[DataHolder]) =
    this(parent, Nullable.empty[MutableDataHolder])

  override def getAll: Map[? <: DataKeyBase[?], AnyRef] =
    if (parent.isDefined) {
      val all = new HashMap[DataKeyBase[?], AnyRef](super.getAll)
      val it  = parent.get.getKeys.iterator()
      while (it.hasNext) {
        val key = it.next()
        if (!contains(key)) {
          all.put(key, key.get(parent).asInstanceOf[AnyRef])
        }
      }
      all
    } else {
      super.getAll
    }

  override def getKeys: Collection[? <: DataKeyBase[?]] =
    if (parent.isDefined) {
      val all = new util.ArrayList[DataKeyBase[?]](super.getKeys)
      val it  = parent.get.getKeys.iterator()
      while (it.hasNext) {
        val key = it.next()
        if (!contains(key)) {
          all.add(key)
        }
      }
      all
    } else {
      super.getKeys
    }

  override def contains(key: DataKeyBase[?]): Boolean =
    super.contains(key) || (parent.isDefined && parent.get.contains(key))

  override def getOrCompute(key: DataKeyBase[?], factory: DataValueFactory[?]): AnyRef =
    if (parent.isEmpty || super.contains(key) || !parent.get.contains(key)) {
      super.getOrCompute(key, factory)
    } else {
      parent.get.getOrCompute(key, factory)
    }
}
