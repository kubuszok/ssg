/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SettableInstance.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.util.data.{DataHolder, DataKey}

import java.util.function.Consumer
import java.{util => ju}
import scala.language.implicitConversions

/**
 * Instance based on aggregated options used for spec test settings which may contain other such settings which can be
 * accessed through other data keys directly
 *
 * For example: Rendering profile contains HTML, Parser and CSS settings. Rendering profile and its contained settings can be set by
 * spec options. In order to handle this properly rendering profile settable instance is defined with HTML, Parser and CSS extracted settable instances.
 * thus allowing setting options on contained instances directly or through the rendering profile container, while keeping the results consistent.
 *
 * @tparam T type for the setting
 */
final class SettableInstance[T](
    private val myConsumerKey: DataKey[Consumer[T]],
    private val myExtractedInstanceSetters: Nullable[ju.Collection[SettableExtractedInstance[T, ?]]]
) {

  def this(consumerKey: DataKey[Consumer[T]], extractedInstanceSetters: ju.Collection[SettableExtractedInstance[T, ?]]) = {
    this(
      consumerKey,
      if (extractedInstanceSetters.size() == 0) Nullable.empty[ju.Collection[SettableExtractedInstance[T, ?]]]
      else Nullable(extractedInstanceSetters)
    )
  }

  def this(consumerKey: DataKey[Consumer[T]]) = {
    this(consumerKey, Nullable.empty[ju.Collection[SettableExtractedInstance[T, ?]]])
  }

  def setInstanceData(instance: T, dataHolder: Nullable[DataHolder]): T = {
    dataHolder.foreach { dh =>
      if (dh.contains(myConsumerKey)) {
        myConsumerKey.get(dh).accept(instance)
      }

      myExtractedInstanceSetters.foreach { setters =>
        val iter = setters.iterator()
        while (iter.hasNext) {
          iter.next().aggregate(instance, dh)
        }
      }
    }
    instance
  }

  def aggregateActions(dataHolder: DataHolder, other: Nullable[DataHolder], overrides: Nullable[DataHolder]): DataHolder = {
    var results = dataHolder

    val hasOther = other.exists(_.contains(myConsumerKey))
    val hasOverrides = overrides.exists(_.contains(myConsumerKey))

    if (hasOther && hasOverrides) {
      // both, need to combine
      val otherSetter = myConsumerKey.get(other.get)
      val overridesSetter = myConsumerKey.get(overrides.get)
      results = results.toMutable.set(myConsumerKey, otherSetter.andThen(overridesSetter))
    }

    myExtractedInstanceSetters.foreach { setters =>
      val iter = setters.iterator()
      while (iter.hasNext) {
        results = iter.next().aggregateActions(results, other, overrides)
      }
    }

    results
  }
}
