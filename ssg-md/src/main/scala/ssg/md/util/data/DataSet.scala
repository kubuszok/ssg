/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataSet.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataSet.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

import java.util
import java.util.{ Collection, HashMap, Map }
import scala.util.boundary
import scala.util.boundary.break

class DataSet(other: Nullable[DataHolder]) extends DataHolder {

  def this() =
    this(Nullable.empty[DataHolder])

  protected[data] val dataSet: HashMap[DataKeyBase[?], AnyRef] =
    if (other.isEmpty) {
      new HashMap[DataKeyBase[?], AnyRef]()
    } else {
      new HashMap[DataKeyBase[?], AnyRef](other.get.getAll)
    }

  override def getAll: Map[? <: DataKeyBase[?], AnyRef] =
    dataSet

  override def getKeys: Collection[? <: DataKeyBase[?]] =
    dataSet.keySet()

  override def contains(key: DataKeyBase[?]): Boolean =
    dataSet.containsKey(key)

  @annotation.nowarn("msg=deprecated") // orNull needed at DataSet storage boundary
  override def getOrCompute(key: DataKeyBase[?], factory: DataValueFactory[?]): AnyRef =
    if (dataSet.containsKey(key)) {
      dataSet.get(key)
    } else {
      // Factory returns Nullable[T] (opaque type). Must unwrap to raw value
      // before returning as AnyRef, otherwise NestedNone leaks out.
      val result = factory.apply(this)
      result.orNull.asInstanceOf[AnyRef]
    }

  /** Apply aggregate action to data and return result
    *
    * @return
    *   resulting data holder
    */
  def aggregate(): DataHolder = {
    var combined: DataHolder = this
    val it = DataSet.ourDataKeyAggregators.iterator()
    while (it.hasNext)
      combined = it.next().aggregate(combined)
    combined
  }

  override def toMutable: MutableDataSet =
    new MutableDataSet(Nullable(this.asInstanceOf[DataHolder]))

  override def toImmutable: DataSet =
    this

  override def toDataSet: DataSet =
    this

  override def toString: String =
    "DataSet{" + "dataSet=" + dataSet + "}"

  override def equals(o: Any): Boolean =
    o match {
      case _ if this eq o.asInstanceOf[AnyRef] => true
      case that: DataSet => dataSet.equals(that.dataSet)
      case _ => false
    }

  override def hashCode(): Int =
    dataSet.hashCode()
}

object DataSet {

  private val ourDataKeyAggregators: util.ArrayList[DataKeyAggregator] =
    new util.ArrayList[DataKeyAggregator]()

  /** aggregate actions of two data sets, actions not applied
    *
    * @param other
    *   first set of options
    * @param overrides
    *   overrides on options
    * @return
    *   resulting options where aggregate action keys were aggregated but not applied
    */
  def aggregateActions(other: DataHolder, overrides: DataHolder): DataHolder = {
    var combined = new DataSet(Nullable(other))
    combined.dataSet.putAll(overrides.getAll)

    val it = ourDataKeyAggregators.iterator()
    while (it.hasNext)
      combined = it.next().aggregateActions(combined, other, overrides).toDataSet
    combined
  }

  /** Aggregate two sets of options by aggregating their aggregate action keys then applying those actions on the resulting collection
    *
    * @param other
    *   options with aggregate actions already applied, no aggregate action keys are expected or checked
    * @param overrides
    *   overrides which may contain aggregate actions
    * @return
    *   resulting options with aggregate actions applied and removed from set
    */
  def aggregate(other: Nullable[DataHolder], overrides: Nullable[DataHolder]): DataHolder =
    if (other.isEmpty && overrides.isEmpty) {
      new DataSet()
    } else if (overrides.isEmpty) {
      other.get
    } else if (other.isEmpty) {
      overrides.get.toDataSet.aggregate().toImmutable
    } else {
      aggregateActions(other.get, overrides.get).toDataSet.aggregate().toImmutable
    }

  def merge(dataHolders: DataHolder*): DataSet = {
    val ds = new DataSet()
    for (dataHolder <- dataHolders)
      ds.dataSet.putAll(dataHolder.getAll)
    ds
  }

  def registerDataKeyAggregator(keyAggregator: DataKeyAggregator): Unit = {
    if (isAggregatorRegistered(keyAggregator)) {
      throw new IllegalStateException("Aggregator " + keyAggregator + " is already registered")
    }

    // find where in the list it should go so that all combiners
    boundary {
      var i = 0
      while (i < ourDataKeyAggregators.size()) {
        val aggregator = ourDataKeyAggregators.get(i)

        if (invokeSetContains(aggregator.invokeAfterSet(), keyAggregator)) {
          // this one needs to be invoked before
          if (invokeSetContains(keyAggregator.invokeAfterSet(), aggregator)) {
            throw new IllegalStateException(
              "Circular invokeAfter dependencies for " + keyAggregator + " and " + aggregator
            )
          }

          // add before this one
          ourDataKeyAggregators.add(i, keyAggregator)
          break(())
        }
        i += 1
      }

      // add at the end
      ourDataKeyAggregators.add(keyAggregator)
    }
  }

  private[data] def isAggregatorRegistered(keyAggregator: DataKeyAggregator): Boolean = {
    val it = ourDataKeyAggregators.iterator()
    boundary {
      while (it.hasNext)
        if (it.next().getClass == keyAggregator.getClass) {
          break(true)
        }
      false
    }
  }

  private[data] def invokeSetContains(
    invokeSet:  Nullable[Set[Class[?]]],
    aggregator: DataKeyAggregator
  ): Boolean =
    if (invokeSet.isEmpty) {
      false
    } else {
      invokeSet.get.contains(aggregator.getClass)
    }
}
