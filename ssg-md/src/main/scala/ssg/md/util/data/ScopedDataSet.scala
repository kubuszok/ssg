/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/ScopedDataSet.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

import java.util.{ Collection, HashMap, HashSet, Map }

class ScopedDataSet(val parent: Nullable[DataHolder], other: Nullable[DataHolder]) extends DataSet(other) {

  def this(parent: Nullable[DataHolder]) = {
    this(parent, Nullable.empty[DataHolder])
  }

  override def getAll: Map[? <: DataKeyBase[?], AnyRef] =
    if (parent.isDefined) {
      val all = new HashMap[DataKeyBase[?], AnyRef](parent.get.getAll)
      all.putAll(super.getAll)
      all
    } else {
      super.getAll
    }

  override def getKeys: Collection[? <: DataKeyBase[?]] =
    if (parent.isDefined) {
      val all = new HashSet[DataKeyBase[?]](parent.get.getKeys)
      all.addAll(super.getKeys)
      all
    } else {
      super.getKeys
    }

  override def toMutable: MutableDataSet = {
    val mutableDataSet = new MutableDataSet()
    mutableDataSet.dataSet.putAll(dataSet)
    if (parent.isDefined) {
      new MutableScopedDataSet(parent, Nullable(mutableDataSet.asInstanceOf[MutableDataHolder]))
    } else {
      mutableDataSet
    }
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
